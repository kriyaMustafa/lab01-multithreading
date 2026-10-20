package edu.itc.se.lab01.task3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Producer implements Runnable {
    private final OrderQueue queue;
    private final int startId;
    private final int orderCount;
    private final int numConsumers;
    private final AtomicInteger activeProducers;

    public Producer(OrderQueue queue, int startId, int orderCount, int numConsumers, AtomicInteger activeProducers) {
        this.queue = queue;
        this.startId = startId;
        this.orderCount = orderCount;
        this.numConsumers = numConsumers;
        this.activeProducers = activeProducers;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < orderCount; i++) {
                int pause = ThreadLocalRandom.current().nextInt(0, 21);
                if (pause > 0) {
                    Thread.sleep(pause);
                }
                int id = startId + i;
                int qty = ThreadLocalRandom.current().nextInt(1, 6);
                queue.put(new Order(id, "item-" + id, qty));
            }
            // Once all producers have finished putting their orders,
            // the last producer puts one poison pill per consumer.
            if (activeProducers.decrementAndGet() == 0) {
                for (int c = 0; c < numConsumers; c++) {
                    queue.put(Order.poisonPill());
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
