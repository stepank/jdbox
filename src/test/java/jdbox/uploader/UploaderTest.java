package jdbox.uploader;

import com.google.common.collect.Lists;
import jdbox.models.fileids.FileId;
import jdbox.models.fileids.FileIdStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.*;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.greaterThan;

public class UploaderTest {

    private ThreadPoolExecutor executor;
    private Uploader uploader;
    private Collection<List<Integer>> expectedOrders;
    private TestTaskFactory taskFactory;

    @Before
    public void setUp() {

        executor = new ThreadPoolExecutor(1, 8, 60, TimeUnit.SECONDS, new LinkedBlockingDeque<Runnable>());
        uploader = new Uploader(executor);

        expectedOrders = new LinkedList<>();
        taskFactory = new TestTaskFactory();
    }

    @After
    public void tearDown() throws Exception {

        uploader.waitUntilIsDone();

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

        List<Runnable> tasks = executor.shutdownNow();
        assertThat(tasks.size(), equalTo(0));
        executor.awaitTermination(5, TimeUnit.SECONDS);
    }

    @Test
    public void singleTask() throws Exception {
        uploader.submit(taskFactory.create(1, "hello"));
    }

    @Test
    public void twoIndependentTasks() throws Exception {
        uploader.submit(taskFactory.create(1, "hello"));
        uploader.submit(taskFactory.create(2, "world"));
    }

    @Test
    public void twoTasksWithSameFileId() throws Exception {
        uploader.submit(taskFactory.create(1, "hello"));
        uploader.submit(taskFactory.create(2, "hello"));
        expectedOrders.add(Lists.newArrayList(1, 2));
    }

    @Test
    public void threeTasksWithSameFileId() throws Exception {
        uploader.submit(taskFactory.create(1, "hello"));
        uploader.submit(taskFactory.create(2, "hello"));
        uploader.submit(taskFactory.create(3, "hello"));
        expectedOrders.add(Lists.newArrayList(1, 2, 3));
    }

    @Test
    public void fourTasksWithTwoFileIds() throws Exception {
        uploader.submit(taskFactory.create(1, "hello"));
        uploader.submit(taskFactory.create(2, "hello"));
        uploader.submit(taskFactory.create(3, "world"));
        uploader.submit(taskFactory.create(4, "world"));
        expectedOrders.add(Lists.newArrayList(1, 2));
        expectedOrders.add(Lists.newArrayList(3, 4));
    }

    @Test
    public void twoTasksWithDependency() throws Exception {
        uploader.submit(taskFactory.create(1, "hello", null, true));
        uploader.submit(taskFactory.create(2, "world", "hello"));
        expectedOrders.add(Lists.newArrayList(1, 2));
    }

    @Test
    public void threeTasksWithDependencies() throws Exception {
        uploader.submit(taskFactory.create(1, "hello", null, true));
        uploader.submit(taskFactory.create(2, "world", "hello", true));
        uploader.submit(taskFactory.create(3, "kitty", "world"));
        expectedOrders.add(Lists.newArrayList(1, 2, 3));
    }

    @Test
    public void twoTasksDependenOnOne() throws Exception {
        uploader.submit(taskFactory.create(1, "hello", null, true));
        uploader.submit(taskFactory.create(2, "world", "hello"));
        uploader.submit(taskFactory.create(3, "kitty", "hello"));
        expectedOrders.add(Lists.newArrayList(1, 2));
        expectedOrders.add(Lists.newArrayList(1, 3));
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
        labels.add(label);
        return new TestTask(
                label, fileIdStore.get(fileId), dependsOn != null ? fileIdStore.get(dependsOn) : null,
                blocksDependentTasks, order);
    }
}

class TestTask extends Task {

    private Integer label;
    private final List<Integer> order;

    public TestTask(
            Integer label, FileId fileId, FileId dependsOn, boolean blocksDependentTasks, List<Integer> order) {
        super(label.toString(), fileId, dependsOn, blocksDependentTasks);
        this.label = label;
        this.order = order;
    }

    @Override
    public void run() throws Exception {
        synchronized (order) {
            order.add(label);
        }
    }
}
