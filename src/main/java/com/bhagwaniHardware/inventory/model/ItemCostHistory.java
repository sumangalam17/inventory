package com.bhagwaniHardware.inventory.model;

import java.time.LocalDateTime;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import lombok.Data;

@Data
@Entity
public class ItemCostHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;
    private String itemId;
    private double costPrice;
    private LocalDateTime recordedAt;

    @PrePersist
    private void setRecordedAt() {
        if (recordedAt == null) {
            recordedAt = LocalDateTime.now();
        }
    }
}
