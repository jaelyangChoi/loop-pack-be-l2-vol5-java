package com.loopers.application.product;

import com.loopers.domain.brand.Brand;
import com.loopers.domain.brand.BrandService;
import com.loopers.domain.like.LikeService;
import com.loopers.domain.product.Product;
import com.loopers.domain.product.ProductService;
import com.loopers.domain.product.ProductSort;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import com.loopers.support.paging.PageRequests;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * 고객용 상품 조회 유스케이스. 상품에 브랜드 정보와 좋아요 수를 조합한다.
 * 요청자에 따라 결과가 달라지지 않는 카탈로그 조회라 요청자를 식별하지 않으며, 형식 오류는 조회보다 먼저 거절한다.
 */
@RequiredArgsConstructor
@Component
public class ProductFacade {

    private final ProductService productService;
    private final BrandService brandService;
    private final LikeService likeService;
    private final ProductListCache productListCache;

    // likes_desc 는 좋아요마다 순서가 바뀌어 캐시하지 않는다(D-37).
    private static final Set<ProductSort> CACHED_SORTS = EnumSet.of(ProductSort.LATEST, ProductSort.PRICE_ASC);

    @Transactional(readOnly = true)
    public ProductDetailInfo get(Long productId) {
        Product product = productService.get(productId);
        Map<Long, Brand> brands = brandService.getAllByIds(List.of(product.getBrandId()));
        return ProductDetailInfo.of(product, brandOf(product, brands), likeService.count(productId));
    }

    /**
     * 상품 목록. {@code latest}·{@code price_asc} 는 캐시를 먼저 보고(D-37), 없으면 DB 에서 읽어 캐시에 저장한다.
     * 캐시 오류는 {@link ProductListCache} 가 miss 로 돌려주므로 여기서는 구분하지 않는다.
     *
     * @param brandId 선택 필터. {@code null} 이면 전체, 존재하지 않는 브랜드이면 빈 목록이다.
     * @param sort    {@code latest}, {@code price_asc}, {@code likes_desc}
     */
    @Transactional(readOnly = true)
    public Page<ProductListItemInfo> getList(Long brandId, String sort, int page, int size) {
        ProductSort productSort = ProductSort.from(sort);
        PageRequest pageRequest = PageRequests.of(page, size);
        if (!CACHED_SORTS.contains(productSort)) {
            return load(brandId, productSort, pageRequest);
        }

        ProductListQuery query = new ProductListQuery(brandId, productSort, page, size);
        Optional<Page<ProductListItemInfo>> cached = productListCache.find(query)
            .flatMap(snapshot -> withLikeCounts(snapshot, pageRequest));
        if (cached.isPresent()) {
            return cached.get();
        }
        Page<ProductListItemInfo> loaded = load(brandId, productSort, pageRequest);
        productListCache.save(query, ProductListSnapshot.from(loaded));
        return loaded;
    }

    // DB 경로. 브랜드와 좋아요 수는 목록의 id 를 모아 한 번에 조회해 항목마다 조회하지 않는다.
    private Page<ProductListItemInfo> load(Long brandId, ProductSort sort, PageRequest pageRequest) {
        Page<Product> products = productService.search(brandId, sort, pageRequest);
        Map<Long, Brand> brands = brandService.getAllByIds(
            products.stream().map(Product::getBrandId).distinct().toList());
        Map<Long, Long> likeCounts = likeService.countByProductIds(
            products.stream().map(Product::getId).toList());

        return products.map(product ->
            ProductListItemInfo.of(product, brandOf(product, brands), likeCounts.get(product.getId())));
    }

    // 캐시 hit 경로. 좋아요 수 조회가 삭제 확인을 겸한다(D-38): 집계 행이 없는 상품(삭제됨)이 하나라도 있으면
    // 캐시 값을 쓰지 않고 DB 로 다시 읽는다. 무효화가 실패해도 삭제된 상품이 나가지 않고 페이지가 짧아지지도 않는다.
    private Optional<Page<ProductListItemInfo>> withLikeCounts(ProductListSnapshot snapshot, PageRequest pageRequest) {
        Map<Long, Long> likeCounts = likeService.countsOfExposed(
            snapshot.items().stream().map(ProductListSnapshot.Item::id).toList());
        if (likeCounts.size() != snapshot.items().size()) {
            return Optional.empty();
        }
        List<ProductListItemInfo> items = snapshot.items().stream()
            .map(item -> new ProductListItemInfo(
                item.id(), item.name(), item.price(), item.brand(), likeCounts.get(item.id())))
            .toList();
        return Optional.of(new PageImpl<>(items, pageRequest, snapshot.totalElements()));
    }

    /**
     * 상품의 브랜드를 찾는다. 상품이 있는 브랜드는 삭제할 수 없어 항상 있어야 하므로, 없으면 데이터가 깨진 것으로 보고
     * 조용히 넘기지 않고 드러낸다. 브랜드 삭제와 상품 등록이 동시에 일어나는 경쟁(D-16)에서만 생길 수 있다.
     */
    private Brand brandOf(Product product, Map<Long, Brand> brands) {
        Brand brand = brands.get(product.getBrandId());
        if (brand == null) {
            throw new CoreException(
                ErrorType.INTERNAL_ERROR,
                "상품 " + product.getId() + " 의 브랜드 " + product.getBrandId() + " 을(를) 찾을 수 없습니다."
            );
        }
        return brand;
    }
}
