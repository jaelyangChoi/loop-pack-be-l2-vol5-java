package com.loopers.infrastructure.product;

import com.loopers.domain.common.PageCondition;
import com.loopers.domain.like.QLike;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSearchCondition;
import com.loopers.domain.product.ProductSortType;
import com.loopers.domain.product.ProductWithLikeCount;
import com.loopers.domain.product.QProduct;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.dsl.NumberExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {
    private static final QProduct product = QProduct.product;
    private static final QLike like = QLike.like;

    private final ProductJpaRepository productJpaRepository;
    private final JPAQueryFactory queryFactory;

    @Override
    public Optional<Product> findActive(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<ProductWithLikeCount> search(ProductSearchCondition condition) {
        NumberExpression<Long> likeCount = like.id.count();

        return queryFactory
            .select(product, likeCount)
            .from(product)
            .leftJoin(like).on(like.productId.eq(product.id))
            .where(searchFilter(condition))
            .groupBy(product.id)
            .orderBy(primaryOrder(condition.sort(), likeCount), product.name.asc(), product.id.asc())
            .offset(condition.page().offset())
            .limit(condition.page().size())
            .fetch()
            .stream()
            .map(row -> new ProductWithLikeCount(row.get(product), row.get(likeCount)))
            .toList();
    }

    @Override
    public long count(ProductSearchCondition condition) {
        return queryFactory
            .select(product.count())
            .from(product)
            .where(searchFilter(condition))
            .fetchOne();
    }

    @Override
    public List<Product> findActiveLikedBy(Long userId, PageCondition page) {
        return queryFactory
            .select(product)
            .from(like)
            .join(product).on(product.id.eq(like.productId))
            .where(like.userId.eq(userId), product.deletedAt.isNull())
            .orderBy(like.createdAt.desc(), like.id.desc())
            .offset(page.offset())
            .limit(page.size())
            .fetch();
    }

    @Override
    public long countActiveLikedBy(Long userId) {
        return queryFactory
            .select(like.count())
            .from(like)
            .join(product).on(product.id.eq(like.productId))
            .where(like.userId.eq(userId), product.deletedAt.isNull())
            .fetchOne();
    }

    private BooleanBuilder searchFilter(ProductSearchCondition condition) {
        BooleanBuilder filter = new BooleanBuilder(product.deletedAt.isNull());
        if (condition.brandId() != null) {
            filter.and(product.brandId.eq(condition.brandId()));
        }
        return filter;
    }

    private OrderSpecifier<?> primaryOrder(ProductSortType sort, NumberExpression<Long> likeCount) {
        return switch (sort) {
            case LATEST -> product.createdAt.desc();
            case PRICE_ASC -> product.price.asc();
            case LIKES_DESC -> likeCount.desc();
        };
    }
}
