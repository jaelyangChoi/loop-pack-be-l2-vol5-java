package com.loopers.domain.like;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

import java.time.ZonedDateTime;

/**
 * 사용자와 상품의 좋아요 관계. {@code (userId, productId)}가 기본키라 같은 관계는 하나만 존재한다.
 *
 * <p>취소하면 행을 지우는 hard delete 이므로 {@code BaseEntity}(수정·삭제 시각)를 상속하지 않고,
 * 좋아요한 시각만 가진다. 사용자와 상품은 id 로만 참조한다.
 */
@Getter
@Entity
@Table(name = "likes")
public class Like {

    @EmbeddedId
    private LikeId id;

    @Column(name = "liked_at", nullable = false)
    private ZonedDateTime likedAt;

    // JPA 가 사용하는 기본 생성자. 새 좋아요는 create() 로 만든다.
    protected Like() {}

    public static Like create(Long userId, Long productId) {
        Like like = new Like();
        like.id = new LikeId(userId, productId);
        like.likedAt = ZonedDateTime.now();
        return like;
    }
}
