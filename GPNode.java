import java.io.Serializable;
import java.util.Random;

/**
 * Abstract node in a Genetic Programming expression tree.
 * Each node evaluates to a double given a bin state and incoming item size.
 */
public abstract class GPNode implements Serializable {
    private static final long serialVersionUID = 1L;

    /** Evaluate this node given the current bin and incoming item size. */
    public abstract double evaluate(Bin bin, int itemSize);

    /** Number of child nodes. */
    public abstract int arity();

    /** Get child at index i. */
    public abstract GPNode getChild(int i);

    /** Set child at index i. */
    public abstract void setChild(int i, GPNode child);

    /** Total number of nodes in this subtree. */
    public int getSize() {
        int size = 1;
        for (int i = 0; i < arity(); i++) {
            if (getChild(i) != null) {
                size += getChild(i).getSize();
            }
        }
        return size;
    }

    /** Maximum depth of this subtree. */
    public int getDepth() {
        int maxChildDepth = 0;
        for (int i = 0; i < arity(); i++) {
            if (getChild(i) != null) {
                maxChildDepth = Math.max(maxChildDepth, getChild(i).getDepth());
            }
        }
        return 1 + maxChildDepth;
    }

    /** Deep copy of this subtree. */
    public abstract GPNode deepCopy();

    /**
     * Generate a random GP tree using the "grow" method.
     * At depth = 0, always terminal. At intermediate depths, 50% terminal.
     */
    public static GPNode generateRandom(int maxDepth, Random rng) {
        return generateRandomGrow(maxDepth, 0, rng);
    }

    static GPNode generateRandomGrow(int maxDepth, int currentDepth, Random rng) {
        boolean pickTerminal = (currentDepth >= maxDepth) || (currentDepth > 0 && rng.nextDouble() < 0.5);
        if (pickTerminal) {
            return GPTerminal.randomTerminal(rng);
        }
        return GPOperator.randomOperator(maxDepth, currentDepth, rng);
    }
}
