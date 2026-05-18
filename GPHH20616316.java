import java.io.*;
import java.util.*;

/**
 * GPHH for Online Bin Packing Problem - Testing Entry Point.
 *
 * Usage:
 *   java GPHH20616316 -s instance_file -o solution_file -t max_time
 *
 * Student ID: 20616316
 */
public class GPHH20616316 {

    private static final String ENSEMBLE_FILE = "trained_ensemble.ser";
    private static final String SINGLE_HEURISTIC_FILE = "trained_heuristic.ser";

    /** Pre-loaded L2 lower bounds for test instances (from coursework). */
    private static final Map<String, Integer> L2_BOUNDS = new HashMap<>();
    static {
        // testdual0
        L2_BOUNDS.put("testdual0/binpack0.txt", 2508);
        L2_BOUNDS.put("testdual0/binpack1.txt", 2536);
        L2_BOUNDS.put("testdual0/binpack2.txt", 2500);
        L2_BOUNDS.put("testdual0/binpack3.txt", 2529);
        L2_BOUNDS.put("testdual0/binpack4.txt", 2578);
        L2_BOUNDS.put("testdual0/binpack5.txt", 2549);
        L2_BOUNDS.put("testdual0/binpack6.txt", 2503);
        L2_BOUNDS.put("testdual0/binpack7.txt", 2517);
        L2_BOUNDS.put("testdual0/binpack8.txt", 2519);
        L2_BOUNDS.put("testdual0/binpack9.txt", 2524);
        L2_BOUNDS.put("testdual0/binpack10.txt", 2495);
        L2_BOUNDS.put("testdual0/binpack11.txt", 2501);
        L2_BOUNDS.put("testdual0/binpack12.txt", 2505);
        L2_BOUNDS.put("testdual0/binpack13.txt", 2561);
        L2_BOUNDS.put("testdual0/binpack14.txt", 2525);
        L2_BOUNDS.put("testdual0/binpack15.txt", 2517);
        L2_BOUNDS.put("testdual0/binpack16.txt", 2523);
        L2_BOUNDS.put("testdual0/binpack17.txt", 2525);
        L2_BOUNDS.put("testdual0/binpack18.txt", 2533);
        L2_BOUNDS.put("testdual0/binpack19.txt", 2507);
        // testdual4
        L2_BOUNDS.put("testdual4/binpack0.txt", 2123);
        L2_BOUNDS.put("testdual4/binpack1.txt", 2117);
        L2_BOUNDS.put("testdual4/binpack2.txt", 2130);
        L2_BOUNDS.put("testdual4/binpack3.txt", 2124);
        L2_BOUNDS.put("testdual4/binpack4.txt", 2133);
        L2_BOUNDS.put("testdual4/binpack5.txt", 2116);
        L2_BOUNDS.put("testdual4/binpack6.txt", 2126);
        L2_BOUNDS.put("testdual4/binpack7.txt", 2131);
        L2_BOUNDS.put("testdual4/binpack8.txt", 2117);
        L2_BOUNDS.put("testdual4/binpack9.txt", 2139);
        L2_BOUNDS.put("testdual4/binpack10.txt", 2122);
        L2_BOUNDS.put("testdual4/binpack11.txt", 2113);
        L2_BOUNDS.put("testdual4/binpack12.txt", 2122);
        L2_BOUNDS.put("testdual4/binpack13.txt", 2127);
        L2_BOUNDS.put("testdual4/binpack14.txt", 2121);
        L2_BOUNDS.put("testdual4/binpack15.txt", 2123);
        L2_BOUNDS.put("testdual4/binpack16.txt", 2126);
        L2_BOUNDS.put("testdual4/binpack17.txt", 2127);
        L2_BOUNDS.put("testdual4/binpack18.txt", 2126);
        L2_BOUNDS.put("testdual4/binpack19.txt", 2121);
        // testdual8
        L2_BOUNDS.put("testdual8/binpack0.txt", 2116);
        L2_BOUNDS.put("testdual8/binpack1.txt", 2115);
        L2_BOUNDS.put("testdual8/binpack2.txt", 2127);
        L2_BOUNDS.put("testdual8/binpack3.txt", 2126);
        L2_BOUNDS.put("testdual8/binpack4.txt", 2129);
        L2_BOUNDS.put("testdual8/binpack5.txt", 2128);
        L2_BOUNDS.put("testdual8/binpack6.txt", 2126);
        L2_BOUNDS.put("testdual8/binpack7.txt", 2126);
        L2_BOUNDS.put("testdual8/binpack8.txt", 2128);
        L2_BOUNDS.put("testdual8/binpack9.txt", 2114);
        L2_BOUNDS.put("testdual8/binpack10.txt", 2124);
        L2_BOUNDS.put("testdual8/binpack11.txt", 2131);
        L2_BOUNDS.put("testdual8/binpack12.txt", 2127);
        L2_BOUNDS.put("testdual8/binpack13.txt", 2123);
        L2_BOUNDS.put("testdual8/binpack14.txt", 2140);
        L2_BOUNDS.put("testdual8/binpack15.txt", 2118);
        L2_BOUNDS.put("testdual8/binpack16.txt", 2110);
        L2_BOUNDS.put("testdual8/binpack17.txt", 2141);
        L2_BOUNDS.put("testdual8/binpack18.txt", 2129);
        L2_BOUNDS.put("testdual8/binpack19.txt", 2134);
    }

    @SuppressWarnings("unchecked")
    public static void main(String[] args) {
        // Parse command-line arguments
        String instanceFile = null;
        String solutionFile = null;
        int maxTime = 10;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-s": instanceFile = args[++i]; break;
                case "-o": solutionFile = args[++i]; break;
                case "-t": maxTime = Integer.parseInt(args[++i]); break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.err.println("Usage: java GPHH20616316 -s instance_file -o solution_file -t max_time");
                    System.exit(1);
            }
        }

        if (instanceFile == null || solutionFile == null) {
            System.err.println("Usage: java GPHH20616316 -s instance_file -o solution_file -t max_time");
            System.exit(1);
        }

        long deadline = System.currentTimeMillis() + maxTime * 1000L;

        try {
            // Load problem instance
            BPPInstance instance = new BPPInstance(instanceFile);
            int l2Bound = lookupL2Bound(instanceFile);

            // Run all strategies (GP-trained + deterministic) and pick best
            long deadlineMillis = deadline - 500;  // leave 500ms for output

            int[] bestAssignment = null;
            int bestBins = Integer.MAX_VALUE;

            // Load all GP heuristics
            List<GPTree> heuristics = loadHeuristics();

            // Use pilot-based selection: try all heuristics on first 200 items,
            // pick the best one, then run it on the full instance.
            // Pilot phase takes ~0.05s total, leaving ~9.45s for main packing.
            int pilotItems = 200;
            if (!heuristics.isEmpty()) {
                bestAssignment = BPPSolver.packWithPilot(instance, heuristics, pilotItems, deadlineMillis);
                if (bestAssignment != null) {
                    bestBins = countBins(bestAssignment);
                }
            }

            if (bestAssignment == null) {
                System.err.println("Failed to produce a solution within time limit.");
                System.exit(1);
            }

            // Verify solution
            if (!BPPSolver.verifySolution(instance, bestAssignment)) {
                System.err.println("Warning: solution verification failed!");
            }

            // Build instance name for output: e.g. "testdual0/binpack0.txt"
            String parentDir = new File(instanceFile).getParentFile() != null
                ? new File(instanceFile).getParentFile().getName() : "";
            String instanceName = parentDir + "/" + instance.getName();

            // Write solution file
            BPPSolver.writeSolution(instanceName, bestAssignment, l2Bound, solutionFile);

            System.out.println("obj=" + bestBins + " L2=" + l2Bound
                + " gap=" + (bestBins - l2Bound));

        } catch (IOException e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<GPTree> loadHeuristics() {
        List<GPTree> heuristics = new ArrayList<>();

        // Try loading ensemble first
        File ensembleFile = new File(ENSEMBLE_FILE);
        if (ensembleFile.exists()) {
            try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(ensembleFile))) {
                Object obj = ois.readObject();
                if (obj instanceof List) {
                    heuristics = (List<GPTree>) obj;
                }
            } catch (Exception e) {
                System.err.println("Failed to load ensemble: " + e.getMessage());
            }
        }

        // Fall back to single heuristic
        if (heuristics.isEmpty()) {
            File singleFile = new File(SINGLE_HEURISTIC_FILE);
            if (singleFile.exists()) {
                try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(singleFile))) {
                    GPTree tree = (GPTree) ois.readObject();
                    heuristics.add(tree);
                } catch (Exception e) {
                    System.err.println("Failed to load heuristic: " + e.getMessage());
                }
            }
        }

        return heuristics;
    }

    private static int countBins(int[] assignment) {
        int max = -1;
        for (int a : assignment) {
            if (a > max) max = a;
        }
        return max + 1;
    }

    /**
     * Look up the L2 lower bound for a test instance from its file path.
     */
    private static int lookupL2Bound(String instanceFilePath) {
        // Convert path like "test/testdual0/binpack0.txt" to key "testdual0/binpack0.txt"
        String normalized = instanceFilePath.replace('\\', '/');
        for (String key : L2_BOUNDS.keySet()) {
            if (normalized.endsWith(key)) {
                return L2_BOUNDS.get(key);
            }
        }
        // If not found, try to match by filename only
        String filename = new File(instanceFilePath).getName();
        String parent = new File(instanceFilePath).getParentFile() != null
            ? new File(instanceFilePath).getParentFile().getName() : "";
        String key = parent + "/" + filename;
        Integer bound = L2_BOUNDS.get(key);
        if (bound != null) return bound;

        System.err.println("Warning: L2 bound not found for " + instanceFilePath + ", using 0");
        return 0;
    }
}
