import java.util.Random;

/**
 * Operator (non-leaf) nodes for the GP tree.
 * Each operator combines the results of its children.
 */
public class GPOperator extends GPNode {
    private static final long serialVersionUID = 1L;

    public enum Type {
        ADD,    // a + b          (2 children)
        SUB,    // a - b          (2 children)
        MUL,    // a * b          (2 children)
        DIV,    // a / b          (2 children, protected)
        MAX,    // max(a, b)      (2 children)
        MIN,    // min(a, b)      (2 children)
        IF_LTE  // a <= b ? c : d (4 children)
    }

    private static final Type[] TYPES = Type.values();
    private final Type type;
    private final GPNode[] children;

    public GPOperator(Type type) {
        this.type = type;
        this.children = new GPNode[type == Type.IF_LTE ? 4 : 2];
    }

    public Type getType() {
        return type;
    }

    @Override
    public int arity() {
        return children.length;
    }

    @Override
    public GPNode getChild(int i) {
        return children[i];
    }

    @Override
    public void setChild(int i, GPNode child) {
        children[i] = child;
    }

    @Override
    public double evaluate(Bin bin, int itemSize) {
        switch (type) {
            case ADD:
                return children[0].evaluate(bin, itemSize) + children[1].evaluate(bin, itemSize);
            case SUB:
                return children[0].evaluate(bin, itemSize) - children[1].evaluate(bin, itemSize);
            case MUL:
                return children[0].evaluate(bin, itemSize) * children[1].evaluate(bin, itemSize);
            case DIV: {
                double denom = children[1].evaluate(bin, itemSize);
                if (Math.abs(denom) < 0.001) denom = 1.0;
                return children[0].evaluate(bin, itemSize) / denom;
            }
            case MAX:
                return Math.max(children[0].evaluate(bin, itemSize), children[1].evaluate(bin, itemSize));
            case MIN:
                return Math.min(children[0].evaluate(bin, itemSize), children[1].evaluate(bin, itemSize));
            case IF_LTE:
                return children[0].evaluate(bin, itemSize) <= children[1].evaluate(bin, itemSize)
                    ? children[2].evaluate(bin, itemSize)
                    : children[3].evaluate(bin, itemSize);
            default:
                return 0.0;
        }
    }

    @Override
    public GPNode deepCopy() {
        GPOperator copy = new GPOperator(type);
        for (int i = 0; i < children.length; i++) {
            if (children[i] != null) {
                copy.children[i] = children[i].deepCopy();
            }
        }
        return copy;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("(").append(type.name());
        for (GPNode child : children) {
            sb.append(" ").append(child != null ? child.toString() : "null");
        }
        sb.append(")");
        return sb.toString();
    }

    public static GPOperator randomOperator(int maxDepth, int currentDepth, Random rng) {
        Type t = TYPES[rng.nextInt(TYPES.length)];
        GPOperator op = new GPOperator(t);
        for (int i = 0; i < op.children.length; i++) {
            op.children[i] = GPNode.generateRandomGrow(maxDepth, currentDepth + 1, rng);
        }
        return op;
    }
}
