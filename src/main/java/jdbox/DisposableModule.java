package jdbox;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;

public abstract class DisposableModule extends AbstractModule {

    public abstract void dispose(Injector injector) throws Exception;
}
