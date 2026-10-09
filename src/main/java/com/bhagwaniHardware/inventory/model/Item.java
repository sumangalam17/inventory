package com.bhagwaniHardware.inventory.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.Data;

@Data
@Entity
public class Item {
    
    @Column(nullable = false)
    private String name;
    private String brand = "";
    private String size = "";
    private double costPrice = 0;
    private double price = 0;
    private int count = 0;
    @Column(nullable = false)
    private int lowStock = 5;
    @Id
    private String itemId;

    @PostLoad
    private void applyDefaultLowStockAfterLoad() {
        if (lowStock < 1) {
            lowStock = 5;
        }
    }

    @PrePersist
    @PreUpdate
    private void applyDefaultLowStockBeforeSave() {
        if (lowStock < 1) {
            lowStock = 5;
        }
    }
}
