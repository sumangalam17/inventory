package com.bhagwaniHardware.inventory.controller;

import java.time.LocalDate;
import java.io.IOException;

import org.springframework.stereotype.Controller;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;

import com.bhagwaniHardware.inventory.dto.BillType;
import com.bhagwaniHardware.inventory.dto.CreateBillRequest;
import com.bhagwaniHardware.inventory.model.Bill;
import com.bhagwaniHardware.inventory.services.BillService;
import com.bhagwaniHardware.inventory.services.BillPdfService;
import com.bhagwaniHardware.inventory.services.CustomerService;

@Controller
@RequestMapping("/bill")
public class BillController {

    private final BillService billService;
    private final BillPdfService billPdfService;
    private final CustomerService customerService;

    public BillController(BillService billService, BillPdfService billPdfService, CustomerService customerService){
        this.billService = billService;
        this.billPdfService = billPdfService;
        this.customerService = customerService;
    }

    @GetMapping("/create")
    public String Bill(@RequestParam(required = false) String customerId,
                       @RequestParam(required = false) String billType,
                       Model model){
        if (customerId != null && !customerId.isBlank()) {
            customerService.getCustomer(customerId)
                    .ifPresent(customer -> model.addAttribute("customer", customer));
        }
        model.addAttribute("selectedBillType", "returnItem".equals(billType) ? "returnItem" : "sale");
        return "/sell";
    }

    @GetMapping("/sale")
    public String sale(@RequestParam(required = false) String customerId, Model model){
        if (customerId != null && !customerId.isBlank()) {
            customerService.getCustomer(customerId)
                    .ifPresent(customer -> model.addAttribute("customer", customer));
        }
        model.addAttribute("selectedBillType", "sale");
        return "/sell";
    } 

    @GetMapping({"", "/"})
    public String getLatestBills(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {
        validateDateRange(fromDate, toDate);
        model.addAttribute("bills", fromDate == null
                ? billService.getAllBills()
                : billService.getBillsBetweenDates(fromDate, toDate));
        addBillListFilterModel(model, "/bill", fromDate, toDate, "all");
        return "bill";
    }

    @GetMapping("/sales")
    public String getSaleBills(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {
        validateDateRange(fromDate, toDate);
        model.addAttribute("bills", fromDate == null
                ? billService.getBillsByType(BillType.sale)
                : billService.getBillsByTypeBetweenDates(BillType.sale, fromDate, toDate));
        addBillListFilterModel(model, "/bill/sales", fromDate, toDate, "sale");
        return "bill";
    }

    @GetMapping("/returns")
    public String getReturnBills(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            Model model) {
        validateDateRange(fromDate, toDate);
        model.addAttribute("bills", fromDate == null
                ? billService.getBillsByType(BillType.returnItem)
                : billService.getBillsByTypeBetweenDates(BillType.returnItem, fromDate, toDate));
        addBillListFilterModel(model, "/bill/returns", fromDate, toDate, "return");
        return "bill";
    }

    private void addBillListFilterModel(Model model, String filterAction, LocalDate fromDate, LocalDate toDate, String billListType) {
        model.addAttribute("dateFilterAction", filterAction);
        model.addAttribute("fromDate", fromDate);
        model.addAttribute("toDate", toDate);
        model.addAttribute("dateRangeSelected", fromDate != null);
        model.addAttribute("billListType", billListType);
    }

    private void validateDateRange(LocalDate fromDate, LocalDate toDate) {
        if ((fromDate == null) != (toDate == null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Select both dates");
        }
        if (fromDate != null && fromDate.isAfter(toDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "From date must not be after To date");
        }
    }

    @GetMapping("/{billNo}")
    public String getBill(@PathVariable String billNo, Model model) {
        Bill bill = billService.getBill(billNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        model.addAttribute("bill", bill);
        return "billDetail";
    }

    @GetMapping(value = "/{billNo}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadBillPdf(@PathVariable String billNo) {
        Bill bill = billService.getBill(billNo)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bill not found"));
        try {
            byte[] pdf = billPdfService.createBillPdf(bill);
            String filename = "bill-" + billNo.replaceAll("[^A-Za-z0-9._-]", "_") + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename).build().toString())
                    .body(pdf);
        } catch (IOException exception) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not generate bill PDF", exception);
        }
    }

    @PostMapping("/{billNo}/delete")
    public String deleteBill(@PathVariable String billNo) {
        try {
            billService.deleteBill(billNo);
            return "redirect:/bill";
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, exception.getMessage(), exception);
        }
    }

    @PostMapping("/create")
    @ResponseBody
    public String createBill(@RequestBody CreateBillRequest request, RedirectAttributes redirectAttributes){
        if(request.getBillType() == BillType.sale) billService.createBill(request);
        else billService.returnItem(request);
        return "redirect:/customer/" + request.getMobileNumber();
    }

    @GetMapping("/totalsale")
    public double totalSale(){
        return billService.totalSale();
    }

}
