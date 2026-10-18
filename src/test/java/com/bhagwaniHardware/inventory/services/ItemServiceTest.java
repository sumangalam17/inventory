package com.bhagwaniHardware.inventory.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.bhagwaniHardware.inventory.dao.ItemDao;
import com.bhagwaniHardware.inventory.model.Item;

class ItemServiceTest {

    @Test
    void editItemAssignsCompositeIdBeforeSaving() {
        ItemDao itemDao = mock(ItemDao.class);
        ItemService itemService = new ItemService(itemDao, mock(ItemCostHistoryService.class));

        itemService.editItem("old-id", "Hammer", "Acme", "10mm", 5.0, 8.0, 3, 2);

        ArgumentCaptor<Item> savedItem = ArgumentCaptor.forClass(Item.class);
        verify(itemDao).save(savedItem.capture());
        assertEquals("Acme Hammer 10mm", savedItem.getValue().getItemId());
    }
}
