package jdbox.localstate.interfaces;

import jdbox.uploader.Task;

public class UpdateResult<T> {

    public final T result;
    public final Task task;

    public UpdateResult(T result, Task task) {
        this.result = result;
        this.task = task;
    }
}
