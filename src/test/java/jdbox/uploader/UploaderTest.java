package jdbox.uploader;

import com.google.common.collect.Lists;
import com.google.inject.Module;
import jdbox.BaseTest;
import jdbox.models.fileids.FileId;
import jdbox.models.fileids.FileIdStore;
import jdbox.utils.TestUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

public class UploaderTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(UploaderTest.class);

    private Uploader uploader;
    private Collection<List<Integer>> expectedOrders;
    private TestTaskFactory taskFactory;

    @Override
    protected Collection<Module> getRequiredModules() {
        return new ArrayList<Module>() {{
            add(new UploaderModule());
        }};
    }

    @Before
    public void setUp() {

        uploader = injectorProvider.getInjector().getInstance(Uploader.class);

        expectedOrders = new LinkedList<>();
        taskFactory = new TestTaskFactory();
    }

    @After
    public void tearDown() throws Exception {

        TestUtils.waitUntilUploaderIsDone(injectorProvider.getInjector());

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

    @Test
    public void concurrency() throws Exception {

        Date start = new Date();

        for (int i = 1; i <= 10; i++) {
            uploader.submit(taskFactory.create(i, Integer.toString(i), null, false, new Runnable() {
                @Override
                public void run() {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }));
        }

        uploader.waitUntilIsDone();

        int elapsed = (int) (new Date().getTime() - start.getTime());

        logger.debug("time spent to run all the tasks is {} ms", elapsed);

        assertThat(elapsed, lessThan(2000));
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
            Integer label, String fileId, String dependsOn, boolean blocksDependentTasks, Runnable runnable) {
        labels.add(label);
        return new TestTask(
                label, fileIdStore.get(fileId), dependsOn != null ? fileIdStore.get(dependsOn) : null,
                blocksDependentTasks, runnable, order);
    }
}

class TestTask extends Task {

    private Integer label;
    private final Runnable runnable;
    private final List<Integer> order;

    public TestTask(
            Integer label, FileId fileId, FileId dependsOn,
            boolean blocksDependentTasks, Runnable runnable, List<Integer> order) {
        super(label.toString(), fileId, dependsOn, blocksDependentTasks);
        this.label = label;
        this.runnable = runnable;
        this.order = order;
    }

    @Override
    public void run() throws Exception {
        synchronized (order) {
            order.add(label);
        }
        if (runnable != null)
            runnable.run();
    }
}
