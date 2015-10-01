package jdbox.uploader;

import com.google.common.collect.Lists;
import com.google.inject.Module;
import jdbox.BaseTest;
import jdbox.models.fileids.FileId;
import jdbox.models.fileids.FileIdStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class UploaderTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(UploaderTest.class);

    private Uploader uploader;
    private Collection<List<Integer>> expectedOrders;
    private TestTaskFactory taskFactory;

    @Override
    protected List<Module> getRequiredModules() {
        return new ArrayList<Module>() {{
            add(new UploaderModule());
        }};
    }

    @Before
    public void setUp() {

        uploader = lifeCycleManager.getInstance(Uploader.class);

        expectedOrders = new LinkedList<>();
        taskFactory = new TestTaskFactory();
    }

    @After
    public void tearDown() throws InterruptedException {

        lifeCycleManager.waitUntilUploaderIsDone();

        List<Integer> order = taskFactory.getOrder();

        assertThat(uploader.getQueueCount(), equalTo(0));
        assertThat(new HashSet<>(order).size(), equalTo(order.size()));
        assertThat(new HashSet<>(order), equalTo(taskFactory.getLabels()));

        for (List<Integer> expectedOrder : expectedOrders) {
            int previous = -1;
            for (Integer i : expectedOrder) {
                int current = order.indexOf(i);
                assertThat(current, greaterThan(previous));
                previous = current;
            }
        }
    }

    @Test
    public void singleTask() {
        uploader.submit(taskFactory.create(1, "hello"));
    }

    @Test
    public void twoIndependentTasks() {
        uploader.submit(taskFactory.create(1, "hello"));
        uploader.submit(taskFactory.create(2, "world"));
    }

    @Test
    public void twoTasksWithSameFileId() {
        uploader.submit(taskFactory.create(1, "hello"));
        uploader.submit(taskFactory.create(2, "hello"));
        expectedOrders.add(Lists.newArrayList(1, 2));
    }

    @Test
    public void threeTasksWithSameFileId() {
        uploader.submit(taskFactory.create(1, "hello"));
        uploader.submit(taskFactory.create(2, "hello"));
        uploader.submit(taskFactory.create(3, "hello"));
        expectedOrders.add(Lists.newArrayList(1, 2, 3));
    }

    @Test
    public void fourTasksWithTwoFileIds() {
        uploader.submit(taskFactory.create(1, "hello"));
        uploader.submit(taskFactory.create(2, "hello"));
        uploader.submit(taskFactory.create(3, "world"));
        uploader.submit(taskFactory.create(4, "world"));
        expectedOrders.add(Lists.newArrayList(1, 2));
        expectedOrders.add(Lists.newArrayList(3, 4));
    }

    @Test
    public void twoTasksWithDependency() {
        uploader.submit(taskFactory.create(1, "hello", null, true));
        uploader.submit(taskFactory.create(2, "world", "hello"));
        expectedOrders.add(Lists.newArrayList(1, 2));
    }

    @Test
    public void threeTasksWithDependencies() {
        uploader.submit(taskFactory.create(1, "hello", null, true));
        uploader.submit(taskFactory.create(2, "world", "hello", true));
        uploader.submit(taskFactory.create(3, "kitty", "world"));
        expectedOrders.add(Lists.newArrayList(1, 2, 3));
    }

    @Test
    public void twoTasksDependenOnOne() {
        uploader.submit(taskFactory.create(1, "hello", null, true));
        uploader.submit(taskFactory.create(2, "world", "hello"));
        uploader.submit(taskFactory.create(3, "kitty", "hello"));
        expectedOrders.add(Lists.newArrayList(1, 2));
        expectedOrders.add(Lists.newArrayList(1, 3));
    }

    @Test
    public void concurrency() throws InterruptedException {

        Date start = new Date();

        for (int i = 1; i <= 4; i++) {
            uploader.submit(taskFactory.create(i, Integer.toString(i), null, false, new IORunnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }));
        }

        uploader.waitUntilIsDone();

        int elapsed = (int) (new Date().getTime() - start.getTime());

        logger.debug("time spent to run all the tasks is {} ms", elapsed);

        assertThat(elapsed, lessThan(3000));
    }

    @Test
    public void retries() throws InterruptedException {

        Date start = new Date();

        final AtomicInteger counter = new AtomicInteger();

        uploader.submit(taskFactory.create(0, "0", null, false, new IORunnable() {
            @Override
            public void run() throws IOException {
                if (counter.getAndIncrement() == 0)
                    throw new IOException("something bad happened");
            }
        }));

        uploader.waitUntilIsDone();

        int elapsed = (int) (new Date().getTime() - start.getTime());

        logger.debug("time spent to run the tasks is {} ms", elapsed);

        assertThat(elapsed, allOf(greaterThan(1000), lessThan(2200)));
    }
}

class TestTaskFactory {

    private final FileIdStore fileIdStore = new FileIdStore();
    private final Set<Integer> labels = new HashSet<>();
    private final List<Integer> order = new LinkedList<>();

    public Set<Integer> getLabels() {
        return labels;
    }

    public List<Integer> getOrder() {
        return order;
    }

    public Task create(Integer label, String fileId) {
        return create(label, fileId, null);
    }

    public Task create(Integer label, String fileId, String dependsOn) {
        return create(label, fileId, dependsOn, false);
    }

    public Task create(Integer label, String fileId, String dependsOn, boolean blocksDependentTasks) {
        return create(label, fileId, dependsOn, blocksDependentTasks, null);
    }

    public Task create(
            Integer label, String fileId, String dependsOn, boolean blocksDependentTasks, IORunnable runnable) {
        labels.add(label);
        return new TestTask(
                label, fileIdStore.get(fileId), dependsOn != null ? fileIdStore.get(dependsOn) : null,
                blocksDependentTasks, runnable, order);
    }
}

class TestTask implements Task {

    private final Integer label;
    private final FileId fileId;
    private final FileId dependsOn;
    private final boolean blocksDependentTasks;
    private final IORunnable runnable;
    private final List<Integer> order;

    public TestTask(
            Integer label, FileId fileId, FileId dependsOn,
            boolean blocksDependentTasks, IORunnable runnable, List<Integer> order) {
        this.label = label;
        this.fileId = fileId;
        this.dependsOn = dependsOn;
        this.blocksDependentTasks = blocksDependentTasks;
        this.runnable = runnable;
        this.order = order;
    }

    @Override
    public String getLabel() {
        return label.toString();
    }

    @Override
    public FileId getFileId() {
        return fileId;
    }

    @Override
    public String getEtag() {
        return null;
    }

    @Override
    public FileId getDependsOn() {
        return dependsOn;
    }

    @Override
    public boolean blocksDependentTasks() {
        return blocksDependentTasks;
    }

    @Override
    public String run(String etag) throws IOException {
        if (runnable != null)
            runnable.run();
        synchronized (order) {
            order.add(label);
        }
        return "does not matter";
    }
}

interface IORunnable {
    void run() throws IOException;
}
