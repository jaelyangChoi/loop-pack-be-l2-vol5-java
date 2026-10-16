package com.loopers.support.concurrent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 테스트가 소유한 트랜잭션이 행 잠금을 쥔 채 기다리게 해, 그 사이에 실제 서비스 요청을 줄 세우는 도구다.
 * 잠금을 쥔 같은 트랜잭션에서 <b>동시에 commit 되는 변경</b>(다른 요청이 재고를 줄이거나 상품을 삭제한 것)을 미리 수행해 두고,
 * {@link #release()} 때 한꺼번에 commit 한다.
 *
 * <p>서비스가 대상 행을 처음 읽을 때 잠그지 않으면, 잠금이 풀리기 전에 이미 낡은 값을 읽고 풀린 뒤 그 값으로 덮어쓴다.
 * 잠그면 풀릴 때까지 기다렸다가 변경이 반영된 최신 값을 읽는다. 어느 쪽이 먼저 풀리는지에 기대지 않아 결정적으로 드러난다.
 * 서비스 코드의 잠금 구간에는 장벽이나 sleep 이 없다. 잠금을 쥐는 쪽은 서비스가 아니라 테스트 트랜잭션이다.
 */
public final class HeldRowLock implements AutoCloseable {

    private static final long TIMEOUT_SECONDS = 15;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final CountDownLatch acquired = new CountDownLatch(1);
    private final CountDownLatch releaseSignal = new CountDownLatch(1);
    private final Future<?> transaction;

    private HeldRowLock(
        PlatformTransactionManager transactionManager, JdbcTemplate jdbcTemplate,
        String lockSql, Object[] lockArgs, String changeSql, Object[] changeArgs
    ) {
        this.transaction = executor.submit(() -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbcTemplate.queryForList(lockSql, lockArgs);
            jdbcTemplate.update(changeSql, changeArgs);
            acquired.countDown();
            awaitRelease();
        }));
    }

    /**
     * {@code lockSql}(예: {@code select id from products where id = ? for update})로 잠금을 쥐고 {@code changeSql} 을
     * 같은 트랜잭션에서 수행한 뒤, {@link #release()} 를 부를 때까지 commit 하지 않고 기다린다. 잠금을 쥘 때까지 기다린다.
     */
    public static HeldRowLock acquire(
        PlatformTransactionManager transactionManager, JdbcTemplate jdbcTemplate,
        String lockSql, Object[] lockArgs, String changeSql, Object... changeArgs
    ) {
        HeldRowLock lock = new HeldRowLock(transactionManager, jdbcTemplate, lockSql, lockArgs, changeSql, changeArgs);
        try {
            if (!lock.acquired.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                lock.close();
                throw new AssertionError("제한 시간 안에 잠금을 잡지 못했다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            lock.close();
            throw new AssertionError("잠금을 기다리다 중단되었다.", e);
        }
        return lock;
    }

    /** 쥐고 있던 잠금을 풀고 변경을 commit 한다. */
    public void release() {
        releaseSignal.countDown();
        try {
            transaction.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("잠금을 풀다 중단되었다.", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new AssertionError("잠금을 쥔 트랜잭션이 제대로 끝나지 않았다.", e);
        }
    }

    /** 실패한 테스트에서도 잠금이 남지 않도록 풀고 스레드를 정리한다. */
    @Override
    public void close() {
        releaseSignal.countDown();
        executor.shutdownNow();
    }

    private void awaitRelease() {
        try {
            if (!releaseSignal.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("제한 시간 안에 잠금이 풀리지 않았다.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
