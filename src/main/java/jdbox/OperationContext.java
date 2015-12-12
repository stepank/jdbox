package jdbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

public class OperationContext {

    private static final Logger logger = LoggerFactory.getLogger(OperationContext.class);

    private static ThreadLocal<OperationContext> self = new ThreadLocal<OperationContext>() {
        @Override
        protected OperationContext initialValue() {
            return new OperationContext();
        }
    };

    public final String path;
    public final String operation;

    public static void initialize(String path, String operation) {
        initialize(path, operation, null);
    }

    public static void initialize(String path, String operation, String message, Object... args) {
        restore(new OperationContext(path, operation));
        if (message == null)
            logger.info("init");
        else
            logger.info("init, " + message, args);
    }

    public static void restore(OperationContext ctx) {
        self.set(ctx);
        MDC.put("path", ctx.path);
        MDC.put("operation", ctx.operation);
    }

    public static void clear() {
        self.set(new OperationContext());
        MDC.remove("path");
        MDC.remove("operation");
    }

    public static OperationContext get() {
        return self.get();
    }

    private OperationContext() {
        this("", "");
    }

    private OperationContext(String path, String operation) {
        this.path = path;
        this.operation = operation;
    }
}
