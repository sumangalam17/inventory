package com.bhagwaniHardware.inventory.dao;

import org.springframework.data.jpa.repository.JpaRepository;

import com.bhagwaniHardware.inventory.model.Customer;

public interface CustomerDao extends JpaRepository<Customer,String>{

}
