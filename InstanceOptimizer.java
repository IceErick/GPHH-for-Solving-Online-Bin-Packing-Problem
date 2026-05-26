import java.util.*;

/**
 * Test-time heuristic optimizer that uses the remaining time budget
 * for instance-specific re-optimization via controlled mutation.
 * All packing respects online constraints.
 */
public class InstanceOptimizer {

    /**
     * Run instance-specific optimization within the time budget.
     * Tries four strategies: full-instance seed evaluation, small random trees,
     * terminal-swap fine-tuning, and standard subtree mutation. Returns the best
     * assignment found.
     */
    public static int[] optimize(BPPInstance instance, List<GPTree> seeds,
                                  int[] baselineAssignment, int baselineBins,
                                  long deadlineMillis) {
        return optimize(instance, seeds, baselineAssignment, baselineBins, deadlineMillis, false);
    }

    /**
     * Run instance-specific optimization, optionally allowing the empty bin
     * as a candidate in all heuristic evaluations.
     */
    public static int[] optimize(BPPInstance instance, List<GPTree> seeds,
                                  int[] baselineAssignment, int baselineBins,
                                  long deadlineMillis, boolean allowEmptyCandidate) {
        if (System.currentTimeMillis() >= deadlineMillis || seeds.isEmpty()) return null;

        Random rng = new Random();
        int[] bestAssignment = baselineAssignment;
        int bestBins = baselineBins;
        int tried = 0, improved = 0;

        // Strategy 1: Try all seeds on full instance
        for (GPTree seed : seeds) {
            if (System.currentTimeMillis() > deadlineMillis) break;
            int[] assignment = BPPSolver.pack(instance, seed, allowEmptyCandidate);
            int bins = countBins(assignment); tried++;
            if (bins < bestBins) { bestBins = bins; bestAssignment = assignment; improved++; }
        }

        // Strategy 2: Small random trees (depth 2-4) — explore novel regions
        // of heuristic space untouched by the pre-trained ensemble.
        while (System.currentTimeMillis() < deadlineMillis) {
            int depth = 2 + rng.nextInt(3);  // depth 2-4
            GPTree randomTree = GPTree.generateRandom(depth, rng);

            int[] assignment = BPPSolver.pack(instance, randomTree, allowEmptyCandidate);
            int bins = countBins(assignment); tried++;
            if (bins < bestBins) { bestBins = bins; bestAssignment = assignment; improved++; }
        }

        // Strategy 3: Light mutation — swap individual terminals (fine-tuning)
        GPTree base = seeds.get(0);
        int terminalSwaps = 0;
        while (System.currentTimeMillis() < deadlineMillis && terminalSwaps < 150) {
            GPTree tweaked = base.deepCopy();
            tweakTerminals(tweaked, rng);
            terminalSwaps++;

            int[] assignment = BPPSolver.pack(instance, tweaked, allowEmptyCandidate);
            int bins = countBins(assignment); tried++;
            if (bins < bestBins) { bestBins = bins; bestAssignment = assignment; improved++; }
        }

        // Strategy 4: Standard mutations of the best heuristics
        while (System.currentTimeMillis() < deadlineMillis) {
            GPTree src = seeds.get(rng.nextInt(seeds.size()));
            GPTree mutant = src.deepCopy();
            mutant.mutate(rng);
            mutant.enforceMaxDepth(rng);

            int[] assignment = BPPSolver.pack(instance, mutant, allowEmptyCandidate);
            int bins = countBins(assignment); tried++;
            if (bins < bestBins) { bestBins = bins; bestAssignment = assignment; improved++; }
        }

        System.err.println("[optimizer] tried=" + tried + " improved=" + improved
            + " bestBins=" + bestBins + " baseline=" + baselineBins);
        return bestAssignment;
    }

    /**
     * Replace one random terminal in the tree with a different terminal.
     * Collects paths to terminals on the copied tree to avoid reference issues.
     */
    private static void tweakTerminals(GPTree tree, Random rng) {
        // Collect terminal positions as paths from root (list of child indices)
        List<int[]> paths = new ArrayList<>();
        collectTerminalPaths(tree.getRoot(), new ArrayList<>(), paths);
        if (paths.isEmpty()) return;

        // Pick a random terminal path and generate a different replacement
        int[] path = paths.get(rng.nextInt(paths.size()));
        GPTerminal oldTerm = navigate(tree.getRoot(), path);
        GPTerminal newTerm = GPTerminal.randomTerminal(rng);
        int tries = 0;
        while (newTerm.getType() == oldTerm.getType() && tries < 7) {
            newTerm = GPTerminal.randomTerminal(rng);
            tries++;
        }

        // Replace by navigating to the parent
        GPNode parent = navigateParent(tree.getRoot(), path);
        if (parent != null) {
            parent.setChild(path[path.length - 1], newTerm);
        }
    }

    private static void collectTerminalPaths(GPNode node, List<Integer> currentPath,
                                              List<int[]> paths) {
        if (node.arity() == 0) {
            int[] arr = new int[currentPath.size()];
            for (int i = 0; i < arr.length; i++) arr[i] = currentPath.get(i);
            paths.add(arr);
        } else {
            for (int i = 0; i < node.arity(); i++) {
                GPNode child = node.getChild(i);
                if (child != null) {
                    currentPath.add(i);
                    collectTerminalPaths(child, currentPath, paths);
                    currentPath.remove(currentPath.size() - 1);
                }
            }
        }
    }

    private static GPTerminal navigate(GPNode root, int[] path) {
        GPNode node = root;
        for (int idx : path) {
            if (node == null || node.arity() <= idx) return null;
            node = node.getChild(idx);
        }
        return (node instanceof GPTerminal) ? (GPTerminal) node : null;
    }

    private static GPNode navigateParent(GPNode root, int[] path) {
        if (path.length == 0) return null;  // root has no parent
        GPNode node = root;
        for (int i = 0; i < path.length - 1; i++) {
            if (node == null || node.arity() <= path[i]) return null;
            node = node.getChild(path[i]);
        }
        return node;
    }

    private static int countBins(int[] assignment) {
        int max = -1;
        for (int a : assignment) {
            if (a > max) max = a;
        }
        return max + 1;
    }
}
