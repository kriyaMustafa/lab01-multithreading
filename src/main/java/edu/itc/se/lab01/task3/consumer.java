package edu.itc.se.lab01.task3;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class consumer implements Runnable{
    private final OrderQueue queue ;
    private final String name;
    private AtomicInteger process;
    private final CountDownLatch allpill;

    public consumer(String name,OrderQueue queue,AtomicInteger process,CountDownLatch allpill){
        this.name=name;
        this.process=process;
        this.queue = queue;
        this.allpill = allpill;

    }


    @Override
    public void run() {
        try {
            while (true) {
                Order o = queue.take();
                if (o.ispoisn()) {
                    allpill.countDown();
                    allpill.await();
                    System.out.println(name+" received poison pill, stopping");
                    return; 
                }
                Thread.sleep(ThreadLocalRandom.current().nextInt(5,31));
                System.out.println(name+" process "+o);
                process.incrementAndGet();
            }
        } catch (Exception e) {
            Thread.currentThread().interrupt();
        }
    }
    
}
