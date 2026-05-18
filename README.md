# GPHH for Online Bin Packing Problem

[![Java](https://img.shields.io/badge/Java-8%2B-orange)](https://www.java.com/)
[![Python](https://img.shields.io/badge/Python-3.7%2B-blue)](https://www.python.org/)
[![License](https://img.shields.io/badge/License-MIT-green)](LICENSE)

A Genetic Programming Hyper-heuristic (GPHH) with memory mechanism for solving the **online one-dimensional Bin Packing Problem (BPP)** under dual-distribution item arrivals.

This project implements the framework described in:

> Burke, E. K., Hyde, M. R., & Kendall, G. (2010). *Providing a Memory Mechanism to Enhance the Evolutionary Design of Heuristics.* Proceedings of CEC 2010, pp. 3883–3890.

## Problem Description

Given a stream of items arriving sequentially, each with a known size, pack all items into the **minimum number of bins** of fixed capacity **without reordering or moving items** once placed. This is the **online** variant — items must be packed in arrival order, and future items are unknown.

The dataset uses a **dual distribution**: items are drawn from two Gaussian distributions (large items ~50, small items ~33), making the problem harder than uniform distributions — a heuristic must adapt to shifting item size profiles.

## Approach

### Genetic Programming Hyper-heuristic

Instead of hand-crafting a packing heuristic, GP evolves an expression tree `f(bin, item)` that **scores each candidate bin** for an incoming item. The bin with the lowest score is chosen; if no bin can accommodate the item, a new bin is opened.

**Terminals (features):** Bin capacity, fullness, item size, emptiness, fullness ratio, item count, running mean item size

**Operators:** `+`, `-`, `*`, `/` (protected), `max`, `min`, `if ≤`

### Memory Mechanism

A memory pool of elite heuristics preserves the best solutions across generations. Every 5 generations, a historically strong individual is re-injected into the population — balancing **intensification** (preserving elites) and **diversification** (reintroducing past successes).

### Specialist Ensemble (key innovation)

Test instances come in two distinct profiles:
- **testdual0**: large items (avg ~50) — standard heuristics perform well
- **testdual4/8**: mid-size items (avg ~42.5) — standard heuristics struggle

To bridge the gap, training data was augmented with non-stationary instances (distribution shifts mid-instance) and mixed-ratio stationary instances. Five **specialist heuristics** were trained exclusively on mid-size instances (avg 42–44), then combined with three generalists into an **8-heuristic ensemble**. At test time, all heuristics are evaluated and the best result is kept.

## Project Structure

```
.
├── GPHH20616316.java      # Testing entry point (loads pre-trained heuristics)
├── TrainGPHH.java         # Training entry point
├── GPEngine.java          # GP evolution engine with memory mechanism
├── GPTree.java            # Expression tree representation
├── GPNode.java            # Abstract GP node
├── GPOperator.java        # Operator nodes (+, -, *, /, max, min, if≤)
├── GPTerminal.java        # Terminal nodes (C, F, S, E, FR, N, MS)
├── BPPSolver.java         # Online bin packing solver
├── BPPInstance.java       # Problem instance loader
├── Bin.java               # Bin data structure
├── PackingContext.java    # Thread-local context for packing state
├── bpp_checker.java       # Solution validator
├── augment_training.py    # Training data augmentation script
├── *.ser                  # Serialized pre-trained heuristics
├── train/                 # Original training instances (20 files)
├── train_augmented*/      # Augmented training data (v2, v3, v4)
├── train_specialist_mid/  # Specialist training instances (177 files)
├── train_scale_5000/      # 5,000-item scale training instances
└── test/
    ├── testdual0/          # Test set 0 (large items)
    ├── testdual4/          # Test set 4 (mid-size items)
    ├── testdual8/          # Test set 8 (mid-size items)
    └── testdual1-3,5-7,9-11/  # Other test sets
```

## Build & Run

### Compile

```bash
javac *.java
```

### Run on a test instance

```bash
java GPHH20616316 -s <instance_file> -o <solution_file> -t <max_time_seconds>
```

Example:

```bash
java GPHH20616316 -s test/testdual0/binpack0.txt -o solution.txt -t 10
```

### Validate a solution

```bash
java bpp_checker -s <problem_file> -c <solution_file>
```

### Retrain (optional)

```bash
python3 augment_training.py    # Generate augmented training data
java TrainGPHH                 # Train and serialize new heuristics
```

## Results

Performance measured by **absolute gap** from the L2 lower bound (Martello & Toth, 1990):

| Test Set | Avg Gap | Score (/5) | Item Profile |
|----------|---------|------------|--------------|
| testdual0 | 29.9 | **5.0** | Large items (~50) |
| testdual4 | 197.8 | 0.8 | Mid-size items (~42.5) |
| testdual8 | 173.6 | 1.9 | Mid-size items (~42.5) |
| **Total** | | **7.7 / 15** | |

- **testdual0**: All instances within 100 bins of L2 bound (max mark)
- **testdual4/8**: Competitive with published results from Burke et al. (2010); specialist ensemble outperforms Best-Fit by ~10–14 bins per instance

### Scoring Criteria

| abs_gap | Marks per instance |
|---------|-------------------|
| ≤100 | 1.0 |
| 100–120 | 0.8 |
| 120–160 | 0.6 |
| 160–180 | 0.4 |
| 180–200 | 0.2 |
| >200 | 0 |

## Key Constraints

- **Online packing only** — items packed in given order, no pre-sorting, no moving items between bins
- **Single run per instance** — 10-second time limit
- **Train/test separation** — training code is separate; testing loads pre-trained serialized heuristics

## References

- Burke, E. K., Hyde, M. R., & Kendall, G. (2010). Providing a Memory Mechanism to Enhance the Evolutionary Design of Heuristics. *CEC 2010*, pp. 3883–3890.
- Martello, S., & Toth, P. (1990). Lower bounds and reduction procedures for the bin packing problem. *Discrete Applied Mathematics*, 28(1), 59–70.
- [ESICUP Dataset Repository](https://github.com/ESICUP/datasets/tree/main/1d/dualdistribution)

## License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.
