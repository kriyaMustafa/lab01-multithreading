package edu.itc.se.lab01.task3;

import java.util.ArrayDeque;

public class BoundedBuffer implements OrderQueue {
    private final ArrayDeque<Order> items = new ArrayDeque<>();
    private final int capacity;

    public BoundedBuffer(int capacity) {
        this.capacity = capacity;
    }

    @Override
    public synchronized void put(Order order) throws InterruptedException {
        while (items.size() == capacity) {
            wait();
        }
        assert items.size() < capacity : "Buffer capacity exceeded: " + items.size() + "/" + capacity;
        items.addLast(order);
        notifyAll();
    }

    @Override
    public synchronized Order take() throws InterruptedException {
        while (items.isEmpty()) {
            wait();
        }
        assert !items.isEmpty() : "Attempted to take from empty buffer";
        Order order = items.removeFirst();
        assert order != null : "Null order retrieved from buffer";
        notifyAll();
        return order;
    }
}
