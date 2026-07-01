package ltd.mc233.core;

public final class MagnetRouter {

    private MagnetRouter() {}

    public static final class Split {

        public final int toInv, toStorage;

        Split(int i, int s) {
            toInv = i;
            toStorage = s;
        }
    }

    public static Split route(int mode, int stackSize, int freeSpace) {
        if (mode == 2) return new Split(0, stackSize);
        if (mode == 1) {
            int inv = Math.min(stackSize, Math.max(0, freeSpace));
            return new Split(inv, stackSize - inv);
        }
        return new Split(0, 0);
    }
}
