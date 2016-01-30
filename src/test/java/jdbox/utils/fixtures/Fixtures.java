package jdbox.utils.fixtures;

import java.util.List;

public class Fixtures {

    public static void runUnder(List<Fixture> fixtures, UnsafeRunnable action) throws Throwable {
        if (fixtures.size() == 0)
            action.run();
        else {

            Fixture head = fixtures.get(0);
            List<Fixture> tail = fixtures.subList(1, fixtures.size());

            head.before();
            try {
                runUnder(tail, action);
            } finally {
                head.after();
            }
        }
    }
}
