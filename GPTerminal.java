import java.util.Random;

/**
 * Terminal nodes for the GP tree.
 * Each terminal reads a feature of the bin or the incoming item.
 */
public class GPTerminal extends GPNode {
    private static final long serialVersionUID = 1L;

    public enum Type {
        CAPACITY,      // bin capacity
        FULLNESS,      // sum of items in bin
        ITEM_SIZE,     // size of arriving item
        EMPTINESS,     // C - F
        FULLNESS_RATIO, // F / C
        ITEM_COUNT,    // number of items in bin
        MEAN_SIZE      // running mean of items seen so far
    }

    private static final Type[] TYPES = Type.values();
    private final Type type;

    public GPTerminal(Type type) {
        this.type = type;
    }

    public Type getType() {
        return type;
    }

    @Override
    public double evaluate(Bin bin, int itemSize) {
        switch (type) {
            case CAPACITY:
                return bin.getCapacity();
            case FULLNESS:
                return bin.getCurrentLoad();
            case ITEM_SIZE:
                return itemSize;
            case EMPTINESS:
                return bin.getRemainingSpace();
            case FULLNESS_RATIO:
                return bin.getFullnessRatio();
            case ITEM_COUNT:
                return bin.getItemCount();
            case MEAN_SIZE: {
                PackingContext ctx = PackingContext.get();
                return ctx != null ? ctx.getRunningMeanSize() : 0.0;
            }
            default:
                return 0.0;
        }
    }

    @Override
    public int arity() {
        return 0;
    }

    @Override
    public GPNode getChild(int i) {
        throw new IndexOutOfBoundsException("Terminal has no children");
    }

    @Override
    public void setChild(int i, GPNode child) {
        throw new IndexOutOfBoundsException("Terminal has no children");
    }

    @Override
    public GPNode deepCopy() {
        return new GPTerminal(type);
    }

    @Override
    public String toString() {
        return type.name();
    }

    public static GPTerminal randomTerminal(Random rng) {
        return new GPTerminal(TYPES[rng.nextInt(TYPES.length)]);
    }
}
