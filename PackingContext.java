/**
 * ThreadLocal context for running statistics during online bin packing.
 * Enables GP trees to access global packing state (running mean, memory of
 * recent pieces, and the is-new-bin-candidate flag) without changing the
 * evaluate() interface.
 */
public class PackingContext {
    private static final ThreadLocal<PackingContext> CURRENT = new ThreadLocal<>();

    // ---- Running mean (MEAN_SIZE terminal) ----
    private double runningMeanSize;
    private int itemsSeen;
    private int totalSize;

    // ---- IS_NEW_BIN terminal ----
    private boolean evaluatingNewBin;

    // ---- MIN_SIZE / MAX_SIZE terminals ----
    private int minPieceSize = Integer.MAX_VALUE;
    private int maxPieceSize = 0;

    // ---- FI terminal: sliding window of last 100 pieces ----
    private static final int MEMORY_SIZE = 100;
    private final int[] recentPieces = new int[MEMORY_SIZE];
    private int memoryIndex = 0;
    private int memoryCount = 0;
    private final int[] freqHistogram = new int[101]; // capacity=100, sizes 0-100
    private final int[] prefixFreq = new int[101];     // prefix sums for O(1) proportion

    public PackingContext() {
        this.runningMeanSize = 0.0;
        this.itemsSeen = 0;
        this.totalSize = 0;
        this.evaluatingNewBin = false;
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

        // Min / max tracking
        if (itemSize < minPieceSize) minPieceSize = itemSize;
        if (itemSize > maxPieceSize) maxPieceSize = itemSize;

        // Sliding window for FI terminal
        if (memoryCount == MEMORY_SIZE) {
            int oldPiece = recentPieces[memoryIndex];
            freqHistogram[oldPiece]--;
        } else {
            memoryCount++;
        }
        recentPieces[memoryIndex] = itemSize;
        freqHistogram[itemSize]++;
        memoryIndex = (memoryIndex + 1) % MEMORY_SIZE;

        // Rebuild prefix sums (O(100) once per item, makes FI O(1))
        prefixFreq[0] = freqHistogram[0];
        for (int i = 1; i <= 100; i++) {
            prefixFreq[i] = prefixFreq[i - 1] + freqHistogram[i];
        }
    }

    // ---- Getters ----

    public double getRunningMeanSize() {
        return runningMeanSize;
    }

    public int getItemsSeen() {
        return itemsSeen;
    }

    public boolean isEvaluatingNewBin() {
        return evaluatingNewBin;
    }

    public void setEvaluatingNewBin(boolean v) {
        this.evaluatingNewBin = v;
    }

    public int getMinPieceSize() {
        return itemsSeen > 0 ? minPieceSize : 0;
    }

    public int getMaxPieceSize() {
        return itemsSeen > 0 ? maxPieceSize : 0;
    }

    /**
     * Proportion of pieces in the memory window ≤ size.
     * Used by FI terminal. O(1) thanks to prefix sums.
     */
    public double getProportionFitting(int size) {
        if (memoryCount == 0) return 0.0;
        int limit = Math.max(0, Math.min(size, 100));
        return (double) prefixFreq[limit] / memoryCount;
    }
}
