import java.util.ArrayList;
import java.util.List;

/**
 * Represents a single bin in the online bin packing problem.
 */
public class Bin {
    private final int capacity;
    private int currentLoad;
    private final List<Integer> items;

    public Bin(int capacity) {
        this.capacity = capacity;
        this.currentLoad = 0;
        this.items = new ArrayList<>();
    }

    public Bin(Bin other) {
        this.capacity = other.capacity;
        this.currentLoad = other.currentLoad;
        this.items = new ArrayList<>(other.items);
    }

    public boolean canFit(int itemSize) {
        return currentLoad + itemSize <= capacity;
    }

    public void addItem(int itemSize) {
        currentLoad += itemSize;
        items.add(itemSize);
    }

    public int getCapacity() {
        return capacity;
    }

    public int getCurrentLoad() {
        return currentLoad;
    }

    public int getRemainingSpace() {
        return capacity - currentLoad;
    }

    public double getFullnessRatio() {
        return (double) currentLoad / capacity;
    }

    public int getItemCount() {
        return items.size();
    }

    public List<Integer> getItems() {
        return items;
    }
}
