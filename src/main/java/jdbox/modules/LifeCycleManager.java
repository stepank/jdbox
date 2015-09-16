package jdbox.modules;

import com.google.common.collect.Lists;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;

public class LifeCycleManager {

    private static final Logger logger = LoggerFactory.getLogger(LifeCycleManager.class);

    private final List<Module> modules;

    private Injector injector;

    public LifeCycleManager(Module... modules) {
        this.modules = Arrays.asList(modules);
    }

    public LifeCycleManager(List<Module> modules) {
        this.modules = modules;
    }

    public Injector getInjector() {
        checkInitialized();
        return injector;
    }

    public void init() throws Exception {

        if (injector != null)
            throw new IllegalStateException("container has already been initialized");

        injector = Guice.createInjector(modules);

        for (Module module : modules) {
            if (module instanceof ActiveModule) {
                ActiveModule am = (ActiveModule) module;
                am.init(injector);
            }
        }
    }

    public void start() throws Exception {

        checkInitialized();

        for (Module module : modules) {
            if (module instanceof ActiveModule) {
                ActiveModule am = (ActiveModule) module;
                am.start(injector);
            }
        }
    }

    public void tearDown() throws MultipleException {

        checkInitialized();

        LinkedList<Exception> exceptions = new LinkedList<>();

        for (Module module : Lists.reverse(modules)) {
            if (module instanceof ActiveModule) {
                ActiveModule a = (ActiveModule) module;
                try {
                    a.tearDown(injector);
                } catch (Exception e) {
                    logger.error("an error occured while disposing module {}", module.getClass().getName(), e);
                    exceptions.add(e);
                }
            }
        }

        injector = null;

        if (exceptions.size() != 0)
            throw new MultipleException(exceptions);
    }

    private void checkInitialized() {
        if (injector == null)
            throw new IllegalStateException("container has already been disposed or has not been initialized yet");
    }
}
