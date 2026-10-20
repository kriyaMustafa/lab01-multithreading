package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class GracfulShutdown {
    static final int thread = 8;
    private static final TimeUnit MILLISECONDS = null;
 
    static final class Account {
        final int id;
        final ReentrantLock lock = new ReentrantLock();
        private long balance;
        Account(int id, long balance) { this.id = id; this.balance = balance; }
        long balance() { return balance; }
        void withdraw(long a) { balance -= a; }
        void deposit(long a)  { balance += a; }
    }
 
    static void transfer(Account from, Account to, long amount) throws InterruptedException {
        while (true) {
            if (from.lock.tryLock(10, MILLISECONDS)) {
                try {
                    if (to.lock.tryLock(10, MILLISECONDS)) {
                        try {
                            if (from.balance() >= amount) { from.withdraw(amount); to.deposit(amount); }
                            return;
                        } finally {
                            to.lock.unlock();
                        }
                    }
                } finally {
                    from.lock.unlock();
                }
            }
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5));
        }
    }
 
    public static void main(String[] args) throws InterruptedException {
        Account[] ac = new Account[4];
        for (int i = 0; i < ac.length; i++) ac[i] = new Account(i, 1_000);
 
        long[] counts = new long[thread];  
        Thread[] worker = new Thread[thread];
        for (int w = 0; w < worker.length; w++) {
            final int i = w;
            worker[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                while (!Thread.currentThread().isInterrupted()) {
                    Account a = ac[rnd.nextInt(ac.length)];
                    Account b = ac[rnd.nextInt(ac.length)];
                    if (a == b) continue;
                    try {
                        transfer(a, b, rnd.nextInt(1, 50));
                        counts[i]++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();   
                    }
                }
            }, "transfer-" + w);
            worker[w].start();
        }
 
        Thread.sleep(2000);
        System.out.println("[main] interrupting " + thread + " workers after 2000 ms");
        for (Thread t : worker) t.interrupt();
 
        boolean allStopped = true;
        for (int w = 0; w < worker.length; w++) {
            worker[w].join(1000);              
            if (worker[w].isAlive()) allStopped = false;
            System.out.println(worker[w].getName() + " stopped after " + counts[w] + " transfers");
        }
 
        long total = 0; for (Account a : ac) total += a.balance();
        System.out.println("all workers stopped=" + allStopped + " total=" + total + " (expected 4000)");
    }
}
