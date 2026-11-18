package com.co.kc.imchat.plugin.lock;

import com.co.kc.imchat.plugin.lock.core.MutexLock;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class MutexLockTest {

    @Test
    void immediateAcquisitionIsNonReentrantAndInstancesAreIndependent() {
        MutexLock lock = new MutexLock();
        MutexLock other = new MutexLock();
        assertTrue(lock.tryLock());
        assertFalse(lock.tryLock());
        assertTrue(other.tryLock());
        other.unlock();
        lock.unlock();
        assertTrue(lock.tryLock());
        lock.unlock();
    }

    @Test
    void anotherThreadCanReleaseAndIdleReleaseFails() throws Exception {
        MutexLock lock = new MutexLock();
        assertThrows(IllegalMonitorStateException.class, lock::unlock);
        lock.lock();
        FutureTask<Void> release = new FutureTask<>(lock::unlock, null);
        Thread.ofVirtual().start(release);
        release.get(5, TimeUnit.SECONDS);
        assertThrows(IllegalMonitorStateException.class, lock::unlock);
        assertTrue(lock.tryLock());
        lock.unlock();
    }

    @Test
    void releaseWakesBlockedAcquirer() throws Exception {
        MutexLock lock = new MutexLock();
        lock.lock();
        FutureTask<Boolean> acquired = new FutureTask<>(() -> {
            lock.lock();
            try {
                return true;
            } finally {
                lock.unlock();
            }
        });
        Thread waiter = Thread.ofVirtual().start(acquired);
        try {
            awaitWaiting(waiter);
            assertFalse(acquired.isDone());
        } finally {
            lock.unlock();
        }
        assertTrue(acquired.get(5, TimeUnit.SECONDS));
    }

    @Test
    void timedAcquisitionHonorsOccupiedAndImmediateCases() throws Exception {
        MutexLock lock = new MutexLock();
        assertTrue(lock.tryLock(0, TimeUnit.NANOSECONDS));
        assertFalse(lock.tryLock(0, TimeUnit.NANOSECONDS));
        assertFalse(lock.tryLock(-1, TimeUnit.NANOSECONDS));
        assertFalse(lock.tryLock(1, TimeUnit.MILLISECONDS));
        lock.unlock();
        assertTrue(lock.tryLock(-1, TimeUnit.NANOSECONDS));
        lock.unlock();
        assertThrows(NullPointerException.class, () -> lock.tryLock(1, null));
        assertThrows(UnsupportedOperationException.class, lock::newCondition);
    }

    @Test
    void timedAcquisitionSucceedsAfterRelease() throws Exception {
        MutexLock lock = new MutexLock();
        lock.lock();
        FutureTask<Boolean> acquired = new FutureTask<>(() -> {
            boolean success = lock.tryLock(30, TimeUnit.SECONDS);
            if (success) {
                lock.unlock();
            }
            return success;
        });
        Thread waiter = Thread.ofVirtual().start(acquired);
        try {
            awaitWaiting(waiter);
            assertFalse(acquired.isDone());
        } finally {
            lock.unlock();
        }
        assertTrue(acquired.get(5, TimeUnit.SECONDS));
    }

    @Test
    void interruptibleMethodsRejectPreInterruptedEntryEvenWhenIdle() throws Exception {
        MutexLock lock = new MutexLock();
        FutureTask<Void> task = new FutureTask<>(() -> {
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class, lock::lockInterruptibly);
            assertFalse(Thread.currentThread().isInterrupted());
            Thread.currentThread().interrupt();
            assertThrows(InterruptedException.class, () -> lock.tryLock(1, TimeUnit.SECONDS));
            assertFalse(Thread.currentThread().isInterrupted());
            return null;
        });
        Thread.ofVirtual().start(task);
        task.get(5, TimeUnit.SECONDS);
        assertTrue(lock.tryLock());
        lock.unlock();
    }

    @Test
    void interruptedWaiterDoesNotPreventQueuedSuccessorAcquisition() throws Exception {
        for (boolean timed : List.of(false, true)) {
            MutexLock lock = new MutexLock();
            lock.lock();
            FutureTask<Void> interrupted = new FutureTask<>(() -> {
                assertThrows(InterruptedException.class, () -> {
                    if (timed) {
                        lock.tryLock(1, TimeUnit.DAYS);
                    } else {
                        lock.lockInterruptibly();
                    }
                });
                assertFalse(Thread.currentThread().isInterrupted());
                return null;
            });
            Thread waiter = Thread.ofVirtual().start(interrupted);
            FutureTask<Boolean> acquired = new FutureTask<>(() -> {
                lock.lock();
                try {
                    return true;
                } finally {
                    lock.unlock();
                }
            });
            try {
                awaitWaiting(waiter);
                Thread successor = Thread.ofVirtual().start(acquired);
                awaitWaiting(successor);
                waiter.interrupt();
                interrupted.get(5, TimeUnit.SECONDS);
                assertFalse(acquired.isDone());
                assertFalse(lock.tryLock());
            } finally {
                waiter.interrupt();
                lock.unlock();
            }
            assertTrue(acquired.get(5, TimeUnit.SECONDS));
            assertTrue(lock.tryLock());
            lock.unlock();
        }
    }

    @Test
    void uninterruptibleAcquisitionRestoresInterruptFlag() throws Exception {
        for (boolean preInterrupted : List.of(false, true)) {
            MutexLock lock = new MutexLock();
            lock.lock();
            FutureTask<Boolean> acquired = new FutureTask<>(() -> {
                if (preInterrupted) {
                    Thread.currentThread().interrupt();
                }
                lock.lock();
                try {
                    return Thread.currentThread().isInterrupted();
                } finally {
                    lock.unlock();
                    Thread.interrupted();
                }
            });
            Thread waiter = Thread.ofVirtual().start(acquired);
            try {
                awaitWaiting(waiter);
                if (!preInterrupted) {
                    waiter.interrupt();
                }
                assertFalse(acquired.isDone());
            } finally {
                lock.unlock();
            }
            assertTrue(acquired.get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void concurrentAcquirersNeverOverlapAndPublishWrites() throws Exception {
        MutexLock lock = new MutexLock();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger active = new AtomicInteger();
        int[] protectedCounter = {0};
        List<FutureTask<Void>> tasks = new ArrayList<>();
        for (int worker = 0; worker < 4; worker++) {
            FutureTask<Void> task = new FutureTask<>(() -> {
                assertTrue(start.await(5, TimeUnit.SECONDS));
                for (int iteration = 0; iteration < 100; iteration++) {
                    lock.lock();
                    try {
                        assertEquals(1, active.incrementAndGet());
                        protectedCounter[0]++;
                    } finally {
                        active.decrementAndGet();
                        lock.unlock();
                    }
                }
                return null;
            });
            tasks.add(task);
            Thread.ofVirtual().start(task);
        }
        start.countDown();
        for (FutureTask<Void> task : tasks) {
            task.get(5, TimeUnit.SECONDS);
        }
        assertEquals(400, protectedCounter[0]);
        assertEquals(0, active.get());
    }

    private static void awaitWaiting(Thread thread) {
        await().atMost(Duration.ofSeconds(5)).until(() -> {
            Thread.State state = thread.getState();
            return state == Thread.State.WAITING || state == Thread.State.TIMED_WAITING;
        });
    }
}
