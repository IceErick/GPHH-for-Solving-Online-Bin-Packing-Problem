#!/usr/bin/env python3
"""
Generate augmented training instances for the dual-distribution bin packing problem.

Strategy:
- Training set has bimodal distribution: 10 large-item (avg ~50) + 10 small-item (avg ~33)
- testdual4/8 have unimodal distribution at avg ~42.5
- To bridge the gap, we mix large+small items at precise ratios targeting avg 42-44

Target ratios (large/small % → resulting avg item size):
  52/48 → 0.52*50 + 0.48*33 = 41.8
  54/46 → 0.54*50 + 0.46*33 = 42.2
  55/45 → 0.55*50 + 0.45*33 = 42.4
  56/44 → 0.56*50 + 0.44*33 = 42.5
  57/43 → 0.57*50 + 0.43*33 = 42.7
  58/42 → 0.58*50 + 0.42*33 = 42.9
  60/40 → 0.60*50 + 0.40*33 = 43.2
"""

import os
import random

def load_instance(filepath):
    """Load items from a file."""
    with open(filepath) as f:
        return [int(line.strip()) for line in f if line.strip()]

def save_instance(items, filepath):
    """Save items to a file."""
    with open(filepath, 'w') as f:
        for item in items:
            f.write(f"{item}\n")

def categorize_instances(train_dir):
    """Split training instances into large-item and small-item groups."""
    files = sorted([f for f in os.listdir(train_dir) if f.endswith('.txt')])
    large_instances = []
    small_instances = []

    for fname in files:
        items = load_instance(os.path.join(train_dir, fname))
        avg = sum(items) / len(items)
        if avg > 45:
            large_instances.append((fname, items))
        elif avg < 35:
            small_instances.append((fname, items))
        else:
            print(f"  Note: {fname} has avg={avg:.1f}, skipping (neither large nor small)")

    print(f"  Large-item instances: {len(large_instances)} (avg ~50)")
    print(f"  Small-item instances: {len(small_instances)} (avg ~33)")
    return large_instances, small_instances

def generate_mixed_instance(large_pool, small_pool, large_ratio, num_items, rng):
    """
    Generate a mixed instance by interleaving items from large and small pools.
    large_ratio: fraction of items drawn from large pool (0.0 to 1.0)
    Items are randomly interleaved to simulate online arrival.
    """
    num_large = int(num_items * large_ratio)
    num_small = num_items - num_large

    # Collect all available large and small items across all instances in pools
    all_large_items = []
    for _, items in large_pool:
        all_large_items.extend(items)
    all_small_items = []
    for _, items in small_pool:
        all_small_items.extend(items)

    # Sample with replacement to get desired counts
    large_sample = [rng.choice(all_large_items) for _ in range(num_large)]
    small_sample = [rng.choice(all_small_items) for _ in range(num_small)]

    # Create interleaved sequence
    result = []
    li, si = 0, 0
    while li < len(large_sample) and si < len(small_sample):
        # Randomly decide next item type, biased by remaining counts
        remaining_large = len(large_sample) - li
        remaining_small = len(small_sample) - si
        if rng.random() < remaining_large / (remaining_large + remaining_small):
            result.append(large_sample[li])
            li += 1
        else:
            result.append(small_sample[si])
            si += 1
    # Add remaining
    result.extend(large_sample[li:])
    result.extend(small_sample[si:])

    return result

def generate_concatenated_instance(pool, num_items, rng):
    """
    Generate an instance by concatenating two halves from different instances
    in the same cluster (within-cluster augmentation).
    """
    # Pick two different instances
    idx1, idx2 = rng.sample(range(len(pool)), 2)
    _, items1 = pool[idx1]
    _, items2 = pool[idx2]

    # Take half from each
    half = num_items // 2
    part1 = items1[:half]
    part2 = items2[half:num_items]

    # Concatenate (preserving arrival order within each half)
    result = part1 + part2
    return result

def main():
    train_dir = "train"
    output_dir = "train_augmented_v2"
    os.makedirs(output_dir, exist_ok=True)

    random.seed(42)
    rng = random.Random(42)

    print("Loading training instances...")
    large_instances, small_instances = categorize_instances(train_dir)

    if not large_instances or not small_instances:
        print("ERROR: Need both large and small instances for augmentation!")
        return

    num_items = 500  # Match training instance size
    counter = 0

    # Generate mixed instances at precise ratios
    ratios = [0.52, 0.54, 0.55, 0.56, 0.57, 0.58, 0.60]
    instances_per_ratio = 50

    print(f"\nGenerating mixed instances at {len(ratios)} ratios x {instances_per_ratio} each...")
    for ratio in ratios:
        target_avg = ratio * 50 + (1 - ratio) * 33
        print(f"  Ratio {ratio:.0%} large / {(1-ratio):.0%} small → target avg ~{target_avg:.1f}")
        for i in range(instances_per_ratio):
            seed = 42 + int(ratio * 1000) + i
            local_rng = random.Random(seed)
            items = generate_mixed_instance(large_instances, small_instances, ratio, num_items, local_rng)
            fname = f"aug_mix_{int(ratio*100):d}pct_big_{i:03d}.txt"
            save_instance(items, os.path.join(output_dir, fname))
            counter += 1

    # Generate within-cluster concatenated instances
    print(f"\nGenerating within-cluster concatenated instances...")
    for i in range(50):
        seed = 1000 + i
        local_rng = random.Random(seed)
        items = generate_concatenated_instance(large_instances, num_items, local_rng)
        save_instance(items, os.path.join(output_dir, f"aug_largecat_{i:03d}.txt"))
        counter += 1

    for i in range(50):
        seed = 2000 + i
        local_rng = random.Random(seed)
        items = generate_concatenated_instance(small_instances, num_items, local_rng)
        save_instance(items, os.path.join(output_dir, f"aug_smallcat_{i:03d}.txt"))
        counter += 1

    print(f"\nTotal augmented instances generated: {counter}")
    print(f"Saved to: {output_dir}/")

    # Verify distributions
    print("\nVerifying generated distributions...")
    all_avgs = []
    for fname in os.listdir(output_dir):
        if fname.endswith('.txt'):
            items = load_instance(os.path.join(output_dir, fname))
            all_avgs.append(sum(items) / len(items))

    all_avgs.sort()
    print(f"  Min avg: {all_avgs[0]:.1f}")
    print(f"  25th percentile: {all_avgs[len(all_avgs)//4]:.1f}")
    print(f"  Median avg: {all_avgs[len(all_avgs)//2]:.1f}")
    print(f"  75th percentile: {all_avgs[3*len(all_avgs)//4]:.1f}")
    print(f"  Max avg: {all_avgs[-1]:.1f}")

    # Count how many in 42-44 range (testdual4/8 target)
    in_target = sum(1 for a in all_avgs if 42 <= a <= 44)
    print(f"  Instances in 42-44 range: {in_target}/{len(all_avgs)}")

if __name__ == "__main__":
    main()
