package jdbox;

import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.inject.Inject;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;

public class Uploader {

    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor;
    private final AtomicBoolean idle = new AtomicBoolean(true);

    private volatile Future isDone;

    @Inject
    public Uploader(ExecutorService executor) {
        this.executor = executor;
    }

    public void submit(Runnable task) {

        ListenableFutureTask t = ListenableFutureTask.create(task, null);

        if (!idle.compareAndSet(true, false)) {
            queue.add(t);
            return;
        }

        queue.add(t);

        isDone = executor.submit(new Runnable() {
            @Override
            public void run() {
                Runnable task;
                while (!idle.compareAndSet((task = queue.poll()) != null, true)) {
                    assert task != null;
                    task.run();
                }
            }
        });
    }

    public Future isDone() {
        Future future = isDone;
        return future != null ? future : Futures.immediateFuture(null);
    }
}
