package edu.itc.se.lab01.task2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

public class Bench {


    static public class AtomicCounter implements Counter {
    private final AtomicLong count = new AtomicLong();

    @Override
    public void increment() {
        count.incrementAndGet();
    }

    @Override
    public long get() {
        return count.get();
    }

    
    }

    public interface Counter { // method who set the rule
        void increment();
        long get();
        
    }

    
    static public class LockCounter implements Counter {
    private long count;
    private final Lock lock = new ReentrantLock(); // lock is a key to allow only 1 thread at a time
    @Override
    public void increment() {
        lock.lock();
        try {
            count++;
        } finally{
            lock.unlock();
        }
    }

    @Override
    public long get() {
        lock.lock();
        try{
            return count;
        }finally{
            lock.unlock();
        }
    }

}


    static public class LongAdderCounter implements Counter {
        private final LongAdder add = new LongAdder();

        @Override
        public void increment() {
            add.increment();
        }

        @Override
        public long get() {
            return add.sum(); 
        }

    }


    static public class UnsafeCounter implements Counter{ // method who follow the rule
        private long count;
        public void increment() {
            count++;
        }
        public long get() {
            return count;
        }
    }

    static public class SynchronizedCounter implements Counter{

        private long count;
        @Override
        public synchronized  void increment() {
            count++;
        }

        @Override
        public synchronized long get() {
            return count;
        }

    }





    static final int THREADS = 8;
    static final int ITERATIONS = 1_000_000;

    static void run(String label, Counter counter) throws InterruptedException {
        long start = System.nanoTime();
        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            Thread th = new Thread(() -> {
                for (int i = 0; i < ITERATIONS; i++) counter.increment();
            }, "inc-" + t);
            threads.add(th);
            th.start();
        }
        for (Thread th : threads) th.join();
        long ms = (System.nanoTime() - start) / 1_000_000;
        long expected = (long) THREADS * ITERATIONS;
        System.out.printf("%-22s value=%,d  correct=%-5b  %d ms%n",
                label, counter.get(), counter.get() == expected, ms);
    }

    public static void main(String[] args) throws InterruptedException {
        run("UnsafeCounter", new UnsafeCounter());
        // TODO: SynchronizedCounter, LockCounter, AtomicCounter, LongAdderCounter
            run("SynchronizedCounter", new SynchronizedCounter());
            run("LockCounter", new LockCounter());
            run("AtomicCounter", new AtomicCounter());
            run("LongAdderCounter", new LongAdderCounter());
    }
}