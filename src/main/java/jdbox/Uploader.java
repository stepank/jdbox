package jdbox;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListenableFutureTask;
import com.google.common.util.concurrent.SettableFuture;
import com.google.inject.Inject;
import com.sun.istack.internal.NotNull;

import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class Uploader {

    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor;
    private final AtomicBoolean idle = new AtomicBoolean(true);

    @Inject
    public Uploader(ExecutorService executor) {
        this.executor = executor;
    }

    public ListenableFuture<?> submit(Runnable task) {

        ListenableFutureTask t = ListenableFutureTask.create(task, null);

        if (!idle.compareAndSet(true, false)) {
            queue.add(t);
            return t;
        }

        queue.add(t);

        executor.submit(new Runnable() {
            @Override
            public void run() {
                Runnable task;
                while (!idle.compareAndSet((task = queue.poll()) != null, true)) {
                    assert task != null;
                    task.run();
                }
            }
        });

        return t;
    }

    public void waitUntilDone() throws InterruptedException, ExecutionException, TimeoutException {
        waitUntilDone(5, TimeUnit.SECONDS);
    }

    public void waitUntilDone(long timeout, @NotNull TimeUnit unit)
            throws InterruptedException, ExecutionException, TimeoutException {
        final SettableFuture<Object> future = SettableFuture.create();
        submit(new Runnable() {
            @Override
            public void run() {
                future.set(null);
            }
        });
        future.get(timeout, unit);
    }
}
