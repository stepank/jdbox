package jdbox.utils;

import jdbox.OperationContext;
import org.junit.rules.ExternalResource;

public class OperationContextSetter extends ExternalResource {

    @Override
    protected void before() throws Throwable {
        OperationContext.clear();
    }

    @Override
    protected void after() {
        OperationContext.clear();
    }
}
