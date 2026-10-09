package com.bhagwaniHardware.inventory.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bhagwaniHardware.inventory.model.Transactions;
import java.util.Date;
import java.util.List;


public interface TransactionDao extends JpaRepository<Transactions,String>{
    public List<Transactions> findByCustomerIDOrderByCreatDateDesc(String customerID);
    public List<Transactions> findByCustomerIDAndCreatDateGreaterThanEqualAndCreatDateLessThanOrderByCreatDateDesc(
            String customerID, Date fromInclusive, Date toExclusive);
    List<Transactions> findByBillNo(String billNo);
    void deleteByBillNo(String billNo);
}
