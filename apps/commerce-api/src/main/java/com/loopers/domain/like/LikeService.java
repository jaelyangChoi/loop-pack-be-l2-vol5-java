package com.loopers.domain.like;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class LikeService {

    private final LikeRepository likeRepository;

    /** 좋아요를 등록한다. 이미 있으면 그대로 두어 관계는 하나만 유지된다. */
    public void like(Long userId, Long productId) {
        if (likeRepository.exists(userId, productId)) {
            return;
        }
        likeRepository.save(Like.create(userId, productId));
    }

    /** 좋아요를 취소한다. 관계가 없어도 성공한다. */
    public void unlike(Long userId, Long productId) {
        likeRepository.delete(userId, productId);
    }

    public long count(Long productId) {
        return likeRepository.countByProductId(productId);
    }
}
