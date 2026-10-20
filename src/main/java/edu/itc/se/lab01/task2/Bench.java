package edu.itc.se.lab01.task2;

import java.util.ArrayList;
import java.util.List;

/*
 * Recorded wrong results from running UnsafeCounter (expected 8,000,000):
 *   Run 1: value=5,429,626  correct=false  (lost 2,570,374 updates)
 *   Run 2: value=3,527,170  correct=false  (lost 4,472,830 updates)
 *   Run 3: value=5,499,067  correct=false  (lost 2,500,933 updates)
 *
 * Cause:
 * The operation `count++` is not atomic. It consists of three distinct bytecode
 * operations: read count from memory, add 1, and write back to memory (read-modify-write).
 * When multiple threads interleave these operations simultaneously without synchronization,
 * updates overwrite each other, leading to lost updates.
 */
public class Bench {
    static final int THREADS = 8;
    static final int ITERATIONS = 1_000_000;

    public static void run(String label, Counter counter) throws InterruptedException {
        long start = System.nanoTime();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Thread th = new Thread(() -> {
                for (int i = 0; i < ITERATIONS; i++) {
                    counter.increment();
                }
            }, "inc-" + t);
            threads.add(th);
            th.start();
        }
        for (Thread th : threads) {
            th.join();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;
        long expected = (long) THREADS * ITERATIONS;
        System.out.printf("%-22s value=%,d  correct=%-5b  %d ms%n",
                label, counter.get(), counter.get() == expected, ms);
    }

    public static void main(String[] args) throws InterruptedException {
        run("UnsafeCounter", new UnsafeCounter());
        run("SynchronizedCounter", new SynchronizedCounter());
        run("LockCounter", new LockCounter());
        run("AtomicCounter", new AtomicCounter());
        run("LongAdderCounter", new LongAdderCounter());
    }
}
