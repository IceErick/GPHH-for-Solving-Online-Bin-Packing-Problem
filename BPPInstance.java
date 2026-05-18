import java.io.*;
import java.util.*;

/**
 * Loads and represents a Bin Packing Problem instance from file.
 * Format: all lines are item sizes (one per line). Bin capacity is a fixed parameter.
 */
public class BPPInstance {
    /** Default bin capacity for dual-distribution instances (Burke et al. 2010). */
    public static final int DEFAULT_CAPACITY = 100;

    private final int capacity;
    private final int[] items;
    private final String name;

    public BPPInstance(String filePath, int capacity) throws IOException {
        this.name = new File(filePath).getName();
        this.capacity = capacity;
        List<Integer> itemList = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    itemList.add(Integer.parseInt(line));
                }
            }
        }
        this.items = new int[itemList.size()];
        for (int i = 0; i < itemList.size(); i++) {
            items[i] = itemList.get(i);
        }
    }

    public BPPInstance(String filePath) throws IOException {
        this(filePath, DEFAULT_CAPACITY);
    }

    public int getCapacity() {
        return capacity;
    }

    public int[] getItems() {
        return items;
    }

    public int getItemCount() {
        return items.length;
    }

    public String getName() {
        return name;
    }
}
