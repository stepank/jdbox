package jdbox;

import com.google.inject.Module;
import jdbox.utils.LifeCycleManagerResource;
import jdbox.utils.OrderedRule;

import java.util.List;

public abstract class BaseLifeCycleManagerTest extends BaseTest {

    @OrderedRule
    public final LifeCycleManagerResource lifeCycleManager =
            new LifeCycleManagerResource(errorCollector, getRequiredModules());

    protected abstract List<Module> getRequiredModules();
}
