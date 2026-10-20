package edu.itc.se.lab01.challenge;

import java.io.IOException;
import java.nio.file.*;
import java.util.Locale;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.Map;

public class WordCount {
    private final BlockingQueue<Path> queue = new ArrayBlockingQueue<>(16);
    private final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();
    private final AtomicInteger filesDone = new AtomicInteger();
    private final AtomicInteger totalFiles = new AtomicInteger();
    private final LongAdder wordsDone = new LongAdder();
    private final AtomicBoolean stopping = new AtomicBoolean();
    private static final Path POISON = Path.of("__POISON__");

    private final AtomicBoolean cancelled = new AtomicBoolean();
    private final AtomicBoolean resultprint = new AtomicBoolean();
    private volatile Thread scanner;
    private volatile ExecutorService pool;


    private final long startTime = System.nanoTime();
    private long lastReportword = 0;
    private long lastReportTime = startTime;

    private final Path dir; private final int workers; private final boolean virtual;
    private final int top; private final long timeoutSec;

    WordCount(Path dir, int workers, boolean virtual, int top, long timeoutSec) {
        this.dir = dir;
        this.workers = workers;
        this.virtual = virtual;
        this.top = top;
        this.timeoutSec = timeoutSec;     
    
    }

   void run() throws InterruptedException {
    long start = System.nanoTime();
    long timeoutNanos = timeoutSec > 0 ? TimeUnit.SECONDS.toNanos(timeoutSec) : Long.MAX_VALUE;
    pool = virtual ? Executors.newVirtualThreadPerTaskExecutor()
                                   : Executors.newFixedThreadPool(workers);
    scanner = Thread.ofPlatform().name("scanner").start(this::scan);
    ScheduledExecutorService reporter = Executors.newSingleThreadScheduledExecutor();
    reporter.scheduleAtFixedRate(this::report, 500, 500, TimeUnit.MILLISECONDS);
    Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "shutdown-hook"));
    boolean finished;
    try {
        for (int i = 0; i < workers; i++) {
            pool.submit(this::work);
        }
        pool.shutdown();

        long remaining = Math.max(0, timeoutNanos - (System.nanoTime() - start));
        finished = pool.awaitTermination(remaining, TimeUnit.NANOSECONDS);

        if (!finished) {
            shutdown();
        }
        if (!cancelled.get() && stopping.compareAndSet(false, true)) {
            if (resultprint.compareAndSet(false, true)) {
                printResult(false);
            }
        }
    } finally {
        reporter.shutdownNow();
    }

}

    private void scan(){ 
        /* Files.walk(dir) → queue.put(path); finally put one POISON per worker */ 
        try (var path = Files.walk(dir)) {
            for (Path p : (Iterable<Path>) path::iterator) {
                if (Files.isRegularFile(p) && p.toString().toLowerCase(Locale.ROOT).endsWith(".txt")) {
                    totalFiles.incrementAndGet();
                    queue.put(p);
                    System.out.println("found: "+p);
                }
            }
        }catch (IOException e) {
            System.out.println("something wrong");
        }catch(InterruptedException e){
            System.out.println("thread interrupt");
        }finally{
            if(!stopping.get() && !Thread.currentThread().isInterrupted()){
                try {
                    for (int i = 0; i < workers; i++) {
                    queue.put(POISON);
                }
                } catch (Exception e) {
                    System.out.println("thread interrupt");
                }
                
            }
        } 
    
    }

    private void work() {
        try {
            while (!Thread.currentThread().isInterrupted()) {
                Path p = queue.take();
                if (p == POISON) return;
                countFile(p);
                filesDone.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void countFile(Path p) { 
        /* read lines, split on [^\p{L}]+, lower-case, counts.computeIfAbsent(w, k -> new LongAdder()).increment() */ 
        try(var reader = Files.newBufferedReader(p)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] word = line.split("[^\\p{L}\\p{N}]+");
                for (String w : word) {
                    if (!w.isEmpty()) {
                        w = w.toLowerCase(Locale.ROOT);
                        counts.computeIfAbsent(w,k-> new LongAdder()).increment();
                        wordsDone.increment();
                    }
                }
            }
        } catch (Exception e) {
            // TODO: handle exception
            System.out.println("can't read file");
        }
    }

    private void report() { 
        /* files done / total, words, words/s, top 3 – never lock the map */ 
        long now = System.nanoTime();
        long word = wordsDone.sum();

        double elapsed = (now - startTime) / 1_000_000_000.0;
        double interval = (now - lastReportTime) / 1_000_000_000.0;

        long newWords = word - lastReportword;
        double speed = interval> 0 ? newWords/interval: 0;
        var top3 = counts.entrySet().stream().sorted((a,b)-> Long.compare(b.getValue().sum(), a.getValue().sum())).limit(3).toList();
        System.out.printf(Locale.ROOT,"[Progress] %.1fs | Files: %d/%d | Words: %,d | Speed: %,.0f words/s%n",elapsed,filesDone.get(),totalFiles.get(),word,speed);
        System.out.print("[Top 3] ");

        if(top3.isEmpty()){
            System.out.println("no word yet");
        }else{
            for(int i=0; i<top3.size();i++){
                var entry = top3.get(i);
                if (i>0) {
                    System.out.print(", ");
                }
                System.out.print( entry.getKey() + "=" + entry.getValue().sum());
            }
            System.out.println();
        }
        lastReportTime  = now;
        lastReportword = word;
    }

    void shutdown() { 
        if (!stopping.compareAndSet(false, true)) { 
        /* interrupt scanner + workers, await ≤ 2 s, printResult(true) */ 
            return;
        }
        cancelled.set(true);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        if (scanner != null) {
            scanner.interrupt();
        }
        if (pool != null) {
            pool.shutdownNow();
        } 
        try {
            if (pool != null) {
                long remaining = Math.max(0, deadline - System.nanoTime());
                pool.awaitTermination(remaining, TimeUnit.NANOSECONDS);
            }
            if (scanner != null) {
                long remaining = deadline - System.nanoTime();
                if (remaining > 0 && scanner.isAlive()) {
                    long millis = remaining / 1_000_000;
                    int nanos = (int) (remaining % 1_000_000);
                    scanner.join(millis, nanos);
            }
            }
        } catch (InterruptedException e) {
            // TODO: handle exception
            System.out.println("thread stuck");
        }finally{
            if (resultprint.compareAndSet(false, true)) {
                printResult(true);
            }
        }
    }

    void printResult(boolean partial) { 
        /* sort entries by count desc, print top N with %, write result.csv */ 
        long total = wordsDone.sum();
        var sort = counts.entrySet().stream().sorted((a,b)-> Long.compare(b.getValue().sum(),a.getValue().sum())).toList();

        if (partial) {
            System.out.println("[PARTIAL] files=" + filesDone.get()+ " words=" + String.format(Locale.ROOT, "%,d", total));
        }else{
            System.out.println("DONE files=" + filesDone.get()+ " words=" + String.format(Locale.ROOT, "%,d", total));
        }
        System.out.printf("%5s  %-12s %12s %10s%n","rank", "word", "count", "share");
        int limit = Math.min(top,sort.size());
        for(int i=0;i<limit;i++){
            var entry = sort.get(i);
            String word = entry.getKey();
            long count = entry.getValue().sum();
        
            double share = total == 0 ? 0.0 : count * 100.0/total;
            System.out.printf(Locale.ROOT,"%5d  %-12s %,12d %9.2f%%%n",i + 1, word, count, share);
        }

        try(var w = Files.newBufferedWriter(Path.of("result.csv"))) {
            w.write("rank,word,count,share_percent");
            w.newLine();
            int rank =1 ;
            for (var entry : sort) {
                long count = entry.getValue().sum();
                double share = total == 0 ? 0.0 : count * 100.0/total;
                w.write(String.format(Locale.ROOT,"%d,%s,%d,%.2f",rank++, entry.getKey(), count, share));
                w.newLine();
            }
            System.out.println("write to csv");
        } catch (Exception e) {
            // TODO: handle exception
            System.out.println("something wrong");
        }

    }

    public static void main(String[] args) throws Exception { 
        /* parse args, new WordCount(...).run() */ 
        if (args.length == 0) {
            System.out.println("Usage: WordCount <dir> [--top N] [--timeout SECONDS] [--virtual]");
            return;
        }
        Path dir = Path.of(args[0]);
        int workers = Runtime.getRuntime().availableProcessors();
        int top = 10;
        long timeoutSec =0;
        boolean virtual = false;
        for(int i=1;i<args.length;i++){
            switch (args[i]) {
                case "--top"->{
                    if (++i >= args.length) {
                        System.out.println("--top need number");
                    }
                    top = Integer.parseInt(args[i]);
                    if (top < 1) {
                        System.out.println("--top must > 1");
                    }
                }
                    
                case "--timeout"->{
                    if (++i >= args.length) {
                        System.out.println("--timeout need number");
                    }
                    timeoutSec = Integer.parseInt(args[i]);
                    if (timeoutSec< 1) {
                        System.out.println("--timeout must > 1s");
                    }
                }
                    
                case "--virtual" -> virtual =  true;
                default ->
                    System.out.println("invalid");
            }
        }

        if (!Files.isDirectory(dir)) {
            System.out.println("directory not found: "+dir);
            return;
        }
        WordCount app = new WordCount(dir, workers, virtual, top, timeoutSec);
        app.run();

    }
    Map<String, Long> getCounts() {
        Map<String, Long> result = new java.util.HashMap<>();
        counts.forEach((word, count) -> result.put(word, count.sum()));
        return result;
    }

}