package com.bhagwaniHardware.inventory.model;

import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import lombok.Data;

@Data 
@Entity 
public class Customer {
    @Id
    private String mobileNo;
    private String name;
    private String address;
    @OneToMany(cascade = CascadeType.ALL)
    private List<Transactions> transactions;
    private double amountPaid;
    private double amountPending;
    private double total;
}
