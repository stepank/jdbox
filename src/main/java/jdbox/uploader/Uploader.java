package jdbox.uploader;

import com.google.inject.Inject;
import com.google.inject.Provider;
import jdbox.OperationContext;
import jdbox.datapersist.ChangeSet;
import jdbox.datapersist.Storage;
import jdbox.models.fileids.FileId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observer;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class Uploader {

    public static final String uploadFailureNotificationFileId = "upload failure notification file id";

    private static final Logger logger = LoggerFactory.getLogger(Uploader.class);
    private static final String namespace = "uploader_tasks";

    private final Storage storage;
    private final Collection<Provider<TaskDeserializer>> taskDeserializerProviders;
    private final Observer<UploadFailureEvent> uploadFailureEvent;
    private final Observer<FileEtagUpdateEvent> fileEtagUpdateEvent;
    private final Map<FileId, Queue> queues = new HashMap<>();
    private final List<Future> futures = new LinkedList<>();
    private final CountDownLatch startLatch = new CountDownLatch(1);
    private final ReadWriteLock remoteStateLock = new ReentrantReadWriteLock(true);

    private volatile UploadStatus uploadStatus;
    private volatile ExecutorService executor;

    private volatile long lastTaskId = 0;

    @Inject
    Uploader(
            Storage storage, Map<Class, Provider<TaskDeserializer>> taskDeserializerProviders,
            Observer<UploadFailureEvent> uploadFailureEvent, Observer<FileEtagUpdateEvent> fileEtagUpdateEvent) {
        this.storage = storage;
        this.taskDeserializerProviders = taskDeserializerProviders.values();
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

    public synchronized void init() {

        if (executor != null)
            throw new IllegalStateException("uploader is already initialized");

        executor = new ThreadPoolExecutor(4, 4, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());

        List<TaskDeserializer> deserializers = new ArrayList<>();
        for (Provider<TaskDeserializer> deserializerProvider : taskDeserializerProviders)
            deserializers.add(deserializerProvider.get());

        List<Map.Entry<String, String>> entries = storage.getData(namespace);
        Collections.sort(entries, new Comparator<Map.Entry<String, String>>() {
            @Override
            public int compare(Map.Entry<String, String> a, Map.Entry<String, String> b) {
                return Long.compare(Long.parseLong(a.getKey()), Long.parseLong(b.getKey()));
            }
        });

        for (Map.Entry<String, String> entry : entries) {

            Task task = null;

            for (TaskDeserializer deserializer : deserializers) {
                task = deserializer.deserialize(entry.getValue());
                if (task != null)
                    break;
            }

            if (task == null)
                throw new IllegalStateException("could not deserialize a task");

            lastTaskId = Long.parseLong(entry.getKey());

            submit(lastTaskId, task);
        }
    }

    public synchronized void start() {

        if (startLatch.getCount() == 0)
            throw new IllegalStateException("uploader is already started");

        startLatch.countDown();
    }

    public synchronized void tearDown() throws InterruptedException {

        if (executor != null) {
            logger.debug("shutting down executor");
            executor.shutdown();
            logger.debug("executor terminated");
            executor = null;
        }
    }

    public synchronized void reset() {
        uploadStatus = null;
        queues.clear();
        futures.clear();
    }

    public synchronized void submit(Task task) {

        lastTaskId++;

        submit(lastTaskId, task);
    }

    public synchronized void submit(ChangeSet changeSet, Task task) {

        if (changeSet == null)
            throw new IllegalArgumentException("changeSet must not be null");

        lastTaskId++;

        changeSet.put(namespace, Long.toString(lastTaskId), task.serialize());

        submit(lastTaskId, task);
    }

    /**
     * TODO Strictly speaking, when a task to delete a directory is submitted, this task must depend on ALL tasks
     * related to files in this directory. However, since deletion just moves files/directories to trash, this will
     * work. Still, I should find a way to fix this.
     */
    private void submit(long taskId, Task task) {

        logger.debug("submitting {}", task);

        FileId fileId = task.getFileId();
        String etag = task.getEtag();

        Queue queue = queues.get(fileId);

        if (queue == null) {
            queue = new Queue(etag);
            queues.put(fileId, queue);
        }

        final Item item = queue.append(taskId, task, OperationContext.get());

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

    public void waitUntilIsDone(long period, TimeUnit units) throws InterruptedException, TimeoutException {
        waitUntilIsDone(false, period, units);
    }

    public void waitUntilIsDoneOrBroken(long period, TimeUnit units) throws InterruptedException, TimeoutException {
        waitUntilIsDone(true, period, units);
    }

    public void pause() {
        remoteStateLock.writeLock().lock();
    }

    public boolean tryPause(int timeout, TimeUnit unit) {

        boolean locked = false;

        try {
            locked = remoteStateLock.writeLock().tryLock(timeout, unit);
        } catch (InterruptedException e) {
            logger.debug("lock acquisition has been interrupted", e);
        }

        return locked;
    }

    public void resume() {
        remoteStateLock.writeLock().unlock();
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

        logger.debug("submitting to executor {}", item.getTask());
        futures.add(executor.submit(new TaskRunner(item)));
    }

    private void waitUntilIsDone(
            boolean canBeBroken, long period, TimeUnit units) throws InterruptedException, TimeoutException {

        if (futures.size() == 0)
            return;

        Date start = new Date();

        List<Future> futures;

        do {

            synchronized (this) {

                if (!canBeBroken && isBroken())
                    throw new AssertionError("uploader is broken even though it is not allowed");

                futures = new LinkedList<>(this.futures);
                this.futures.clear();
            }

            for (Future future : futures) {
                long timeToWait =
                        TimeUnit.MILLISECONDS.convert(period, units) - (new Date().getTime() - start.getTime());
                try {
                    future.get(timeToWait, TimeUnit.MILLISECONDS);
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
        @SuppressWarnings("ConstantConditions")
        public void run() {

            try {
                startLatch.await();
            } catch (InterruptedException e) {
                logger.error("an interruption has been unexpectedly requested on uploader start up", e);
                return;
            }

            int delay = 1;
            Random random = new Random();

            String etag = null;
            Queue queue = item.getQueue();

            while (etag == null) {

                try {

                    remoteStateLock.readLock().lock();

                    try {

                        OperationContext.restore(item.getCtx());

                        ChangeSet changeSet = new ChangeSet();

                        try {

                            logger.debug("starting {} with etag {}", item.getTask(), queue.getEtag());

                            etag = item.getTask().run(changeSet, queue.getEtag());

                            logger.debug("completed {}, new etag is {}", item.getTask(), etag);

                        } finally {
                            OperationContext.clear();
                        }

                        FileId fileId = item.getTask().getFileId();

                        fileEtagUpdateEvent.onNext(new FileEtagUpdateEvent(fileId, etag));

                        synchronized (Uploader.this) {

                            changeSet.remove(namespace, Long.toString(item.getTaskId()));
                            storage.applyChangeSet(changeSet);

                            queue.setEtag(etag);

                            if (item.getQueue().removeHead())
                                queues.remove(fileId);

                            for (Item dependent : new HashSet<>(item.getDependents())) {
                                if (dependent.removeDependency(item))
                                    trySubmitToExecutor(dependent);
                            }
                        }

                    } finally {
                        remoteStateLock.readLock().unlock();
                    }

                    if (executor == null)
                        return;

                } catch (ConflictException e) {

                    logger.error("conflict has been detected while executing {}", item.getTask(), e);

                    if (executor == null)
                        return;

                    synchronized (Uploader.this) {
                        updateStatus(e);
                    }

                    return;

                } catch (IOException e) {

                    logger.warn("an error occured while executing {}", item.getTask(), e);

                    if (executor == null)
                        return;

                    try {
                        Thread.sleep(delay * 1000 + random.nextInt(1000));
                    } catch (InterruptedException ie) {

                        logger.error("an interruption has been unexpectedly requested on task {}", item.getTask(), ie);

                        synchronized (Uploader.this) {
                            updateStatus(ie);
                        }

                        return;
                    }

                    if (delay < 60)
                        delay *= 2;
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

    public Item append(long taskId, Task task, OperationContext ctx) {

        Item item = new Item(this, taskId, task, ctx);

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
    private final long taskId;
    private final Task task;
    private final OperationContext ctx;
    private final Set<Item> dependencies = new HashSet<>();
    private final Set<Item> dependents = new HashSet<>();

    private Item next;

    public Item(Queue queue, long taskId, Task task, OperationContext ctx) {
        this.queue = queue;
        this.taskId = taskId;
        this.task = task;
        this.ctx = ctx;
    }

    public Queue getQueue() {
        return queue;
    }

    public long getTaskId() {
        return taskId;
    }

    public Task getTask() {
        return task;
    }

    public OperationContext getCtx() {
        return ctx;
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
