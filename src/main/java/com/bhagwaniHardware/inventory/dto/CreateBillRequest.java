package com.bhagwaniHardware.inventory.dto;

import java.util.List;

import com.bhagwaniHardware.inventory.dto.BillType;

import lombok.Data;

@Data
public class CreateBillRequest {
    private String customerName;
    private String mobileNumber;
    private String address;
    private BillType billType;
    private double discount;
    private List<SaleItem> items;
    private double totalAmount;

    @Data
    public static class SaleItem {
        private String itemId;
        private String name;
        private String brand;
        private String size;
        private int count;
        private double price;
    }
}