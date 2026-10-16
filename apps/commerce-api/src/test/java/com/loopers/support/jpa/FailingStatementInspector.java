package com.loopers.support.jpa;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 테스트에서만 쓰는 SQL 기록·실패 주입 장치. 켜져 있는 동안 DB 로 나가려던 SQL 을 순서대로 기록하고, 지정한 접두사에 맞는
 * N번째 변경 문장이 나가기 직전에 예외를 던진다.
 *
 * <p>앞선 변경 SQL 은 이미 DB 로 전송된 뒤이므로, 트랜잭션이 rollback 되는지 실제 MySQL 로 확인할 수 있다. 기록은 잠금
 * 순서(어떤 테이블을 어떤 순서로 {@code for update} 했는지)를 SQL 로 확인하는 데도 쓴다.
 * 운영 코드에는 실패용 스위치를 두지 않고, {@code spring.jpa.properties.hibernate.session_factory.statement_inspector}
 * 속성으로 이 클래스를 쓰는 테스트에서만 등록한다. 기본은 꺼져 있고 {@link #disarm()} 으로 끈다.
 *
 * <p>상태가 static 인 이유는 Hibernate 가 클래스 이름으로 인스턴스를 만들기 때문이다. 테스트는 순차 실행된다.
 */
public class FailingStatementInspector implements StatementInspector {

    private static volatile boolean active;
    private static volatile String failPrefix;
    private static volatile int failOnNth;
    private static final AtomicInteger MATCHED = new AtomicInteger();
    private static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    /** SQL 을 기록하되 실패시키지는 않는다. 이전 기록은 지운다. */
    public static void record() {
        arm("", 0);
    }

    /** {@code prefix} 로 시작하는 변경 문장 중 {@code nth} 번째(1부터)에서 실패시킨다. 이전 기록은 지운다. */
    public static void arm(String prefix, int nth) {
        STATEMENTS.clear();
        MATCHED.set(0);
        failOnNth = nth;
        failPrefix = prefix.toLowerCase(Locale.ROOT);
        active = true;
    }

    public static void disarm() {
        active = false;
        failPrefix = null;
        MATCHED.set(0);
        STATEMENTS.clear();
    }

    /** 켜진 뒤 DB 로 나가려던 모든 SQL 을 순서대로 돌려준다(소문자). 실패시킨 문장도 마지막에 담긴다. */
    public static List<String> statements() {
        return List.copyOf(STATEMENTS);
    }

    /** {@link #statements()} 중 변경(insert·update·delete) 문장만 순서대로 돌려준다. */
    public static List<String> mutations() {
        return STATEMENTS.stream().filter(FailingStatementInspector::isMutation).toList();
    }

    @Override
    public String inspect(String sql) {
        if (!active) {
            return sql;
        }
        String normalized = sql.stripLeading().toLowerCase(Locale.ROOT);
        STATEMENTS.add(normalized);
        if (failOnNth > 0 && isMutation(normalized) && normalized.startsWith(failPrefix)
            && MATCHED.incrementAndGet() == failOnNth) {
            throw new InjectedFailureException("주입된 실패: " + normalized);
        }
        return sql;
    }

    private static boolean isMutation(String normalized) {
        return normalized.startsWith("insert") || normalized.startsWith("update") || normalized.startsWith("delete");
    }

    /** 주입한 실패임을 원인 사슬에서 알아보기 위한 전용 예외. */
    public static class InjectedFailureException extends RuntimeException {
        public InjectedFailureException(String message) {
            super(message);
        }
    }
}
