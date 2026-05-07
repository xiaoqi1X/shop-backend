package com.hmall.trade.mq;

public class LocalTokenBucket {

    private final int permitsPerSecond;
    private final int burstCapacity;

    private double storedPermits;
    private long lastRefillNanos;

    public LocalTokenBucket(int permitsPerSecond, int burstCapacity) {
        this.permitsPerSecond = Math.max(1, permitsPerSecond);
        this.burstCapacity = Math.max(1, burstCapacity);
        this.storedPermits = this.burstCapacity;
        this.lastRefillNanos = System.nanoTime();
    }

    public synchronized void acquire() throws InterruptedException {
        while (!tryAcquire()) {
            long sleepMillis = Math.max(1L, Math.round(1000.0 / permitsPerSecond));
            wait(sleepMillis);
        }
    }

    private boolean tryAcquire() {
        refill();
        if (storedPermits >= 1.0) {
            storedPermits -= 1.0;
            return true;
        }
        return false;
    }

    private void refill() {
        long now = System.nanoTime();
        long elapsedNanos = now - lastRefillNanos;
        if (elapsedNanos <= 0) {
            return;
        }
        double newPermits = elapsedNanos / 1_000_000_000.0 * permitsPerSecond;
        if (newPermits > 0) {
            storedPermits = Math.min(burstCapacity, storedPermits + newPermits);
            lastRefillNanos = now;
        }
    }
}
