package com.bhagwaniHardware.inventory.services;

import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bhagwaniHardware.inventory.dao.ItemCostHistoryDao;
import com.bhagwaniHardware.inventory.model.Item;
import com.bhagwaniHardware.inventory.model.ItemCostHistory;

@Service
public class ItemCostHistoryService {
    private final ItemCostHistoryDao itemCostHistoryDao;

    public ItemCostHistoryService(ItemCostHistoryDao itemCostHistoryDao) {
        this.itemCostHistoryDao = itemCostHistoryDao;
    }

    @Transactional
    public void recordIfChanged(String itemId, double costPrice) {
        boolean unchanged = itemCostHistoryDao.findFirstByItemIdOrderByRecordedAtDesc(itemId)
                .map(latest -> Double.compare(latest.getCostPrice(), costPrice) == 0)
                .orElse(false);
        if (!unchanged) {
            ItemCostHistory history = new ItemCostHistory();
            history.setItemId(itemId);
            history.setCostPrice(costPrice);
            itemCostHistoryDao.save(history);
            List<String> latestIds = itemCostHistoryDao.findTop5ByItemIdOrderByRecordedAtDesc(itemId).stream()
                    .map(ItemCostHistory::getId)
                    .toList();
            itemCostHistoryDao.deleteOlderThanLatestFive(itemId, latestIds);
        }
    }

    @Transactional
    public Map<String, List<Double>> getLatestCosts(List<Item> items) {
        return items.stream().collect(java.util.stream.Collectors.toMap(
                Item::getItemId,
                item -> {
                    recordIfChanged(item.getItemId(), item.getCostPrice());
                    return itemCostHistoryDao.findTop5ByItemIdOrderByRecordedAtDesc(item.getItemId()).stream()
                            .map(ItemCostHistory::getCostPrice)
                            .toList();
                }));
    }
}
