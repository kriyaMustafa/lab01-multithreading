package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Main {

    static long countPrimes(long from, long to) {
        long count = 0;
        for (long n = Math.max(from, 2); n < to; n++) {
            boolean prime = true;
            for (long d = 2; d * d <= n; d++) {
                if (n % d == 0) {
                    prime = false;
                    break;
                }
            }
            if (prime) {
                count++;
            }
        }
        return count;
    }

    static Callable<Long> primeCounter(long from, long to) {
        return () -> countPrimes(from, to);
    }

    static Callable<String> fakeDownload(String url) {
        return () -> {
            int id = Integer.parseInt(url.substring(url.lastIndexOf('/') + 1));
            if (id % 50 == 0) {
                throw new IOException("boom");
            }
            if (id == 30) {
                Thread.sleep(5000);
            } else {
                Thread.sleep(200);
            }
            return url + " -> " + url.length();
        };
    }

    static void runDownloads(ExecutorService executor, String label) throws InterruptedException {
        long start = System.nanoTime();
        List<Future<String>> futures = new ArrayList<>(500);
        for (int i = 1; i <= 500; i++) {
            String url = "https://example.com/" + i;
            futures.add(executor.submit(fakeDownload(url)));
        }

        int ok = 0;
        int failed = 0;
        int timedOut = 0;

        for (Future<String> future : futures) {
            try {
                future.get(1, TimeUnit.SECONDS);
                ok++;
            } catch (TimeoutException e) {
                timedOut++;
                future.cancel(true);
            } catch (ExecutionException e) {
                failed++;
            }
        }

        long elapsedMs = (System.nanoTime() - start) / 1_000_000;
        System.out.printf("%-40s : ok=%d failed=%d timedOut=%d in %d ms%n",
                label, ok, failed, timedOut, elapsedMs);
    }

    // Bonus: CompletableFuture with handle() fallback and allOf
    static void runDownloadsCompletableFuture(ExecutorService executor, String label) {
        long start = System.nanoTime();
        List<CompletableFuture<String>> futures = new ArrayList<>(500);

        for (int i = 1; i <= 500; i++) {
            String url = "https://example.com/" + i;
            CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
                try {
                    return fakeDownload(url).call();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, executor)
            .orTimeout(1, TimeUnit.SECONDS)
            .handle((result, ex) -> {
                if (ex != null) {
                    Throwable cause = ex instanceof CompletionException ? ex.getCause() : ex;
                    if (cause instanceof TimeoutException) {
                        return "[TIMED_OUT]";
                    }
                    return "[FAILED]";
                }
                return result;
            });

            futures.add(cf);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        long ok = futures.stream().filter(f -> !f.join().startsWith("[")).count();
        long failed = futures.stream().filter(f -> f.join().equals("[FAILED]")).count();
        long timedOut = futures.stream().filter(f -> f.join().equals("[TIMED_OUT]")).count();
        long elapsedMs = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("%-40s : ok=%d failed=%d timedOut=%d in %d ms (CompletableFuture bonus)%n",
                label, ok, failed, timedOut, elapsedMs);
    }

    public static void main(String[] args) throws Exception {
        final long LIMIT = 4_000_000;
        final long CHUNKS = 16;

        // CPU-bound single-threaded
        long t0 = System.nanoTime();
        long single = countPrimes(0, LIMIT);
        long singleMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("single-threaded: %d primes in %d ms%n", single, singleMs);

        // CPU-bound pool
        int processors = Runtime.getRuntime().availableProcessors();
        t0 = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(processors)) {
            List<Future<Long>> futures = new ArrayList<>();
            long chunkSize = LIMIT / CHUNKS;
            for (int i = 0; i < CHUNKS; i++) {
                long from = i * chunkSize;
                long to = (i == CHUNKS - 1) ? LIMIT : from + chunkSize;
                futures.add(pool.submit(primeCounter(from, to)));
            }
            long total = 0;
            for (Future<Long> f : futures) {
                total += f.get();
            }
            long poolMs = (System.nanoTime() - t0) / 1_000_000;
            System.out.printf("pool (%d threads): %d primes in %d ms%n", processors, total, poolMs);
        }

        // I/O-bound platform pool vs virtual threads
        try (ExecutorService platformPool = Executors.newFixedThreadPool(20)) {
            runDownloads(platformPool, "downloads on fixed(20) platform threads");
        }

        try (ExecutorService virtualPool = Executors.newVirtualThreadPerTaskExecutor()) {
            runDownloads(virtualPool, "downloads on virtual threads");
        }

        // Bonus demonstration
        try (ExecutorService platformPool = Executors.newFixedThreadPool(20)) {
            runDownloadsCompletableFuture(platformPool, "downloads CF on fixed(20) platform");
        }

        try (ExecutorService virtualPool = Executors.newVirtualThreadPerTaskExecutor()) {
            runDownloadsCompletableFuture(virtualPool, "downloads CF on virtual threads");
        }
    }
}
