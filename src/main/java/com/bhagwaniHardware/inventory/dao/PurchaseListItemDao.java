package com.bhagwaniHardware.inventory.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bhagwaniHardware.inventory.model.PurchaseListItem;

public interface PurchaseListItemDao extends JpaRepository<PurchaseListItem, String> {
    List<PurchaseListItem> findAllByOrderByNameAsc();
    Optional<PurchaseListItem> findByInventoryItemId(String inventoryItemId);
}
