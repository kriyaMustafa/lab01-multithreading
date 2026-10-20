package edu.itc.se.lab01.task2;

import java.util.concurrent.atomic.LongAdder;

/*
 * Why LongAdder is faster under contention:
 *
 * AtomicLong uses a single memory location with a Compare-And-Swap (CAS) loop.
 * Under high contention from multiple threads (e.g., 8 threads hammering the counter),
 * CAS operations repeatedly fail, forcing threads to spin and re-try while causing
 * cache-line bouncing (MESI cache invalidation across CPU cores).
 *
 * LongAdder, by contrast, maintains an internal dynamically expanding table of Cell
 * variables (striped 64-bit cells). When contention is detected, threads are hashed
 * to different cells and update their own local cell without blocking or interfering
 * with each other. The final total is aggregated via sum() when get() is called.
 * This distributes the write traffic across cache lines, dramatically reducing contention
 * and boosting throughput.
 */
public class LongAdderCounter implements Counter {
    private final LongAdder adder = new LongAdder();

    @Override
    public void increment() {
        adder.increment();
    }

    @Override
    public long get() {
        return adder.sum();
    }
}
