package com.bhagwaniHardware.inventory.services;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.Normalizer;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

import com.bhagwaniHardware.inventory.model.Bill;
import com.bhagwaniHardware.inventory.model.SoldItem;

@Service
public class BillPdfService {
    private static final int PAGE_HEIGHT = 842;
    private static final int PAGE_WIDTH = 595;
    private static final int LEFT = 30;
    private static final int RIGHT = 565;
    private static final int ROW_HEIGHT = 42;
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
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
        int nextItem = 0;
        boolean firstPage = true;

        do {
            PageCanvas page = new PageCanvas();
            int y = drawHeader(page, bill, firstPage);
            y = drawTableHeader(page, y);

            while (nextItem < items.size() && y - ROW_HEIGHT >= 190) {
                SoldItem item = items.get(nextItem++);
                drawItemRow(page, item, y, nextItem % 2 == 0);
                y -= ROW_HEIGHT;
            }

            boolean lastPage = nextItem >= items.size();
            if (lastPage) {
                drawSummary(page, bill, y - 13);
            }
            drawFooter(page);
            pageStreams.add(page.bytes());
            firstPage = false;
        } while (nextItem < items.size());

        return writePdf(pageStreams, bill.getBillNo());
    }

    private static int drawHeader(PageCanvas page, Bill bill, boolean firstPage) {
        if (firstPage) {
            page.strokeRectangle(LEFT, 727, 62, 62, NAVY, 3);
            page.centeredText(LEFT + 31, 750, 22, NAVY, "BH", true, true);
            page.serifText(LEFT, 704, 15, NAVY, "BHAGWANI HARDWARE", true);
            page.text(LEFT, 689, 8, DARK, "HARDWARE  |  INVENTORY", false);

            String title = isReturnBill(bill) ? "RETURN BILL" : "BILLING STATEMENT";
            page.rightText(RIGHT, 783, 18, NAVY, title, true, true);
            page.rightText(RIGHT, 763, 18, NAVY, isReturnBill(bill) ? "STATEMENT" : "", true, true);
            page.rightText(RIGHT, 729, 9, DARK, "BILL NO: " + safeText(bill.getBillNo()), true, false);
            page.rightText(RIGHT, 713, 9, DARK, "ISSUE DATE: "
                    + (bill.getCreatedAt() == null ? "" : DATE_FORMAT.format(bill.getCreatedAt())), true, false);

            page.line(LEFT, 650, RIGHT, 650, BLUE, 1.2f);
            page.serifText(LEFT, 617, 11, NAVY, "BILL TO", true);
            page.text(LEFT, 596, 9, DARK, truncate(safeText(bill.getCustomerName()), 65), false);
            String mobile = safeText(bill.getMobileNumber());
            if (!mobile.isBlank()) {
                page.text(LEFT, 580, 9, DARK, truncate(mobile, 65), false);
            }
            String address = safeText(bill.getAddress() == null ? "" :
                    bill.getAddress().replace('\n', ' ').replace('\r', ' '));
            if (!address.isBlank()) {
                page.text(LEFT, mobile.isBlank() ? 580 : 564, 9, DARK, truncate(address, 82), false);
            }
            return 510;
        }

        page.serifText(LEFT, 790, 15, NAVY, "BHAGWANI HARDWARE", true);
        page.rightText(RIGHT, 790, 14, NAVY,
                (isReturnBill(bill) ? "RETURN BILL" : "BILLING STATEMENT") + "  |  "
                        + safeText(bill.getBillNo()), true, true);
        page.line(LEFT, 773, RIGHT, 773, BLUE, 1.2f);
        return 750;
    }

    private static int drawTableHeader(PageCanvas page, int y) {
        int headerHeight = 40;
        page.rectangle(LEFT, y - headerHeight, RIGHT - LEFT, headerHeight, BLUE);
        page.text(LEFT + 8, y - 24, 9, WHITE, "PRODUCT ID", true);
        page.centeredText(325, y - 24, 9, WHITE, "QTY", true, false);
        page.rightText(447, y - 24, 9, WHITE, "RATE", true, false);
        page.rightText(RIGHT - 8, y - 24, 9, WHITE, "AMOUNT", true, false);
        drawTableDividers(page, y, y - headerHeight);
        return y - headerHeight;
    }

    private static void drawTableDividers(PageCanvas page, int top, int bottom) {
        page.line(300, top, 300, bottom, "0.82 0.85 0.97", 0.6f);
        page.line(355, top, 355, bottom, "0.82 0.85 0.97", 0.6f);
        page.line(455, top, 455, bottom, "0.82 0.85 0.97", 0.6f);
    }

    private static void drawItemRow(PageCanvas page, SoldItem item, int top, boolean shaded) {
        int bottom = top - ROW_HEIGHT;
        page.rectangle(LEFT, bottom, RIGHT - LEFT, ROW_HEIGHT, shaded ? PALE_BLUE : WHITE);
        drawTableDividers(page, top, bottom);
        page.text(LEFT + 8, bottom + 17, 9, DARK,
                truncate(safeText(item.getInventoryItemId()), 43), false);
        page.centeredText(325, bottom + 17, 9, DARK, Integer.toString(item.getCount()), false, false);
        page.rightText(447, bottom + 17, 9, DARK, money(item.getSellingPrice()), false, false);
        page.rightText(RIGHT - 8, bottom + 17, 9, DARK,
                money(item.getSellingPrice() * item.getCount()), false, false);
        page.line(LEFT, bottom, RIGHT, bottom, PALE_GRAY, 0.5f);
    }

    private static void drawSummary(PageCanvas page, Bill bill, int top) {
        page.serifText(LEFT, top - 10, 10, NAVY, "THANK YOU", true);
        page.text(LEFT, top - 29, 8, DARK, "Thank you for shopping with Bhagwani Hardware.", false);
        page.text(LEFT, top - 43, 8, DARK, "We appreciate your business.", false);

        int totalsLeft = 350;
        page.text(totalsLeft, top - 10, 9, DARK, "SUBTOTAL", true);
        page.rightText(RIGHT - 8, top - 10, 9, DARK,
                money(bill.getTotal() + bill.getDiscount()), true, false);
        page.text(totalsLeft, top - 28, 9, DARK, "DISCOUNT", true);
        page.rightText(RIGHT - 8, top - 28, 9, DARK, "-" + money(bill.getDiscount()), true, false);

        int boxBottom = top - 91;
        page.rectangle(totalsLeft - 10, boxBottom, RIGHT - totalsLeft + 10, 50, NAVY);
        page.text(totalsLeft + 2, boxBottom + 19, 9, WHITE, "AMOUNT DUE", true);
        page.rightText(RIGHT - 11, boxBottom + 18, 13, WHITE, money(bill.getTotal()), true, false);
    }

    private static void drawFooter(PageCanvas page) {
        page.rectangle(0, 0, PAGE_WIDTH, 28, "0.10 0.11 0.17");
        page.centeredText(PAGE_WIDTH / 2, 10, 8, WHITE,
                "BHAGWANI HARDWARE  |  THANK YOU FOR YOUR BUSINESS", false, false);
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

    private static byte[] writePdf(List<byte[]> pageStreams, String billNumber) throws IOException {
        int pageCount = pageStreams.size();
        int firstPageObject = 7;
        int infoObject = firstPageObject + pageCount * 2;
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
            objects[pageObject] = ascii("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 "
                    + PAGE_WIDTH + " " + PAGE_HEIGHT
                    + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R /F4 6 0 R >> >> /Contents "
                    + streamObject + " 0 R >>");
            objects[streamObject] = streamObject(pageStreams.get(index));
        }
        objects[infoObject] = ascii("<< /Title (" + escapePdfString("Bill " + safeText(billNumber))
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

    private static String escapePdfString(String value) {
        return value.replace("\\", "\\\\").replace("(", "\\(").replace(")", "\\)");
    }
}
