package com.bhagwaniHardware.inventory.services;

import java.util.List;
import java.util.Optional;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bhagwaniHardware.inventory.dao.BillDao;
import com.bhagwaniHardware.inventory.dao.CustomerDao;
import com.bhagwaniHardware.inventory.dao.ItemDao;
import com.bhagwaniHardware.inventory.dao.TransactionDao;
import com.bhagwaniHardware.inventory.model.Bill;
import com.bhagwaniHardware.inventory.model.Customer;
import com.bhagwaniHardware.inventory.model.Item;
import com.bhagwaniHardware.inventory.model.SoldItem;
import com.bhagwaniHardware.inventory.dto.BillType;
import com.bhagwaniHardware.inventory.dto.CreateBillRequest;
import com.bhagwaniHardware.inventory.dto.TransactionType;

@Service 
public class BillService {
    private final BillDao billDao;
    private final CustomerService customerService;
    private final TransactionService transactionService;
    private final ItemService itemService;
    private final CustomerDao customerDao;
    private final ItemDao itemDao;
    private final TransactionDao transactionDao;
    
    public BillService(BillDao billDao, CustomerService customerService, TransactionService transactionService,
                       ItemService itemService, CustomerDao customerDao, ItemDao itemDao,
                       TransactionDao transactionDao){
        this.billDao = billDao;
        this.customerService = customerService;
        this.transactionService = transactionService;
        this.itemService = itemService;
        this.customerDao = customerDao;
        this.itemDao = itemDao;
        this.transactionDao = transactionDao;
    }

    public List<Bill> getLatestBills(){
        return billDao.findTop10ByOrderByCreatedAtDesc();
    }

    public List<Bill> getBillsBetweenDates(LocalDate fromDate, LocalDate toDate) {
        return billDao.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay());
    }

    public List<Bill> getAllBills() {
        return billDao.findAllByOrderByCreatedAtDesc();
    }

    public List<Bill> getBillsByType(BillType billType) {
        return billDao.findByBillTypeOrderByCreatedAtDesc(billType);
    }

    public List<Bill> getBillsByTypeBetweenDates(BillType billType, LocalDate fromDate, LocalDate toDate) {
        return billDao.findByBillTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                billType, fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay());
    }

    public List<MonthlySales> getLastYearMonthlySales() {
        YearMonth currentMonth = YearMonth.now();
        YearMonth firstMonth = currentMonth.minusMonths(11);
        List<Bill> sales = billDao.findByBillTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
                BillType.sale, firstMonth.atDay(1).atStartOfDay(), currentMonth.plusMonths(1).atDay(1).atStartOfDay());
        Map<YearMonth, Double> salesByMonth = sales.stream()
                .collect(Collectors.groupingBy(
                        bill -> YearMonth.from(bill.getCreatedAt()),
                        Collectors.summingDouble(Bill::getTotal)));
        double maximumSales = 0;
        for (YearMonth month = firstMonth; !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            maximumSales = Math.max(maximumSales, salesByMonth.getOrDefault(month, 0.0));
        }

        List<MonthlySales> monthlySales = new ArrayList<>(12);
        for (YearMonth month = firstMonth; !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            double total = salesByMonth.getOrDefault(month, 0.0);
            int barHeight = maximumSales == 0 ? 0 : (int) Math.round(total / maximumSales * 100);
            monthlySales.add(new MonthlySales(
                    month.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    total,
                    barHeight));
        }
        return monthlySales;
    }

    public record MonthlySales(String month, double total, int barHeight) {}

    public double getSalesBetween(LocalDate fromDate, LocalDate toDate) {
        return billDao.getTotalSalesBetween(fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay());
    }

    public long getBillCountBetweenDates(LocalDate fromDate, LocalDate toDate) {
        return billDao.countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(
                fromDate.atStartOfDay(), toDate.plusDays(1).atStartOfDay());
    }

    public Optional<Bill> getBill(String billNo) {
        return billDao.findById(billNo);
    }

    @Transactional
    public void deleteBill(String billNo) {
        Bill bill = billDao.findById(billNo)
                .orElseThrow(() -> new IllegalArgumentException("Bill not found."));
        if (bill.getBillType() != BillType.sale && bill.getBillType() != BillType.returnItem) {
            throw new IllegalStateException("Cannot reverse a bill with an unknown type.");
        }

        boolean saleBill = bill.getBillType() == BillType.sale;
        bill.getItems().forEach(soldItem -> {
            if (soldItem.getInventoryItemId() == null || soldItem.getInventoryItemId().isBlank()) {
                return;
            }
            Item inventoryItem = itemDao.findById(soldItem.getInventoryItemId())
                    .orElseThrow(() -> new IllegalStateException(
                            "Inventory item for bill line not found: " + soldItem.getInventoryItemId()));
            int quantityChange = saleBill ? soldItem.getCount() : -soldItem.getCount();
            inventoryItem.setCount(inventoryItem.getCount() + quantityChange);
            itemDao.save(inventoryItem);
        });

        Customer customer = customerDao.findById(bill.getMobileNumber())
                .orElseThrow(() -> new IllegalStateException("Customer associated with bill not found."));
        double balanceChange = saleBill ? -bill.getTotal() : bill.getTotal();
        customer.setTotal(customer.getTotal() + balanceChange);
        customer.setAmountPending(customer.getAmountPending() + balanceChange);
        customerDao.save(customer);

        transactionDao.deleteByBillNo(billNo);
        billDao.delete(bill);
    }

    @Transactional
    public void createBill(CreateBillRequest request){
        if (request.getBillType() == BillType.returnItem) {
            returnItem(request);
            return;
        }
        if (request.getDiscount() < 0 || request.getDiscount() > request.getTotalAmount()) {
            throw new IllegalArgumentException("Discount must be between zero and the subtotal");
        }

        Bill bill = new Bill();
        bill.setCustomerName(request.getCustomerName());
        bill.setMobileNumber(request.getMobileNumber());
        bill.setBillType(BillType.sale);
        bill.setAddress(request.getAddress());
        bill.setDiscount(request.getDiscount());
        bill.setTotal(request.getTotalAmount() - request.getDiscount());
        bill.setItems(request.getItems().stream().map(requestItem -> {
            SoldItem item = new SoldItem();
            item.setInventoryItemId(requestItem.getItemId());
            item.setName(requestItem.getName());
            item.setBrand(requestItem.getBrand());
            item.setSize(requestItem.getSize());
            item.setCount(requestItem.getCount());
            item.setSellingPrice(requestItem.getPrice());
            return item;
        }).toList());

        List<Item> inventoryItems = toInventoryItems(request);
        if (!inventoryItems.isEmpty()) {
            itemService.sellItem(inventoryItems);
        }
        Bill savedBill = billDao.save(bill);

        Customer customer = customerService.getCustomer(request.getMobileNumber())
                .orElseGet(() -> customerService.createCustomer(
                        request.getCustomerName(),
                        request.getMobileNumber(),
                        request.getAddress()));

        transactionService.createTransactions(
                customer.getMobileNo(),
                savedBill.getBillNo(),
                savedBill.getTotal(),
                TransactionType.sale);
        customer.setTotal(customer.getTotal() + savedBill.getTotal());
        customer.setAmountPending(customer.getAmountPending() + savedBill.getTotal());
        customerService.saveCustomer(customer);
    }

    @Transactional
    public void returnItem(CreateBillRequest request){
        Bill bill = new Bill();
        bill.setCustomerName(request.getCustomerName());
        bill.setMobileNumber(request.getMobileNumber());
        bill.setAddress(request.getAddress());
        bill.setTotal(request.getTotalAmount());
        bill.setBillType(BillType.returnItem);
        bill.setItems(request.getItems().stream().map(requestItem -> {
            SoldItem item = new SoldItem();
            item.setInventoryItemId(requestItem.getItemId());
            item.setName(requestItem.getName());
            item.setBrand(requestItem.getBrand());
            item.setSize(requestItem.getSize());
            item.setCount(requestItem.getCount());
            item.setSellingPrice(requestItem.getPrice());
            return item;
        }).toList());

        Bill savedBill = billDao.save(bill);

        Customer customer = customerService.getCustomer(request.getMobileNumber())
                .orElseGet(() -> customerService.createCustomer(
                        request.getCustomerName(),
                        request.getMobileNumber(),
                        request.getAddress()));

        transactionService.createTransactions(
                customer.getMobileNo(),
                savedBill.getBillNo(),
                savedBill.getTotal(),
                TransactionType.itemReturn);
        List<Item> inventoryItems = toInventoryItems(request);
        if (!inventoryItems.isEmpty()) {
            itemService.returnItem(inventoryItems);
        }
        customer.setTotal(customer.getTotal() - savedBill.getTotal());
        customer.setAmountPending(customer.getAmountPending() - savedBill.getTotal());
        customerService.saveCustomer(customer);
    }

    private List<Item> toInventoryItems(CreateBillRequest request) {
        return request.getItems().stream()
                .filter(requestItem -> requestItem.getItemId() != null && !requestItem.getItemId().isBlank())
                .map(requestItem -> {
                    Item item = new Item();
                    item.setItemId(requestItem.getItemId());
                    item.setCount(requestItem.getCount());
                    return item;
                })
                .toList();
    }

    public double totalSale(){
        return billDao.getTotalSales();
    }

    public List<Bill> getReturnBill(){
        return getBillsByType(BillType.returnItem);
    }

}
