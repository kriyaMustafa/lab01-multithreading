package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

public class OrderedBank {
    static final int THREADS = 8;
    static final int TRANSFERS_PER_THREAD = 100_000;
    static final AtomicLong done = new AtomicLong();

    public static final class Account {
        final int id;
        private long balance;

        public Account(int id, long balance) {
            this.id = id;
            this.balance = balance;
        }

        public long balance() {
            return balance;
        }

        public void withdraw(long a) {
            balance -= a;
        }

        public void deposit(long a) {
            balance += a;
        }
    }

    // Fix A: Acquire monitors in ascending order of account IDs to prevent circular wait
    public static void transfer(Account from, Account to, long amount) {
        Account first = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;
        synchronized (first) {
            synchronized (second) {
                if (from.balance() >= amount) {
                    from.withdraw(amount);
                    to.deposit(amount);
                }
            }
        }
    }

    public static void main(String[] args) throws InterruptedException {
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new Account(i, 1_000);
        }

        done.set(0);
        Thread[] workers = new Thread[THREADS];
        long start = System.nanoTime();
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < TRANSFERS_PER_THREAD; i++) {
                    int fromIdx = rnd.nextInt(accounts.length);
                    int toIdx = (fromIdx + 1 + rnd.nextInt(accounts.length - 1)) % accounts.length;
                    transfer(accounts[fromIdx], accounts[toIdx], rnd.nextInt(1, 50));
                    done.incrementAndGet();
                }
            }, "transfer-" + w);
            workers[w].start();
        }

        for (Thread t : workers) {
            t.join();
        }
        long ms = (System.nanoTime() - start) / 1_000_000;

        long total = 0;
        for (Account a : accounts) {
            total += a.balance();
        }
        System.out.println("transfers=" + done.get() + " total=" + total + " (expected 4000) in " + ms + " ms");
    }
}
