package com.bhagwaniHardware.inventory.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bhagwaniHardware.inventory.dao.ItemDao;
import com.bhagwaniHardware.inventory.dao.PurchaseListItemDao;
import com.bhagwaniHardware.inventory.dao.StockReceiptItemDao;
import com.bhagwaniHardware.inventory.model.Item;
import com.bhagwaniHardware.inventory.model.PurchaseListItem;
import com.bhagwaniHardware.inventory.model.StockReceiptItem;

@Service
public class StockReceiptService {
    private final StockReceiptItemDao stockReceiptItemDao;
    private final PurchaseListItemDao purchaseListItemDao;
    private final ItemDao itemDao;
    private final ItemCostHistoryService itemCostHistoryService;

    public StockReceiptService(StockReceiptItemDao stockReceiptItemDao,
                               PurchaseListItemDao purchaseListItemDao,
                               ItemDao itemDao,
                               ItemCostHistoryService itemCostHistoryService) {
        this.stockReceiptItemDao = stockReceiptItemDao;
        this.purchaseListItemDao = purchaseListItemDao;
        this.itemDao = itemDao;
        this.itemCostHistoryService = itemCostHistoryService;
    }

    public List<StockReceiptItem> getItems() {
        return stockReceiptItemDao.findAllByOrderByNameAsc();
    }

    @Transactional
    public void removeItem(String id) {
        if (!stockReceiptItemDao.existsById(id)) {
            throw new IllegalArgumentException("Stock list item not found.");
        }
        stockReceiptItemDao.deleteById(id);
    }

    @Transactional
    public void addFromPurchaseList(String purchaseListItemId) {
        PurchaseListItem requested = purchaseListItemDao.findById(purchaseListItemId)
                .orElseThrow(() -> new IllegalArgumentException("Purchase list item not found."));
        StockReceiptItem receiptItem = findReceiptItem(requested)
                .orElseGet(() -> {
                    StockReceiptItem newItem = new StockReceiptItem();
                    newItem.setInventoryItemId(requested.getInventoryItemId());
                    newItem.setName(requested.getName());
                    newItem.setBrand(requested.getBrand() == null ? "" : requested.getBrand());
                    newItem.setSize(requested.getSize() == null ? "" : requested.getSize());
                    newItem.setQuantity(0);
                    if (requested.getInventoryItemId() != null) {
                        itemDao.findById(requested.getInventoryItemId())
                                .ifPresent(inventoryItem -> newItem.setCostPrice(inventoryItem.getCostPrice()));
                    }
                    return newItem;
                });
        receiptItem.setQuantity(receiptItem.getQuantity() + requested.getQuantity());
        stockReceiptItemDao.save(receiptItem);
    }

    private java.util.Optional<StockReceiptItem> findReceiptItem(PurchaseListItem requested) {
        if (requested.getInventoryItemId() == null) {
            return stockReceiptItemDao.findByInventoryItemIdIsNullAndNameIgnoreCase(requested.getName());
        }
        return stockReceiptItemDao.findByInventoryItemId(requested.getInventoryItemId());
    }

    @Transactional
    public void updateItems(List<String> ids, List<Integer> quantities, List<Double> costPrices) {
        if (ids == null || quantities == null || costPrices == null
                || ids.size() != quantities.size() || ids.size() != costPrices.size()) {
            throw new IllegalArgumentException("The stock receipt details are invalid.");
        }

        List<StockReceiptItem> receiptItems = ids.stream()
                .map(id -> stockReceiptItemDao.findById(id)
                        .orElseThrow(() -> new IllegalArgumentException("Stock list item not found.")))
                .toList();

        for (int index = 0; index < receiptItems.size(); index++) {
            int quantity = quantities.get(index);
            double costPrice = costPrices.get(index);
            if (quantity < 1 || !Double.isFinite(costPrice) || costPrice < 0) {
                throw new IllegalArgumentException("Quantity must be at least 1 and cost price must be non-negative.");
            }
            receiptItems.get(index).setQuantity(quantity);
            receiptItems.get(index).setCostPrice(costPrice);
        }
        stockReceiptItemDao.saveAll(receiptItems);
    }

    @Transactional
    public void receiveAll(List<String> ids, List<Integer> quantities, List<Double> costPrices) {
        updateItems(ids, quantities, costPrices);
        List<StockReceiptItem> receiptItems = stockReceiptItemDao.findAllByOrderByNameAsc();
        if (ids.size() != receiptItems.size()
                || !new java.util.HashSet<>(ids).equals(receiptItems.stream()
                        .map(StockReceiptItem::getId)
                        .collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalArgumentException("The submitted stock list changed. Reload the page and try again.");
        }
        for (StockReceiptItem receiptItem : receiptItems) {
            Item inventoryItem = receiptItem.getInventoryItemId() == null
                    ? null
                    : itemDao.findById(receiptItem.getInventoryItemId()).orElse(null);

            if (inventoryItem == null) {
                String itemId = receiptItem.getBrand() + " " + receiptItem.getName() + " " + receiptItem.getSize();
                inventoryItem = itemDao.findById(itemId).orElse(null);
            }
            if (inventoryItem == null) {
                inventoryItem = new Item();
                inventoryItem.setName(receiptItem.getName());
                inventoryItem.setBrand(receiptItem.getBrand());
                inventoryItem.setSize(receiptItem.getSize());
                inventoryItem.setItemId(receiptItem.getBrand() + " " + receiptItem.getName() + " " + receiptItem.getSize());
                inventoryItem.setCount(0);
                inventoryItem.setLowStock(5);
            }

            if (inventoryItem.getItemId() != null) {
                itemCostHistoryService.recordIfChanged(inventoryItem.getItemId(), inventoryItem.getCostPrice());
            }
            inventoryItem.setCostPrice(receiptItem.getCostPrice());
            inventoryItem.setPrice(Math.round(receiptItem.getCostPrice() * 1.3 * 100) / 100.0);
            inventoryItem.setCount(inventoryItem.getCount() + receiptItem.getQuantity());
            Item savedItem = itemDao.save(inventoryItem);
            itemCostHistoryService.recordIfChanged(savedItem.getItemId(), savedItem.getCostPrice());
        }
        stockReceiptItemDao.deleteAllInBatch();
    }
}
