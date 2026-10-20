package edu.itc.se.lab01.task3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
    static final int producer = 2,order = 50,consumer=3,capacity=5;
    public static void main(String[] args) throws InterruptedException {
        OrderQueue queue = new BoundedBuffer(capacity);
        AtomicInteger process = new AtomicInteger();
        AtomicInteger producerleft = new AtomicInteger(producer);
        List<Thread> thread = new ArrayList<>();
        long start = System.nanoTime();
        CountDownLatch allpill = new CountDownLatch(consumer);
        for (int i = 0; i < producer; i++) {
            thread.add(new Thread(new Producer(queue,i* order +1 ,order,consumer,producerleft),"producer-"+(i+1)));
        }
        for (int i = 0; i < consumer; i++) {
            String name = "consumer-"+(i+1);
            thread.add(new Thread(new consumer(name,queue, process,allpill),name));
        }
        for (Thread th : thread) {
            th.start();
        }
        for (Thread th : thread) {
            th.join();
        }
        long ms = (System.nanoTime()-start)/1000000;
        int expect = producer*order;
        System.out.printf("Total processed: %d (expected %d) in %d ms%n", process.get(), expect, ms);
        if (process.get() != expect) {
            throw new AssertionError("expect " + expect + " but got " + process.get());
        }
    }
}
