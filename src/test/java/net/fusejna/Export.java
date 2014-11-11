package net.fusejna;

public class Export {

    public static StatHolder createStatHolder() {
        return new StatHolder(new StructStat.X86_64());
    }
}
