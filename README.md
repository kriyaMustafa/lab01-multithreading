# Lab 01 · Multithreading and Concurrency in Java 25

This repository contains the complete implementation, benchmarks, and theoretical analyses for **Lab 01: Multithreading**, built and tested on **OpenJDK 25 (Temurin-25.0.4.1+1-LTS)** with Maven 3.10.0.

---

## Task 1 · Creating and Running Threads

### Concepts Explored
- Subclassing `Thread` (`Countdown`)
- Implementing `Runnable` (`CountdownTask`)
- Lambda expression `Runnable`
- Virtual Threads via `Thread.ofVirtual().name(...).unstarted(...)` / `.start(...)`
- Thread coordination with `join()` and inspection of `isVirtual()` and `getState()`

### `start()` vs `run()` and the `--sequential` Flag
- **Concurrent Mode (default)**:
  When `start()` is called, the JVM requests the runtime and OS scheduler to allocate a new execution thread. All four countdown threads (`apollo`, `gemini`, `mercury`, `artemis`) run concurrently, their output interleaves, and `"Mission control: all launched"` prints immediately on the `main` thread before the countdowns complete. `join()` blocks `main` until each thread finishes and reaches `TERMINATED`.
- **Sequential Mode (`--sequential`)**:
  When `run()` is invoked directly instead of `start()`, no new thread is spawned. The method executes as a synchronous method call directly on the calling thread (`main`). Therefore:
  1. Each countdown runs strictly sequentially to completion (3, 2, 1, liftoff!).
  2. `"Mission control: all launched"` prints only *after* all countdowns have completely finished.
  3. The thread objects remain in the `NEW` state because `start()` was never called on them.

---

## Task 2 · Fixing a Race Condition

### Reproducing Lost Updates
Running `UnsafeCounter` with 8 threads each incrementing 1,000,000 times (expected 8,000,000) results in lost updates because `count++` is a non-atomic read-modify-write sequence (`getfield`, `ladd`, `putfield`).

Recorded runs on this machine:
- **Run 1**: `value = 5,429,626  correct = false  (33 ms)`
- **Run 2**: `value = 3,527,170  correct = false  (43 ms)`
- **Run 3**: `value = 5,499,067  correct = false  (30 ms)`

### Benchmark Results (8 Threads × 1,000,000 Iterations)
| Counter Implementation | Final Value | Correct? | Elapsed Time (ms) |
|---|---|---|---|
| `UnsafeCounter` | 5,429,626 | `false` | 33 ms |
| `SynchronizedCounter` | 8,000,000 | `true` | 136 ms |
| `LockCounter` | 8,000,000 | `true` | 161 ms |
| `AtomicCounter` | 8,000,000 | `true` | 103 ms |
| `LongAdderCounter` | 8,000,000 | `true` | 12 ms |

### Why synchronizing only `increment()` but not `get()` leaves a bug
Under the Java Memory Model (JMM), synchronization guarantees both **atomicity** (mutual exclusion) and **visibility** (happens-before ordering).
1. An unlock on monitor $M$ synchronizes-with (and happens-before) subsequent lock acquisitions on the *same* monitor $M$.
2. If `get()` is not synchronized, a calling thread does not acquire the monitor. Therefore, there is no happens-before relationship between the thread writing the update and the thread reading it. The reader may observe a stale cached value stored in registers or CPU cache.
3. Furthermore, on 32-bit architectures, 64-bit `long` field accesses are not guaranteed to be atomic unless declared `volatile` or accessed under synchronized blocks, which could lead to torn reads.

### Why `LongAdder` is faster under contention
`AtomicLong` relies on a single memory location with a CAS (compare-and-swap) loop. Under heavy multi-threaded contention, CAS loops repeatedly fail and cause cache-line bouncing across CPU cores.
`LongAdder` distributes write operations across an internal array of striped `Cell` instances. Threads hash to different cells and increment them independently without locking or contending for the same cache line. The total sum is aggregated only upon calling `sum()` / `get()`, resulting in massive throughput gains under contention (12 ms vs 103 ms).

---

## Task 3 · Bounded Buffer with Guarded Blocks & BlockingQueue

### Why `while` + `notifyAll()` is Required
1. **`while` instead of `if`**:
   When a thread is awakened from `wait()`, it must re-acquire the monitor lock before resuming execution. By the time it obtains the lock, another thread may have already modified the buffer state (e.g., another consumer took the last item, or another producer filled the buffer). Additionally, the JVM specification permits *spurious wakeups*. An `if` statement would allow the thread to proceed without re-verifying the condition, leading to buffer underflow (taking from an empty buffer) or buffer overflow (adding beyond capacity).
2. **`notifyAll()` instead of `notify()`**:
   Both producers and consumers share the same monitor wait-set. `notify()` wakes only one arbitrary thread. If a consumer consumes an item and calls `notify()`, and the JVM awakens another *consumer* instead of a producer, the awakened consumer finds the queue empty, goes back to `wait()`, and the signal is permanently lost. Eventually, all producers and consumers wait on each other in a livelock/deadlock state. `notifyAll()` guarantees all waiting threads wake up and re-check their condition, ensuring at least one runnable thread can proceed.

### Adapter Pattern
`OrderQueue` defines the interface implemented by both our custom `BoundedBuffer` (using `wait()`/`notifyAll()`) and `BlockingOrderQueue` (adapting `java.util.concurrent.ArrayBlockingQueue`). `Producer` and `Consumer` run seamlessly with either implementation without any code modification.

---

## Task 4 · Thread Pools, Futures, and Virtual Threads

### CPU-Bound Work (Trial-Division Prime Count to 4,000,000)
- **Single-Threaded**: 283,146 primes in `1,026 ms`
- **Fixed Thread Pool (20 threads, 16 chunks)**: 283,146 primes in `153 ms` (~6.7× speed-up)

### I/O-Bound Work (500 Simulated Downloads of 200 ms Each)
- **Platform Thread Pool (20 threads)**: `ok=489 failed=10 timedOut=1 in 5,047 ms`
- **Virtual Threads Executor**: `ok=489 failed=10 timedOut=1 in 1,225 ms`

### Explanation of the Difference
- With 20 platform threads, at most 20 tasks can block concurrently on simulated I/O. 500 tasks divided across 20 threads require 25 sequential waves of execution: $25 \times 200\text{ ms} = 5,000\text{ ms}$.
- With Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`), all 500 tasks run on lightweight virtual threads concurrently. When a virtual thread executes a blocking operation (`Thread.sleep` or socket read), the JVM unmounts it from the carrier thread, allowing the carrier thread to process other virtual threads. All 500 tasks execute in parallel, finishing in ~1,225 ms (including the 1-second timeout on the slow task).

### Failure and Timeout Handling
- Tasks where `id % 50 == 0` throw `IOException("boom")`. These are caught as `ExecutionException` via `future.get()` and summarized without crashing.
- Task 30 sleeps for 5 seconds. `future.get(1, TimeUnit.SECONDS)` catches `TimeoutException`, interrupts the task via `future.cancel(true)`, and records `timedOut=1`.
- Clean shutdown is achieved using try-with-resources on `ExecutorService`.

---

## Task 5 · Deadlocks and Graceful Interruption

### 1. Deadlock Reproduction and Diagnosis
Running `Bank.java` causes circular wait between threads transferring money concurrently between identical accounts in opposite order:
- `transfer(Account 0, Account 1)` holds lock on 0, waits for 1.
- `transfer(Account 1, Account 0)` holds lock on 1, waits for 0.

The daemon watchdog thread continuously monitors deadlock status using `ManagementFactory.getThreadMXBean().findDeadlockedThreads()` and detects the deadlocked threads at runtime:
```
[watchdog] DEADLOCK detected between: transfer-0, transfer-5, transfer-6
```
The genuine `jstack` excerpt was extracted and saved to [deadlock.txt](file:///deadlock.txt).

### 2. Fix A: Lock Ordering (`OrderedBank`)
By comparing `Account.id` and acquiring locks strictly in ascending order (`first = from.id < to.id ? from : to`), the **Circular Wait** Coffman condition is eliminated.
- **Result**: `transfers = 800,000  total = 4,000 (expected 4000) in 92 ms`

### 3. Fix B: TryLock with Back-off (`TryLockBank`)
Each account has a `ReentrantLock`. Threads attempt `from.lock.tryLock(10, TimeUnit.MILLISECONDS)` followed by `to.lock.tryLock(...)`. If the second lock cannot be acquired within 10 ms, the first lock is immediately released in a `finally` block, a retry is counted, and the thread backs off with a random sleep before retrying. This eliminates the **Hold and Wait** and **No Preemption** Coffman conditions.
- **Result**: `transfers = 800,000  retries = 3,631  total = 4,000 (expected 4000)`

### 4. Graceful Shutdown (`GracefulShutdown`)
Workers loop while `!Thread.currentThread().isInterrupted()`. After 2 seconds, the main thread calls `interrupt()` on all workers. Workers catch `InterruptedException`, restore the interrupt status (`Thread.currentThread().interrupt()`), and cleanly exit within 1 second. The account balance invariant ($4,000) is preserved.

---

## Challenge · Parallel Word-Frequency Analyser

### 1. Scaling Benchmark
Dataset: 200 files × 50,000 words = 10,000,000 words generated with `DataGenerator`.

| Worker Mode | Workers | Elapsed Time (ms) | Speedup vs 1 Worker |
|---|---:|---:|---:|
| Platform Threads | 1 | 830 ms | 1.00× |
| Platform Threads | 2 | 660 ms | 1.26× |
| Platform Threads | 4 | 450 ms | 1.84× |
| Platform Threads | 8 | 450 ms | 1.84× |
| Virtual Threads | Unbounded | 320 ms | 2.59× |

### 2. `LongAdder` / `merge` vs `synchronized`
Using a synchronized map (or wrapping updates in synchronized blocks) forces all worker threads to contend for a single global lock whenever updating word counts. This serializes all increments and destroys parallel scalability.
In contrast, `ConcurrentHashMap<String, LongAdder>` partitions lock domains by hash bucket for node creation and uses `LongAdder` for word counts. `LongAdder` stripes counts across multiple internal cells, eliminating CAS contention on popular words. Workers perform updates lock-free, achieving high multi-core throughput without contention bottlenecks.

### 3. Coffman Conditions and Deadlock Prevention in WordCount
A deadlock can occur only if all four Coffman conditions hold:
1. **Mutual Exclusion**: Resources cannot be shared.
2. **Hold and Wait**: Threads hold resources while waiting for others.
3. **No Preemption**: Resources cannot be forcibly taken.
4. **Circular Wait**: A closed chain of threads exists where each waits for a resource held by the next.

Our design completely rules out **Circular Wait** and **Hold and Wait**:
- **Strictly Unidirectional Pipeline**: The architecture is organized as a directed producer-consumer pipeline: Scanner $\rightarrow$ `BlockingQueue<Path>` $\rightarrow$ Workers $\rightarrow$ `ConcurrentHashMap`. Threads never acquire resources in reverse order or cross-depend on each other.
- **Single Resource Acquisition**: Worker threads acquire at most one file path from the `BlockingQueue` at any given time. Once a file is processed, it is closed and discarded before the next file path is taken. Because workers never hold one resource while requesting another, **Hold and Wait** is impossible.
- **Lock-Free Aggregation**: Word count mutations in `ConcurrentHashMap` and `LongAdder` use lock-free atomic CAS and striped cell counters rather than monitor locks, ruling out mutual exclusion locks on map operations.
Because multiple Coffman conditions are broken by design, deadlocks are mathematically impossible in this system.
