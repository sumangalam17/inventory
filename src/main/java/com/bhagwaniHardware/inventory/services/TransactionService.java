package com.bhagwaniHardware.inventory.services;

import java.util.Date;
import java.util.List;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Service;

import com.bhagwaniHardware.inventory.dao.TransactionDao;
import com.bhagwaniHardware.inventory.dto.TransactionType;
import com.bhagwaniHardware.inventory.model.Transactions;

@Service
public class TransactionService {

    private final TransactionDao transactionDao;

    TransactionService(TransactionDao transactionDao){
        this.transactionDao = transactionDao;
    }

    public void createTransactions(String customerId, String billNo, double amount, TransactionType transactionType) {
        Transactions transactions = new Transactions();
        transactions.setAmount(amount);
        transactions.setBillNo(billNo);
        transactions.setCustomerID(customerId);
        transactions.setTransactionType(transactionType);
        transactions.setCreatDate(new Date());
        transactionDao.save(transactions);
    }

    public void createTransactions(String customerId, double amount, TransactionType transactionType){
        Transactions trans = new Transactions();
        trans.setCustomerID(customerId);
        trans.setAmount(amount);
        trans.setTransactionType(transactionType);
        transactionDao.save(trans);
    }

    public List<Transactions> getTransactions(String customerId){
        return transactionDao.findByCustomerIDOrderByCreatDateDesc(customerId);
    }

    public List<Transactions> getTransactions(String customerId, LocalDate fromDate, LocalDate toDate){
        ZoneId zone = ZoneId.systemDefault();
        Date fromInclusive = Date.from(fromDate.atStartOfDay(zone).toInstant());
        Date toExclusive = Date.from(toDate.plusDays(1).atStartOfDay(zone).toInstant());
        return transactionDao.findByCustomerIDAndCreatDateGreaterThanEqualAndCreatDateLessThanOrderByCreatDateDesc(
                customerId, fromInclusive, toExclusive);
    }
}
