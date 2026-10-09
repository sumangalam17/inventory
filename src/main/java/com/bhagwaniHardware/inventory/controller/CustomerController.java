package com.bhagwaniHardware.inventory.controller;

import java.util.List;
import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.bhagwaniHardware.inventory.dto.TransactionType;
import com.bhagwaniHardware.inventory.model.Transactions;
import com.bhagwaniHardware.inventory.services.CustomerService;
import com.bhagwaniHardware.inventory.services.TransactionService;

@Controller 
@RequestMapping("/customer")
public class CustomerController {

    private final CustomerService customerService;
    private final TransactionService transactionService;
    
    public CustomerController(CustomerService customerService, TransactionService transactionService){
        this.customerService = customerService;
        this.transactionService = transactionService;
    }

    @GetMapping("/{mobileNo}")
    public String getCustomerByMobile(
            @PathVariable String mobileNo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {
        model.addAttribute("customer", customerService.getCustomer(mobileNo)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found with mobile: " + mobileNo)));
        if ((fromDate == null) != (toDate == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select both transaction dates");
        }
        if (fromDate != null && fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not be after To date");
        }

        List<Transactions> transactions = fromDate == null
                ? transactionService.getTransactions(mobileNo)
                : transactionService.getTransactions(mobileNo, fromDate, toDate);
        model.addAttribute("transactions", transactions);
        model.addAttribute("dateFilterAction", "/customer/" + mobileNo);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("dateRangeSelected", fromDate != null);
        return "individualCustomer";
    }

    @PostMapping("/payAmount/{customerId}")
    public String payAmount(@PathVariable String customerId, @RequestParam double amount, @RequestParam TransactionType transactionType,RedirectAttributes redirectAttributes){
        customerService.payAmount(customerId,amount,transactionType);
        redirectAttributes.addFlashAttribute("transactionType", transactionType);
        return "redirect:/customer/" + customerId;
    }

    @PostMapping("/{mobileNo}/transactions/{transactionId}/delete")
    public String deletePaymentTransaction(
            @PathVariable String mobileNo,
            @PathVariable String transactionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            RedirectAttributes redirectAttributes) {
        if ((fromDate == null) != (toDate == null)) {
            redirectAttributes.addFlashAttribute("paymentError", "Both transaction dates are required to keep the date filter.");
            return "redirect:/customer/" + mobileNo;
        }
        if (fromDate != null && fromDate.isAfter(toDate)) {
            redirectAttributes.addFlashAttribute("paymentError", "From date must not be after To date.");
            return "redirect:/customer/" + mobileNo;
        }

        try {
            customerService.deleteStandalonePaymentTransaction(mobileNo, transactionId);
            redirectAttributes.addFlashAttribute("paymentSuccess", "Transaction deleted and customer balances updated.");
        } catch (IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("paymentError", exception.getMessage());
        }
        if (fromDate != null) {
            redirectAttributes.addAttribute("fromDate", fromDate);
            redirectAttributes.addAttribute("toDate", toDate);
        }
        return "redirect:/customer/" + mobileNo;
    }

    @PostMapping("/create")
    public String createCustomer(@RequestParam String name, @RequestParam String mobileNo, @RequestParam(required = false) String address, RedirectAttributes redirectAttributes){
        customerService.createCustomer(name,mobileNo,address);
        return "redirect:/customer/" + mobileNo;
    } 

    @GetMapping("/create")
    public String newCustomer(){
        return "/createCustomer";
    }

    @GetMapping({"" , "/"})
    public String getCustomers(Model model) {
        model.addAttribute("customers", customerService.getAll());
        return "customer";
    }
}
