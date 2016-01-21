package jdbox.uploader;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.multibindings.MapBinder;
import jdbox.BaseTest;
import jdbox.models.fileids.FileId;
import jdbox.models.fileids.FileIdStore;
import jdbox.datapersist.ChangeSet;
import jdbox.datapersist.Storage;
import jdbox.datapersist.DataPersistenceModule;
import jdbox.utils.LifeCycleManagerResource;
import jdbox.utils.fixtures.Fixture;
import jdbox.utils.fixtures.Fixtures;
import jdbox.utils.fixtures.UnsafeRunnable;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

public class RestoreTest extends BaseTest {

    private static final Logger logger = LoggerFactory.getLogger(RestoreTest.class);

    private List<Module> getRequiredModules(final Path tempFolder) {
        return new ArrayList<Module>() {{
            add(new DataPersistenceModule(tempFolder));
            add(new UploaderModule());
        }};
    }

    @Test
    public void simple() throws Throwable {

        Path tempFolder = tempFolderProvider.create();

        final ArrayList<Integer> numbers = new ArrayList<>();

        final LifeCycleManagerResource lifeCycleManager =
                new LifeCycleManagerResource(errorCollector, getRequiredModules(tempFolder));

        Fixtures.runUnder(ImmutableList.<Fixture>of(lifeCycleManager), new UnsafeRunnable() {
            @Override
            public void run() throws Exception {

                FileIdStore fileIdStore = lifeCycleManager.getInstance(FileIdStore.class);
                Uploader uploader = lifeCycleManager.getInstance(Uploader.class);
                Storage storage = lifeCycleManager.getInstance(Storage.class);

                for (int i = 0; i < 4; i++) {
                    ChangeSet changeSet = new ChangeSet();
                    uploader.submit(changeSet, new AnotherTestTask(i, fileIdStore.get("some"), numbers));
                    storage.applyChangeSet(changeSet);
                }

                logger.info("sleeping to give the uploader some time to work");
                Thread.sleep(600);
            }
        });

        assertThat(numbers, equalTo(Lists.newArrayList(0, 1)));

        List<Module> modules = getRequiredModules(tempFolder);
        modules.add(new AbstractModule() {
            @Override
            protected void configure() {
                bind(new TypeLiteral<List<Integer>>() {}).toInstance(numbers);
                MapBinder<Class, TaskDeserializer> deserializerBinder =
                        MapBinder.newMapBinder(binder(), Class.class, TaskDeserializer.class);
                deserializerBinder.addBinding(TestTaskDeserializer.class).to(TestTaskDeserializer.class);
            }
        });

        final LifeCycleManagerResource lifeCycleManager2 = new LifeCycleManagerResource(errorCollector, modules);

        Fixtures.runUnder(ImmutableList.<Fixture>of(lifeCycleManager2), new UnsafeRunnable() {
            @Override
            public void run() throws Exception {
                lifeCycleManager2.waitUntilUploaderIsDone();
            }
        });

        assertThat(numbers, equalTo(Lists.newArrayList(0, 1, 1, 2, 3)));
    }
}

class AnotherTestTask extends BaseTask {

    private final List<Integer> numbers;

    public AnotherTestTask(Integer label, FileId fileId, List<Integer> numbers) {
        super(label.toString(), fileId, null, null, false);
        this.numbers = numbers;
    }

    @Override
    public String run(ChangeSet changeSet, String etag) throws ConflictException, IOException {
        try {
            numbers.add(Integer.parseInt(getLabel()));
            Thread.sleep(400);
            return "does not matter";
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String serialize() {
        return getLabel();
    }

    @Override
    public String toString() {
        return "AnotherTestTask{" +
                "label='" + getLabel() + '\'' +
                ", fileId=" + getFileId() +
                ", dependsOn=" + getDependsOn() +
                ", blocksDependentTasks=" + blocksDependentTasks() +
                '}';
    }
}

class TestTaskDeserializer implements TaskDeserializer {

    private final FileIdStore fileIdStore;
    private final List<Integer> numbers;

    @Inject
    TestTaskDeserializer(FileIdStore fileIdStore, List<Integer> numbers) {
        this.fileIdStore = fileIdStore;
        this.numbers = numbers;
    }

    @Override
    public Task deserialize(String data) {
        return new AnotherTestTask(Integer.parseInt(data), fileIdStore.get("some"), numbers);
    }
}