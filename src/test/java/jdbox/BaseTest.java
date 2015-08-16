package jdbox;

import com.google.inject.Module;
import jdbox.utils.*;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.rules.ErrorCollector;

import java.util.Collection;

public abstract class BaseTest {

    @ClassRule
    public static DriveServiceProvider driveServiceProvider = new DriveServiceProvider();

    @Rule
    public OrderedRuleCollector collector = new OrderedRuleCollector();

    @OrderedRule(0)
    public RepeatRule repeatRule = new RepeatRule();

    @OrderedRule(1)
    public ErrorCollector errorCollector = new ErrorCollector();

    @OrderedRule(2)
    public InjectorProvider injectorProvider = new InjectorProvider(errorCollector, getRequiredModules());

    protected abstract Collection<Module> getRequiredModules();
}
