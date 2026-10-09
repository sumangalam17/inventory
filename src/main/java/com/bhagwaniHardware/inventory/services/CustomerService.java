package com.bhagwaniHardware.inventory.services;

import com.bhagwaniHardware.inventory.dao.TransactionDao;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.bhagwaniHardware.inventory.dao.CustomerDao;
import com.bhagwaniHardware.inventory.dto.TransactionType;
import com.bhagwaniHardware.inventory.model.Customer;
import com.bhagwaniHardware.inventory.model.Transactions;

import jakarta.transaction.Transactional;

@Service 
public class CustomerService {

    private final TransactionDao transactionDao;
    private final TransactionService transactionService;
    private final CustomerDao customerDao;

    public CustomerService(CustomerDao customerDao, TransactionService transactionService, TransactionDao transactionDao){
        this.customerDao = customerDao;
        this.transactionService = transactionService;
        this.transactionDao = transactionDao;
    }

    public List<Customer> getAll(){
        return customerDao.findAll();
    }

    public void saveCustomer(Customer customer){
        customerDao.save(customer);
    }

    @Transactional 
    public void payAmount(String customerId, double amount, TransactionType transactionType){
        Customer customer = customerDao.findById(customerId).get();
        transactionService.createTransactions(customerId, amount, transactionType);
        if(transactionType == TransactionType.recieved){
            customer.setAmountPaid(customer.getAmountPaid() + amount);
            customer.setAmountPending(customer.getAmountPending() - amount);
        }else if(transactionType == TransactionType.refund){
            customer.setAmountPaid(customer.getAmountPaid() - amount);
            customer.setAmountPending(customer.getAmountPending() + amount);
        }
        customerDao.save(customer);
    }

    @Transactional
    public void deleteStandalonePaymentTransaction(String customerId, String transactionId) {
        Customer customer = customerDao.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found"));
        Transactions transaction = transactionDao.findById(transactionId)
                .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));

        if (!customerId.equals(transaction.getCustomerID())
                || transaction.getBillNo() != null
                || (transaction.getTransactionType() != TransactionType.recieved
                    && transaction.getTransactionType() != TransactionType.refund)) {
            throw new IllegalArgumentException("Only standalone payment or refund transactions can be deleted");
        }

        if (transaction.getTransactionType() == TransactionType.recieved) {
            customer.setAmountPaid(customer.getAmountPaid() - transaction.getAmount());
            customer.setAmountPending(customer.getAmountPending() + transaction.getAmount());
        } else {
            customer.setAmountPaid(customer.getAmountPaid() + transaction.getAmount());
            customer.setAmountPending(customer.getAmountPending() - transaction.getAmount());
        }

        customerDao.save(customer);
        transactionDao.delete(transaction);
    }

    public Optional<Customer> getCustomer(String id){
        return customerDao.findById(id);
    }

    public boolean isCustomerExist(String id){
        return customerDao.findById(id).isPresent();
    }

    public Customer createCustomer(String name, String mobileNo, String address){
        Customer customer = new Customer();
        customer.setAddress(address);
        customer.setMobileNo(mobileNo);
        customer.setName(name);
        return customerDao.save(customer);
    }
}
