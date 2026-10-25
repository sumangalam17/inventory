package com.bhagwaniHardware.inventory.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import org.springframework.stereotype.Service;

import com.bhagwaniHardware.inventory.dto.TransactionType;
import com.bhagwaniHardware.inventory.model.Bill;
import com.bhagwaniHardware.inventory.model.Customer;
import com.bhagwaniHardware.inventory.model.SoldItem;
import com.bhagwaniHardware.inventory.model.Transactions;

@Service
public class BillPdfService {
    private static final int PAGE_HEIGHT = 842;
    private static final int PAGE_WIDTH = 595;
    private static final int LEFT = 30;
    private static final int RIGHT = 565;
    private static final int ROW_HEIGHT = 20;
    private static final int ITEMS_PER_PAGE = 35;
    private static final int BILL_SUMMARY_MIN_ROW_BOTTOM = 169;
    private static final int TRANSACTION_ROW_HEIGHT = 30;
    private static final int TRANSACTION_CLOSING_SPACE = 130;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
    private static final DateTimeFormatter TRANSACTION_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
    private static final DateTimeFormatter STATEMENT_DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final String NAVY = "0.13 0.16 0.48";
    private static final String BLUE = "0.25 0.30 0.80";
    private static final String PALE_BLUE = "0.92 0.94 0.99";
    private static final String PALE_GRAY = "0.96 0.97 1.00";
    private static final String DARK = "0.13 0.17 0.22";
    private static final String MUTED = "0.38 0.43 0.49";
    private static final String WHITE = "1 1 1";

    public byte[] createBillPdf(Bill bill) throws IOException {
        List<byte[]> pageStreams = new ArrayList<>();
        List<SoldItem> items = bill.getItems() == null ? List.of() : bill.getItems();
        PdfImage stamp = readStampImage();
        int nextItem = 0;
        boolean firstPage = true;
        int pageNumber = 1;

        do {
            PageCanvas page = new PageCanvas();
            int y = drawHeader(page, bill, firstPage);
            y = drawTableHeader(page, y);

            int itemsOnPage = 0;
            while (nextItem < items.size() && itemsOnPage < ITEMS_PER_PAGE && y - ROW_HEIGHT >= 50) {
                boolean lastItem = nextItem == items.size() - 1;
                if (lastItem && y - ROW_HEIGHT < BILL_SUMMARY_MIN_ROW_BOTTOM) {
                    break;
                }
                SoldItem item = items.get(nextItem++);
                drawItemRow(page, item, y);
                y -= ROW_HEIGHT;
                itemsOnPage++;
            }

            boolean lastPage = nextItem >= items.size();
            if (lastPage) {
                drawSummary(page, bill, y - 10);
            }
            drawBillFooter(page, bill, pageNumber++);
            pageStreams.add(page.bytes());
            firstPage = false;
        } while (nextItem < items.size());

        return writePdf(pageStreams, "Bill " + safeText(bill.getBillNo()), stamp);
    }

    public byte[] createCustomerTransactionPdf(Customer customer, List<Transactions> transactions,
            LocalDate fromDate, LocalDate toDate) throws IOException {
        List<Transactions> statementTransactions = transactions == null ? List.of() : transactions;
        PdfImage stamp = readStampImage();
        List<byte[]> pageStreams = new ArrayList<>();
        int nextTransaction = 0;
        int pageNumber = 1;

        do {
            PageCanvas page = new PageCanvas();
            int y = drawTransactionHeader(page, customer, fromDate, toDate, pageNumber == 1);
            y = drawCustomerPaymentSummary(page, customer, y);
            y = drawTransactionTableHeader(page, y);

            int firstTransactionOnPage = nextTransaction;
            while (nextTransaction < statementTransactions.size()) {
                boolean lastTransaction = nextTransaction == statementTransactions.size() - 1;
                int minimumBottom = lastTransaction ? TRANSACTION_CLOSING_SPACE : 60;
                if (y - TRANSACTION_ROW_HEIGHT < minimumBottom) {
                    break;
                }
                drawTransactionRow(page, statementTransactions.get(nextTransaction++), y,
                        nextTransaction % 2 == 0);
                y -= TRANSACTION_ROW_HEIGHT;
            }
            if (firstTransactionOnPage == nextTransaction) {
                page.text(LEFT + 8, y - 20, 10, MUTED, "No transactions found for this period.", false);
            }

            boolean lastPage = nextTransaction >= statementTransactions.size();
            if (lastPage) {
                drawTransactionClosing(page, y);
            }
            drawStatementFooter(page, pageNumber++, "CUSTOMER TRANSACTION STATEMENT");
            pageStreams.add(page.bytes());
        } while (nextTransaction < statementTransactions.size());

        return writePdf(pageStreams, "Customer transaction statement - " + safeText(customer.getName()), stamp);
    }

    public byte[] createTransactionReportPdf(List<Transactions> transactions, LocalDate fromDate,
            LocalDate toDate, Map<String, String> customerNames) throws IOException {
        List<Transactions> reportTransactions = transactions == null ? List.of() : transactions;
        PdfImage stamp = readStampImage();
        double creditTotal = reportTransactions.stream()
                .filter(BillPdfService::isCreditTransaction)
                .mapToDouble(Transactions::getAmount).sum();
        double debitTotal = reportTransactions.stream()
                .filter(BillPdfService::isDebitTransaction)
                .mapToDouble(Transactions::getAmount).sum();
        List<byte[]> pageStreams = new ArrayList<>();
        int nextTransaction = 0;
        int pageNumber = 1;

        do {
            PageCanvas page = new PageCanvas();
            int y = drawTransactionReportHeader(page, fromDate, toDate, pageNumber == 1);
            y = drawTransactionReportSummary(page, y, creditTotal, debitTotal);
            y = drawTransactionReportTableHeader(page, y);
            int firstTransactionOnPage = nextTransaction;

            while (nextTransaction < reportTransactions.size()) {
                boolean lastTransaction = nextTransaction == reportTransactions.size() - 1;
                int minimumBottom = lastTransaction ? TRANSACTION_CLOSING_SPACE : 60;
                if (y - TRANSACTION_ROW_HEIGHT < minimumBottom) {
                    break;
                }
                drawTransactionReportRow(page, reportTransactions.get(nextTransaction++), y,
                        nextTransaction % 2 == 0, customerNames);
                y -= TRANSACTION_ROW_HEIGHT;
            }
            if (firstTransactionOnPage == nextTransaction && reportTransactions.isEmpty()) {
                page.text(LEFT + 8, y - 20, 10, MUTED, "No transactions found for this period.", false);
            }

            boolean lastPage = nextTransaction >= reportTransactions.size();
            if (lastPage) {
                drawTransactionClosing(page, y);
            }
            drawStatementFooter(page, pageNumber++, "ALL TRANSACTIONS");
            pageStreams.add(page.bytes());
        } while (nextTransaction < reportTransactions.size());

        return writePdf(pageStreams, "Transaction report " + fromDate + " to " + toDate, stamp);
    }

    private static int drawTransactionReportHeader(PageCanvas page, LocalDate fromDate, LocalDate toDate,
            boolean firstPage) {
        if (firstPage) {
            page.text(LEFT, 800, 17, BLUE, "BHAGWANI HARDWARE", true);
            page.serifText(LEFT, 785, 9, DARK, "Near petrol pump, Chirailli Tekari Gaya", false);
            page.serifText(LEFT, 770, 9, DARK, "GSTIN : 10ENLPA9740M1ZU", false);
            page.serifText(LEFT + 330, 810, 10, BLUE, "ALL TRANSACTIONS", true);
            page.serifText(LEFT + 330, 795, 9, DARK,
                    "PERIOD: " + STATEMENT_DATE_FORMAT.format(fromDate) + " - "
                            + STATEMENT_DATE_FORMAT.format(toDate), false);
            page.line(LEFT, 760, RIGHT, 760, DARK, 1.1f);
            return 760;
        }

        page.text(LEFT, 800, 15, BLUE, "BHAGWANI HARDWARE", true);
        page.rightText(RIGHT, 800, 11, NAVY, "ALL TRANSACTIONS", true, true);
        page.line(LEFT, 780, RIGHT, 780, DARK, 1.1f);
        return 760;
    }

    private static int drawTransactionReportSummary(PageCanvas page, int top,
            double creditTotal, double debitTotal) {
        int height = 38;
        int bottom = top - height;
        int columnWidth = (RIGHT - LEFT) / 2;
        page.rectangle(LEFT, bottom, RIGHT - LEFT, height, PALE_BLUE);
        page.text(LEFT + 10, bottom + 23, 7, "0.08 0.45 0.25", "TOTAL CREDIT", true);
        page.text(LEFT + 10, bottom + 9, 9, "0.08 0.45 0.25", money(creditTotal), true);
        page.text(LEFT + columnWidth + 10, bottom + 23, 7, "0.72 0.12 0.12", "TOTAL DEBIT", true);
        page.text(LEFT + columnWidth + 10, bottom + 9, 9, "0.72 0.12 0.12", money(debitTotal), true);
        return bottom - 8;
    }

    private static int drawTransactionReportTableHeader(PageCanvas page, int y) {
        int headerHeight = 28;
        page.text(LEFT, y - 18, 7, MUTED, "TYPE", true);
        page.text(105, y - 18, 7, MUTED, "DATE / TIME", true);
        page.text(235, y - 18, 7, MUTED, "CUSTOMER / BILL NO", true);
        page.rightText(465, y - 18, 7, MUTED, "CREDIT", true, false);
        page.rightText(RIGHT, y - 18, 7, MUTED, "DEBIT", true, false);
        page.line(LEFT, y - headerHeight, RIGHT, y - headerHeight, "0.75 0.78 0.82", 0.6f);
        return y - headerHeight;
    }

    private static int drawCustomerPaymentSummary(PageCanvas page, Customer customer, int top) {
        int left = LEFT;
        int height = 38;
        int bottom = top - height;
        int columnWidth = (RIGHT - LEFT) / 3;
        page.rectangle(left, bottom, RIGHT - LEFT, height, PALE_BLUE);
        page.text(left + 10, bottom + 23, 7, MUTED, "TOTAL", true);
        page.text(left + 10, bottom + 9, 9, DARK, money(customer.getTotal()), true);
        page.text(left + columnWidth + 10, bottom + 23, 7, "0.08 0.45 0.25", "PAID", true);
        page.text(left + columnWidth + 10, bottom + 9, 9, "0.08 0.45 0.25",
                money(customer.getAmountPaid()), true);
        page.text(left + 2 * columnWidth + 10, bottom + 23, 7, "0.72 0.12 0.12", "PENDING", true);
        page.text(left + 2 * columnWidth + 10, bottom + 9, 9, "0.72 0.12 0.12",
                money(customer.getAmountPending()), true);
        return bottom - 8;
    }

    private static void drawTransactionReportRow(PageCanvas page, Transactions transaction,
            int top, boolean shaded, Map<String, String> customerNames) {
        int bottom = top - TRANSACTION_ROW_HEIGHT;
        page.rectangle(LEFT, bottom, RIGHT - LEFT, TRANSACTION_ROW_HEIGHT, shaded ? PALE_BLUE : WHITE);
        page.line(LEFT, bottom, RIGHT, bottom, PALE_GRAY, 0.5f);
        String type = transaction.getTransactionType() == null ? ""
                : transaction.getTransactionType().name().equals("itemReturn") ? "Return"
                        : transaction.getTransactionType().name();
        String date = transaction.getCreatDate() == null ? ""
                : TRANSACTION_DATE_FORMAT.format(transaction.getCreatDate().toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        page.text(LEFT + 6, bottom + 11, 7, DARK, truncate(safeText(type), 12), false);
        page.text(105, bottom + 11, 7, DARK, date, false);
        page.text(235, bottom + 11, 7, DARK,
                truncate(safeText(customerNames.get(transaction.getCustomerID())), 20)
                + " / " + truncate(safeText(transaction.getBillNo()), 16), false);
        if (isCreditTransaction(transaction)) {
            page.rightText(465, bottom + 11, 7, "0.08 0.45 0.25",
                    money(transaction.getAmount()), false, false);
        }
        if (isDebitTransaction(transaction)) {
            page.rightText(RIGHT - 6, bottom + 11, 7, "0.72 0.12 0.12",
                    money(transaction.getAmount()), false, false);
        }
    }

    private static boolean isCreditTransaction(Transactions transaction) {
        return transaction.getTransactionType() == TransactionType.recieved
                || transaction.getTransactionType() == TransactionType.itemReturn;
    }

    private static boolean isDebitTransaction(Transactions transaction) {
        return transaction.getTransactionType() == TransactionType.sale
                || transaction.getTransactionType() == TransactionType.refund;
    }

    private static int drawTransactionHeader(PageCanvas page, Customer customer,
            LocalDate fromDate, LocalDate toDate, boolean firstPage) {
        page.text(LEFT, 800, 17, BLUE, "BHAGWANI HARDWARE", true);
        page.serifText(LEFT, 785, 9, DARK, "Near petrol pump, Chirailli Tekari Gaya", false);
        page.serifText(LEFT, 770, 9, DARK, "GSTIN : 10ENLPA9740M1ZU", false);
        page.serifText(LEFT+330, 810, 10, BLUE, "CUSTOMER TRANSACTION STATEMENT", true);
        page.serifText(LEFT+330, 795, 9, DARK, "CUSTOMER: "
                + truncate(safeText(customer.getName()), 42), false);
        page.serifText(LEFT+330, 785, 9, DARK, "MOBILE: "
                + truncate(safeText(customer.getMobileNo()), 42), false);
        String period = fromDate == null ? "ALL TRANSACTIONS"
                : STATEMENT_DATE_FORMAT.format(fromDate) + " - " + STATEMENT_DATE_FORMAT.format(toDate);
        page.serifText(LEFT+330, 775, 9, DARK, "PERIOD: " + period, false);
        page.line(LEFT, 760, RIGHT, 760, DARK, 1.1f);
        return 760;
        
    }

    private static int drawTransactionTableHeader(PageCanvas page, int y) {
        int headerHeight = 28;
        page.text(LEFT, y - 18, 8, MUTED, "TYPE", true);
        page.text(155, y - 18, 8, MUTED, "DATE / TIME", true);
        page.text(300, y - 18, 8, MUTED, "BILL ID", true);
        page.rightText(465, y - 18, 8, MUTED, "CREDIT", true, false);
        page.rightText(RIGHT, y - 18, 8, MUTED, "DEBIT", true, false);
        page.line(LEFT, y - headerHeight, RIGHT, y - headerHeight, "0.75 0.78 0.82", 0.6f);
        return y - headerHeight;
    }

    private static void drawTransactionRow(PageCanvas page, Transactions transaction, int top, boolean shaded) {
        int bottom = top - 30;
        page.rectangle(LEFT, bottom, RIGHT - LEFT, 30, shaded ? PALE_BLUE : WHITE);
        page.line(LEFT, bottom, RIGHT, bottom, PALE_GRAY, 0.5f);
        String type = transaction.getTransactionType() == null ? ""
                : transaction.getTransactionType().name().equals("itemReturn") ? "Return"
                        : transaction.getTransactionType().name();
        String date = transaction.getCreatDate() == null ? ""
                : TRANSACTION_DATE_FORMAT.format(transaction.getCreatDate().toInstant()
                        .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime());
        String billNumber = safeText(transaction.getBillNo());
        boolean isCredit = transaction.getTransactionType() != null
                && (transaction.getTransactionType().name().equals("recieved")
                        || transaction.getTransactionType().name().equals("itemReturn"));
        boolean isDebit = transaction.getTransactionType() != null
                && (transaction.getTransactionType().name().equals("sale")
                        || transaction.getTransactionType().name().equals("refund"));

        page.text(LEFT + 8, bottom + 11, 8, DARK, truncate(safeText(type), 18), false);
        page.text(155, bottom + 11, 8, DARK, date, false);
        page.text(300, bottom + 11, 8, DARK, truncate(billNumber, 20), false);
        if (isCredit) {
            page.rightText(465, bottom + 11, 8, "0.08 0.45 0.25",
                    money(transaction.getAmount()), false, false);
        }
        if (isDebit) {
            page.rightText(RIGHT - 8, bottom + 11, 8, "0.72 0.12 0.12",
                    money(transaction.getAmount()), false, false);
        }
    }

    private static void drawTransactionClosing(PageCanvas page, int y) {
        int thanksLineY = y - 70;
        page.line(350, thanksLineY, RIGHT, thanksLineY, "0.82 0.85 0.88", 0.5f);
        page.image("Stamp", (350 + RIGHT) / 2 - 30, thanksLineY + 7, 60, 60);
        page.centeredText((350 + RIGHT) / 2, thanksLineY - 10, 8, MUTED,
                "Thank you for doing business with us.", false, false);
    }

    private static void drawStatementFooter(PageCanvas page, int pageNumber, String label) {
        page.line(LEFT, 42, RIGHT, 42, PALE_GRAY, 0.7f);
        page.text(LEFT, 27, 8, BLUE, "BHAGWANI HARDWARE  |  " + label, false);
        page.rightText(RIGHT, 27, 8, MUTED, "PAGE " + pageNumber, false, false);
    }

    private static int drawHeader(PageCanvas page, Bill bill, boolean firstPage) {
        page.text(LEFT, 800, 17, BLUE, "BHAGWANI HARDWARE", true);
        page.serifText(LEFT, 785, 9, DARK, "Near petrol pump, Chirailli Tekari Gaya", false);
        page.serifText(LEFT, 770, 9, DARK, "GSTIN : 10ENLPA9740M1ZU", false);
        page.serifText(LEFT+330, 810, 9, DARK, "Bill To : " + truncate(safeText(bill.getCustomerName()), 65), false);
        String mobile = safeText(bill.getMobileNumber());
        if (!mobile.isBlank()) {
            page.serifText(LEFT+330, 800, 9, MUTED, "Mobile No: " + truncate(mobile, 65), false);
        }
        page.serifText(LEFT+330, 790, 9, MUTED, "Bill number: " + safeText(bill.getBillNo()), false);
        page.serifText(LEFT+330, 780, 9, MUTED, "Date: "
                + (bill.getCreatedAt() == null ? "" : DATE_FORMAT.format(bill.getCreatedAt())), false);
        
        String address = safeText(bill.getAddress() == null ? "" :
                bill.getAddress().replace('\n', ' ').replace('\r', ' '));
        if (!address.isBlank()) {
            page.serifText(LEFT+330, 770, 8, MUTED, truncate(address, 82), false);
        }
        page.line(LEFT, 760, RIGHT, 760, DARK, 1.1f);
        return 760;
    }

    private static int drawTableHeader(PageCanvas page, int y) {
        int headerHeight = 28;
        page.text(LEFT, y - 18, 8, MUTED, "PRODUCT", true);
        page.rightText(350, y - 18, 8, MUTED, "QTY", true, false);
        page.rightText(450, y - 18, 8, MUTED, "UNIT PRICE", true, false);
        page.rightText(RIGHT, y - 18, 8, MUTED, "AMOUNT", true, false);
        page.line(LEFT, y - headerHeight, RIGHT, y - headerHeight, "0.75 0.78 0.82", 0.6f);
        return y - headerHeight;
    }

    private static void drawItemRow(PageCanvas page, SoldItem item, int top) {
        int bottom = top - ROW_HEIGHT;
        String productName = item.getName() == null || item.getName().isBlank()
                ? item.getInventoryItemId() : item.getName();
        page.text(LEFT, bottom + 6, 8, DARK, truncate(safeText(productName), 48), false);
        page.rightText(350, bottom + 6, 8, DARK, Integer.toString(item.getCount()), false, false);
        page.rightText(450, bottom + 6, 8, DARK, money(item.getSellingPrice()), false, false);
        page.rightText(RIGHT, bottom + 6, 8, DARK,
                money(item.getSellingPrice() * item.getCount()), false, false);
        page.line(LEFT, bottom, RIGHT, bottom, "0.88 0.90 0.92", 0.4f);
    }

    private static void drawSummary(PageCanvas page, Bill bill, int top) {
        int totalsLeft = 350;
        page.line(totalsLeft, top, RIGHT, top, "0.75 0.78 0.82", 0.6f);
        page.text(totalsLeft, top - 16, 9, DARK, "Subtotal", false);
        page.rightText(RIGHT, top - 16, 9, DARK, money(bill.getTotal() + bill.getDiscount()), false, false);

        int totalY = top - 38;
        if (bill.getDiscount() > 0) {
            page.text(totalsLeft, top - 32, 9, DARK, "Discount", false);
            page.rightText(RIGHT, top - 32, 9, DARK, money(bill.getDiscount()), false, false);
            totalY = top - 52;
        }
        page.text(totalsLeft, totalY, 10, DARK, "Total due", true);
        page.rightText(RIGHT, totalY, 10, DARK, money(bill.getTotal()), true, false);

        int thanksLineY = top - 79;
        int stampSize = 60;
        page.line(totalsLeft, thanksLineY, RIGHT, thanksLineY, "0.82 0.85 0.88", 0.5f);
        page.image("Stamp", (totalsLeft + RIGHT) / 2 - stampSize / 2,
                thanksLineY + 7, stampSize, stampSize);
        page.centeredText((totalsLeft + RIGHT) / 2, thanksLineY - 20, 8, MUTED,
                "Thank you for your business.", false, false);
    }

    private static void drawBillFooter(PageCanvas page, Bill bill, int pageNumber) {
        page.line(LEFT, 42, RIGHT, 42, PALE_GRAY, 0.7f);
        page.rightText(RIGHT, 27, 8, MUTED, "PAGE " + pageNumber, false, false);
    }

    private static PdfImage readStampImage() throws IOException {
        try (InputStream input = BillPdfService.class.getResourceAsStream("/static/images/bhagwani-stamp.png")) {
            if (input == null) {
                throw new IOException("Missing bill stamp image: /static/images/bhagwani-stamp.png");
            }
            BufferedImage image = ImageIO.read(input);
            if (image == null) {
                throw new IOException("Could not read bill stamp image: /static/images/bhagwani-stamp.png");
            }

            int width = image.getWidth();
            int height = image.getHeight();
            int[] pixels = image.getRGB(0, 0, width, height, null, 0, width);
            ByteArrayOutputStream rgb = new ByteArrayOutputStream(width * height * 3);
            ByteArrayOutputStream alpha = new ByteArrayOutputStream(width * height);
            for (int pixel : pixels) {
                rgb.write((pixel >>> 16) & 0xff);
                rgb.write((pixel >>> 8) & 0xff);
                rgb.write(pixel & 0xff);
                alpha.write((pixel >>> 24) & 0xff);
            }
            return new PdfImage(width, height, compress(rgb.toByteArray()), compress(alpha.toByteArray()));
        }
    }

    private static byte[] compress(byte[] data) throws IOException {
        ByteArrayOutputStream compressed = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater = new DeflaterOutputStream(compressed)) {
            deflater.write(data);
        }
        return compressed.toByteArray();
    }

    private static boolean isReturnBill(Bill bill) {
        return bill.getBillType() != null && bill.getBillType().name().equals("returnItem");
    }

    private static String safeText(String value) {
        if (value == null) {
            return "";
        }
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private static String truncate(String value, int maxLength) {
        return value.length() <= maxLength ? value : value.substring(0, maxLength - 3) + "...";
    }

    private static String money(double amount) {
        return String.format(Locale.US, "%.2f", amount);
    }

    private static float approximateWidth(String value, float fontSize) {
        return (float) (value.length() * fontSize * 0.52);
    }

    private static float approximateSerifWidth(String value, float fontSize) {
        return (float) (value.length() * fontSize * 0.55);
    }

    private static byte[] writePdf(List<byte[]> pageStreams, String documentTitle) throws IOException {
        return writePdf(pageStreams, documentTitle, null);
    }

    private static byte[] writePdf(List<byte[]> pageStreams, String documentTitle, PdfImage stamp)
            throws IOException {
        int pageCount = pageStreams.size();
        int firstPageObject = 7;
        int imageObject = firstPageObject + pageCount * 2;
        int alphaObject = imageObject + 1;
        int infoObject = stamp == null ? imageObject : imageObject + 2;
        int objectCount = infoObject;
        byte[][] objects = new byte[objectCount + 1][];
        objects[1] = ascii("<< /Type /Catalog /Pages 2 0 R >>");

        StringBuilder kids = new StringBuilder();
        for (int index = 0; index < pageCount; index++) {
            kids.append(firstPageObject + index * 2).append(" 0 R ");
        }
        objects[2] = ascii("<< /Type /Pages /Kids [" + kids + "] /Count " + pageCount + " >>");
        objects[3] = ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>");
        objects[4] = ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica-Bold /Encoding /WinAnsiEncoding >>");
        objects[5] = ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Times-Roman /Encoding /WinAnsiEncoding >>");
        objects[6] = ascii("<< /Type /Font /Subtype /Type1 /BaseFont /Times-Bold /Encoding /WinAnsiEncoding >>");

        for (int index = 0; index < pageCount; index++) {
            int pageObject = firstPageObject + index * 2;
            int streamObject = pageObject + 1;
            String imageResource = stamp == null ? "" : " /XObject << /Stamp " + imageObject + " 0 R >>";
            objects[pageObject] = ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                    + PAGE_WIDTH + " " + PAGE_HEIGHT
                    + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R /F4 6 0 R >>"
                    + imageResource + " >> /Contents "
                    + streamObject + " 0 R >>");
            objects[streamObject] = streamObject(pageStreams.get(index));
        }
        if (stamp != null) {
            objects[imageObject] = imageStreamObject(stamp.rgb, stamp.width, stamp.height,
                    "/DeviceRGB", alphaObject);
            objects[alphaObject] = imageStreamObject(stamp.alpha, stamp.width, stamp.height,
                    "/DeviceGray", null);
        }
        objects[infoObject] = ascii("<< /Title (" + escapePdfString(safeText(documentTitle))
                + ") /Author (Bhagwani Hardware) >>");

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        output.write(ascii("%PDF-1.4\n% Inventory bill\n"));
        long[] offsets = new long[objectCount + 1];
        for (int objectId = 1; objectId <= objectCount; objectId++) {
            offsets[objectId] = output.size();
            output.write(ascii(objectId + " 0 obj\n"));
            output.write(objects[objectId]);
            output.write(ascii("\nendobj\n"));
        }

        long xrefOffset = output.size();
        output.write(ascii("xref\n0 " + (objectCount + 1) + "\n"));
        output.write(ascii("0000000000 65535 f \n"));
        for (int objectId = 1; objectId <= objectCount; objectId++) {
            output.write(ascii(String.format(Locale.ROOT, "%010d 00000 n \n", offsets[objectId])));
        }
        output.write(ascii("trailer\n<< /Size " + (objectCount + 1) + " /Root 1 0 R /Info "
                + infoObject + " 0 R >>\nstartxref\n" + xrefOffset + "\n%%EOF\n"));
        return output.toByteArray();
    }

    private static byte[] imageStreamObject(byte[] data, int width, int height, String colorSpace,
            Integer softMaskObject) throws IOException {
        ByteArrayOutputStream object = new ByteArrayOutputStream();
        String softMask = softMaskObject == null ? "" : " /SMask " + softMaskObject + " 0 R";
        object.write(ascii("<< /Type /XObject /Subtype /Image /Width " + width + " /Height " + height
                + " /ColorSpace " + colorSpace + " /BitsPerComponent 8 /Filter /FlateDecode"
                + softMask + " /Length " + data.length + " >>\nstream\n"));
        object.write(data);
        object.write(ascii("endstream"));
        return object.toByteArray();
    }

    private static byte[] streamObject(byte[] stream) throws IOException {
        ByteArrayOutputStream object = new ByteArrayOutputStream();
        object.write(ascii("<< /Length " + stream.length + " >>\nstream\n"));
        object.write(stream);
        object.write(ascii("endstream"));
        return object.toByteArray();
    }

    private static byte[] ascii(String value) {
        return value.getBytes(java.nio.charset.StandardCharsets.ISO_8859_1);
    }

    private static final class PageCanvas {
        private final ByteArrayOutputStream content = new ByteArrayOutputStream();

        private void image(String name, int x, int y, int width, int height) {
            write("q " + width + " 0 0 " + height + " " + x + " " + y + " cm /" + name + " Do Q\n");
        }

        private void rectangle(int x, int y, int width, int height, String color) {
            write(color + " rg " + x + " " + y + " " + width + " " + height + " re f\n");
        }

        private void strokeRectangle(int x, int y, int width, int height, String color, float lineWidth) {
            write(color + " RG " + lineWidth + " w " + x + " " + y + " " + width + " " + height + " re S\n");
        }

        private void line(int x1, int y1, int x2, int y2, String color, float width) {
            write(color + " RG " + width + " w " + x1 + " " + y1 + " m " + x2 + " " + y2 + " l S\n");
        }

        private void text(float x, float y, float size, String color, String value, boolean bold) {
            writeText(x, y, size, color, value, bold ? "F2" : "F1");
        }

        private void serifText(float x, float y, float size, String color, String value, boolean bold) {
            writeText(x, y, size, color, value, bold ? "F4" : "F3");
        }

        private void rightText(float right, float y, float size, String color, String value,
                boolean bold, boolean serif) {
            float width = serif ? approximateSerifWidth(value, size) : approximateWidth(value, size);
            float x = Math.max(LEFT, right - width);
            writeText(x, y, size, color, value, serif ? (bold ? "F4" : "F3") : (bold ? "F2" : "F1"));
        }

        private void centeredText(float center, float y, float size, String color, String value,
                boolean bold, boolean serif) {
            float width = serif ? approximateSerifWidth(value, size) : approximateWidth(value, size);
            writeText(center - width / 2, y, size, color, value,
                    serif ? (bold ? "F4" : "F3") : (bold ? "F2" : "F1"));
        }

        private void writeText(float x, float y, float size, String color, String value, String font) {
            write("BT /" + font + " " + size + " Tf " + color + " rg "
                    + x + " " + y + " Td (" + escapePdfString(value) + ") Tj ET\n");
        }

        private void write(String value) {
            try {
                content.write(ascii(value));
            } catch (IOException exception) {
                throw new IllegalStateException("Could not write bill PDF content.", exception);
            }
        }

        private byte[] bytes() {
            return content.toByteArray();
        }
    }

    private static final class PdfImage {
        private final int width;
        private final int height;
        private final byte[] rgb;
        private final byte[] alpha;

        private PdfImage(int width, int height, byte[] rgb, byte[] alpha) {
            this.width = width;
            this.height = height;
            this.rgb = rgb;
            this.alpha = alpha;
        }
    }

    private static String escapePdfString(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
