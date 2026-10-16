package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductRepository;
import com.loopers.domain.product.ProductSort;
import com.loopers.infrastructure.like.ProductLikeCountJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@RequiredArgsConstructor
@Component
public class ProductRepositoryImpl implements ProductRepository {
    private final ProductJpaRepository productJpaRepository;
    private final ProductLikeCountJpaRepository productLikeCountJpaRepository;

    /**
     * 상품을 저장하고 좋아요 수 집계의 행을 노출 상태에 맞춘다. 노출 중이면 행을 만들고(없을 때 0, 있으면 좋아요에서 다시 센 값),
     * 삭제됐으면 행을 지워 브랜드 없는 좋아요순 목록에서 뺀다. 삭제 처리는 이 저장을 거쳐야 한다.
     */
    @Override
    public Product save(Product product) {
        Product saved = productJpaRepository.save(product);
        if (saved.getDeletedAt() == null) {
            productLikeCountJpaRepository.upsertFromLikes(saved.getId());
        } else {
            productLikeCountJpaRepository.deleteByProductId(saved.getId());
        }
        return saved;
    }

    @Override
    public Optional<Product> findActive(Long id) {
        return productJpaRepository.findByIdAndDeletedAtIsNull(id);
    }

    @Override
    public List<Product> findAllActiveByIds(Collection<Long> ids) {
        // 빈 목록으로 IN 조회를 하면 DB 에 따라 문법 오류가 나므로 조회하지 않는다.
        if (ids.isEmpty()) {
            return List.of();
        }
        return productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids);
    }

    @Override
    public Page<Product> findAllActive(Pageable pageable) {
        return productJpaRepository.findAllByDeletedAtIsNullOrderByIdDesc(pageable);
    }

    @Override
    public Page<Product> findAllActive(Long brandId, ProductSort sort, Pageable pageable) {
        return switch (sort) {
            case LATEST -> productJpaRepository.findAllActiveOrderByLatest(brandId, pageable);
            case PRICE_ASC -> productJpaRepository.findAllActiveOrderByPriceAsc(brandId, pageable);
            case LIKES_DESC -> brandId == null
                ? findAllActiveOrderByLikesDesc(pageable)
                : productJpaRepository.findAllActiveOrderByLikesDescOfBrand(brandId, pageable);
        };
    }

    // 브랜드 없는 likes_desc: ① 집계 테이블에서 페이지의 상품 id 를 읽고 ② 그 id 로 상품을 조회한다. 집계는 노출 상품만
    // 행을 가지므로 ①이 페이지 크기만 읽고, ②에서 삭제 여부를 한 번 더 확인한다(경쟁으로 빠진 id 는 건너뛴다).
    private Page<Product> findAllActiveOrderByLikesDesc(Pageable pageable) {
        List<Long> ids = productLikeCountJpaRepository.findProductIdsOrderByLikeCountDesc(pageable);
        long total = productJpaRepository.countActive();
        if (ids.isEmpty()) {
            return new PageImpl<>(List.of(), pageable, total);
        }
        Map<Long, Product> found = productJpaRepository.findAllByIdInAndDeletedAtIsNull(ids).stream()
            .collect(Collectors.toMap(Product::getId, Function.identity()));
        List<Product> ordered = ids.stream().map(found::get).filter(Objects::nonNull).toList();
        return new PageImpl<>(ordered, pageable, total);
    }

    @Override
    public Page<Product> findAllActiveLikedBy(Long userId, Pageable pageable) {
        return productJpaRepository.findAllActiveLikedBy(userId, pageable);
    }

    @Override
    public Optional<Product> findActiveForUpdate(Long id) {
        return productJpaRepository.findActiveForUpdate(id);
    }

    @Override
    public List<Product> findAllActiveByIdsForUpdate(Collection<Long> ids) {
        // 빈 목록으로 IN 조회를 하면 DB 에 따라 문법 오류가 나므로 조회하지 않는다.
        if (ids.isEmpty()) {
            return List.of();
        }
        return productJpaRepository.findAllActiveByIdsForUpdate(ids);
    }

    @Override
    public List<Product> findAllActiveByBrandIdForUpdate(Long brandId) {
        return productJpaRepository.findAllActiveByBrandIdForUpdate(brandId);
    }
}
