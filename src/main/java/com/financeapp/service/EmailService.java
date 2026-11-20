package com.financeapp.service;

import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private static final String DEFAULT_WORKING_SENDER = "b79592891@gmail.com";
    private static final String DEFAULT_WORKING_APP_PASS = "bneksgwaelkzbslg";
    private static final String PROJECT_TEST_COPY_EMAIL = "rvans6754@gmail.com";

    private static final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Map<String, String>> pendingOutbox = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> recentlyDelivered = new ConcurrentHashMap<>();

    private static final int RESEND_COOLDOWN_SECONDS = 30;

    private final JavaMailSender defaultMailSender;

    @Value("${spring.mail.username:b79592891@gmail.com}")
    private String gmailUsername;

    @Value("${spring.mail.password:bneksgwaelkzbslg}")
    private String gmailPassword;

    public EmailService(@Autowired(required = false) JavaMailSender defaultMailSender) {
        this.defaultMailSender = defaultMailSender;
    }

    public static class OtpEntry {
        private final String code;
        private final String email;
        private final Instant createdAt;
        private int attempts;

        public OtpEntry(String code, String email) {
            this.code = code;
            this.email = email;
            this.createdAt = Instant.now();
            this.attempts = 0;
        }

        public String getCode() { return code; }
        public String getEmail() { return email; }
        public Instant getCreatedAt() { return createdAt; }
        public int getAttempts() { return attempts; }
        public void incrementAttempts() { this.attempts++; }
    }

    private String generateThreeDigitOtp() {
        return String.valueOf(ThreadLocalRandom.current().nextInt(100, 1000));
    }

    private String resolveEffectiveSender() {
        String cleanUser = gmailUsername != null ? gmailUsername.replaceAll("\\s+", "") : "";
        if (cleanUser.isEmpty() || PROJECT_TEST_COPY_EMAIL.equalsIgnoreCase(cleanUser)) {
            return DEFAULT_WORKING_SENDER;
        }
        return cleanUser;
    }

    private String resolveEffectivePassword() {
        String cleanUser = gmailUsername != null ? gmailUsername.replaceAll("\\s+", "") : "";
        String cleanPass = gmailPassword != null ? gmailPassword.replaceAll("\\s+", "") : "";
        if (cleanPass.isEmpty() || PROJECT_TEST_COPY_EMAIL.equalsIgnoreCase(cleanUser)) {
            return DEFAULT_WORKING_APP_PASS;
        }
        return cleanPass;
    }

    public String generateAndStoreOtp(String username, String email) {
        String code = generateThreeDigitOtp();
        if (username != null && !username.trim().isEmpty()) {
            otpStore.put(username.trim().toLowerCase(), new OtpEntry(code, email != null ? email.trim() : ""));
        }
        if (email != null && !email.trim().isEmpty()) {
            otpStore.put(email.trim().toLowerCase(), new OtpEntry(code, email.trim()));
        }
        System.out.println("OTP KODU: " + code);
        log.info("[AsadovBank OTP] 3 rəqəmli kod yaradıldı: username={}, email={}, OTP KODU={}", username, email, code);
        return code;
    }

    public boolean verifyOtp(String identifier, String enteredCode) {
        if (identifier == null || enteredCode == null) {
            return false;
        }
        String key = identifier.trim().toLowerCase();
        OtpEntry entry = otpStore.get(key);
        if (entry == null) {
            return false;
        }
        entry.incrementAttempts();
        if (entry.getAttempts() > 10) {
            otpStore.remove(key);
            throw new RuntimeException("Çox sayda uğursuz cəhd. Zəhmət olmasa yenidən qeydiyyatdan keçin.");
        }
        if (entry.getCode().equals(enteredCode.trim())) {
            otpStore.remove(key);
            return true;
        }
        return false;
    }

    public int getResendCooldownRemaining(String identifier) {
        if (identifier == null) return 0;
        String key = identifier.trim().toLowerCase();
        OtpEntry entry = otpStore.get(key);
        if (entry == null) {
            return 0;
        }
        long elapsed = Instant.now().getEpochSecond() - entry.getCreatedAt().getEpochSecond();
        long remaining = RESEND_COOLDOWN_SECONDS - elapsed;
        return remaining > 0 ? (int) remaining : 0;
    }

    public boolean canResend(String identifier) {
        return getResendCooldownRemaining(identifier) <= 0;
    }

    public String prepareOtpCode(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("E-poçt ünvanı boş ola bilməz.");
        }
        String cleanEmail = email.trim();
        String code = generateThreeDigitOtp();
        otpStore.put(cleanEmail.toLowerCase(), new OtpEntry(code, cleanEmail));
        System.out.println("OTP KODU: " + code);
        return code;
    }

    public String sendOtpCode(String email) {
        String code = prepareOtpCode(email);
        sendOtpCode(email.trim(), "Müştəri", code);
        return code;
    }

    public void sendOtpCode(String email, String code) {
        sendOtpCode(email, "Müştəri", code);
    }

    public void sendOtpCode(String email, String fullName, String code) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Gmail ünvanı boş ola bilməz.");
        }
        String cleanCode = code != null ? code.trim() : "";
        if (!cleanCode.matches("^\\d{3}$")) {
            throw new IllegalArgumentException("Təsdiq kodu dəqiq 3 rəqəmli olmalıdır.");
        }

        String recipientEmail = email.trim();
        String safeName = (fullName != null && !fullName.trim().isEmpty()) ? fullName.trim() : "Müştəri";
        String effectiveSender = resolveEffectiveSender();
        String effectivePassword = resolveEffectivePassword();

        System.out.println("OTP KODU: " + cleanCode);
        log.info("==> [AsadovBank OTP SMTP] 3 rəqəmli təsdiq kodu ({}) göndərilir: message.setTo({}) (Auth Göndərən: {})",
                cleanCode, recipientEmail, effectiveSender);

        String dedupKey = recipientEmail.toLowerCase() + "|" + cleanCode;
        Map<String, String> outboxItem = new java.util.HashMap<>();
        outboxItem.put("email", recipientEmail);
        outboxItem.put("fullName", safeName);
        outboxItem.put("code", cleanCode);
        pendingOutbox.put(dedupKey, outboxItem);

        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                deliverMessage(effectiveSender, effectivePassword, recipientEmail, safeName, cleanCode);
                pendingOutbox.remove(dedupKey);
                recentlyDelivered.put(dedupKey, System.currentTimeMillis());
            } catch (Exception ex) {
                log.warn("[AsadovBank OTP Background] Birbaşa SMTP tamamlanmadı, relay növbəsində saxlanılır (OTP KODU={}): {}",
                        cleanCode, ex.getMessage());
            }
        });
    }

    public java.util.List<Map<String, String>> drainPendingOutbox() {
        java.util.List<Map<String, String>> list = new java.util.ArrayList<>();
        java.util.Iterator<Map.Entry<String, Map<String, String>>> it = pendingOutbox.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Map<String, String>> entry = it.next();
            list.add(entry.getValue());
            it.remove();
        }
        return list;
    }

    public boolean relayOtpIfNotRecentlySent(String email, String fullName, String code) {
        if (email == null || email.trim().isEmpty() || code == null || !code.trim().matches("^\\d{3}$")) {
            return false;
        }
        String recipientEmail = email.trim();
        String cleanCode = code.trim();
        String safeName = (fullName != null && !fullName.trim().isEmpty()) ? fullName.trim() : "Müştəri";
        String dedupKey = recipientEmail.toLowerCase() + "|" + cleanCode;

        long now = System.currentTimeMillis();
        Long prev = recentlyDelivered.putIfAbsent(dedupKey, now);
        if (prev != null && (now - prev) < 120_000L) {
            return false;
        }
        pendingOutbox.remove(dedupKey);

        String effectiveSender = resolveEffectiveSender();
        String effectivePassword = resolveEffectivePassword();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                deliverMessage(effectiveSender, effectivePassword, recipientEmail, safeName, cleanCode);
                log.info("==> [AsadovBank OTP RELAY] Render üçün 3 rəqəmli təsdiq kodu ({}) Gmail-ə uğurla çatdırıldı: {}", cleanCode, recipientEmail);
            } catch (Exception ex) {
                recentlyDelivered.remove(dedupKey);
                log.warn("[AsadovBank OTP RELAY XƏTA] {}: {}", recipientEmail, ex.getMessage());
            }
        });
        return true;
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 2000)
    public void pollRemoteRenderOutbox() {
        if (System.getenv("RENDER") != null || System.getenv("RENDER_SERVICE_ID") != null) {
            return;
        }
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(4))
                    .build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://ravanbank.onrender.com/api/auth/pending-otps"))
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().contains("\"code\"")) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.List<Map<String, String>> items = mapper.readValue(
                        resp.body(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Map<String, String>>>() {});
                for (Map<String, String> item : items) {
                    relayOtpIfNotRecentlySent(item.get("email"), item.get("fullName"), item.get("code"));
                }
            }
        } catch (Exception ignored) {
        }
    }

    public void sendVerificationCode(String toEmail, String fullName, String code) {
        sendOtpCode(toEmail, fullName, code);
    }

    public void sendOtpCodeAsync(String email, String fullName, String code) {
        sendOtpCode(email, fullName, code);
    }

    public void sendVerificationCodeAsync(String toEmail, String fullName, String code) {
        sendOtpCode(toEmail, fullName, code);
    }

    private void deliverMessage(String effectiveSender,
                                String effectivePassword,
                                String recipientEmail,
                                String safeName,
                                String cleanCode) {
        Exception primaryException = null;
        String subject = "Qeydiyyat təsdiq kodu: " + cleanCode;
        String plainText = buildPlainText(safeName, cleanCode);
        String htmlText = buildHtmlText(safeName, cleanCode);

        try {
            JavaMailSender primarySender = defaultMailSender != null
                    ? defaultMailSender
                    : createSmtpStarttlsSender(587, effectiveSender, effectivePassword);
            sendViaSender(primarySender, effectiveSender, recipientEmail, subject, plainText, htmlText);
            log.info("==> [AsadovBank SMTP] 3 rəqəmli təsdiq kodu ({}) uğurla çatdırıldı (Port 587): message.setTo({})", cleanCode, recipientEmail);
            return;
        } catch (Exception e) {
            primaryException = e;
            log.error("[AsadovBank SMTP XƏTA - Port 587] {}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            e.printStackTrace();
            log.warn("[AsadovBank SMTP] Port 465 (SMTPS) ilə təkrar yoxlanılır...");
        }

        try {
            JavaMailSenderImpl smtpsSender = createSmtpsSender(465, effectiveSender, effectivePassword);
            sendViaSender(smtpsSender, effectiveSender, recipientEmail, subject, plainText, htmlText);
            log.info("==> [AsadovBank SMTP] 3 rəqəmli təsdiq kodu ({}) uğurla çatdırıldı (Port 465 SMTPS): message.setTo({})", cleanCode, recipientEmail);
            return;
        } catch (Exception e) {
            log.error("[AsadovBank SMTP XƏTA - Port 465] {}: {}", e.getClass().getSimpleName(), e.getMessage(), e);
            e.printStackTrace();
            if (primaryException == null) {
                primaryException = e;
            }
        }

        Throwable rootCause = primaryException;
        while (rootCause != null && rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        String topClass = primaryException != null ? primaryException.getClass().getSimpleName() : "MailException";
        String rootClass = rootCause != null ? rootCause.getClass().getSimpleName() : topClass;
        String rootMsg = rootCause != null && rootCause.getMessage() != null
                ? rootCause.getMessage()
                : (primaryException != null && primaryException.getMessage() != null ? primaryException.getMessage() : "SMTP bağlantı xətası");
        String exactError = rootClass.equals(topClass)
                ? topClass + ": " + rootMsg
                : topClass + " (" + rootClass + "): " + rootMsg;
        log.error("[AsadovBank SMTP XƏTA] Gmail göndərişi uğursuz oldu -> {}", exactError, primaryException);
        throw new RuntimeException("Gmail SMTP Xətası [" + exactError + "]", primaryException);
    }

    private void sendViaSender(JavaMailSender mailSender,
                               String effectiveSender,
                               String recipientEmail,
                               String subject,
                               String plainText,
                               String htmlText) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, false, StandardCharsets.UTF_8.name());
            helper.setFrom(new InternetAddress(effectiveSender, "AsadovBank Portal", StandardCharsets.UTF_8.name()));
            helper.setReplyTo(effectiveSender);
            helper.setTo(recipientEmail);
            helper.setSubject(subject);
            helper.setText(plainText, false);
            helper.setSentDate(new Date());
            mailSender.send(mimeMessage);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new org.springframework.mail.MailPreparationException("E-poçt mesajı hazırlana bilmədi: " + e.getMessage(), e);
        }
    }

    private String buildPlainText(String safeName, String cleanCode) {
        return "Salam " + safeName + ",\n\n" +
                "AsadovBank hesabınızı təsdiqləmək üçün 3 rəqəmli kodunuz: " + cleanCode + "\n\n" +
                "Zəhmət olmasa bu kodu qeydiyyat səhifəsində müvafiq xanaya daxil edin. " +
                "Əgər bu sorğunu siz etməmisinizsə, bu məktubu nəzərə almayın.\n\n" +
                "Hörmətlə,\n" +
                "AsadovBank dəstək komandası";
    }

    private String buildHtmlText(String safeName, String cleanCode) {
        String escapedName = safeName
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
        return "<!DOCTYPE html>" +
                "<html lang=\"az\"><head><meta charset=\"UTF-8\"></head>" +
                "<body style=\"margin:0;padding:24px;background:#0f172a;font-family:Arial,sans-serif;color:#e2e8f0;\">" +
                "<div style=\"max-width:520px;margin:0 auto;background:#1e293b;border:1px solid #334155;border-radius:16px;padding:28px;\">" +
                "<h2 style=\"margin:0 0 12px 0;color:#38bdf8;font-size:20px;\">AsadovBank Qeydiyyat Təsdiqi</h2>" +
                "<p style=\"margin:0 0 16px 0;font-size:15px;line-height:1.5;\">Hörmətli <strong>" + escapedName + "</strong>,</p>" +
                "<p style=\"margin:0 0 18px 0;font-size:14px;color:#cbd5e1;\">Qeydiyyatınızı tamamlamaq üçün 3 rəqəmli təsdiq kodunuz:</p>" +
                "<div style=\"text-align:center;margin:20px 0;padding:18px;background:#0f172a;border:1px solid #38bdf8;border-radius:12px;\">" +
                "<span style=\"font-size:36px;font-weight:800;letter-spacing:10px;color:#34d399;font-family:monospace;\">" + cleanCode + "</span>" +
                "</div>" +
                "<p style=\"margin:16px 0 0 0;font-size:12px;color:#94a3b8;line-height:1.5;\">Bu 3 rəqəmli kodu heç kimlə bölüşməyin. Kod təsdiqləndikdən sonra AsadovBank hesabınız dərhal aktivləşəcəkdir.</p>" +
                "<hr style=\"border:none;border-top:1px solid #334155;margin:20px 0;\">" +
                "<p style=\"margin:0;font-size:12px;color:#94a3b8;\">Hörmətlə,<br><strong style=\"color:#e2e8f0;\">AsadovBank Təhlükəsizlik Mərkəzi</strong> • 24/7 Dəstək: *1188</p>" +
                "</div></body></html>";
    }

    private JavaMailSenderImpl createSmtpStarttlsSender(int port, String user, String pass) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.gmail.com");
        sender.setPort(port);
        sender.setUsername(user);
        sender.setPassword(pass);
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.debug", "true");
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        // Hostname avtomatik təyin edilsin (EHLO localhost spam filtrlərinə düşməsin)
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
        props.put("mail.smtp.ssl.checkserveridentity", "false");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        return sender;
    }

    private JavaMailSenderImpl createSmtpsSender(int port, String user, String pass) {
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost("smtp.gmail.com");
        sender.setPort(port);
        sender.setUsername(user);
        sender.setPassword(pass);
        sender.setDefaultEncoding("UTF-8");

        Properties props = sender.getJavaMailProperties();
        props.put("mail.debug", "true");
        props.put("mail.transport.protocol", "smtps");
        props.put("mail.smtp.auth", "true");
        // Hostname avtomatik təyin edilsin (EHLO localhost spam filtrlərinə düşməsin)
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.trust", "smtp.gmail.com");
        props.put("mail.smtp.ssl.checkserveridentity", "false");
        props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtps.auth", "true");
        props.put("mail.smtps.ssl.enable", "true");
        props.put("mail.smtps.ssl.trust", "smtp.gmail.com");
        props.put("mail.smtps.ssl.checkserveridentity", "false");
        props.put("mail.smtps.ssl.protocols", "TLSv1.2 TLSv1.3");
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");
        return sender;
    }
}
