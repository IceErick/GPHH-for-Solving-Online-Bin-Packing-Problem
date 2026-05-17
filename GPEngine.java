import java.util.*;

/**
 * Genetic Programming engine with memory mechanism (Burke et al. 2010).
 * Evolves a population of GP trees to minimise bins used across training instances.
 */
public class GPEngine {

    private static final int POP_SIZE = 150;
    private static final int GENERATIONS = 60;
    private static final int TOURNAMENT_SIZE = 3;
    private static final double CROSSOVER_RATE = 0.8;
    private static final double MUTATION_RATE = 0.20;
    private static final int MAX_INITIAL_DEPTH = 5;
    private static final int ELITE_COUNT = 3;
    private static final int MEMORY_SIZE = 10;
    private static final int MEMORY_INJECT_INTERVAL = 5;

    private List<GPTree> population;
    private List<GPTree> memoryPool;
    private final Random rng;
    private final List<BPPInstance> trainingInstances;

    private double bestEverFitness = Double.POSITIVE_INFINITY;
    private GPTree bestEverTree = null;

    public GPEngine(long seed, List<BPPInstance> trainingInstances) {
        this.rng = new Random(seed);
        this.trainingInstances = trainingInstances;
        this.population = new ArrayList<>();
        this.memoryPool = new ArrayList<>();
    }

    public void initialise() {
        population.clear();
        // Single seed: Best-Fit as performance floor without dominating gene pool
        population.add(makeBestFitTree());

        // Fill rest with random trees
        while (population.size() < POP_SIZE) {
            population.add(GPTree.generateRandom(MAX_INITIAL_DEPTH, rng));
        }
    }

    // Best-Fit: SUB(EMPTINESS, ITEM_SIZE)  → minimize E - S
    private GPTree makeBestFitTree() {
        GPOperator root = new GPOperator(GPOperator.Type.SUB);
        root.setChild(0, new GPTerminal(GPTerminal.Type.EMPTINESS));
        root.setChild(1, new GPTerminal(GPTerminal.Type.ITEM_SIZE));
        return new GPTree(root);
    }

    public GPTree evolve() {
        initialise();
        fitnessMap.clear();
        evaluatePopulation();

        double bestFit = getBestFitness();
        System.out.println("Gen 0: best=" + String.format("%.4f", bestFit)
            + " avg=" + String.format("%.4f", getAvgFitness()));

        for (int gen = 1; gen <= GENERATIONS; gen++) {
            List<GPTree> newPopulation = new ArrayList<>();

            // Elitism
            List<GPTree> sorted = getSortedPopulation();
            for (int i = 0; i < ELITE_COUNT; i++) {
                newPopulation.add(sorted.get(i).deepCopy());
            }

            // Fill population
            while (newPopulation.size() < POP_SIZE) {
                GPTree offspring;
                if (rng.nextDouble() < CROSSOVER_RATE) {
                    GPTree p1 = tournamentSelect();
                    GPTree p2 = tournamentSelect();
                    offspring = p1.crossover(p2, rng);
                } else if (rng.nextDouble() < MUTATION_RATE / (1.0 - CROSSOVER_RATE)) {
                    offspring = tournamentSelect().deepCopy();
                    offspring.mutate(rng);
                } else {
                    offspring = tournamentSelect().deepCopy();
                }
                newPopulation.add(offspring);
            }

            population = newPopulation;
            fitnessMap.clear();
            evaluatePopulation();

            // Memory mechanism
            GPTree bestOfGen = getBestTree();
            updateMemory(bestOfGen);

            if (gen % MEMORY_INJECT_INTERVAL == 0 && !memoryPool.isEmpty()) {
                injectFromMemory();
            }

            bestFit = getBestFitness();
            if (bestFit < bestEverFitness) {
                bestEverFitness = bestFit;
                bestEverTree = getBestTree().deepCopy();
            }

            if (gen % 10 == 0 || gen == 1 || gen == GENERATIONS) {
                System.out.println("Gen " + gen + ": best=" + String.format("%.4f", bestFit)
                    + " avg=" + String.format("%.4f", getAvgFitness())
                    + " everBest=" + String.format("%.4f", bestEverFitness)
                    + " bestSize=" + getBestTree().getSize()
                    + " depth=" + getBestTree().getDepth());
            }
        }

        return bestEverTree != null ? bestEverTree : getBestTree();
    }

    // ---- Fitness ----

    private final IdentityHashMap<GPTree, Double> fitnessMap = new IdentityHashMap<>();

    /**
     * Fitness = averageBins + totalWaste / (totalItems * capacity * numInstances)
     * The integer part is the bin count (primary objective).
     * The fractional part breaks ties by preferring less total waste.
     */
    private void evaluatePopulation() {
        for (GPTree tree : population) {
            evaluateFitness(tree);
        }
    }

    private double evaluateFitness(GPTree tree) {
        Double cached = fitnessMap.get(tree);
        if (cached != null) return cached;

        int totalBins = 0;
        long totalWaste = 0;
        long totalItems = 0;
        int capacity = trainingInstances.get(0).getCapacity();

        for (BPPInstance inst : trainingInstances) {
            int[] assignment = BPPSolver.pack(inst, tree);
            int bins = countBins(assignment);
            totalBins += bins;
            totalItems += inst.getItemCount();

            // Compute waste for this instance
            int[] binLoads = new int[bins];
            int[] items = inst.getItems();
            for (int i = 0; i < items.length; i++) {
                binLoads[assignment[i]] += items[i];
            }
            for (int load : binLoads) {
                totalWaste += (capacity - load);
            }
        }

        double avgBins = (double) totalBins / trainingInstances.size();
        // Normalize waste to [0, 1) range so it only breaks ties
        double wasteRatio = (double) totalWaste / (totalItems * (long) capacity);
        double fitness = avgBins + wasteRatio / trainingInstances.size();

        fitnessMap.put(tree, fitness);
        return fitness;
    }

    private double getFitness(GPTree tree) {
        Double f = fitnessMap.get(tree);
        if (f != null) return f;
        return evaluateFitness(tree);
    }

    private int countBins(int[] assignment) {
        int max = -1;
        for (int a : assignment) {
            if (a > max) max = a;
        }
        return max + 1;
    }

    private double getBestFitness() {
        double best = Double.POSITIVE_INFINITY;
        for (GPTree t : population) {
            double f = getFitness(t);
            if (f < best) best = f;
        }
        return best;
    }

    private double getAvgFitness() {
        double sum = 0;
        for (GPTree t : population) sum += getFitness(t);
        return sum / population.size();
    }

    private GPTree getBestTree() {
        GPTree best = null;
        double bestFit = Double.POSITIVE_INFINITY;
        for (GPTree t : population) {
            double f = getFitness(t);
            if (f < bestFit) {
                bestFit = f;
                best = t;
            }
        }
        return best;
    }

    private List<GPTree> getSortedPopulation() {
        List<GPTree> sorted = new ArrayList<>(population);
        sorted.sort(Comparator.comparingDouble(this::getFitness));
        return sorted;
    }

    // ---- Selection ----

    private GPTree tournamentSelect() {
        GPTree best = null;
        double bestFit = Double.POSITIVE_INFINITY;
        for (int i = 0; i < TOURNAMENT_SIZE; i++) {
            GPTree c = population.get(rng.nextInt(population.size()));
            double f = getFitness(c);
            if (f < bestFit) {
                bestFit = f;
                best = c;
            }
        }
        return best;
    }

    // ---- Memory Mechanism ----

    private void updateMemory(GPTree bestOfGen) {
        double genFit = getFitness(bestOfGen);

        if (memoryPool.size() < MEMORY_SIZE) {
            GPTree copy = bestOfGen.deepCopy();
            fitnessMap.put(copy, genFit);
            memoryPool.add(copy);
            return;
        }

        int worstIdx = -1;
        double worstFit = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < memoryPool.size(); i++) {
            double f = getFitness(memoryPool.get(i));
            if (f > worstFit) {
                worstFit = f;
                worstIdx = i;
            }
        }

        if (genFit < worstFit && worstIdx >= 0) {
            GPTree copy = bestOfGen.deepCopy();
            fitnessMap.put(copy, genFit);
            memoryPool.set(worstIdx, copy);
        }
    }

    private void injectFromMemory() {
        int worstIdx = -1;
        double worstFit = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < population.size(); i++) {
            double f = getFitness(population.get(i));
            if (f > worstFit) {
                worstFit = f;
                worstIdx = i;
            }
        }

        GPTree memTree = memoryPool.get(rng.nextInt(memoryPool.size()));
        population.set(worstIdx, memTree.deepCopy());
    }

    // ---- Results ----

    public List<GPTree> getTopTrees(int n) {
        List<GPTree> sorted = getSortedPopulation();
        List<GPTree> top = new ArrayList<>();
        for (int i = 0; i < Math.min(n, sorted.size()); i++) {
            top.add(sorted.get(i).deepCopy());
        }
        return top;
    }

    public List<GPTree> getMemoryPool() {
        return new ArrayList<>(memoryPool);
    }
}
