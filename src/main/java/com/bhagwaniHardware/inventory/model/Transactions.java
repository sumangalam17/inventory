package com.bhagwaniHardware.inventory.model;

import java.util.Date;

import com.bhagwaniHardware.inventory.dto.TransactionType;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import lombok.Data;

@Entity 
@Data 
public class Transactions {
    
    @Id 
    @GeneratedValue (strategy = GenerationType.UUID)
    private String transactionId;
    private TransactionType transactionType;
    private double amount;
    private String customerID;
    private String billNo;
    private Date creatDate;

    @PrePersist
    protected void onCreate() {
        if (this.creatDate == null) {
            this.creatDate = new Date();
        }
    }

}
