package com.loopers.infrastructure.product;

import com.loopers.domain.product.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductJpaRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByIdAndDeletedAtIsNull(Long id);

    Page<Product> findAllByDeletedAtIsNullOrderByIdDesc(Pageable pageable);

    boolean existsByBrandIdAndDeletedAtIsNull(Long brandId);

    // 고객 상품 목록 쿼리 3종(latest, price_asc, likes_desc).
    // - 정렬은 쿼리에 고정한다. 호출하는 쪽이 Pageable 에 Sort 를 실어도 반영하지 않으며, 동률은 모두 상품 id 내림차순이다.
    // - 삭제된 상품은 제외한다 (p.deletedAt is null).
    // - `:brandId is null or ...` 의 brandId 는 상품의 컬럼이 아니라 목록 API 의 선택 필터 인자다.
    //   null 이면 브랜드를 가리지 않고 전체를, 값이 있으면 그 브랜드의 상품만 조회한다. (상품의 brand_id 는 null 일 수 없다)
    @Query(
        value = "select p from Product p where p.deletedAt is null and (:brandId is null or p.brandId = :brandId) "
            + "order by p.id desc",
        countQuery = "select count(p) from Product p where p.deletedAt is null and (:brandId is null or p.brandId = :brandId)"
    )
    Page<Product> findAllActiveOrderByLatest(@Param("brandId") Long brandId, Pageable pageable);

    @Query(
        value = "select p from Product p where p.deletedAt is null and (:brandId is null or p.brandId = :brandId) "
            + "order by p.price.amount asc, p.id desc",
        countQuery = "select count(p) from Product p where p.deletedAt is null and (:brandId is null or p.brandId = :brandId)"
    )
    Page<Product> findAllActiveOrderByPriceAsc(@Param("brandId") Long brandId, Pageable pageable);

    // likes_desc: 상품에 Like 를 left join 해 상품별 좋아요 행 수로 정렬한다(좋아요가 없는 상품은 0이라 포함된다).
    // 총 개수 쿼리는 join 없이 상품만 센다. join 하면 좋아요 행 수만큼 부풀기 때문이다.
    @Query(
        value = "select p from Product p left join Like l on l.id.productId = p.id "
            + "where p.deletedAt is null and (:brandId is null or p.brandId = :brandId) "
            + "group by p order by count(l) desc, p.id desc",
        countQuery = "select count(p) from Product p where p.deletedAt is null and (:brandId is null or p.brandId = :brandId)"
    )
    Page<Product> findAllActiveOrderByLikesDesc(@Param("brandId") Long brandId, Pageable pageable);

    // 좋아요와 상품을 id 로 join 해 삭제된 상품을 제외한 채 한 번에 페이징한다. 연관관계 없이 명시적 on 절을 쓴다.
    @Query(
        value = "select p from Product p join Like l on l.id.productId = p.id "
            + "where l.id.userId = :userId and p.deletedAt is null "
            + "order by l.likedAt desc, p.id desc",
        countQuery = "select count(p) from Product p join Like l on l.id.productId = p.id "
            + "where l.id.userId = :userId and p.deletedAt is null"
    )
    Page<Product> findAllActiveLikedBy(@Param("userId") Long userId, Pageable pageable);
}
