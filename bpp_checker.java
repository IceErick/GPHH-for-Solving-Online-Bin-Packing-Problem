import java.io.*;
import java.util.*;

/**
 * Solution checker for the Bin Packing Problem.
 * Verifies: item count, unique assignments, capacity constraints, bin count match.
 * Usage: java bpp_checker -s problem_file -c solution_file
 */
public class bpp_checker {
    public static void main(String[] args) throws Exception {
        String problemFile = null;
        String solutionFile = null;
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-s")) problemFile = args[++i];
            else if (args[i].equals("-c")) solutionFile = args[++i];
        }
        if (problemFile == null || solutionFile == null) {
            System.err.println("Usage: java bpp_checker -s problem_file -c solution_file");
            System.exit(1);
        }

        // Load problem
        List<Integer> problemItems = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(problemFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) problemItems.add(Integer.parseInt(line));
            }
        }

        // Load solution
        List<String> solLines = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(solutionFile))) {
            String line;
            while ((line = br.readLine()) != null) {
                solLines.add(line.trim());
            }
        }

        if (solLines.isEmpty()) {
            System.err.println("Solution file is empty");
            System.exit(1);
        }

        // Parse first line: obj= <bins> <L2>
        String firstLine = solLines.get(0);
        if (!firstLine.startsWith("obj=")) {
            System.err.println("First line must start with 'obj='");
            System.exit(1);
        }
        String[] parts = firstLine.split("\\s+");
        if (parts.length < 2) {
            System.err.println("First line format: obj= <bins> <L2>");
            System.exit(1);
        }
        int declaredBins;
        try {
            // Format: "obj=\t<bins>\t<L2>" → parts = ["obj=", "<bins>", "<L2>"]
            String binsStr = parts[0].replace("obj=", "");
            if (binsStr.isEmpty() && parts.length > 1) {
                binsStr = parts[1];
            }
            declaredBins = Integer.parseInt(binsStr);
        } catch (NumberFormatException e) {
            System.err.println("For input string: " + parts[0].replace("obj=", ""));
            System.exit(1);
            return;
        }

        // Parse bin assignments
        List<List<Integer>> bins = new ArrayList<>();
        Set<Integer> assigned = new HashSet<>();
        int totalAssigned = 0;

        for (int i = 1; i < solLines.size(); i++) {
            String line = solLines.get(i);
            if (line.isEmpty()) continue;
            List<Integer> binItems = new ArrayList<>();
            for (String token : line.split("\\s+")) {
                if (token.isEmpty()) continue;
                try {
                    int itemIdx = Integer.parseInt(token);
                    if (assigned.contains(itemIdx)) {
                        System.err.println("Item " + itemIdx + " assigned more than once");
                        System.exit(1);
                    }
                    assigned.add(itemIdx);
                    binItems.add(itemIdx);
                    totalAssigned++;
                } catch (NumberFormatException e) {
                    System.err.println("For input string: " + token);
                    System.exit(1);
                }
            }
            bins.add(binItems);
        }

        // Check item count
        if (totalAssigned != problemItems.size()) {
            System.err.println("Item count mismatch: problem has " + problemItems.size()
                + ", solution has " + totalAssigned);
            System.exit(1);
        }

        // Check bin count
        if (bins.size() != declaredBins) {
            System.err.println("Declared objective " + declaredBins
                + " does not match actual bins " + bins.size());
            System.exit(1);
        }

        // Check capacity constraints
        for (int b = 0; b < bins.size(); b++) {
            int load = 0;
            for (int itemIdx : bins.get(b)) {
                if (itemIdx < 0 || itemIdx >= problemItems.size()) {
                    System.err.println("Invalid item index: " + itemIdx);
                    System.exit(1);
                }
                load += problemItems.get(itemIdx);
            }
            if (load > 100) {
                System.err.println("Bin " + b + " exceeds capacity: " + load);
                System.exit(1);
            }
        }

        System.out.println("Success!");
    }
}
