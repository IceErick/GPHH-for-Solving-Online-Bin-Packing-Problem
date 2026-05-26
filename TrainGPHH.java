import java.io.*;
import java.util.*;

/**
 * Training pipeline for the GPHH bin packing heuristic.
 * Loads training instances, runs GP evolution, and serializes the best heuristics.
 */
public class TrainGPHH {

    public static void main(String[] args) {
        System.out.println("=== GPHH Training Pipeline v2 ===");
        System.out.println();

        // Load original training instances
        List<BPPInstance> originalInstances = loadTrainingInstances("train");
        System.out.println("Loaded " + originalInstances.size() + " original training instances");

        // Load v3 augmented training instances (non-stationary, 500 items each)
        List<BPPInstance> augmentedInstances = loadTrainingInstances("train_augmented_v3");
        System.out.println("Loaded " + augmentedInstances.size() + " augmented v3 instances (500 items each)");

        // Categorize by type for balanced selection
        List<BPPInstance> augNonStationary = new ArrayList<>();  // switch/alternating blocks
        List<BPPInstance> augMixed = new ArrayList<>();           // mixed-ratio stationary (42-44 avg)
        List<BPPInstance> augStationary = new ArrayList<>();      // pure large/small
        for (BPPInstance inst : augmentedInstances) {
            String name = inst.getName();
            if (name.startsWith("ns_switch") || name.startsWith("ns_alternating")) {
                augNonStationary.add(inst);
            } else if (name.startsWith("ns_mixed")) {
                augMixed.add(inst);
            } else {
                augStationary.add(inst);
            }
        }
        System.out.println("  Non-stationary (switch/alternating): " + augNonStationary.size());
        System.out.println("  Mixed-ratio (42-44 target): " + augMixed.size());
        System.out.println("  Pure cluster: " + augStationary.size());

        // Build training set: balanced with emphasis on non-stationary
        List<BPPInstance> allTraining = new ArrayList<>();
        allTraining.addAll(originalInstances);  // 20 originals (bimodal distribution)

        // Non-stationary: forces adaptive MEAN_SIZE usage
        Collections.shuffle(augNonStationary, new Random(42));
        int numNS = Math.min(80, augNonStationary.size());
        for (int i = 0; i < numNS; i++) {
            allTraining.add(augNonStationary.get(i));
        }

        // Mixed-ratio: targets testdual4/8 distribution
        Collections.shuffle(augMixed, new Random(123));
        int numMixed = Math.min(40, augMixed.size());
        for (int i = 0; i < numMixed; i++) {
            allTraining.add(augMixed.get(i));
        }

        // Add some pure stationary for baseline diversity
        Collections.shuffle(augStationary, new Random(456));
        int numStat = Math.min(10, augStationary.size());
        for (int i = 0; i < numStat; i++) {
            allTraining.add(augStationary.get(i));
        }

        System.out.println("Total generalist training instances: " + allTraining.size());
        System.out.println("Bin capacity: " + allTraining.get(0).getCapacity());

        double totalSum = 0;
        for (BPPInstance inst : allTraining) {
            for (int item : inst.getItems()) totalSum += item;
        }
        double avgSum = totalSum / allTraining.size();
        System.out.println("Avg instance total volume: " + String.format("%.0f", avgSum)
            + " (min bins: " + String.format("%.0f", avgSum / allTraining.get(0).getCapacity()) + ")");
        System.out.println();

        List<GPTree> allBestTrees = new ArrayList<>();

        // ---- Phase 1: Generalist Training (3 runs) ----
        // Trained on the full mixed set for broad applicability.
        int numGeneralists = 3;
        System.out.println("=== Phase 1: Generalist Training (" + numGeneralists + " runs) ===");
        for (int run = 0; run < numGeneralists; run++) {
            long seed = 42 + run * 1000;
            System.out.println("--- Generalist Run " + (run + 1) + "/" + numGeneralists
                + " (seed=" + seed + ") ---");

            GPEngine engine = new GPEngine(seed, allTraining);
            GPTree bestTree = engine.evolve();

            int totalBins = 0;
            for (BPPInstance inst : allTraining) {
                int[] assignment = BPPSolver.pack(inst, bestTree);
                totalBins += countBins(assignment);
            }
            double avgBins = (double) totalBins / allTraining.size();
            System.out.println("  Best tree: avg bins = " + String.format("%.1f", avgBins)
                + ", size = " + bestTree.getSize() + ", depth = " + bestTree.getDepth());
            System.out.println("  Best tree: " + bestTree.toString());

            allBestTrees.add(bestTree);
            serializeTree(bestTree, "trained_heuristic_" + (run + 1) + ".ser");
            System.out.println();
        }

        // ---- Phase 2: Specialist Training (5 runs) ----
        // Filter instances with avg item size 42-44 (matching testdual4/8 distribution).
        // Specialists learn adaptive strategies for mid-size items.
        List<BPPInstance> specialistInstances = new ArrayList<>();
        for (BPPInstance inst : augmentedInstances) {
            double avg = avgItemSize(inst);
            if (avg >= 42.0 && avg <= 44.0) {
                specialistInstances.add(inst);
            }
        }
        System.out.println("Specialist instances (avg 42-44): " + specialistInstances.size()
            + " out of " + augmentedInstances.size());
        // Also include some originals that match the mid-size range
        for (BPPInstance inst : originalInstances) {
            double avg = avgItemSize(inst);
            if (avg >= 42.0 && avg <= 44.0) {
                specialistInstances.add(inst);
            }
        }
        System.out.println("Specialist instances (with originals): " + specialistInstances.size());
        System.out.println();

        int numSpecialists = 5;
        System.out.println("=== Phase 2: Specialist Training (" + numSpecialists + " runs) ===");
        for (int run = 0; run < numSpecialists; run++) {
            long seed = 10000 + run * 1000;
            System.out.println("--- Specialist Run " + (run + 1) + "/" + numSpecialists
                + " (seed=" + seed + ") ---");

            GPEngine engine = new GPEngine(seed, specialistInstances);
            GPTree bestTree = engine.evolve();

            int totalBins = 0;
            for (BPPInstance inst : specialistInstances) {
                int[] assignment = BPPSolver.pack(inst, bestTree);
                totalBins += countBins(assignment);
            }
            double avgBins = (double) totalBins / specialistInstances.size();
            System.out.println("  Best tree: avg bins = " + String.format("%.1f", avgBins)
                + ", size = " + bestTree.getSize() + ", depth = " + bestTree.getDepth());
            System.out.println("  Best tree: " + bestTree.toString());

            allBestTrees.add(bestTree);
            serializeTree(bestTree, "trained_specialist_" + (run + 1) + ".ser");
            System.out.println();
        }

        // Serialize the best generalist as default
        serializeTree(allBestTrees.get(0), "trained_heuristic.ser");

        // Serialize combined ensemble (3 generalists + 5 specialists = 8)
        System.out.println("Serializing ensemble of " + allBestTrees.size()
            + " heuristics (" + numGeneralists + " generalist + " + numSpecialists + " specialist)...");
        try (ObjectOutputStream oos = new ObjectOutputStream(
                new FileOutputStream("trained_ensemble.ser"))) {
            oos.writeObject(allBestTrees);
            System.out.println("Ensemble saved to trained_ensemble.ser");
        } catch (IOException e) {
            System.err.println("Failed to serialize ensemble: " + e.getMessage());
        }

        System.out.println();
        System.out.println("=== Training Complete ===");
    }

    private static List<BPPInstance> loadTrainingInstances(String trainDir) {
        List<BPPInstance> instances = new ArrayList<>();
        File dir = new File(trainDir);
        if (!dir.exists() || !dir.isDirectory()) {
            System.err.println("Training directory not found: " + trainDir);
            return instances;
        }
        File[] files = dir.listFiles((d, name) -> name.endsWith(".txt"));
        if (files != null) {
            Arrays.sort(files);
            for (File f : files) {
                try {
                    instances.add(new BPPInstance(f.getPath()));
                } catch (IOException e) {
                    System.err.println("Failed to load " + f.getName() + ": " + e.getMessage());
                }
            }
        }
        return instances;
    }

    private static void serializeTree(GPTree tree, String filename) {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(filename))) {
            oos.writeObject(tree);
            System.out.println("  Saved: " + filename);
        } catch (IOException e) {
            System.err.println("  Failed to save " + filename + ": " + e.getMessage());
        }
    }

    private static int countBins(int[] assignment) {
        int max = -1;
        for (int a : assignment) {
            if (a > max) max = a;
        }
        return max + 1;
    }

    private static double avgItemSize(BPPInstance instance) {
        int[] items = instance.getItems();
        long sum = 0;
        for (int s : items) sum += s;
        return (double) sum / items.length;
    }
}
