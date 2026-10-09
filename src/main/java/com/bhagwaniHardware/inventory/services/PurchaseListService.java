package com.bhagwaniHardware.inventory.services;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bhagwaniHardware.inventory.dao.ItemDao;
import com.bhagwaniHardware.inventory.dao.PurchaseListItemDao;
import com.bhagwaniHardware.inventory.model.Item;
import com.bhagwaniHardware.inventory.model.PurchaseListItem;

@Service
public class PurchaseListService {
    private final PurchaseListItemDao purchaseListItemDao;
    private final ItemDao itemDao;

    public PurchaseListService(PurchaseListItemDao purchaseListItemDao, ItemDao itemDao) {
        this.purchaseListItemDao = purchaseListItemDao;
        this.itemDao = itemDao;
    }

    public List<PurchaseListItem> getItems() {
        return purchaseListItemDao.findAllByOrderByNameAsc();
    }

    public Set<String> getInventoryItemIds() {
        return getItems().stream()
                .map(PurchaseListItem::getInventoryItemId)
                .filter(itemId -> itemId != null)
                .collect(Collectors.toSet());
    }

    @Transactional
    public void addInventoryItem(String inventoryItemId) {
        Item item = itemDao.findById(inventoryItemId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found."));
        PurchaseListItem purchaseItem = purchaseListItemDao.findByInventoryItemId(inventoryItemId)
                .orElseGet(() -> {
                    PurchaseListItem newItem = new PurchaseListItem();
                    newItem.setInventoryItemId(item.getItemId());
                    newItem.setName(item.getName());
                    newItem.setBrand(item.getBrand());
                    newItem.setSize(item.getSize());
                    newItem.setQuantity(0);
                    return newItem;
                });
        purchaseItem.setQuantity(purchaseItem.getQuantity() + 1);
        purchaseListItemDao.save(purchaseItem);
    }

    @Transactional
    public void removeInventoryItem(String inventoryItemId) {
        purchaseListItemDao.findByInventoryItemId(inventoryItemId)
                .ifPresent(purchaseListItemDao::delete);
    }

    @Transactional
    public void removeItem(String id) {
        if (!purchaseListItemDao.existsById(id)) {
            throw new IllegalArgumentException("Purchase list item not found.");
        }
        purchaseListItemDao.deleteById(id);
    }

    @Transactional
    public void clear() {
        purchaseListItemDao.deleteAllInBatch();
    }

    @Transactional
    public void addCustomItem(String name, int quantity) {
        String normalizedName = name == null ? "" : name.trim();
        if (normalizedName.isEmpty() || quantity < 1) {
            throw new IllegalArgumentException("Enter an item name and a quantity of at least 1.");
        }

        String comparisonName = normalizedName.toLowerCase(Locale.ROOT);
        PurchaseListItem purchaseItem = getItems().stream()
                .filter(item -> item.getInventoryItemId() == null)
                .filter(item -> item.getName().toLowerCase(Locale.ROOT).equals(comparisonName))
                .findFirst()
                .orElseGet(() -> {
                    PurchaseListItem newItem = new PurchaseListItem();
                    newItem.setName(normalizedName);
                    newItem.setQuantity(0);
                    return newItem;
                });
        purchaseItem.setQuantity(purchaseItem.getQuantity() + quantity);
        purchaseListItemDao.save(purchaseItem);
    }

    @Transactional
    public void updateQuantities(List<String> ids, List<Integer> quantities) {
        if (ids == null || quantities == null || ids.size() != quantities.size()) {
            throw new IllegalArgumentException("The purchase list quantities are invalid.");
        }

        List<PurchaseListItem> items = ids.stream()
                .map(id -> purchaseListItemDao.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Purchase list item not found.")))
                .toList();
        for (int index = 0; index < items.size(); index++) {
            int quantity = quantities.get(index);
            if (quantity < 1) {
                throw new IllegalArgumentException("Quantity must be at least 1.");
            }
            items.get(index).setQuantity(quantity);
        }
        purchaseListItemDao.saveAll(items);
    }
}
