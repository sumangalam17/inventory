package com.bhagwaniHardware.inventory.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.bhagwaniHardware.inventory.dto.TransactionType;
import com.bhagwaniHardware.inventory.model.Bill;
import com.bhagwaniHardware.inventory.model.Customer;
import com.bhagwaniHardware.inventory.model.SoldItem;
import com.bhagwaniHardware.inventory.model.Transactions;

class BillPdfServiceTest {

    private final BillPdfService billPdfService = new BillPdfService();

    @Test
    void keepsTwentyProductsOnOnePageAndStartsTheNextPageAtProductTwentyOne() throws Exception {
        String twentyProductPdf = pdfText(billPdfService.createBillPdf(billWithItems(20)));
        String twentyOneProductPdf = pdfText(billPdfService.createBillPdf(billWithItems(21)));

        assertEquals(1, pageCount(twentyProductPdf));
        assertEquals(2, pageCount(twentyOneProductPdf));
        assertTrue(twentyProductPdf.contains("Product 20"));
        assertTrue(twentyOneProductPdf.contains("Product 21"));
    }

    @Test
    void overlaysTransparentStampBehindThankYouTextInBillSummary() throws Exception {
        String pdf = pdfText(billPdfService.createBillPdf(billWithItems(1)));

        assertTrue(pdf.contains("/Subtype /Image"));
        assertTrue(pdf.contains("/SMask"));
        assertTrue(pdf.indexOf("/Stamp Do") < pdf.indexOf("Thank you for your business."));
        assertTrue(pdf.contains("q 60 0 0 60 427 630 cm /Stamp Do Q"));
        assertTrue(pdf.contains("PAGE 1"));
    }

    @Test
    void addsPageNumberFooterToEachBillPage() throws Exception {
        String pdf = pdfText(billPdfService.createBillPdf(billWithItems(31)));

        assertEquals(2, pageCount(pdf));
        assertTrue(pdf.contains("PAGE 1"));
        assertTrue(pdf.contains("PAGE 2"));
    }

    @Test
    void matchesBillHeaderAndAddsStampedThankYouAfterFinalTransaction() throws Exception {
        Customer customer = new Customer();
        customer.setName("Test Customer");
        customer.setMobileNo("1234567890");
        customer.setTotal(1500);
        customer.setAmountPaid(900);
        customer.setAmountPending(600);
        ArrayList<Transactions> transactions = new ArrayList<>();
        for (int index = 0; index < 21; index++) {
            Transactions transaction = new Transactions();
            transaction.setTransactionType(index % 2 == 0 ? TransactionType.sale : TransactionType.recieved);
            transaction.setAmount(10);
            transactions.add(transaction);
        }

        String pdf = pdfText(billPdfService.createCustomerTransactionPdf(customer, transactions, null, null));

        assertEquals(2, pageCount(pdf));
        assertTrue(pdf.contains("BHAGWANI HARDWARE"));
        assertTrue(pdf.contains("Near petrol pump, Chirailli Tekari Gaya"));
        assertTrue(pdf.contains("CUSTOMER TRANSACTION STATEMENT"));
        assertTrue(pdf.contains("Thank you for doing business with us."));
        assertTrue(pdf.contains("(1500.00) Tj"));
        assertTrue(pdf.contains("(PAID) Tj"));
        assertTrue(pdf.contains("(PENDING) Tj"));
        assertTrue(pdf.indexOf("(CUSTOMER TRANSACTION STATEMENT)")
                < pdf.indexOf("(TOTAL) Tj"));
        assertTrue(pdf.indexOf("(TOTAL) Tj") < pdf.indexOf("(TYPE) Tj"));
        assertEquals(1, pdf.split("/Stamp Do", -1).length - 1);
        assertTrue(pdf.indexOf("/Stamp Do") < pdf.indexOf("Thank you for doing business with us."));
        assertTrue(pdf.contains("0.08 0.45 0.25 rg"));
        assertTrue(pdf.contains("0.72 0.12 0.12 rg"));
    }

    @Test
    void createsPaginatedAllTransactionReportForSelectedDateRange() throws Exception {
        ArrayList<Transactions> transactions = new ArrayList<>();
        for (int index = 1; index <= 21; index++) {
            Transactions transaction = new Transactions();
            transaction.setTransactionType(index % 2 == 0 ? TransactionType.sale : TransactionType.recieved);
            transaction.setCustomerID("customer-" + index);
            transaction.setBillNo(index % 2 == 0 ? "bill-" + index : null);
            transaction.setAmount(100);
            transaction.setCreatDate(java.util.Date.from(
                    LocalDateTime.of(2026, 10, 25, 10, 15).atZone(java.time.ZoneId.systemDefault()).toInstant()));
            transactions.add(transaction);
        }

        String pdf = pdfText(billPdfService.createTransactionReportPdf(
                transactions, LocalDate.of(2026, 10, 25), LocalDate.of(2026, 10, 25),
                Map.of("customer-1", "Customer One", "customer-2", "Customer Two")));

        assertEquals(2, pageCount(pdf));
        assertTrue(pdf.contains("ALL TRANSACTIONS"));
        assertTrue(pdf.contains("25 Oct 2026"));
        assertTrue(pdf.contains("Customer One"));
        assertTrue(!pdf.contains("customer-1"));
        assertTrue(pdf.contains("bill-2"));
        assertTrue(pdf.contains("PAGE 1"));
        assertTrue(pdf.contains("PAGE 2"));
        assertTrue(pdf.contains("(1100.00) Tj"));
        assertTrue(pdf.contains("(1000.00) Tj"));
        assertTrue(pdf.contains("Thank you for doing business with us."));
        assertEquals(1, pdf.split("/Stamp Do", -1).length - 1);
        assertTrue(pdf.contains("0.08 0.45 0.25 rg"));
        assertTrue(pdf.contains("0.72 0.12 0.12 rg"));
    }

    private static Bill billWithItems(int count) {
        Bill bill = new Bill();
        bill.setCustomerName("Test Customer");
        bill.setBillNo("test-bill");
        bill.setItems(new ArrayList<>());
        for (int index = 1; index <= count; index++) {
            SoldItem item = new SoldItem();
            item.setName("Product " + index);
            item.setCount(1);
            item.setSellingPrice(10);
            bill.getItems().add(item);
        }
        return bill;
    }

    private static String pdfText(byte[] pdf) {
        return new String(pdf, StandardCharsets.ISO_8859_1);
    }

    private static int pageCount(String pdf) {
        return pdf.split("/Type /Page /Parent", -1).length - 1;
    }
}
