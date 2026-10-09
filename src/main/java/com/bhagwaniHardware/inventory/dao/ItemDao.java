package com.bhagwaniHardware.inventory.dao;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bhagwaniHardware.inventory.model.Item;

public interface ItemDao extends JpaRepository<Item,String>{
    List<Item> findTop8ByNameContainingIgnoreCaseOrderByNameAsc(String name);
    List<Item> findByNameContainingIgnoreCaseOrBrandContainingIgnoreCaseOrSizeContainingIgnoreCaseOrderByNameAsc(
            String name, String brand, String size);
    
}
