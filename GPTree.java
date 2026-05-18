import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A GP expression tree that evaluates a bin's suitability for an incoming item.
 * Lower evaluation scores indicate better bins for placing the item.
 */
public class GPTree implements Serializable {
    private static final long serialVersionUID = 1L;

    public static final int MAX_DEPTH = 12;

    private GPNode root;

    public GPTree(GPNode root) {
        this.root = root;
    }

    public static GPTree generateRandom(int maxDepth, Random rng) {
        return new GPTree(GPNode.generateRandom(maxDepth, rng));
    }

    public double evaluate(Bin bin, int itemSize) {
        return root.evaluate(bin, itemSize);
    }

    public GPNode getRoot() {
        return root;
    }

    public int getSize() {
        return root.getSize();
    }

    public int getDepth() {
        return root.getDepth();
    }

    public GPTree deepCopy() {
        return new GPTree(root.deepCopy());
    }

    private List<NodeRef> getAllNodes() {
        List<NodeRef> nodes = new ArrayList<>();
        collectNodes(root, null, -1, nodes);
        return nodes;
    }

    private static class NodeRef {
        final GPNode parent;
        final int childIndex;
        final GPNode node;

        NodeRef(GPNode parent, int childIndex, GPNode node) {
            this.parent = parent;
            this.childIndex = childIndex;
            this.node = node;
        }
    }

    private void collectNodes(GPNode node, GPNode parent, int childIndex, List<NodeRef> nodes) {
        nodes.add(new NodeRef(parent, childIndex, node));
        for (int i = 0; i < node.arity(); i++) {
            GPNode child = node.getChild(i);
            if (child != null) {
                collectNodes(child, node, i, nodes);
            }
        }
    }

    /**
     * Subtree crossover. Returns a new tree; does not modify parents.
     * Enforces MAX_DEPTH after crossover.
     */
    public GPTree crossover(GPTree other, Random rng) {
        GPTree child = this.deepCopy();
        List<NodeRef> childNodes = child.getAllNodes();

        // Pick a random node in child to replace
        NodeRef target = childNodes.get(rng.nextInt(childNodes.size()));

        // Pick a random subtree from the other parent
        List<NodeRef> otherNodes = other.getAllNodes();
        GPNode donorSubtree = otherNodes.get(rng.nextInt(otherNodes.size())).node.deepCopy();

        // Replace the target with the donor subtree
        if (target.parent == null) {
            child.root = donorSubtree;
        } else {
            target.parent.setChild(target.childIndex, donorSubtree);
        }

        // Enforce max depth
        child.enforceMaxDepth(rng);

        return child;
    }

    /**
     * Mutate this tree by replacing a random subtree.
     */
    public void mutate(Random rng) {
        List<NodeRef> nodes = getAllNodes();
        NodeRef target = nodes.get(rng.nextInt(nodes.size()));

        // Generate a new small subtree (depth 1-3)
        int newDepth = 1 + rng.nextInt(3);
        GPNode newSubtree = GPNode.generateRandom(newDepth, rng);

        if (target.parent == null) {
            root = newSubtree;
        } else {
            target.parent.setChild(target.childIndex, newSubtree);
        }

        enforceMaxDepth(rng);
    }

    /**
     * Ensure tree depth does not exceed MAX_DEPTH.
     * Replaces any subtree that exceeds the limit with a random terminal.
     */
    public void enforceMaxDepth(Random rng) {
        root = enforceMaxDepthNode(root, 0, rng);
    }

    private GPNode enforceMaxDepthNode(GPNode node, int depth, Random rng) {
        if (depth >= MAX_DEPTH && node.arity() > 0) {
            // Replace with a random terminal
            return GPTerminal.randomTerminal(rng);
        }
        if (node.arity() > 0) {
            GPOperator op = (GPOperator) node;
            for (int i = 0; i < op.arity(); i++) {
                GPNode child = op.getChild(i);
                if (child != null) {
                    op.setChild(i, enforceMaxDepthNode(child, depth + 1, rng));
                }
            }
        }
        return node;
    }

    @Override
    public String toString() {
        return root != null ? root.toString() : "null";
    }
}
