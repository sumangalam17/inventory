package com.bhagwaniHardware.inventory.model;

import java.util.List;

import com.bhagwaniHardware.inventory.dto.BillType;

import java.time.LocalDateTime;


import jakarta.persistence.Entity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.PrePersist;
import lombok.Data;

@Data
@Entity
public class Bill {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String BillNo;
    private String CustomerName;
    private String MobileNumber;
    private String address;
    @OneToMany(cascade = CascadeType.ALL)
    private List<SoldItem> items;
    private double total;
    private double discount;
    private LocalDateTime createdAt;
    private BillType billType;

    @PrePersist
    private void setCreatedAt() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }
    
}
