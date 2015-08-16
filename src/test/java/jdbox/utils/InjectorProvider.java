package jdbox.utils;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import jdbox.DisposableModule;
import jdbox.TestCommonModule;
import org.junit.rules.ErrorCollector;
import org.junit.rules.ExternalResource;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedList;

public class InjectorProvider extends ExternalResource {

    private final ErrorCollector errorCollector;
    private final Module[] modules;

    protected Injector injector;

    public InjectorProvider(ErrorCollector errorCollector, Module... requiredModules) {
        this.errorCollector = errorCollector;
        this.modules = requiredModules;
    }

    public InjectorProvider(ErrorCollector errorCollector, Collection<Module> requiredModules) {
        this.errorCollector = errorCollector;
        this.modules = requiredModules.toArray(new Module[requiredModules.size()]);
    }

    public Injector getInjector() {
        return injector;
    }

    public Module[] getModules() {
        return modules;
    }

    @Override
    protected void before() throws Throwable {
        injector = createInjector();
    }

    @Override
    protected void after() {
        destroyInjector(injector);
    }

    private Injector createInjector() {
        return Guice.createInjector(new LinkedList<Module>() {{
            add(new TestCommonModule());
            addAll(Arrays.asList(modules));
        }});
    }

    private void destroyInjector(Injector injector) {

        for (Module module : modules) {
            if (module instanceof DisposableModule) {
                DisposableModule dm = (DisposableModule) module;
                try {
                    dm.dispose(injector);
                } catch (Exception e) {
                    errorCollector.addError(e);
                }
            }
        }

        try {
            new TestCommonModule().dispose(injector);
        } catch (Exception e) {
            errorCollector.addError(e);
        }
    }
}
