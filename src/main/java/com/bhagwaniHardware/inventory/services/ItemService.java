package com.bhagwaniHardware.inventory.services;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bhagwaniHardware.inventory.dao.ItemDao;
import com.bhagwaniHardware.inventory.model.Item;

import jakarta.transaction.Transactional;

@Service 
public class ItemService {

    private final ItemDao itemDao;
    private final ItemCostHistoryService itemCostHistoryService;

    public ItemService(ItemDao itemDao, ItemCostHistoryService itemCostHistoryService){
        this.itemDao = itemDao;
        this.itemCostHistoryService = itemCostHistoryService;
    }

    public List<Item> getAll(){
        return itemDao.findAll();
    }

    public List<Item> getLowStockItems() {
        return getAll().stream()
                .filter(item -> item.getCount() < item.getLowStock())
                .toList();
    }

    public List<Item> searchInventory(String searchTerm) {
        if (searchTerm == null || searchTerm.isBlank()) {
            return getAll();
        }
        String term = searchTerm.trim();
        return itemDao.findByNameContainingIgnoreCaseOrBrandContainingIgnoreCaseOrSizeContainingIgnoreCaseOrderByNameAsc(
                term, term, term);
    }

    public Optional<Item> findById(String itemId) {
        return itemDao.findById(itemId);
    }

    public Item editItem(String itemId, double costPrice, Double price, int count, int lowStock) {
        Item item = itemDao.findById(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Inventory item not found: " + itemId));
        if (costPrice < 0 || (price != null && price < 0) || count < 0 || lowStock < 1) {
            throw new IllegalArgumentException("Prices and quantity must be non-negative, and low-stock threshold must be at least 1.");
        }

        itemCostHistoryService.recordIfChanged(itemId, item.getCostPrice());
        item.setCostPrice(costPrice);
        item.setPrice(price == null ? (costPrice * 130 / 100) : price);
        item.setCount(count);
        item.setLowStock(lowStock);
        Item savedItem = itemDao.save(item);
        itemCostHistoryService.recordIfChanged(itemId, savedItem.getCostPrice());
        return savedItem;
    }

    @Transactional
    public void editItem(String itemId, String name, String brand, String size, double costPrice, Double sellingPrice,
            int count, int lowStock) {

        itemDao.deleteById(itemId);

        Item item = new Item();
        item.setName(name);
        item.setBrand(brand);
        item.setSize(size);
        item.setItemId(brand + " " + name + " " + size);
        item.setCostPrice(costPrice);
        item.setPrice(sellingPrice);
        item.setCount(count);
        item.setLowStock(lowStock);
        
        itemDao.save(item);
        itemCostHistoryService.recordIfChanged(item.getItemId(), item.getCostPrice());

    }
    
    public List<Item> addStock(List<Item> item){
        for(int i=0;i<item.size();i++){
            Item product = item.get(i);
            String itemId = product.getBrand() + " " + product.getName() + " " + product.getSize();
            
            product.setItemId(itemId);
            Optional<Item> currItem = itemDao.findById(itemId);
            if(currItem.isPresent()){
                product.setCount(product.getCount() + currItem.get().getCount());
            }
            itemDao.findById(product.getItemId())
                    .ifPresent(existing -> updateStock(Arrays.asList(existing)));
            if (product.getPrice() <= 0 && product.getCostPrice() > 0) {
                product.setPrice(Math.round(product.getCostPrice() * 1.3 * 100) / 100.0);
            }
            if (product.getLowStock() < 1) {
                product.setLowStock(5);
            }
        }
        List<Item> savedItems = itemDao.saveAll(item);
        savedItems.forEach(product -> itemCostHistoryService.recordIfChanged(product.getItemId(), product.getCostPrice()));
        return savedItems;
    }

    @Transactional
    public List<Item> updateStock(List<Item> item){
        for(int i=0;i<item.size();i++){
            Item curr = item.get(i);
            Optional<Item> temp = itemDao.findById(curr.getItemId());
            if(temp.isPresent())curr.setCount(curr.getCount() + temp.get().getCount());
            item.set(i, curr);
            itemDao.save(curr);
            itemCostHistoryService.recordIfChanged(curr.getItemId(), curr.getCostPrice());
        }
        return item;   
    }

    public String returnItem(List<Item> item){
        for(int i=0;i<item.size();i++){
            Item curr = item.get(i);
            Optional<Item> temp = itemDao.findById(curr.getItemId());
            if(temp.isPresent()){
                Item existingItem = temp.get();
                existingItem.setCount(existingItem.getCount() + curr.getCount());
                itemDao.save(existingItem);
            }
        }
        return "return successfull";
    }

    public String sellItem(List<Item> item){
        for (Item soldItem : item) {
            Optional<Item> inventoryItem = itemDao.findById(soldItem.getItemId());
            if (inventoryItem.isPresent()) {
                Item existingItem = inventoryItem.get();
                existingItem.setCount(existingItem.getCount() - soldItem.getCount());
                itemDao.save(existingItem);
            }
        }
        return "sell successfull";
    }

    public List<Item> findProductByName(String name){
        if(name.length()<3) return new ArrayList<>();
        return itemDao.findTop8ByNameContainingIgnoreCaseOrderByNameAsc(name);
    }

    public String deleteProductbyId(String id){
        itemDao.deleteById(id);
        return id + " deleted successfully";
    }
}