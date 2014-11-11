package net.fusejna;

import net.fusejna.types.TypeMode;

public class StatHolder {

    public final StructStat stat;
    public final StructStat.StatWrapper wrapper;

    public StatHolder(StructStat stat) {
        this.stat = stat;
        this.wrapper = new StructStat.StatWrapper(stat);
    }

    public int size() {
        return (int) stat.st_size();
    }

    public TypeMode.NodeType type() {
        return wrapper.type();
    }
}
