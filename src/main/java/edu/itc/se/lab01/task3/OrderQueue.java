package edu.itc.se.lab01.task3;

public interface OrderQueue {
    void put(Order order) throws InterruptedException;
    Order take() throws InterruptedException;
}

// Adapter so a java.util.concurrent queue can be used where OrderQueue is expected
final class BlockingOrderQueue implements OrderQueue {
    private final java.util.concurrent.BlockingQueue<Order> q;
    BlockingOrderQueue(int capacity) { this.q = new java.util.concurrent.ArrayBlockingQueue<>(capacity); }
    public void put(Order o) throws InterruptedException { q.put(o); }
    public Order take() throws InterruptedException { return q.take(); }
}

class BoundedBuffer implements OrderQueue {
    private final java.util.ArrayDeque<Order> items = new java.util.ArrayDeque<>();
    private final int capacity;
    public BoundedBuffer(int capacity) { this.capacity = capacity; }

    @Override public synchronized void put(Order order) throws InterruptedException {
        while (items.size() == capacity) {
            wait();
        }
        items.add(order);
        notifyAll();
    }
    @Override public synchronized Order take() throws InterruptedException {
        while (items.isEmpty()) {
            wait();
        }
        Order o = items.removeFirst();
        notifyAll();
        return o;
    }
}