package com.khoaluan.digishop.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Sends transactional emails via the Brevo (ex-Sendinblue) HTTP API (https://api.brevo.com/v3/smtp/email).
 *
 * We switched away from Gmail SMTP because Render's free web services block outbound
 * traffic on SMTP ports 25/465/587 (see https://render.com/changelog - "Free web services
 * will no longer allow outbound traffic to SMTP ports"), which made every OTP/order email
 * time out in production even though it worked fine locally. The Brevo API runs over plain
 * HTTPS (port 443), so it isn't affected by that block.
 *
 * Requires BREVO_API_KEY (Settings -> SMTP & API -> API Keys in the Brevo dashboard) and a
 * sender address that has been verified in Brevo (Transactional -> Email -> Senders).
 *
 * All send methods are @Async and swallow API failures internally (log only) so a
 * broken/misconfigured mailer never fails checkout or blocks the scheduler. They only ever
 * take plain data (String/records), never JPA entities - entities can carry LAZY fields tied to
 * the caller's transaction/session, which would throw LazyInitializationException once accessed
 * from this method's own @Async thread after that transaction has already closed.
 */
@Slf4j
@Service
public class EmailService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final String BREVO_API_URL = "https://api.brevo.com/v3/smtp/email";

    private final RestTemplate restTemplate;
    private final String fromAddress;
    private final String fromName;
    private final boolean mailEnabled;
    private final String brevoApiKey;

    public EmailService(
            @Value("${app.mail.enabled:false}") boolean mailEnabled,
            @Value("${app.mail.from}") String fromAddress,
            @Value("${app.mail.from-name}") String fromName,
            @Value("${app.mail.brevo-api-key:}") String brevoApiKey
    ) {
        this.restTemplate = new RestTemplate();
        this.mailEnabled = mailEnabled;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.brevoApiKey = brevoApiKey;
    }

    @Async
    public void sendOtpEmail(String toEmail, String otpCode, String purposeLabel) {
        String subject = "REGISTER".equals(purposeLabel)
                ? "DigiShop - Ma xac minh dang ky tai khoan"
                : "DigiShop - Ma dat lai mat khau";

        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto">
                  <h2 style="color:#0f172a">DigiShop</h2>
                  <p>Ma xac minh cua ban la:</p>
                  <div style="font-size:32px;font-weight:700;letter-spacing:8px;color:#0f172a;
                              background:#f1f5f9;padding:16px 24px;border-radius:12px;text-align:center">
                    %s
                  </div>
                  <p style="color:#64748b;font-size:14px;margin-top:16px">
                    Ma co hieu luc trong 5 phut. Khong chia se ma nay cho bat ky ai.
                  </p>
                </div>
                """.formatted(otpCode);

        sendHtml(toEmail, subject, html);
    }

    /** Gui email chao mung ngay sau khi tai khoan duoc xac thuc OTP dang ky thanh cong. */
    @Async
    public void sendRegistrationSuccessEmail(String toEmail, String userName) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Skip registration success email: no recipient email");
            return;
        }
        String subject = "DigiShop - Dang ky tai khoan thanh cong";
        String safeName = (userName == null || userName.isBlank()) ? "ban" : userName;

        String html = """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:0 auto">
                  <h2 style="color:#442a22">DigiShop</h2>
                  <p>Xin chao <strong>%s</strong>,</p>
                  <p>Tai khoan cua ban tren DigiShop da duoc xac thuc va kich hoat
                     <strong>thanh cong</strong> voi email <strong>%s</strong>.</p>
                  <p style="color:#64748b;font-size:14px;margin-top:16px">
                    Ban co the dang nhap ngay bay gio de bat dau mua sam hoac thue thiet bi may anh
                    yeu thich. Neu day khong phai la ban, vui long lien he voi chung toi de duoc ho tro.
                  </p>
                  <p style="margin-top:24px">Cam on ban da tin tuong DigiShop!</p>
                </div>
                """.formatted(safeName, toEmail);

        sendHtml(toEmail, subject, html);
    }

    /** UC-30: gui email xac nhan ngay sau khi don (mua hoac thue) duoc tao thanh cong. */
    @Async
    public void sendOrderConfirmationEmail(String toEmail, OrderEmailData data) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Skip order confirmation email for order {}: no recipient email", data.orderCode());
            return;
        }
        String subject = "DigiShop - Xac nhan don hang " + data.orderCode();

        StringBuilder itemsHtml = new StringBuilder();
        for (OrderEmailData.Line item : data.items()) {
            itemsHtml.append("""
                    <tr>
                      <td style="padding:8px;border-bottom:1px solid #e2e8f0">%s</td>
                      <td style="padding:8px;border-bottom:1px solid #e2e8f0;text-align:center">%d</td>
                      <td style="padding:8px;border-bottom:1px solid #e2e8f0;text-align:right">%s</td>
                    </tr>
                    """.formatted(item.productName(), item.quantity(), formatVnd(item.subtotal())));
        }

        String rentalInfoHtml = data.rental()
                ? """
                  <p><strong>Thoi gian thue:</strong> %s &rarr; %s (%d ngay)</p>
                  <p><strong>Tien coc (30%%):</strong> %s</p>
                  """.formatted(
                formatDate(data.rentalStartDate()),
                formatDate(data.rentalEndDate()),
                data.rentalDays() == null ? 0 : data.rentalDays(),
                formatVnd(data.depositAmount()))
                : "";

        String html = """
                <div style="font-family:Arial,sans-serif;max-width:560px;margin:0 auto">
                  <h2 style="color:#442a22">DigiShop</h2>
                  <p>Cam on ban da %s tai DigiShop. Don hang cua ban da duoc ghi nhan va dang cho xac nhan.</p>
                  <p><strong>Ma don:</strong> %s</p>
                  <p><strong>Nguoi nhan:</strong> %s - %s</p>
                  <p><strong>Dia chi:</strong> %s</p>
                  %s
                  <table style="width:100%%;border-collapse:collapse;margin-top:12px">
                    <thead>
                      <tr style="background:#f1efd9">
                        <th style="padding:8px;text-align:left">San pham</th>
                        <th style="padding:8px;text-align:center">SL</th>
                        <th style="padding:8px;text-align:right">Thanh tien</th>
                      </tr>
                    </thead>
                    <tbody>%s</tbody>
                  </table>
                  <p style="text-align:right;font-size:18px;font-weight:700;margin-top:12px;color:#442a22">
                    Tong cong: %s
                  </p>
                  <p style="color:#64748b;font-size:13px;margin-top:24px">
                    Chung toi se lien he xac nhan trong thoi gian som nhat. Cam on ban da tin tuong DigiShop!
                  </p>
                </div>
                """.formatted(
                data.rental() ? "thue thiet bi" : "mua hang",
                data.orderCode(), data.recipientName(), data.recipientPhone(),
                data.shippingAddress(), rentalInfoHtml, itemsHtml, formatVnd(data.totalAmount()));

        sendHtml(toEmail, subject, html);
    }

    /** UC-30: nhac khach truoc ngay het han thue, goi tu RentalReminderScheduler. */
    @Async
    public void sendRentalReturnReminderEmail(String toEmail, String orderCode, LocalDate rentalEndDate, List<OrderEmailData.Line> items) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Skip rental reminder email for order {}: no recipient email", orderCode);
            return;
        }
        String subject = "DigiShop - Nhac lich tra may, don " + orderCode;

        StringBuilder itemsHtml = new StringBuilder();
        for (OrderEmailData.Line item : items) {
            itemsHtml.append("<li>%s (x%d)</li>".formatted(item.productName(), item.quantity()));
        }

        String html = """
                <div style="font-family:Arial,sans-serif;max-width:560px;margin:0 auto">
                  <h2 style="color:#442a22">DigiShop</h2>
                  <p>Nhac ban: don thue <strong>%s</strong> sap den han tra may vao ngay
                     <strong>%s</strong>.</p>
                  <ul>%s</ul>
                  <p>Vui long tra may dung han tai cua hang de duoc hoan coc day du.
                     Tra tre han co the bi tru mot phan tien coc.</p>
                  <p style="color:#64748b;font-size:13px;margin-top:24px">Cam on ban da su dung dich vu cua DigiShop!</p>
                </div>
                """.formatted(orderCode, formatDate(rentalEndDate), itemsHtml);

        sendHtml(toEmail, subject, html);
    }

    /** Nhac khach ve don thue da qua han khong tra, goi tu RentalReminderScheduler. */
    @Async
    public void sendRentalOverdueReminderEmail(String toEmail, String orderCode, LocalDate rentalEndDate, List<OrderEmailData.Line> items) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("Skip rental overdue reminder email for order {}: no recipient email", orderCode);
            return;
        }
        String subject = "DigiShop - CANH BAO: Don thue qua han, don " + orderCode;

        StringBuilder itemsHtml = new StringBuilder();
        for (OrderEmailData.Line item : items) {
            itemsHtml.append("<li>%s (x%d)</li>".formatted(item.productName(), item.quantity()));
        }

        String html = """
                <div style="font-family:Arial,sans-serif;max-width:560px;margin:0 auto">
                  <h2 style="color:#991b1b">DigiShop</h2>
                  <p style="color:#991b1b;font-weight:bold">CANH BAO: Don thue <strong>%s</strong> da qua han tra may!
                     Han tra duoc: <strong>%s</strong>.</p>
                  <ul>%s</ul>
                  <p style="color:#dc2626;font-weight:bold">Ban da phai tra may tu ngay %s. Vui long tra may ngay lap tuc.</p>
                  <p>Tra tre han se bi tru mot phan tien coc. Lien he voi chung toi ngay de thoa thuan lich tra.</p>
                  <p style="color:#64748b;font-size:13px;margin-top:24px">Cam on ban da su dung dich vu cua DigiShop!</p>
                </div>
                """.formatted(orderCode, formatDate(rentalEndDate), itemsHtml, formatDate(rentalEndDate));

        sendHtml(toEmail, subject, html);
    }

    private void sendHtml(String toEmail, String subject, String html) {
        if (!mailEnabled) {
            log.info("Skip sending email to {} with subject '{}': app.mail.enabled=false.", toEmail, subject);
            return;
        }

        if (fromAddress == null || fromAddress.isBlank()) {
            log.warn("Skip sending email to {} with subject '{}': app.mail.from is not configured.", toEmail, subject);
            return;
        }

        if (brevoApiKey == null || brevoApiKey.isBlank()) {
            log.warn("Skip sending email to {} with subject '{}': app.mail.brevo-api-key (BREVO_API_KEY) is not configured.", toEmail, subject);
            return;
        }

        try {
            Map<String, Object> sender = new HashMap<>();
            sender.put("email", fromAddress);
            sender.put("name", fromName);

            Map<String, Object> recipient = new HashMap<>();
            recipient.put("email", toEmail);

            Map<String, Object> body = new HashMap<>();
            body.put("sender", sender);
            body.put("to", List.of(recipient));
            body.put("subject", subject);
            body.put("htmlContent", html);

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.set("Accept", "application/json");
            headers.set("api-key", brevoApiKey);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            var response = restTemplate.postForEntity(BREVO_API_URL, request, String.class);

            HttpStatusCode status = response.getStatusCode();
            if (status.is2xxSuccessful()) {
                log.info("Sent email to {} with subject '{}' via Brevo (status {}).", toEmail, subject, status.value());
            } else {
                log.error("Brevo returned non-success status {} sending email to {}: {}", status.value(), toEmail, response.getBody());
            }
        } catch (RestClientException e) {
            // Don't leak API failures as a generic 500 to the client, but do log
            // loudly since a broken mailer would otherwise silently break notifications.
            log.error("Failed to send email to {} via Brevo: {}", toEmail, e.getMessage(), e);
        }
    }

    private String formatVnd(BigDecimal amount) {
        if (amount == null) amount = BigDecimal.ZERO;
        NumberFormat fmt = NumberFormat.getInstance(new Locale("vi", "VN"));
        return fmt.format(amount) + " VND";
    }

    private String formatDate(LocalDate date) {
        return date == null ? "-" : date.format(DATE_FMT);
    }
}