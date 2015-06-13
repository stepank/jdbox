package jdbox.uploader;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.inject.Inject;
import jdbox.models.fileids.FileId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

public class Uploader {

    private static final Logger logger = LoggerFactory.getLogger(Uploader.class);

    private final ListeningExecutorService executor;
    private final Map<FileId, Queue> queues = new HashMap<>();
    private final List<ListenableFuture<?>> futures = new LinkedList<>();

    @Inject
    public Uploader(ExecutorService executor) {
        this.executor = MoreExecutors.listeningDecorator(executor);
    }

    public synchronized int getQueueCount() {
        return queues.size();
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
            futures.add(executor.submit(new TaskRunner(item)));
    }

    public void waitUntilIsDone() throws ExecutionException, InterruptedException {

        if (futures.size() == 0)
            return;

        List<Future> futures;

        do {

            synchronized (this) {
                futures = new LinkedList<Future>(this.futures);
                this.futures.clear();
            }

            for (Future future : futures) {
                future.get();
            }

        } while (futures.size() > 0);
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
                            futures.add(executor.submit(new TaskRunner(dependent)));
                    }
                }

            } catch (Exception e) {
                logger.error("an error occured while executing {}", item.getTask(), e);
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
