package com.khoaluan.digishop.service;

import com.khoaluan.digishop.entity.Order;
import com.khoaluan.digishop.entity.OrderItem;
import com.khoaluan.digishop.entity.OrderType;
import com.khoaluan.digishop.exception.ApiException;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Xuất hoá đơn / biên nhận PDF cho đơn hàng (UC-27).
 *
 * Dùng Apache PDFBox với font TrueType nhúng sẵn (NotoSans) thay vì font chuẩn
 * Helvetica/Times, vì các font 14 chuẩn của PDF KHÔNG hiển thị được tiếng Việt
 * có dấu. Font đặt tại src/main/resources/fonts/NotoSans-Regular.ttf và
 * NotoSans-Bold.ttf — nếu muốn đổi font khác, thay 2 file này (giữ nguyên tên)
 * hoặc sửa hằng số FONT_REGULAR_PATH / FONT_BOLD_PATH bên dưới.
 */
@Slf4j
@Service
public class InvoiceService {

    private static final String FONT_REGULAR_PATH = "fonts/NotoSans-Regular.ttf";
    private static final String FONT_BOLD_PATH = "fonts/NotoSans-Bold.ttf";

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault());
    private static final DecimalFormat MONEY_FMT = new DecimalFormat("#,##0");

    private static final float MARGIN = 50f;
    private static final float LINE_GAP = 16f;

    public byte[] generateInvoice(Order order) {
        try (PDDocument document = new PDDocument()) {
            PDFont fontRegular = loadFont(document, FONT_REGULAR_PATH);
            PDFont fontBold = loadFont(document, FONT_BOLD_PATH);

            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            float pageWidth = page.getMediaBox().getWidth();
            float y = page.getMediaBox().getHeight() - MARGIN;

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                y = writeCentered(cs, fontBold, 20, "DIGISHOP", pageWidth, y);
                y -= 6;
                String title = order.getOrderType() == OrderType.RENTAL
                        ? "HOÁ ĐƠN THUÊ THIẾT BỊ"
                        : "HOÁ ĐƠN BÁN HÀNG";
                y = writeCentered(cs, fontBold, 14, title, pageWidth, y);
                y -= 20;

                y = writeLine(cs, fontRegular, 11, y, "Mã đơn hàng: " + order.getOrderCode());
                y = writeLine(cs, fontRegular, 11, y, "Ngày đặt: " + DATE_FMT.format(order.getCreatedAt()));
                y = writeLine(cs, fontRegular, 11, y, "Khách hàng: " + order.getRecipientName());
                y = writeLine(cs, fontRegular, 11, y, "Điện thoại: " + order.getRecipientPhone());
                y = writeLine(cs, fontRegular, 11, y, "Địa chỉ: " + order.getShippingAddress());

                if (order.getOrderType() == OrderType.RENTAL && order.getRentalStartDate() != null) {
                    y = writeLine(cs, fontRegular, 11, y,
                            "Thời gian thuê: " + order.getRentalStartDate() + " đến " + order.getRentalEndDate()
                                    + " (" + order.getRentalDays() + " ngày)");
                }
                y -= 10;

                // --- Bảng sản phẩm ---
                float colProduct = MARGIN;
                float colPrice = MARGIN + 230;
                float colQty = MARGIN + 340;
                float colSubtotal = MARGIN + 400;
                float tableRight = pageWidth - MARGIN;

                writeAt(cs, fontBold, 10, colProduct, y, "Sản phẩm");
                writeAt(cs, fontBold, 10, colPrice, y, "Đơn giá");
                writeAt(cs, fontBold, 10, colQty, y, "SL");
                writeAt(cs, fontBold, 10, colSubtotal, y, "Thành tiền");
                y -= 6;
                y = drawHorizontalLine(cs, MARGIN, y, tableRight);
                y -= 14;

                for (OrderItem item : order.getItems()) {
                    writeAt(cs, fontRegular, 10, colProduct, y, truncate(item.getProductName(), 34));
                    writeAt(cs, fontRegular, 10, colPrice, y, MONEY_FMT.format(item.getUnitPrice()) + "đ");
                    writeAt(cs, fontRegular, 10, colQty, y, String.valueOf(item.getQuantity()));
                    writeAt(cs, fontRegular, 10, colSubtotal, y, MONEY_FMT.format(item.getSubtotal()) + "đ");
                    y -= 15;
                }

                y -= 3;
                y = drawHorizontalLine(cs, MARGIN, y, tableRight);
                y -= 18;

                // --- Tổng kết ---
                y = writeRightAligned(cs, fontRegular, 11, pageWidth,
                        "Tạm tính: " + MONEY_FMT.format(order.getSubtotalAmount()) + "đ", y);

                if (order.getDiscountAmount() != null && order.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0) {
                    String label = order.getPromotionCode() != null
                            ? "Giảm giá (" + order.getPromotionCode() + "): -"
                            : "Giảm giá: -";
                    y = writeRightAligned(cs, fontRegular, 11, pageWidth,
                            label + MONEY_FMT.format(order.getDiscountAmount()) + "đ", y);
                }

                if (order.getDepositAmount() != null && order.getDepositAmount().compareTo(BigDecimal.ZERO) > 0) {
                    y = writeRightAligned(cs, fontRegular, 11, pageWidth,
                            "Tiền cọc: " + MONEY_FMT.format(order.getDepositAmount()) + "đ", y);
                }

                y -= 4;
                y = writeRightAligned(cs, fontBold, 13, pageWidth,
                        "Tổng cộng: " + MONEY_FMT.format(order.getTotalAmount()) + "đ", y);

                y -= 35;
                writeCentered(cs, fontRegular, 10, "Cảm ơn quý khách đã mua sắm tại DigiShop!", pageWidth, y);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            log.error("Không thể tạo hoá đơn PDF cho đơn {}", order.getOrderCode(), e);
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "INVOICE_GENERATION_FAILED",
                    "Không thể tạo hoá đơn, vui lòng thử lại sau");
        }
    }

    private PDFont loadFont(PDDocument document, String classpathPath) throws IOException {
        try (InputStream is = new ClassPathResource(classpathPath).getInputStream()) {
            return PDType0Font.load(document, is);
        }
    }

    private float writeLine(PDPageContentStream cs, PDFont font, float size, float y, String text) throws IOException {
        writeAt(cs, font, size, MARGIN, y, text);
        return y - LINE_GAP;
    }

    private void writeAt(PDPageContentStream cs, PDFont font, float size, float x, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private float writeCentered(PDPageContentStream cs, PDFont font, float size, String text, float pageWidth, float y) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000 * size;
        float x = (pageWidth - textWidth) / 2;
        writeAt(cs, font, size, x, y, text);
        return y - (size + 6);
    }

    private float writeRightAligned(PDPageContentStream cs, PDFont font, float size, float pageWidth, String text, float y) throws IOException {
        float textWidth = font.getStringWidth(text) / 1000 * size;
        float x = pageWidth - MARGIN - textWidth;
        writeAt(cs, font, size, x, y, text);
        return y - (size + 6);
    }

    private float drawHorizontalLine(PDPageContentStream cs, float x1, float y, float x2) throws IOException {
        cs.setLineWidth(0.5f);
        cs.moveTo(x1, y);
        cs.lineTo(x2, y);
        cs.stroke();
        return y;
    }

    private String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() > max ? s.substring(0, max - 1) + "…" : s;
    }
}
