package edu.itc.se.lab01.task3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    static final int NUM_PRODUCERS = 2;
    static final int ORDERS_PER_PRODUCER = 50;
    static final int NUM_CONSUMERS = 3;
    static final int BUFFER_CAPACITY = 5;

    public static void main(String[] args) throws InterruptedException {
        OrderQueue queue = new BoundedBuffer(BUFFER_CAPACITY);
        AtomicInteger processedCounter = new AtomicInteger(0);
        AtomicInteger activeProducers = new AtomicInteger(NUM_PRODUCERS);

        List<Thread> threads = new ArrayList<>();
        long start = System.nanoTime();

        for (int p = 0; p < NUM_PRODUCERS; p++) {
            int startId = p * ORDERS_PER_PRODUCER + 1;
            Thread t = new Thread(
                    new Producer(queue, startId, ORDERS_PER_PRODUCER, NUM_CONSUMERS, activeProducers),
                    "producer-" + (p + 1));
            threads.add(t);
        }

        for (int c = 0; c < NUM_CONSUMERS; c++) {
            String name = "consumer-" + (c + 1);
            Thread t = new Thread(new Consumer(name, queue, processedCounter), name);
            threads.add(t);
        }

        for (Thread t : threads) {
            t.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        long ms = (System.nanoTime() - start) / 1_000_000;
        int expected = NUM_PRODUCERS * ORDERS_PER_PRODUCER;
        int actual = processedCounter.get();
        System.out.printf("Total processed: %d (expected %d) in %d ms%n", actual, expected, ms);

        if (actual != expected) {
            throw new AssertionError("Expected " + expected + " orders processed, but got " + actual);
        }
    }
}
