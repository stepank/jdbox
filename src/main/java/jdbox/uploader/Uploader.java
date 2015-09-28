package jdbox.uploader;

import com.google.inject.Inject;
import jdbox.models.fileids.FileId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.*;

public class Uploader {

    public static final String uploadFailureNotificationFileId = "upload failure notification file id";

    private static final Logger logger = LoggerFactory.getLogger(Uploader.class);

    private final Observer<UploadFailureEvent> uploadFailureEvent;
    private final Observer<FileEtagUpdateEvent> fileEtagUpdateEvent;
    private final Map<FileId, Queue> queues = new HashMap<>();
    private final List<Future> futures = new LinkedList<>();

    private volatile UploadStatus uploadStatus;
    private volatile ExecutorService executor;

    @Inject
    public Uploader(
            Observer<UploadFailureEvent> uploadFailureEvent, Observer<FileEtagUpdateEvent> fileEtagUpdateEvent) {
        this.uploadFailureEvent = uploadFailureEvent;
        this.fileEtagUpdateEvent = fileEtagUpdateEvent;
    }

    public UploadStatus getCurrentStatus() {
        return uploadStatus;
    }

    public synchronized int getQueueCount() {
        return queues.size();
    }

    public synchronized boolean fileIsQueued(FileId fileId) {
        return queues.get(fileId) != null;
    }

    public void init() {
        executor = new ThreadPoolExecutor(4, 4, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());
    }

    public void tearDown() throws InterruptedException {

        if (executor != null) {
            executor.shutdown();
            executor.shutdown();
            executor.awaitTermination(5, TimeUnit.SECONDS);
            executor = null;
        }
    }

    public synchronized void reset() {
        uploadStatus = null;
        queues.clear();
        futures.clear();
    }

    /**
     * TODO Strictly speaking, when a task to delete a directory is submitted, this task must depend on ALL tasks
     * related to files in this directory. However, since deletion just moves files/directories to trash, this will
     * work. Still, I should find a way to fix this.
     */
    public synchronized void submit(Task task) {

        FileId fileId = task.getFileId();
        String etag = task.getEtag();

        Queue queue = queues.get(fileId);

        if (queue == null) {
            queue = new Queue(etag);
            queues.put(fileId, queue);
        }

        final Item item = queue.append(task);

        if (task.getDependsOn() != null) {
            Queue dependency = queues.get(task.getDependsOn());
            if (dependency != null && dependency.getHead().getTask().blocksDependentTasks())
                item.addDependency(dependency.getHead());
        }

        if (item.getDependencies().size() == 0)
            trySubmitToExecutor(item);

        if (isBroken())
            updateStatus(uploadStatus.exception);
    }

    public void waitUntilIsDone() throws InterruptedException {
        waitUntilIsDone(false);
    }

    public void waitUntilIsDoneOrBroken() throws InterruptedException {
        waitUntilIsDone(true);
    }

    private boolean isBroken() {
        return uploadStatus != null;
    }

    private void updateStatus(Exception e) {
        uploadStatus = new UploadStatus(e);
        uploadFailureEvent.onNext(new UploadFailureEvent(uploadStatus));
    }

    private void trySubmitToExecutor(Item item) {

        if (isBroken())
            return;

        logger.debug("submitting {}", item.getTask());
        futures.add(executor.submit(new TaskRunner(item)));
    }

    private void waitUntilIsDone(boolean canBeBroken) throws InterruptedException {

        if (futures.size() == 0)
            return;

        List<Future> futures;

        do {

            synchronized (this) {

                if (!canBeBroken && isBroken())
                    throw new AssertionError("uploader is broken even though it is not allowed");

                futures = new LinkedList<>(this.futures);
                this.futures.clear();
            }

            for (Future future : futures) {
                try {
                    future.get();
                } catch (ExecutionException e) {
                    throw new AssertionError("an unexpected error occurred while waiting for a task to finish", e);
                }
            }

        } while (futures.size() > 0);
    }

    public class UploadStatus {

        public final Date date = new Date();

        private final Exception exception;

        private volatile String serialized;

        private UploadStatus(Exception exception) {
            this.exception = exception;
        }

        public String asString() {

            if (serialized == null) {
                synchronized (Uploader.this) {
                    if (serialized == null)
                        serialized = serialize();
                }
            }

            return serialized;
        }

        private String serialize() {

            StringBuilder sb = new StringBuilder();

            //                                                                                        | 79 chars
            sb.append("Upload is broken due to an error occured while uploading changes to the cloud.\n\n");
            sb.append("Back up all the data that you recently added to or modified in JdBox and then\n");
            sb.append("delete this file to discard the local state and start from the state in the\n");
            sb.append("cloud. Until you remove this file, all local modifications will be accumulated,\n");
            sb.append("to allow you to back up your data safely, but JdBox will never attempt to\n");
            sb.append("upload these changes to the cloud.\n\n");

            sb.append("The following changes have been accumulated:\n\n");

            for (Queue queue : queues.values()) {
                Item item = queue.getHead();
                do {
                    sb.append("    ").append(item.getTask().getLabel()).append("\n");
                } while ((item = item.getNext()) != null);
                sb.append("\n");
            }

            sb.append("The error that originally occured:\n\n    ");

            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            exception.printStackTrace(pw);
            sb.append(sw.toString()).append("\n");

            return sb.toString();
        }
    }

    private class TaskRunner implements Runnable {

        private final Item item;

        private TaskRunner(Item item) {
            this.item = item;
        }

        @Override
        public void run() {

            try {

                logger.debug("starting {}", item.getTask());

                Queue queue = item.getQueue();

                String etag = item.getTask().run(queue.getEtag());

                if (etag == null)
                    throw new AssertionError("etag must not be null");

                queue.setEtag(etag);

                logger.debug("completed {}", item.getTask());

                synchronized (Uploader.this) {

                    if (item.getQueue().removeHead()) {
                        FileId fileId = item.getTask().getFileId();
                        queues.remove(fileId);
                        fileEtagUpdateEvent.onNext(new FileEtagUpdateEvent(fileId, etag));
                    }

                    for (Item dependent : new HashSet<>(item.getDependents())) {
                        if (dependent.removeDependency(item))
                            trySubmitToExecutor(dependent);
                    }
                }

            } catch (Exception e) {

                logger.error("an error occured while executing {}", item.getTask(), e);

                synchronized (Uploader.this) {
                    updateStatus(e);
                }
            }
        }
    }
}

/**
 * This class is not thread safe, external synchronization is required.
 */
class Queue {

    private Item head;
    private Item tail;

    private String etag;

    public Queue(String etag) {
        this.etag = etag;
    }

    public String getEtag() {
        return etag;
    }

    public void setEtag(String etag) {
        this.etag = etag;
    }

    public Item getHead() {
        return head;
    }

    public Item append(Task task) {

        Item item = new Item(this, task);

        if (head == null) {
            this.head = item;
            this.tail = item;
        } else {
            tail.attachNext(item);
            tail = item;
        }

        return item;
    }

    public boolean removeHead() {
        if (head == tail)
            return true;
        head = head.getNext();
        return false;
    }
}

class Item {

    private final Queue queue;
    private final Task task;
    private final Set<Item> dependencies = new HashSet<>();
    private final Set<Item> dependents = new HashSet<>();

    private Item next;

    public Item(Queue queue, Task task) {
        this.queue = queue;
        this.task = task;
    }

    public Queue getQueue() {
        return queue;
    }

    public Task getTask() {
        return task;
    }

    public Set<Item> getDependencies() {
        return Collections.unmodifiableSet(dependencies);
    }

    public Set<Item> getDependents() {
        return Collections.unmodifiableSet(dependents);
    }

    public Item getNext() {
        return next;
    }

    public void attachNext(Item item) {
        item.addDependency(this);
        this.next = item;
    }

    public void addDependency(Item item) {
        dependencies.add(item);
        item.dependents.add(this);
    }

    public boolean removeDependency(Item item) {
        dependencies.remove(item);
        item.dependents.remove(this);
        return dependencies.size() == 0;
    }
}
