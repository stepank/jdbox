package jdbox.uploader;

import com.google.inject.Inject;
import jdbox.models.fileids.FileId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observer;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class Uploader {

    public static final String uploadFailureNotificationFileId = "upload failure notification file id";

    private static final Logger logger = LoggerFactory.getLogger(Uploader.class);

    private final Observer<UploadFailureEvent> uploadFailureEvent;
    private final ExecutorService executor;
    private final Map<FileId, Queue> queues = new HashMap<>();
    private final List<Future> futures = new LinkedList<>();

    private volatile UploadStatus uploadStatus;

    @Inject
    public Uploader(Observer<UploadFailureEvent> uploadFailureEvent, ExecutorService executor) {
        this.uploadFailureEvent = uploadFailureEvent;
        this.executor = executor;
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

        if (fileId == null)
            throw new NullPointerException();

        Queue queue = queues.get(task.getFileId());

        if (queue == null) {
            queue = new Queue();
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
    }

    public void waitUntilIsDone() throws Exception {
        waitUntilIsDone(true);
    }

    public void waitUntilIsDoneOrBroken() throws Exception {
        waitUntilIsDone(false);
    }

    private boolean isBroken() {
        return uploadStatus != null;
    }

    private void waitUntilIsDone(boolean throwWhenIsBroken) throws Exception {

        if (futures.size() == 0)
            return;

        List<Future> futures;

        do {

            synchronized (this) {

                if (throwWhenIsBroken && isBroken())
                    throw new Exception("upload is broken");

                futures = new LinkedList<>(this.futures);
                this.futures.clear();
            }

            for (Future future : futures)
                future.get();

        } while (futures.size() > 0);
    }

    private void trySubmitToExecutor(Item item) {

        if (isBroken()) {

            uploadStatus = new UploadStatus(uploadStatus.exception);

            uploadFailureEvent.onNext(new UploadFailureEvent(uploadStatus));

        } else {

            logger.debug("submitting {}", item.getTask());

            futures.add(executor.submit(new TaskRunner(item)));
        }
    }

    public class UploadStatus {

        public final Date date = new Date();

        private final Exception exception;

        private volatile String serialized;

        private UploadStatus(Exception exception) {
            this.exception = exception;
        }

        public String asString() {

            if (serialized == null)
                serialized = serialize();

            return serialized;
        }

        private String serialize() {

            StringBuilder sb = new StringBuilder();

            sb.append("Upload is broken due to an error occured while uploading changes to Google Drive.\n\n");
            sb.append("Back up all the data that you recently added to or modified in JdBox and ");
            sb.append("then delete this file to discard the local state.\n\n");

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

                item.getTask().run();

                logger.debug("completed {}", item.getTask());

                synchronized (Uploader.this) {

                    if (item.getQueue().removeHead())
                        queues.remove(item.getTask().getFileId());

                    for (Item dependent : new HashSet<>(item.getDependents())) {
                        if (dependent.removeDependency(item))
                            trySubmitToExecutor(dependent);
                    }
                }

            } catch (Exception e) {

                logger.error("an error occured while executing {}", item.getTask(), e);

                synchronized (Uploader.this) {

                    uploadStatus = new UploadStatus(e);

                    uploadFailureEvent.onNext(new UploadFailureEvent(uploadStatus));
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

    public Item getHead() {
        return head;
    }

    public Item append(Task task) {

        Item item = new Item(this, task);

        if (head == null) {
            this.head = item;
            this.tail = item;
        } else {
            item.addDependency(tail);
            tail = item;
        }

        return item;
    }

    public boolean removeHead() {
        if (head == tail)
            return true;
        for (Item item : head.getDependents()) {
            if (item.getQueue() == this) {
                head = item;
                break;
            }
        }
        return false;
    }
}

class Item {

    private final Queue queue;
    private final Task task;
    private final Set<Item> dependencies = new HashSet<>();
    private final Set<Item> dependents = new HashSet<>();

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
