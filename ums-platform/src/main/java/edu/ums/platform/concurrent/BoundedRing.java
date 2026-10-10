package edu.ums.platform.concurrent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReferenceArray;

/**
 * Fixed-capacity ring buffer keeping the most recent {@code capacity} items. Lock-free: writers
 * claim a sequence number from an {@link AtomicLong}. {@link #snapshot()} is weakly consistent
 * under concurrent writes (it may miss an item being written) but never exceeds the capacity.
 */
// Complexity: time O(1) per add, O(capacity) per snapshot; space O(capacity), fixed at creation
public final class BoundedRing<T> {

  private final int capacity;
  private final AtomicReferenceArray<T> slots;
  private final AtomicLong written = new AtomicLong();

  /**
   * Creates a ring.
   *
   * @throws IllegalArgumentException if {@code capacity < 1}
   */
  public BoundedRing(int capacity) {
    if (capacity < 1) {
      throw new IllegalArgumentException("capacity must be positive");
    }
    this.capacity = capacity;
    this.slots = new AtomicReferenceArray<>(capacity);
  }

  /** Adds {@code item}, overwriting the oldest once full. */
  public void add(T item) {
    Objects.requireNonNull(item, "item");
    long sequence = written.getAndIncrement();
    slots.set((int) (sequence % capacity), item);
  }

  /** Returns at most {@code capacity} items, newest first. */
  public List<T> snapshot() {
    long end = written.get();
    int count = (int) Math.min(end, capacity);
    List<T> items = new ArrayList<>(count);
    for (int i = 0; i < count; i++) {
      T item = slots.get((int) ((end - 1 - i) % capacity));
      if (item != null) {
        items.add(item);
      }
    }
    return List.copyOf(items);
  }

  /** Returns the number of items held, at most {@code capacity}. */
  public int size() {
    return (int) Math.min(written.get(), capacity);
  }

  /** Returns the fixed capacity. */
  public int capacity() {
    return capacity;
  }
}
