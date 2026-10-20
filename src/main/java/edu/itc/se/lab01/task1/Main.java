package edu.itc.se.lab01.task1;

/*
 * Explanation of start() vs run() (--sequential mode):
 *
 * When start() is called, the JVM requests the runtime/OS scheduler to spawn a new thread
 * of execution (a platform thread or a virtual thread) and invoke run() asynchronously on
 * that distinct thread. All four threads run concurrently, interleaving their countdown
 * output, while "Mission control: all launched" prints immediately on the main thread
 * before the countdowns finish. The join() calls then wait for all threads to terminate.
 *
 * When run() is called directly (--sequential flag), no new thread is created. The run()
 * method is simply executed synchronously as a regular method call on the calling thread
 * (the 'main' thread). Consequently:
 * 1. Each countdown must finish completely (3, 2, 1, liftoff!) before the next one starts.
 * 2. "Mission control: all launched" prints only AFTER all four countdowns have finished.
 * 3. Calling join() has no effect because the threads were never started via start().
 */
public class Main {

    static void countdown(String name) {
        try {
            for (int i = 3; i >= 1; i--) {
                System.out.println(name + ": " + i);
                Thread.sleep(200);
            }
            System.out.println(name + ": liftoff!");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public static class Countdown extends Thread {
        public Countdown(String name) {
            super(name);
        }

        @Override
        public void run() {
            countdown(getName());
        }
    }

    public static class CountdownTask implements Runnable {
        @Override
        public void run() {
            countdown(Thread.currentThread().getName());
        }
    }

    public static void main(String[] args) throws InterruptedException {
        boolean sequential = args.length > 0 && args[0].equals("--sequential");

        Thread apollo  = new Countdown("apollo");
        Thread gemini  = new Thread(new CountdownTask(), "gemini");
        Thread mercury = new Thread(() -> new CountdownTask().run(), "mercury");
        Thread artemis = Thread.ofVirtual().name("artemis").unstarted(new CountdownTask());

        Thread[] threads = { apollo, gemini, mercury, artemis };

        for (Thread t : threads) {
            if (sequential) {
                t.run();
            } else {
                t.start();
            }
        }

        System.out.println("Mission control: all launched");

        for (Thread t : threads) {
            t.join();
        }

        System.out.println("Mission control: all landed");

        for (Thread t : threads) {
            System.out.printf("%-8s virtual=%-5b state=%s%n",
                    t.getName(), t.isVirtual(), t.getState());
        }
    }
}
