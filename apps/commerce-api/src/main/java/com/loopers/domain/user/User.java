package com.loopers.domain.user;

import com.loopers.domain.BaseEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User extends BaseEntity {

    private String name;

    protected User() {}

    public User(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }
}
