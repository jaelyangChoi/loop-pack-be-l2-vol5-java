package com.loopers.domain.brand;

import com.loopers.domain.BaseEntity;
import com.loopers.support.error.CoreException;
import com.loopers.support.error.ErrorType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;

/**
 * 상품이 속하는 브랜드. 이름만 가진다.
 *
 * <p>이름은 앞뒤 공백을 제거한 뒤 1~50자여야 한다. 검증이 실패하면 예외가 먼저 발생하므로 기존 이름이 유지된다.
 */
@Getter
@Entity
@Table(name = "brands")
public class Brand extends BaseEntity {

    private static final int NAME_MAX_LENGTH = 50;

    @Column(name = "name", nullable = false, length = NAME_MAX_LENGTH)
    private String name;

    // JPA 가 사용하는 기본 생성자. 새 브랜드는 create() 로 만든다.
    protected Brand() {}

    public static Brand create(String name) {
        Brand brand = new Brand();
        brand.name = validatedName(name);
        return brand;
    }

    public void update(String name) {
        this.name = validatedName(name);
    }

    private static String validatedName(String name) {
        if (name == null || name.isBlank()) {
            throw new CoreException(ErrorType.INVALID_INPUT, "브랜드 이름은 비어 있을 수 없습니다.");
        }
        String trimmed = name.strip();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new CoreException(ErrorType.INVALID_INPUT, "브랜드 이름은 " + NAME_MAX_LENGTH + "자 이하여야 합니다.");
        }
        return trimmed;
    }
}
