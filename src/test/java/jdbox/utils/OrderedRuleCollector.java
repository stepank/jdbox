package jdbox.utils;

import org.junit.rules.MethodRule;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class OrderedRuleCollector implements MethodRule {

    @Override
    public Statement apply(Statement base, FrameworkMethod method, Object target) {

        List<Field> fields = new ArrayList<>();
        for (Field field : target.getClass().getFields()) {
            if (field.isAnnotationPresent(OrderedRule.class)) {
                if (!TestRule.class.isAssignableFrom(field.getType()))
                    throw new IllegalArgumentException(
                            String.format(
                                    "OrderedRule annotation cannot be applied to field %s.%s " +
                                            "because it is not of type TestRule",
                                    field.getDeclaringClass().getName(), field.getName()));
                fields.add(field);
            }
        }

        Collections.sort(fields, new Comparator<Field>() {
            @Override
            public int compare(Field a, Field b) {
                if (a.getDeclaringClass() == b.getDeclaringClass())
                    return b.getAnnotation(OrderedRule.class).value() - a.getAnnotation(OrderedRule.class).value();
                return a.getDeclaringClass().isAssignableFrom(b.getDeclaringClass()) ? 1 : -1;
            }
        });

        Statement result = base;

        for (Field field : fields) {
            TestRule rule;
            try {
                rule = (TestRule) field.get(target);
            } catch (IllegalAccessException e) {
                throw new RuntimeException(e);
            }
            Description description =
                    Description.createTestDescription(target.getClass(), method.getName(), method.getAnnotations());
            result = rule.apply(result, description);
        }

        return result;
    }
}
