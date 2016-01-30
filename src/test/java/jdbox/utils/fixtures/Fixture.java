package jdbox.utils.fixtures;

public interface Fixture {

    void before() throws Throwable;

    void after();
}
