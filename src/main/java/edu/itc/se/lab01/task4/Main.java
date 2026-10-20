package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

public class Main {
    static long countPrimes(long from, long to) {
        long count = 0;
        for (long n = Math.max(from, 2); n < to; n++) {
            boolean prime = true;
            for (long d = 2; d * d <= n; d++) if (n % d == 0) { prime = false; break; }
            if (prime) count++;
        }
        return count;
    }
    static Callable<Long> PrimeCounter(long from,long to){
        return ()-> countPrimes(from, to);
    }

    // TODO: failures every 50th task, one 5-second task 
    static Callable<String> FakeDownload(String url){

       return ()->{
            int tnum = Integer.parseInt(url.substring(url.lastIndexOf("/")+1));
            if (tnum % 50 == 0) {
                throw new IOException("fail");
            }
            if (tnum == 30) {
                Thread.sleep(5000);
            } else {
                Thread.sleep(200);
            }
        return url + " -> " + url.length();
       }; 
    }

    static void RunDownload(ExecutorService excute, String exname) throws InterruptedException{
        long start = System.nanoTime();
        List<Future<String>> futures = new ArrayList<>();
        int download = 500;
        for (int i = 1; i <= download; i++) {
            String url = "https://example.com/"+i;
            futures.add(excute.submit(FakeDownload(url)));
        }
        // a 1-second timeout
        int ok=0,fail =0,timeout=0;
        for (int i = 0; i < futures.size(); i++) {
            Future<String> future = futures.get(i);
            try {
                String result = future.get(1,TimeUnit.SECONDS);
                ok++;
            } catch (TimeoutException e) {
                timeout++;
                future.cancel(true);
            }catch(ExecutionException e){
                fail++;
            }
        }

        long elapsed = (System.nanoTime() - start) / 1_000_000;

        System.out.printf("%s : ok=%d failed=%d timedOut=%d in %d ms%n",exname,ok,fail,timeout,elapsed);
    }


    public static void main(String[] args) throws Exception {
        final long LIMIT = 4_000_000, CHUNKS = 16;

        long t0 = System.nanoTime();
        long single = countPrimes(0, LIMIT);
        System.out.printf("single-threaded: %d primes in %d ms%n", single, (System.nanoTime() - t0) / 1_000_000);

        t0 = System.nanoTime();
        try (ExecutorService pool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())) {
            List<Future<Long>> futures = new ArrayList<>();
            // TODO: submit CHUNKS PrimeCounter callables covering [0, LIMIT)
            long size = LIMIT/CHUNKS;
            for (int i = 0; i < CHUNKS; i++) {
                long from = i*size;
                long to;
                if (i == CHUNKS-1){
                    to = LIMIT;
                }else{
                    to = from + size;
                }

                futures.add(pool.submit(PrimeCounter(from, to)));
            }
            long total = 0;
            // TODO: sum futures
            for (Future<Long> future : futures) {
                total += future.get();
            }
            int thread = Runtime.getRuntime().availableProcessors();
            System.out.printf("pool (%d threads): %d primes in %d ms%n",thread, total, (System.nanoTime() - t0) / 1_000_000);
        }

        // TODO: I/O-bound part with 500 FakeDownload tasks on platform pool vs virtual threads
        
        try(ExecutorService platformpool = Executors.newFixedThreadPool(20)){
            RunDownload(platformpool, "downloads on fixed(20) platform threads");
        }

        try(ExecutorService virtualpool = Executors.newVirtualThreadPerTaskExecutor()){
            RunDownload(virtualpool, "downloads on virtual threads");
        }


    }
}