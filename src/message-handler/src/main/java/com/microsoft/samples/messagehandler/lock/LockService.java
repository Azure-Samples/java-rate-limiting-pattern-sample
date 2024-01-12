package com.microsoft.samples.messagehandler.lock;

import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;

import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class LockService {
    private final RedisDistributedLock lock;

    public LockService(RedisDistributedLock lock) {
        this.lock = lock;
    }

    public boolean acquire(String lockKey, int lockDurationInSeconds) {
        try {
            var locked = lock.acquireLock(lockKey, lockDurationInSeconds, TimeUnit.SECONDS);

            if (locked) {
                log.info("Lock acquired for {} seconds.", lockDurationInSeconds);
            } else {
                log.info("Lock not acquired, resource is busy");
            }

            return locked;
        } catch (Exception e) {
            log.error("Failed to acquire lock.", e);
            return false;
        }
    }

    public boolean release(String lockKey) {
        try {
            lock.releaseLock(lockKey);
            log.info("Lock released.");
            return true;
        } catch (Exception e) {
            log.error("Failed to release lock.", e);
            return false;
        }
    }
}
