package jdbox.modules;

import java.util.List;

public class MultipleException extends Exception {

    public final List<Exception> exceptions;

    MultipleException(List<Exception> exceptions) {
        this.exceptions = exceptions;
    }
}
