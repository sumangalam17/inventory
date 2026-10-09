package com.bhagwaniHardware.inventory.dao;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bhagwaniHardware.inventory.model.ItemCostHistory;

public interface ItemCostHistoryDao extends JpaRepository<ItemCostHistory, String> {
    List<ItemCostHistory> findTop5ByItemIdOrderByRecordedAtDesc(String itemId);
    Optional<ItemCostHistory> findFirstByItemIdOrderByRecordedAtDesc(String itemId);

    @Modifying
    @Query("DELETE FROM ItemCostHistory history WHERE history.itemId = :itemId AND history.id NOT IN :keepIds")
    void deleteOlderThanLatestFive(@Param("itemId") String itemId, @Param("keepIds") List<String> keepIds);
}
