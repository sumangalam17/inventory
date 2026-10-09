package com.bhagwaniHardware.inventory.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Data;

@Data 
@Entity 
public class SoldItem {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String inventoryItemId;
    private String name;
    private String brand;
    private String size;
    private int count;
    private double sellingPrice;
}
