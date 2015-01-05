package jdbox;

import com.google.inject.Inject;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

public class Uploader {

    private final Queue<Runnable> queue = new ConcurrentLinkedQueue<>();
    private final ExecutorService executor;
    private final AtomicBoolean idle = new AtomicBoolean(true);

    @Inject
    public Uploader(ExecutorService executor) {
        this.executor = executor;
    }

    public void submit(Runnable task) {

        if (!idle.compareAndSet(true, false)) {
            queue.add(task);
            return;
        }

        queue.add(task);

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
    }
}
