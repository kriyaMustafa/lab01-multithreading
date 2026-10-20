package edu.itc.se.lab01.challenge;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.stream.Collectors;

public class WordCount {
    private final BlockingQueue<Path> queue = new ArrayBlockingQueue<>(16);
    private final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();
    private final AtomicInteger filesDone = new AtomicInteger();
    private final AtomicInteger totalFiles = new AtomicInteger();
    private final LongAdder wordsDone = new LongAdder();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private final AtomicBoolean resultPrinted = new AtomicBoolean();
    private static final Path POISON = Path.of("__POISON__");

    private volatile Thread scanner;
    private volatile ExecutorService pool;

    private final long startTime = System.nanoTime();
    private long lastReportWords = 0;
    private long lastReportTime = startTime;

    private final Path dir;
    private final int workers;
    private final boolean virtual;
    private final int top;
    private final long timeoutSec;

    public WordCount(Path dir, int workers, boolean virtual, int top, long timeoutSec) {
        this.dir = dir;
        this.workers = workers;
        this.virtual = virtual;
        this.top = top;
        this.timeoutSec = timeoutSec;
    }

    public void run() throws InterruptedException {
        pool = virtual ? Executors.newVirtualThreadPerTaskExecutor()
                       : Executors.newFixedThreadPool(workers);

        scanner = Thread.ofPlatform().name("scanner").start(this::scan);

        ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "reporter");
            t.setDaemon(true);
            return t;
        });
        reporter.scheduleAtFixedRate(this::report, 500, 500, TimeUnit.MILLISECONDS);

        Thread shutdownHook = new Thread(this::shutdown, "shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        try {
            for (int i = 0; i < workers; i++) {
                pool.submit(this::work);
            }
            pool.shutdown();

            boolean finished;
            if (timeoutSec > 0) {
                finished = pool.awaitTermination(timeoutSec, TimeUnit.SECONDS);
                if (!finished) {
                    double elapsed = (System.nanoTime() - startTime) / 1_000_000_000.0;
                    System.out.printf(Locale.ROOT, "[%.1fs] deadline reached – stopping%n", elapsed);
                    shutdown();
                }
            } else {
                finished = pool.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
            }

            if (finished && stopping.compareAndSet(false, true)) {
                if (resultPrinted.compareAndSet(false, true)) {
                    printResult(false);
                }
            }
        } finally {
            reporter.shutdownNow();
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down
            }
        }
    }

    private void scan() {
        try (var stream = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) stream::iterator) {
                if (Thread.currentThread().isInterrupted() || stopping.get()) {
                    return;
                }
                if (Files.isRegularFile(p) && p.toString().toLowerCase(Locale.ROOT).endsWith(".txt")) {
                    totalFiles.incrementAndGet();
                    queue.put(p);
                }
            }
        } catch (IOException e) {
            // Scanner I/O error
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (!stopping.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    for (int i = 0; i < workers; i++) {
                        queue.put(POISON);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    private void work() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Path p = queue.take();
                if (p == POISON) {
                    return;
                }
                countFile(p);
                filesDone.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void countFile(Path p) {
        try (var reader = Files.newBufferedReader(p)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] words = line.split("[^\\p{L}]+");
                for (String w : words) {
                    if (!w.isEmpty()) {
                        w = w.toLowerCase(Locale.ROOT);
                        counts.computeIfAbsent(w, k -> new LongAdder()).increment();
                        wordsDone.increment();
                    }
                }
            }
        } catch (IOException e) {
            // File read error
        }
    }

    private void report() {
        long now = System.nanoTime();
        long words = wordsDone.sum();
        int done = filesDone.get();
        int total = totalFiles.get();

        double elapsed = (now - startTime) / 1_000_000_000.0;
        double interval = (now - lastReportTime) / 1_000_000_000.0;

        long newWords = words - lastReportWords;
        double throughput = interval > 0 ? (newWords / interval) : 0;

        var top3 = counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
                .limit(3)
                .toList();

        String top3Str = top3.isEmpty()
                ? "none"
                : top3.stream()
                        .map(e -> String.format(Locale.ROOT, "%s(%,d)", e.getKey(), e.getValue().sum()))
                        .collect(Collectors.joining(" "));

        System.out.printf(Locale.ROOT, "[%.1fs] files %d/%d  words %,d  %,.0f w/s  top: %s%n",
                elapsed, done, total, words, throughput, top3Str);

        lastReportTime = now;
        lastReportWords = words;
    }

    public void shutdown() {
        if (!stopping.compareAndSet(false, true)) {
            return;
        }

        if (scanner != null) {
            scanner.interrupt();
        }
        if (pool != null) {
            pool.shutdownNow();
        }

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        try {
            if (pool != null) {
                long rem = Math.max(0, deadline - System.nanoTime());
                pool.awaitTermination(rem, TimeUnit.NANOSECONDS);
            }
            if (scanner != null && scanner.isAlive()) {
                long rem = deadline - System.nanoTime();
                if (rem > 0) {
                    long millis = rem / 1_000_000;
                    int nanos = (int) (rem % 1_000_000);
                    scanner.join(millis, nanos);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            if (resultPrinted.compareAndSet(false, true)) {
                printResult(true);
            }
        }
    }

    public void printResult(boolean partial) {
        long totalWords = wordsDone.sum();
        int totalFilesDone = filesDone.get();
        double elapsedSec = (System.nanoTime() - startTime) / 1_000_000_000.0;
        String modeStr = virtual ? "virtual" : ("platform(" + workers + ")");

        if (partial) {
            System.out.printf(Locale.ROOT, "[PARTIAL] files=%d words=%,d elapsed=%.2fs mode=%s%n",
                    totalFilesDone, totalWords, elapsedSec, modeStr);
        } else {
            System.out.printf(Locale.ROOT, "DONE  files=%d words=%,d elapsed=%.2fs mode=%s%n",
                    totalFilesDone, totalWords, elapsedSec, modeStr);
        }

        var sorted = counts.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
                .toList();

        System.out.println(" rank  word        count     share");
        int limit = Math.min(top, sorted.size());
        for (int i = 0; i < limit; i++) {
            var entry = sorted.get(i);
            long cnt = entry.getValue().sum();
            double share = totalWords == 0 ? 0.0 : (cnt * 100.0 / totalWords);
            System.out.printf(Locale.ROOT, "%5d  %-10s %,10d %7.2f %%%n",
                    i + 1, entry.getKey(), cnt, share);
        }

        try (var writer = Files.newBufferedWriter(Path.of("result.csv"))) {
            writer.write("rank,word,count,share_percent");
            writer.newLine();
            int rank = 1;
            for (var entry : sorted) {
                long cnt = entry.getValue().sum();
                double share = totalWords == 0 ? 0.0 : (cnt * 100.0 / totalWords);
                writer.write(String.format(Locale.ROOT, "%d,%s,%d,%.2f",
                        rank++, entry.getKey(), cnt, share));
                writer.newLine();
            }
            System.out.println("result.csv written");
        } catch (IOException e) {
            System.err.println("Failed to write result.csv: " + e.getMessage());
        }
    }

    public Map<String, Long> getCounts() {
        Map<String, Long> result = new HashMap<>();
        counts.forEach((word, adder) -> result.put(word, adder.sum()));
        return result;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.out.println("Usage: WordCount <dir> [--top N] [--timeout SECONDS] [--virtual]");
            return;
        }

        Path dir = Path.of(args[0]);
        int workers = Runtime.getRuntime().availableProcessors();
        int top = 10;
        long timeoutSec = 0;
        boolean virtual = false;

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--top" -> {
                    if (++i < args.length) {
                        top = Integer.parseInt(args[i]);
                    }
                }
                case "--timeout" -> {
                    if (++i < args.length) {
                        timeoutSec = Long.parseLong(args[i]);
                    }
                }
                case "--virtual" -> virtual = true;
                default -> {
                    if (args[i].matches("\\d+")) {
                        workers = Integer.parseInt(args[i]);
                    }
                }
            }
        }

        if (!Files.isDirectory(dir)) {
            System.err.println("Directory not found: " + dir);
            return;
        }

        WordCount app = new WordCount(dir, workers, virtual, top, timeoutSec);
        app.run();
    }
}
