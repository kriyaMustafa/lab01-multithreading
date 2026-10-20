package edu.itc.se.lab01.task3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Consumer implements Runnable {
    private final String name;
    private final OrderQueue queue;
    private final AtomicInteger processedCounter;

    public Consumer(String name, OrderQueue queue, AtomicInteger processedCounter) {
        this.name = name;
        this.queue = queue;
        this.processedCounter = processedCounter;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Order order = queue.take();
                if (order.isPoison()) {
                    System.out.println(name + " received poison pill, stopping");
                    return;
                }
                int sleepMs = ThreadLocalRandom.current().nextInt(5, 31);
                Thread.sleep(sleepMs);
                System.out.println(name + " processed " + order);
                processedCounter.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
