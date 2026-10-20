package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

public class GracefulShutdown {
    static final int THREADS = 8;

    public static final class Account {
        final int id;
        final ReentrantLock lock = new ReentrantLock();
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

    public static void transfer(Account from, Account to, long amount) throws InterruptedException {
        while (!Thread.currentThread().isInterrupted()) {
            if (from.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                try {
                    if (to.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                        try {
                            if (from.balance() >= amount) {
                                from.withdraw(amount);
                                to.deposit(amount);
                            }
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
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new Account(i, 1_000);
        }

        long[] counts = new long[THREADS];
        Thread[] workers = new Thread[THREADS];

        for (int w = 0; w < workers.length; w++) {
            final int index = w;
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                while (!Thread.currentThread().isInterrupted()) {
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a == b) continue;
                    try {
                        transfer(a, b, rnd.nextInt(1, 50));
                        counts[index]++;
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }, "transfer-" + w);
            workers[w].start();
        }

        Thread.sleep(2000);
        System.out.println("[main] interrupting " + THREADS + " workers after 2000 ms");
        for (Thread t : workers) {
            t.interrupt();
        }

        boolean allStopped = true;
        for (int w = 0; w < workers.length; w++) {
            workers[w].join(1000);
            if (workers[w].isAlive()) {
                allStopped = false;
            }
            System.out.println(workers[w].getName() + " stopped after " + counts[w] + " transfers");
        }

        long total = 0;
        for (Account a : accounts) {
            total += a.balance();
        }
        System.out.println("all workers stopped=" + allStopped + " total=" + total + " (expected 4000)");
    }
}
