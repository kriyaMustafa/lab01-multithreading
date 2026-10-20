package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class TryLockBank {
    static final int thread = 8;
    static final int perthread = 100_000;
    static final AtomicLong done = new AtomicLong();
    static final AtomicLong retry = new AtomicLong();
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
            retry.incrementAndGet();
            Thread.sleep(ThreadLocalRandom.current().nextInt(1, 5));  
        }
    }
 
    public static void main(String[] args) throws InterruptedException {
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) accounts[i] = new Account(i, 1_000);
 
        Thread[] workers = new Thread[thread];
        long start = System.nanoTime();
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                try {
                    for (int i = 0; i < perthread; i++) {
                        Account a = accounts[rnd.nextInt(accounts.length)];
                        Account b = accounts[rnd.nextInt(accounts.length)];
                        if (a != b) transfer(a, b, rnd.nextInt(1, 50));
                        done.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();   
                }
            }, "transfer-" + w);
            workers[w].start();
        }
        for (Thread t : workers) t.join();
        long ms = (System.nanoTime() - start) / 1_000_000;
 
        long total = 0; for (Account a : accounts) total += a.balance();
        System.out.println("transfers=" + done.get() + " retries=" + retry.get() + " total=" + total + " (expected 4000) in " + ms + " ms");
    }
}
