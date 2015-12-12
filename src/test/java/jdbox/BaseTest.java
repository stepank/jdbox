package jdbox;

import com.google.inject.Module;
import jdbox.utils.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.ErrorCollector;

import java.util.List;

public abstract class BaseTest {

    @ClassRule
    public final static DriveServiceProvider driveServiceProvider = new DriveServiceProvider();

    @Rule
    public final OrderedRuleCollector collector = new OrderedRuleCollector();

    @OrderedRule(0)
    public final RepeatRule repeatRule = new RepeatRule();

    @OrderedRule(1)
    public final OperationContextSetter operationContextSetter = new OperationContextSetter();

    @OrderedRule(2)
    public final ErrorCollector errorCollector = new ErrorCollector();

    @OrderedRule(3)
    public final LifeCycleManagerResource lifeCycleManager =
            new LifeCycleManagerResource(errorCollector, getRequiredModules());

    protected abstract List<Module> getRequiredModules();
}
