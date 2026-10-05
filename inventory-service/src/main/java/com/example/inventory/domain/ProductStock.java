package com.example.inventory.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "product_stock")
public class ProductStock {
    @Id
    @Column(name = "product_id")
    private String productId;
    @Column(nullable = false)
    private int available;

    protected ProductStock() {}

    public String getProductId() { return productId; }
    public int getAvailable() { return available; }
    public void setAvailable(int available) { this.available = available; }
}
