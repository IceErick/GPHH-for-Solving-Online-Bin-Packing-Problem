#!/usr/bin/env python3
"""
Generate non-stationary augmented training instances.

The key insight: GP has a MEAN_SIZE terminal but never learns to use it
adaptively because training instances have stationary distributions.
Within a single 500-item instance, the running mean stabilizes after ~100 items
and stays constant. The GP treats MEAN_SIZE as a constant within each instance.

Solution: Create instances where the distribution CHANGES mid-way,
forcing the GP to learn adaptive strategies. For example:
- Small→Large: first 250 small items (avg ~33), then 250 large items (avg ~50)
- Large→Small: first 250 large items, then 250 small items
- Alternating blocks: 50 large, 50 small, repeating...

An effective online heuristic should adapt when the running mean changes.
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

def categorize_instances(train_dir):
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

    print(f"  Large-item instances: {len(large_instances)}")
    print(f"  Small-item instances: {len(small_instances)}")
    return large_instances, small_instances

def main():
    train_dir = "train"
    output_dir = "train_augmented_v3"
    os.makedirs(output_dir, exist_ok=True)

    random.seed(42)
    rng = random.Random(42)

    print("Loading training instances...")
    large_instances, small_instances = categorize_instances(train_dir)

    num_items = 500
    counter = 0

    # Pool all items
    all_large_items = []
    for inst in large_instances:
        all_large_items.extend(inst)
    all_small_items = []
    for inst in small_instances:
        all_small_items.extend(inst)

    print(f"  Large item pool: {len(all_large_items)} items")
    print(f"  Small item pool: {len(all_small_items)} items")

    # 1. Non-stationary: Distribution switch mid-instance
    # Small→Large: first half small, second half large
    print("\nGenerating Small→Large switch instances...")
    for i in range(100):
        local_rng = random.Random(1000 + i)
        half = num_items // 2
        small_part = [local_rng.choice(all_small_items) for _ in range(half)]
        large_part = [local_rng.choice(all_large_items) for _ in range(num_items - half)]
        items = small_part + large_part
        avg1 = sum(small_part) / len(small_part)
        avg2 = sum(large_part) / len(large_part)
        # Shuffle within each half to avoid any ordering bias
        save_instance(items, os.path.join(output_dir, f"ns_switch_small_to_large_{i:03d}.txt"))
        counter += 1

    # Large→Small: first half large, second half small
    print("Generating Large→Small switch instances...")
    for i in range(100):
        local_rng = random.Random(2000 + i)
        half = num_items // 2
        large_part = [local_rng.choice(all_large_items) for _ in range(half)]
        small_part = [local_rng.choice(all_small_items) for _ in range(num_items - half)]
        items = large_part + small_part
        save_instance(items, os.path.join(output_dir, f"ns_switch_large_to_small_{i:03d}.txt"))
        counter += 1

    # 2. Alternating blocks (forces continuous adaptation)
    print("Generating alternating block instances...")
    for i in range(100):
        local_rng = random.Random(3000 + i)
        block_size = 50
        items = []
        num_blocks = num_items // block_size
        for b in range(num_blocks):
            if b % 2 == 0:
                items.extend([local_rng.choice(all_large_items) for _ in range(block_size)])
            else:
                items.extend([local_rng.choice(all_small_items) for _ in range(block_size)])
        save_instance(items, os.path.join(output_dir, f"ns_alternating_blocks_{i:03d}.txt"))
        counter += 1

    # 3. Mixed-stationary at precise ratios (from v2, targeting 42-44 range)
    print("Generating mixed-ratio stationary instances...")
    for ratio in [0.55, 0.56, 0.57, 0.58]:
        for i in range(50):
            local_rng = random.Random(4000 + int(ratio * 1000) + i)
            num_large = int(num_items * ratio)
            num_small = num_items - num_large
            large_sample = [local_rng.choice(all_large_items) for _ in range(num_large)]
            small_sample = [local_rng.choice(all_small_items) for _ in range(num_small)]
            # Random interleaving
            result = []
            li, si = 0, 0
            while li < len(large_sample) and si < len(small_sample):
                if local_rng.random() < num_large / (num_large + num_small):
                    result.append(large_sample[li]); li += 1
                else:
                    result.append(small_sample[si]); si += 1
            result.extend(large_sample[li:])
            result.extend(small_sample[si:])
            save_instance(result, os.path.join(output_dir, f"ns_mixed_{int(ratio*100):d}pct_{i:03d}.txt"))
            counter += 1

    # 4. Stationary from original clusters (for baseline performance)
    print("Generating stationary cluster instances...")
    for i in range(30):
        local_rng = random.Random(5000 + i)
        items = [local_rng.choice(all_large_items) for _ in range(num_items)]
        save_instance(items, os.path.join(output_dir, f"ns_large_stationary_{i:03d}.txt"))
        counter += 1

    for i in range(30):
        local_rng = random.Random(6000 + i)
        items = [local_rng.choice(all_small_items) for _ in range(num_items)]
        save_instance(items, os.path.join(output_dir, f"ns_small_stationary_{i:03d}.txt"))
        counter += 1

    print(f"\nTotal augmented instances: {counter}")
    print(f"Saved to: {output_dir}/")

    # Verify
    print("\nSample verification:")
    for fname in sorted(os.listdir(output_dir))[:5]:
        items = load_instance(os.path.join(output_dir, fname))
        avg_first_half = sum(items[:250]) / 250
        avg_second_half = sum(items[250:]) / 250
        avg_all = sum(items) / len(items)
        print(f"  {fname}: first_half={avg_first_half:.1f}, second_half={avg_second_half:.1f}, all={avg_all:.1f}")

if __name__ == "__main__":
    main()
