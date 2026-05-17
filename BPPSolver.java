import java.io.*;
import java.util.*;

/**
 * Online bin packing solver driven by a GP heuristic tree.
 * Items are packed strictly in arrival order; once placed, items cannot be moved.
 */
public class BPPSolver {

    /**
     * Pack items from an instance using a single GP heuristic.
     * Returns an array where result[i] = bin index for item i.
     */
    public static int[] pack(BPPInstance instance, GPTree heuristic) {
        int[] items = instance.getItems();
        int capacity = instance.getCapacity();
        int[] assignment = new int[items.length];

        List<Bin> bins = new ArrayList<>();
        PackingContext.beginPacking();

        for (int i = 0; i < items.length; i++) {
            int itemSize = items[i];

            double bestScore = Double.POSITIVE_INFINITY;
            int bestBinIndex = -1;

            // Evaluate all bins that can fit this item
            for (int b = 0; b < bins.size(); b++) {
                Bin bin = bins.get(b);
                if (bin.canFit(itemSize)) {
                    double score = heuristic.evaluate(bin, itemSize);
                    if (score < bestScore) {
                        bestScore = score;
                        bestBinIndex = b;
                    }
                }
            }

            if (bestBinIndex >= 0) {
                bins.get(bestBinIndex).addItem(itemSize);
                assignment[i] = bestBinIndex;
            } else {
                // Create new bin
                Bin newBin = new Bin(capacity);
                newBin.addItem(itemSize);
                bins.add(newBin);
                assignment[i] = bins.size() - 1;
            }

            // Update running statistics AFTER placing item (online constraint:
            // only past items inform the running mean for future decisions)
            PackingContext.get().update(itemSize);
        }

        return assignment;
    }

    /**
     * Run packing with multiple heuristics (ensemble) and return the best result.
     * Best = fewest bins used.
     */
    public static EnsembleResult packEnsemble(BPPInstance instance, List<GPTree> heuristics,
                                               long deadlineMillis) {
        int[] bestAssignment = null;
        int bestBins = Integer.MAX_VALUE;
        GPTree bestHeuristic = null;

        for (GPTree h : heuristics) {
            if (System.currentTimeMillis() > deadlineMillis) break;

            int[] assignment = pack(instance, h);
            int binsUsed = countBins(assignment);

            if (binsUsed < bestBins) {
                bestBins = binsUsed;
                bestAssignment = assignment;
                bestHeuristic = h;
            }
        }

        return new EnsembleResult(bestAssignment, bestBins, bestHeuristic);
    }

    /**
     * Count the number of distinct bins used (0-based indices → +1 for count).
     */
    private static int countBins(int[] assignment) {
        int maxBin = -1;
        for (int b : assignment) {
            if (b > maxBin) maxBin = b;
        }
        return maxBin + 1;
    }

    /**
     * Verify that a solution is feasible (all bin capacities respected).
     */
    public static boolean verifySolution(BPPInstance instance, int[] assignment) {
        int[] items = instance.getItems();
        int capacity = instance.getCapacity();
        int numBins = countBins(assignment);
        int[] binLoads = new int[numBins];

        for (int i = 0; i < items.length; i++) {
            int binIdx = assignment[i];
            if (binIdx < 0 || binIdx >= numBins) return false;
            binLoads[binIdx] += items[i];
            if (binLoads[binIdx] > capacity) return false;
        }
        return true;
    }

    /**
     * Write the solution file in the required format.
     * instanceName: e.g. "testdual0/binpack0.txt"
     */
    public static void writeSolution(String instanceName, int[] assignment,
                                      int l2Bound, String outputPath) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(outputPath))) {
            int numBins = countBins(assignment);
            pw.println("obj=\t" + numBins + "\t" + l2Bound);

            // Group items by bin and output
            List<List<Integer>> bins = new ArrayList<>(numBins);
            for (int b = 0; b < numBins; b++) bins.add(new ArrayList<>());
            for (int i = 0; i < assignment.length; i++) {
                bins.get(assignment[i]).add(i);
            }
            for (int b = 0; b < numBins; b++) {
                List<Integer> binItems = bins.get(b);
                for (int j = 0; j < binItems.size(); j++) {
                    pw.print(binItems.get(j));
                    if (j < binItems.size() - 1) pw.print(" ");
                }
                pw.println();
            }
        }
    }

    // ---- Deterministic online heuristics ----

    /**
     * Best-Fit: place item in bin with least remaining space after placing the item.
     * Equivalent to minimising (C - F - S), i.e. (EMPTINESS - ITEM_SIZE).
     */
    public static int[] packBestFit(BPPInstance instance) {
        int[] items = instance.getItems();
        int capacity = instance.getCapacity();
        int[] assignment = new int[items.length];
        List<Bin> bins = new ArrayList<>();

        for (int i = 0; i < items.length; i++) {
            int s = items[i];
            int bestIdx = -1;
            int bestWaste = Integer.MAX_VALUE;

            for (int b = 0; b < bins.size(); b++) {
                Bin bin = bins.get(b);
                int waste = bin.getRemainingSpace() - s;
                if (waste >= 0 && waste < bestWaste) {
                    bestWaste = waste;
                    bestIdx = b;
                }
            }
            if (bestIdx >= 0) {
                bins.get(bestIdx).addItem(s);
                assignment[i] = bestIdx;
            } else {
                Bin nb = new Bin(capacity);
                nb.addItem(s);
                bins.add(nb);
                assignment[i] = bins.size() - 1;
            }
        }
        return assignment;
    }

    /**
     * Modified Best-Fit: penalise bins with few items to encourage filling.
     * score = (C - F - S) * (1 + itemCount) effectively.
     */
    public static int[] packBestFitMod(BPPInstance instance) {
        int[] items = instance.getItems();
        int capacity = instance.getCapacity();
        int[] assignment = new int[items.length];
        List<Bin> bins = new ArrayList<>();

        for (int i = 0; i < items.length; i++) {
            int s = items[i];
            int bestIdx = -1;
            long bestScore = Long.MAX_VALUE;

            for (int b = 0; b < bins.size(); b++) {
                Bin bin = bins.get(b);
                int waste = bin.getRemainingSpace() - s;
                if (waste >= 0) {
                    // Weight: waste, but with bonus for bins that already have items
                    long score = (long) waste * (bin.getItemCount() + 1);
                    if (score < bestScore) {
                        bestScore = score;
                        bestIdx = b;
                    }
                }
            }
            if (bestIdx >= 0) {
                bins.get(bestIdx).addItem(s);
                assignment[i] = bestIdx;
            } else {
                Bin nb = new Bin(capacity);
                nb.addItem(s);
                bins.add(nb);
                assignment[i] = bins.size() - 1;
            }
        }
        return assignment;
    }

    /**
     * Worst-Fit: place item in bin with MOST remaining space after placing.
     * Keeps bins open for future items.
     */
    public static int[] packWorstFit(BPPInstance instance) {
        int[] items = instance.getItems();
        int capacity = instance.getCapacity();
        int[] assignment = new int[items.length];
        List<Bin> bins = new ArrayList<>();

        for (int i = 0; i < items.length; i++) {
            int s = items[i];
            int bestIdx = -1;
            int bestWaste = Integer.MIN_VALUE;

            for (int b = 0; b < bins.size(); b++) {
                Bin bin = bins.get(b);
                int waste = bin.getRemainingSpace() - s;
                if (waste >= 0 && waste > bestWaste) {
                    bestWaste = waste;
                    bestIdx = b;
                }
            }
            if (bestIdx >= 0) {
                bins.get(bestIdx).addItem(s);
                assignment[i] = bestIdx;
            } else {
                Bin nb = new Bin(capacity);
                nb.addItem(s);
                bins.add(nb);
                assignment[i] = bins.size() - 1;
            }
        }
        return assignment;
    }

    /**
     * First-Fit: place item in the first bin that can accommodate it.
     */
    public static int[] packFirstFit(BPPInstance instance) {
        int[] items = instance.getItems();
        int capacity = instance.getCapacity();
        int[] assignment = new int[items.length];
        List<Bin> bins = new ArrayList<>();

        for (int i = 0; i < items.length; i++) {
            int s = items[i];
            int target = -1;
            for (int b = 0; b < bins.size(); b++) {
                if (bins.get(b).canFit(s)) {
                    target = b;
                    break;
                }
            }
            if (target >= 0) {
                bins.get(target).addItem(s);
                assignment[i] = target;
            } else {
                Bin nb = new Bin(capacity);
                nb.addItem(s);
                bins.add(nb);
                assignment[i] = bins.size() - 1;
            }
        }
        return assignment;
    }

    /**
     * Result from ensemble packing.
     */
    public static class EnsembleResult {
        public final int[] assignment;
        public final int binsUsed;
        public final GPTree heuristic;

        public EnsembleResult(int[] assignment, int binsUsed, GPTree heuristic) {
            this.assignment = assignment;
            this.binsUsed = binsUsed;
            this.heuristic = heuristic;
        }
    }

    /**
     * Pack with pilot-based heuristic selection.
     * Runs all GP heuristics on the first pilotItems, picks the best one,
     * then repacks all items from scratch with that heuristic.
     * Also tries deterministic heuristics on the full instance.
     */
    public static int[] packWithPilot(BPPInstance instance, List<GPTree> heuristics,
                                       int pilotItems, long deadlineMillis) {
        int[] items = instance.getItems();
        int[] bestAssignment = null;
        int bestBins = Integer.MAX_VALUE;

        // Phase 1: Pilot — run each GP heuristic on first pilotItems
        if (pilotItems > 0 && pilotItems < items.length && !heuristics.isEmpty()) {
            GPTree bestHeuristic = null;
            int bestPilotBins = Integer.MAX_VALUE;

            for (GPTree h : heuristics) {
                if (System.currentTimeMillis() > deadlineMillis) break;
                int bins = countBinsPilot(instance, h, pilotItems);
                if (bins < bestPilotBins) {
                    bestPilotBins = bins;
                    bestHeuristic = h;
                }
            }

            // Phase 2: Repack ALL items from scratch with the best heuristic
            if (bestHeuristic != null && System.currentTimeMillis() <= deadlineMillis) {
                int[] assignment = pack(instance, bestHeuristic);
                int bins = countBins(assignment);
                if (bins < bestBins) {
                    bestBins = bins;
                    bestAssignment = assignment;
                }
            }
        }

        // Fallback: try all GP heuristics on full instance
        for (GPTree h : heuristics) {
            if (System.currentTimeMillis() > deadlineMillis) break;
            int[] assignment = pack(instance, h);
            int bins = countBins(assignment);
            if (bins < bestBins) {
                bestBins = bins;
                bestAssignment = assignment;
            }
        }

        // Try deterministic heuristics
        if (System.currentTimeMillis() <= deadlineMillis) {
            int[] bf = packBestFit(instance);
            int bfBins = countBins(bf);
            if (bfBins < bestBins) { bestBins = bfBins; bestAssignment = bf; }
        }
        if (System.currentTimeMillis() <= deadlineMillis) {
            int[] bfm = packBestFitMod(instance);
            int bfmBins = countBins(bfm);
            if (bfmBins < bestBins) { bestBins = bfmBins; bestAssignment = bfm; }
        }
        if (System.currentTimeMillis() <= deadlineMillis) {
            int[] ff = packFirstFit(instance);
            int ffBins = countBins(ff);
            if (ffBins < bestBins) { bestBins = ffBins; bestAssignment = ff; }
        }
        if (System.currentTimeMillis() <= deadlineMillis) {
            int[] wf = packWorstFit(instance);
            int wfBins = countBins(wf);
            if (wfBins < bestBins) { bestBins = wfBins; bestAssignment = wf; }
        }

        return bestAssignment;
    }

    /**
     * Pack only the first n items and return the number of bins used.
     * Used for pilot phase to quickly evaluate heuristics.
     */
    private static int countBinsPilot(BPPInstance instance, GPTree heuristic, int n) {
        int[] items = instance.getItems();
        int capacity = instance.getCapacity();
        List<Bin> bins = new ArrayList<>();
        PackingContext.beginPacking();

        for (int i = 0; i < n; i++) {
            int itemSize = items[i];
            double bestScore = Double.POSITIVE_INFINITY;
            int bestBinIndex = -1;

            for (int b = 0; b < bins.size(); b++) {
                Bin bin = bins.get(b);
                if (bin.canFit(itemSize)) {
                    double score = heuristic.evaluate(bin, itemSize);
                    if (score < bestScore) {
                        bestScore = score;
                        bestBinIndex = b;
                    }
                }
            }

            if (bestBinIndex >= 0) {
                bins.get(bestBinIndex).addItem(itemSize);
            } else {
                Bin newBin = new Bin(capacity);
                newBin.addItem(itemSize);
                bins.add(newBin);
            }

            PackingContext.get().update(itemSize);
        }

        return bins.size();
    }
}
