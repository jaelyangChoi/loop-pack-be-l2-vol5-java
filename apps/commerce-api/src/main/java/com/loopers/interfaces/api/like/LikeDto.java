package com.loopers.interfaces.api.like;

public class LikeDto {
    public record LikeResponse(Long productId, boolean liked, Long likeCount) {}
}
