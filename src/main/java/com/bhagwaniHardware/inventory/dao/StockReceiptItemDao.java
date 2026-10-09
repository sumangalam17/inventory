package com.bhagwaniHardware.inventory.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bhagwaniHardware.inventory.model.StockReceiptItem;

public interface StockReceiptItemDao extends JpaRepository<StockReceiptItem, String> {
    List<StockReceiptItem> findAllByOrderByNameAsc();
    Optional<StockReceiptItem> findByInventoryItemId(String inventoryItemId);
    Optional<StockReceiptItem> findByInventoryItemIdIsNullAndNameIgnoreCase(String name);
}
