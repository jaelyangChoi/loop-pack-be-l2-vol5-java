package com.loopers.support.concurrent;

import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 같은 자원을 향한 요청을 여러 스레드에서 동시에 실행하고, 요청마다 결과를 성공 / 업무 거절 / 기술 오류로 나눠 모은다.
 *
 * <p>스레드는 모두 준비된 뒤 한꺼번에 출발한다. 출발 시점만 맞추고, 요청 안(잠금 구간)에는 장벽이나 sleep 을 두지 않는다.
 * 각 요청은 실제 Facade·Repository·MySQL 을 지나는 독립 트랜잭션이다. 제한 시간을 넘긴 요청은 업무 거절로 세지 않고
 * 테스트를 실패시킨다. 예외를 출력만 하고 버리지 않으며 {@link Outcome} 에 그대로 담는다.
 */
public final class ConcurrentRequests {

    private static final long READY_TIMEOUT_SECONDS = 10;
    private static final long RESULT_TIMEOUT_SECONDS = 30;

    private ConcurrentRequests() {
    }

    public enum Kind { SUCCESS, REJECTED, TECHNICAL }

    /** {@code errorType} 은 업무 거절일 때만, {@code error} 는 기술 오류일 때만 있다. */
    public record Outcome(Kind kind, ErrorType errorType, Throwable error) {
        @Override
        public String toString() {
            return switch (kind) {
                case SUCCESS -> "SUCCESS";
                case REJECTED -> "REJECTED(" + errorType + ")";
                case TECHNICAL -> "TECHNICAL(" + error + ")";
            };
        }
    }

    public record Results(List<Outcome> outcomes) {
        public int total() {
            return outcomes.size();
        }

        public long successes() {
            return outcomes.stream().filter(outcome -> outcome.kind() == Kind.SUCCESS).count();
        }

        public long rejectedWith(ErrorType errorType) {
            return outcomes.stream()
                .filter(outcome -> outcome.kind() == Kind.REJECTED && outcome.errorType() == errorType)
                .count();
        }

        public long rejections() {
            return outcomes.stream().filter(outcome -> outcome.kind() == Kind.REJECTED).count();
        }

        public long technicalErrors() {
            return outcomes.stream().filter(outcome -> outcome.kind() == Kind.TECHNICAL).count();
        }
    }

    /** 요청을 모두 동시에 출발시키고, 모두 끝난 뒤 결과를 돌려준다. */
    public static Results run(List<Runnable> requests) {
        return run(requests, () -> { });
    }

    /**
     * 요청을 모두 동시에 출발시키고, <b>모든 요청이 서비스 호출에 들어간 뒤</b> {@code afterAllEntered} 를 한 번 실행하고,
     * 모두 끝난 뒤 결과를 돌려준다. 잠금을 쥔 테스트 트랜잭션을 푸는 데 쓴다. 서비스 호출 안에는 개입하지 않는다.
     */
    public static Results run(List<Runnable> requests, Runnable afterAllEntered) {
        return run(requests, afterAllEntered, RESULT_TIMEOUT_SECONDS);
    }

    /**
     * {@link #run(List, Runnable)} 와 같지만 요청마다 결과를 기다리는 제한 시간을 정한다. 잠금이 걸려 있는 동안에도 요청이
     * 막히지 않고 끝나야 하는 검증에서 짧게 준다. 제한 시간을 넘기면 업무 거절로 세지 않고 테스트를 실패시킨다.
     */
    public static Results run(List<Runnable> requests, Runnable afterAllEntered, long resultTimeoutSeconds) {
        int count = requests.size();
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch entered = new CountDownLatch(count);
        try {
            List<Future<Outcome>> futures = new ArrayList<>();
            for (Runnable request : requests) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    if (!start.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("출발 신호를 받지 못했다.");
                    }
                    entered.countDown();
                    return execute(request);
                }));
            }
            if (!ready.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("모든 요청 스레드가 제한 시간 안에 준비되지 않았다.");
            }
            start.countDown();
            if (!entered.await(READY_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                throw new AssertionError("모든 요청이 제한 시간 안에 서비스 호출에 들어가지 않았다.");
            }
            afterAllEntered.run();

            List<Outcome> outcomes = new ArrayList<>();
            for (Future<Outcome> future : futures) {
                outcomes.add(future.get(resultTimeoutSeconds, TimeUnit.SECONDS));
            }
            return new Results(outcomes);
        } catch (TimeoutException e) {
            throw new AssertionError("요청이 제한 시간 안에 끝나지 않았다. 업무 거절로 세지 않고 실패로 본다.", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("요청 대기 중 중단되었다.", e);
        } catch (java.util.concurrent.ExecutionException e) {
            throw new AssertionError("요청 스레드 자체가 실패했다.", e);
        } finally {
            start.countDown();
            executor.shutdownNow();
        }
    }

    private static Outcome execute(Runnable request) {
        try {
            request.run();
            return new Outcome(Kind.SUCCESS, null, null);
        } catch (CoreException e) {
            // 서버 오류(INTERNAL_ERROR)는 업무 규칙에 의한 거절이 아니라 기술 오류다.
            if (e.getErrorType() == ErrorType.INTERNAL_ERROR) {
                return new Outcome(Kind.TECHNICAL, null, e);
            }
            return new Outcome(Kind.REJECTED, e.getErrorType(), null);
        } catch (Throwable e) {
            return new Outcome(Kind.TECHNICAL, null, e);
        }
    }
}
