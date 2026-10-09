package com.bhagwaniHardware.inventory.controller;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import com.bhagwaniHardware.inventory.model.Item;
import com.bhagwaniHardware.inventory.services.BillService;
import com.bhagwaniHardware.inventory.services.CustomerService;
import com.bhagwaniHardware.inventory.services.ItemCostHistoryService;
import com.bhagwaniHardware.inventory.services.ItemService;
import com.bhagwaniHardware.inventory.services.PurchaseListService;
import com.bhagwaniHardware.inventory.services.StockReceiptService;
import com.bhagwaniHardware.inventory.services.TransactionService;

@Controller
public class BaseController {
    final ItemService itemService;
    final BillService billService;
    final CustomerService customerService;
    final TransactionService transactionService;
    final PurchaseListService purchaseListService;
    final StockReceiptService stockReceiptService;
    final ItemCostHistoryService itemCostHistoryService;

    BaseController(ItemService itemService, BillService billService, CustomerService customerService,
                   TransactionService transactionService, PurchaseListService purchaseListService,
                   StockReceiptService stockReceiptService, ItemCostHistoryService itemCostHistoryService) {
        this.itemService = itemService;
        this.billService = billService;
        this.customerService = customerService;
        this.transactionService = transactionService;
        this.purchaseListService = purchaseListService;
        this.stockReceiptService = stockReceiptService;
        this.itemCostHistoryService = itemCostHistoryService;
    }

    @GetMapping({"/", "/dashboard"})
    public String dashboard(Model model) {
        LocalDate today = LocalDate.now();
        LocalDate monthStart = today.withDayOfMonth(1);
        List<Item> items = itemService.getAll();

        model.addAttribute("todaySales", billService.getSalesBetween(today, today));
        model.addAttribute("monthSales", billService.getSalesBetween(monthStart, today));
        model.addAttribute("totalSales", billService.totalSale());
        model.addAttribute("monthBillCount", billService.getBillCountBetweenDates(monthStart, today));
        model.addAttribute("latestBills", billService.getLatestBills());
        model.addAttribute("monthlySales", billService.getLastYearMonthlySales());
        model.addAttribute("skuCount", items.size());
        model.addAttribute("lowStockCount", itemService.getLowStockItems().size());
        return "dashboard";
    }

    @GetMapping("/inventory")
    public String getAll(@RequestParam(required = false) String q, Model model){
        List<Item> items = itemService.searchInventory(q);
        model.addAttribute("items", items);
        model.addAttribute("costHistoryByItemId", itemCostHistoryService.getLatestCosts(items));
        model.addAttribute("purchaseItemIds", purchaseListService.getInventoryItemIds());
        model.addAttribute("searchAction", "/inventory");
        model.addAttribute("searchTerm", q);
        model.addAttribute("searchPerformed", q != null && !q.isBlank());
        return "/home";
    }

    @GetMapping("/low-stock")
    public String lowStock(Model model) {
        List<Item> items = itemService.getLowStockItems();
        model.addAttribute("items", items);
        model.addAttribute("costHistoryByItemId", itemCostHistoryService.getLatestCosts(items));
        model.addAttribute("purchaseItemIds", purchaseListService.getInventoryItemIds());
        return "lowStock";
    }

    @GetMapping("/purchase-list")
    public String purchaseList(Model model) {
        model.addAttribute("items", purchaseListService.getItems());
        return "purchaseList";
    }

    @GetMapping("/add-to-stock")
    public String addToStockPage(Model model) {
        model.addAttribute("items", stockReceiptService.getItems());
        return "addToStock";
    }

    @PostMapping("/add-to-stock/add")
    public String addToStockList(@RequestParam String id, RedirectAttributes redirectAttributes) {
        try {
            stockReceiptService.addFromPurchaseList(id);
            redirectAttributes.addFlashAttribute("successMessage", "Added to the stock receipt list.");
            return "redirect:/purchase-list";
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/add-to-stock/update")
    public String updateStockReceiptList(@RequestParam(required = false) List<String> itemIds,
                                         @RequestParam(required = false) List<Integer> quantities,
                                         @RequestParam(required = false) List<Double> costPrices,
                                         RedirectAttributes redirectAttributes) {
        try {
            stockReceiptService.updateItems(itemIds, quantities, costPrices);
            redirectAttributes.addFlashAttribute("successMessage", "Stock receipt list updated.");
            return "redirect:/add-to-stock";
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/add-to-stock/remove")
    public String removeStockReceiptItem(@RequestParam String id) {
        try {
            stockReceiptService.removeItem(id);
            return "redirect:/add-to-stock";
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @PostMapping("/add-to-stock/receive")
    public String receiveStock(@RequestParam(required = false) List<String> itemIds,
                               @RequestParam(required = false) List<Integer> quantities,
                               @RequestParam(required = false) List<Double> costPrices,
                               RedirectAttributes redirectAttributes) {
        try {
            stockReceiptService.receiveAll(itemIds, quantities, costPrices);
            redirectAttributes.addFlashAttribute("successMessage", "Stock received and the stock receipt list was cleared.");
            return "redirect:/inventory";
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @PostMapping("/purchase-list/add")
    @ResponseBody
    public void addInventoryItemToPurchaseList(@RequestParam String itemId) {
        purchaseListService.addInventoryItem(itemId);
    }

    @PostMapping("/purchase-list/remove")
    @ResponseBody
    public void removeInventoryItemFromPurchaseList(@RequestParam String itemId) {
        purchaseListService.removeInventoryItem(itemId);
    }

    @PostMapping("/purchase-list/add-custom")
    public String addCustomItemToPurchaseList(@RequestParam String name,
                                              @RequestParam(defaultValue = "1") int quantity) {
        purchaseListService.addCustomItem(name, quantity);
        return "redirect:/purchase-list";
    }

    @PostMapping("/purchase-list/update")
    public String updatePurchaseList(@RequestParam List<String> itemIds,
                                     @RequestParam List<Integer> quantities) {
        purchaseListService.updateQuantities(itemIds, quantities);
        return "redirect:/purchase-list";
    }

    @PostMapping("/purchase-list/remove-item")
    public String removePurchaseListItem(@RequestParam String id) {
        purchaseListService.removeItem(id);
        return "redirect:/purchase-list";
    }

    @PostMapping("/purchase-list/clear")
    public String clearPurchaseList() {
        purchaseListService.clear();
        return "redirect:/purchase-list";
    }

    @GetMapping("/inventory/edit")
    public String editItemForm(@RequestParam String itemId, Model model) {
        Item item = itemService.findById(itemId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inventory item not found"));
        model.addAttribute("item", item);
        return "editItem";
    }

    @PostMapping("/inventory/edit")
    public String editItem(@RequestParam String itemId,
                           @RequestParam double costPrice,
                           @RequestParam(required = false) Double price,
                           @RequestParam int count,
                           @RequestParam int lowStock,
                           RedirectAttributes redirectAttributes) {
        try {
            itemService.editItem(itemId, costPrice, price, count, lowStock);
            redirectAttributes.addFlashAttribute("successMessage", "Inventory item updated.");
            return "redirect:/inventory";
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, exception.getMessage(), exception);
        }
    }

    @GetMapping("/add")
    public String addStock(){
        return "/addStock";
    }

    @GetMapping("/customers")
    public String getCustomers(Model model) {
        model.addAttribute("customers", customerService.getAll());
        return "customer";
    }

    

    @PostMapping("/add")
    @ResponseBody
    public List<Item> postAddStock(@RequestBody List<Item> item){
        return itemService.addStock(item);
    }

    @PostMapping("/update")
    public List<Item> postUpdateStock(@RequestBody List<Item> item){
        return itemService.updateStock(item);
    }

    @PostMapping("/return")
    public String postReturnItem(@RequestBody List<Item> item){
        return itemService.returnItem(item);
    }

    @PostMapping("/sell")
    public String postSell(@RequestBody List<Item> item){
        return itemService.sellItem(item);
    }

    @GetMapping("/getProductSuggestion")
    @ResponseBody
    public List<Item> getProductSuggestion(@RequestParam("name") String name){
        return itemService.findProductByName(name);
    }
}
