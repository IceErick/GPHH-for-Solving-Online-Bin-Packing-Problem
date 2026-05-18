/**
 * ThreadLocal context for running statistics during online bin packing.
 * Enables GP trees to access global packing state (running mean, etc.)
 * without changing the evaluate() interface.
 */
public class PackingContext {
    private static final ThreadLocal<PackingContext> CURRENT = new ThreadLocal<>();

    private double runningMeanSize;
    private int itemsSeen;
    private int totalSize;

    public PackingContext() {
        this.runningMeanSize = 0.0;
        this.itemsSeen = 0;
        this.totalSize = 0;
    }

    public static void beginPacking() {
        CURRENT.set(new PackingContext());
    }

    public static PackingContext get() {
        return CURRENT.get();
    }

    public static void clear() {
        CURRENT.remove();
    }

    /** Call AFTER placing each item to update running statistics. */
    public void update(int itemSize) {
        totalSize += itemSize;
        itemsSeen++;
        runningMeanSize = (double) totalSize / itemsSeen;
    }

    public double getRunningMeanSize() {
        return runningMeanSize;
    }

    public int getItemsSeen() {
        return itemsSeen;
    }
}
