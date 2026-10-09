package com.bhagwaniHardware.inventory.dao;

import java.util.List;
import java.time.LocalDateTime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.bhagwaniHardware.inventory.dto.BillType;
import com.bhagwaniHardware.inventory.model.Bill;

public interface BillDao extends JpaRepository<Bill, String>{
	List<Bill> findTop10ByOrderByCreatedAtDesc();
	List<Bill> findByBillTypeOrderByCreatedAtDesc(BillType billType);
	List<Bill> findByBillTypeAndCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
			BillType billType, LocalDateTime fromInclusive, LocalDateTime toExclusive);
	List<Bill> findAllByOrderByCreatedAtDesc();
	List<Bill> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(
			LocalDateTime fromInclusive, LocalDateTime toExclusive);
	@Query("SELECT COALESCE(SUM(b.total), 0.0) FROM Bill b")
	double getTotalSales();
	@Query("SELECT COALESCE(SUM(b.total), 0.0) FROM Bill b WHERE b.createdAt >= :fromInclusive AND b.createdAt < :toExclusive")
	double getTotalSalesBetween(@Param("fromInclusive") LocalDateTime fromInclusive,
			@Param("toExclusive") LocalDateTime toExclusive);
	long countByCreatedAtGreaterThanEqualAndCreatedAtLessThan(LocalDateTime fromInclusive,
			LocalDateTime toExclusive);

}
