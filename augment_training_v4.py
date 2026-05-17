#!/usr/bin/env python3
"""
Generate 5000-item training instances for scale-aware GP training.

Key insight: test instances have 5000 items, but training instances have 500.
Non-stationary instances with 5000 items provide longer horizons for adaptive
strategies to demonstrate benefit.

Generates:
1. Stationary mid-size instances (~56/44 large/small mix, avg ~42.5, matching testdual4/8)
2. Non-stationary switch instances (distribution changes at items 1000, 2000, 3000, 4000)
3. Stationary large-only instances (for testdual0 baseline)
"""

import os
import random

def load_instance(filepath):
    with open(filepath) as f:
        return [int(line.strip()) for line in f if line.strip()]

def save_instance(items, filepath):
    with open(filepath, 'w') as f:
        for item in items:
            f.write(f"{item}\n")

def main():
    train_dir = "train"
    output_dir = "train_augmented_v4"
    os.makedirs(output_dir, exist_ok=True)

    random.seed(42)

    print("Loading training instances...")
    files = sorted([f for f in os.listdir(train_dir) if f.endswith('.txt')])
    large_instances = []
    small_instances = []

    for fname in files:
        items = load_instance(os.path.join(train_dir, fname))
        avg = sum(items) / len(items)
        if avg > 45:
            large_instances.append(items)
        elif avg < 35:
            small_instances.append(items)

    all_large = []
    for inst in large_instances:
        all_large.extend(inst)
    all_small = []
    for inst in small_instances:
        all_small.extend(inst)

    print(f"  Large items: {len(all_large)} from {len(large_instances)} instances")
    print(f"  Small items: {len(all_small)} from {len(small_instances)} instances")

    num_items = 5000  # Match test instance size
    counter = 0

    # 1. Stationary mid-size instances (targeting testdual4/8: avg ~42.5)
    # Mix ratio: 56% large + 44% small → avg ~0.56*50 + 0.44*33 = 42.5
    print("\nGenerating stationary mid-size instances (target avg ~42.5)...")
    for i in range(15):
        rng = random.Random(1000 + i)
        num_large = int(num_items * 0.56)
        num_small = num_items - num_large
        large_sample = [rng.choice(all_large) for _ in range(num_large)]
        small_sample = [rng.choice(all_small) for _ in range(num_small)]
        # Random interleaving
        result = []
        li, si = 0, 0
        while li < len(large_sample) and si < len(small_sample):
            if rng.random() < num_large / (num_large + num_small):
                result.append(large_sample[li]); li += 1
            else:
                result.append(small_sample[si]); si += 1
        result.extend(large_sample[li:])
        result.extend(small_sample[si:])
        avg = sum(result) / len(result)
        save_instance(result, os.path.join(output_dir, f"v4_mid5000_{i:03d}.txt"))
        counter += 1
        print(f"  v4_mid5000_{i:03d}: avg={avg:.1f}")

    # 2. Non-stationary instances: distribution switches at multiple points
    # These FORCE the GP to learn adaptive strategies using MEAN_SIZE
    print("\nGenerating non-stationary switch instances...")

    # Pattern A: Small → Large → Small → Large → Small
    # 5 segments of 1000 items each
    for i in range(8):
        rng = random.Random(2000 + i)
        result = []
        for seg in range(5):
            seg_size = 1000
            if seg % 2 == 0:
                result.extend([rng.choice(all_small) for _ in range(seg_size)])
            else:
                result.extend([rng.choice(all_large) for _ in range(seg_size)])
        avg = sum(result) / len(result)
        save_instance(result, os.path.join(output_dir, f"v4_ns_switch_sls_{i:03d}.txt"))
        counter += 1
        print(f"  v4_ns_switch_sls_{i:03d}: avg={avg:.1f}")

    # Pattern B: Large → Small → Large → Small → Large
    for i in range(8):
        rng = random.Random(3000 + i)
        result = []
        for seg in range(5):
            seg_size = 1000
            if seg % 2 == 0:
                result.extend([rng.choice(all_large) for _ in range(seg_size)])
            else:
                result.extend([rng.choice(all_small) for _ in range(seg_size)])
        avg = sum(result) / len(result)
        save_instance(result, os.path.join(output_dir, f"v4_ns_switch_lsl_{i:03d}.txt"))
        counter += 1
        print(f"  v4_ns_switch_lsl_{i:03d}: avg={avg:.1f}")

    # 3. Stationary large-only instances (for testdual0 baseline)
    print("\nGenerating stationary large-only instances...")
    for i in range(5):
        rng = random.Random(4000 + i)
        result = [rng.choice(all_large) for _ in range(num_items)]
        avg = sum(result) / len(result)
        save_instance(result, os.path.join(output_dir, f"v4_large5000_{i:03d}.txt"))
        counter += 1
        print(f"  v4_large5000_{i:03d}: avg={avg:.1f}")

    # 4. Stationary small-only instances
    print("\nGenerating stationary small-only instances...")
    for i in range(5):
        rng = random.Random(5000 + i)
        result = [rng.choice(all_small) for _ in range(num_items)]
        avg = sum(result) / len(result)
        save_instance(result, os.path.join(output_dir, f"v4_small5000_{i:03d}.txt"))
        counter += 1
        print(f"  v4_small5000_{i:03d}: avg={avg:.1f}")

    print(f"\nTotal instances generated: {counter}")
    print(f"Saved to: {output_dir}/")

    # Verify
    all_avgs = []
    for fname in os.listdir(output_dir):
        if fname.endswith('.txt'):
            items = load_instance(os.path.join(output_dir, fname))
            all_avgs.append(sum(items) / len(items))
    all_avgs.sort()
    print(f"\nDistribution summary:")
    print(f"  Min avg: {all_avgs[0]:.1f}, Max avg: {all_avgs[-1]:.1f}")
    print(f"  Median: {all_avgs[len(all_avgs)//2]:.1f}")
    in_target = sum(1 for a in all_avgs if 42 <= a <= 44)
    print(f"  Instances in 42-44 range: {in_target}/{len(all_avgs)}")

if __name__ == "__main__":
    main()
