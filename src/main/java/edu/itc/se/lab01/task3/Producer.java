package edu.itc.se.lab01.task3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Producer implements Runnable{

    private final OrderQueue queue;
    private final int firstid,count,consumer;
    private final AtomicInteger producerleft;

    public Producer(OrderQueue queue,int firstid,int count,int consumer,AtomicInteger producerleft){
        this.queue = queue;
        this.firstid = firstid;
        this.count = count;
        this.consumer = consumer;
        this.producerleft = producerleft;
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < count; i++) {
                Thread.sleep(ThreadLocalRandom.current().nextInt(0, 21));
                int id = firstid + i;
                queue.put(new Order(id, "item-"+id, ThreadLocalRandom.current().nextInt(1,6)));              
            }
            if (producerleft.decrementAndGet() == 0) {
                for (int i = 0; i < consumer; i++) {
                    queue.put(new Order(Order.poison_id, "poison", 0));
                }
            }
        } catch(Exception e) {
            System.out.println("thread is interrupt1");
        }
    }
    
}
