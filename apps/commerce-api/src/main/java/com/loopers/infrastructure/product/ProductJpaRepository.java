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
