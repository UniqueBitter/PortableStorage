package ltd.mc233.core;

// 背包优先、仓库补差的纯算术。need=需求量, invHave=背包里有的, storeHave=仓库里有的。
// 仿 MagnetRouter 的 Split 风格, 零依赖, 便于单测。
public final class DeductPlan {

    private DeductPlan() {}

    public static final class Split {

        public final long fromInv, fromStorage;
        public final boolean satisfied; // 背包+仓库合起来是否够 need

        Split(long fromInv, long fromStorage, boolean satisfied) {
            this.fromInv = fromInv;
            this.fromStorage = fromStorage;
            this.satisfied = satisfied;
        }
    }

    public static Split plan(long need, long invHave, long storeHave) {
        if (need <= 0) return new Split(0, 0, true);
        long fromInv = Math.min(need, Math.max(0, invHave));
        long remain = need - fromInv;
        long fromStorage = Math.min(remain, Math.max(0, storeHave));
        boolean satisfied = (fromInv + fromStorage) >= need;
        return new Split(fromInv, fromStorage, satisfied);
    }
}
