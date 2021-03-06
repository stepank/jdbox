package jdbox.utils;

import com.google.inject.Key;
import com.google.inject.Module;
import jdbox.CommonModule;
import jdbox.content.OpenedFiles;
import jdbox.modules.LifeCycleManager;
import jdbox.modules.MultipleException;
import jdbox.uploader.Uploader;
import jdbox.utils.fixtures.Fixture;
import org.junit.rules.ErrorCollector;
import org.junit.rules.ExternalResource;

import java.lang.annotation.Annotation;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.lessThan;

public class LifeCycleManagerResource extends ExternalResource implements Fixture {

    private final ErrorCollector errorCollector;
    private final LifeCycleManager lifeCycleManager;
    private final List<Module> modules;

    public LifeCycleManagerResource(ErrorCollector errorCollector, final List<Module> modules) {
        this.errorCollector = errorCollector;
        this.modules = modules;
        this.lifeCycleManager = new LifeCycleManager(new LinkedList<Module>() {{
            add(new CommonModule());
            addAll(modules);
        }});
    }

    public <T> T getInstance(Class<T> type) {
        return lifeCycleManager.getInjector().getInstance(type);
    }

    public <T> T getInstance(Class<T> type, Class<? extends Annotation> annotation) {
        return lifeCycleManager.getInjector().getInstance(Key.get(type, annotation));
    }

    public List<Module> getModules() {
        return modules;
    }

    @Override
    public void before() throws Throwable {
        lifeCycleManager.init();
        lifeCycleManager.start();
    }

    @Override
    public void after() {
        try {
            lifeCycleManager.tearDown();
        } catch (MultipleException me) {
            for (Exception e : me.exceptions)
                errorCollector.addError(e);
        }
    }

    public void waitUntilUploaderIsDone() throws InterruptedException {
        waitUntilUploaderIsDone(15, TimeUnit.SECONDS);
    }

    public void waitUntilUploaderIsDone(long period, TimeUnit units) throws InterruptedException {
        try {
            getInstance(Uploader.class).waitUntilIsDone(period, units);
        } catch (TimeoutException e) {
            throw new AssertionError("uploader could not finish its work in the given amount of time");
        }
    }

    public void waitUntilUploaderIsDoneOrBroken() throws InterruptedException {
        try {
            getInstance(Uploader.class).waitUntilIsDoneOrBroken(15, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new AssertionError("uploader could not finish its work in the given amount of time");
        }
    }

    public void waitUntilLocalStorageIsEmpty() throws InterruptedException {
        waitUntilUploaderIsDone();
        Date start = new Date();
        while (getInstance(OpenedFiles.class).getLocalFilesCount() != 0) {
            Thread.sleep(100);
            assertThat(new Date().getTime() - start.getTime(), lessThan((long) 5000));
        }
    }
}
