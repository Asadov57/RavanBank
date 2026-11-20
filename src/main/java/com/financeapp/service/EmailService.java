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

    public static final String GMAIL_NOT_FOUND_MESSAGE =
            "Belə bir Gmail mövcud deyil! Zəhmət olmasa mövcud olan düzgün Gmail ünvanı daxil edin.";

    public enum GmailVerifyResult {
        EXISTS,
        NOT_FOUND,
        UNREACHABLE
    }

    private static final ConcurrentHashMap<String, OtpEntry> otpStore = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Map<String, String>> pendingOutbox = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, java.util.concurrent.CompletableFuture<String>> pendingDeliveryFutures = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Long> recentlyDelivered = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<String, Boolean> verifiedGmailCache = new ConcurrentHashMap<>();

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

    public static void validateGmailSyntaxOrThrow(String email) {
        if (email == null || email.trim().isEmpty()) {
            throw new IllegalArgumentException("Gmail ünvanı boş ola bilməz.");
        }
        String clean = email.trim().toLowerCase();
        int atIdx = clean.indexOf('@');
        if (atIdx <= 0 || atIdx != clean.lastIndexOf('@')) {
            throw new IllegalArgumentException(GMAIL_NOT_FOUND_MESSAGE);
        }
        String local = clean.substring(0, atIdx);
        String domain = clean.substring(atIdx + 1);
        if (!"gmail.com".equals(domain) && !"googlemail.com".equals(domain)) {
            throw new IllegalArgumentException(GMAIL_NOT_FOUND_MESSAGE);
        }
        if (local.length() < 6 || local.length() > 30) {
            throw new IllegalArgumentException(GMAIL_NOT_FOUND_MESSAGE);
        }
        if (local.startsWith(".") || local.endsWith(".") || local.contains("..")) {
            throw new IllegalArgumentException(GMAIL_NOT_FOUND_MESSAGE);
        }
        if (!local.matches("^[a-z0-9.]+$")) {
            throw new IllegalArgumentException(GMAIL_NOT_FOUND_MESSAGE);
        }
        if (DEFAULT_WORKING_SENDER.equalsIgnoreCase(clean)) {
            throw new IllegalArgumentException(GMAIL_NOT_FOUND_MESSAGE);
        }
    }

    public GmailVerifyResult checkGmailExistsOnGoogleMx(String email) {
        try {
            validateGmailSyntaxOrThrow(email);
        } catch (IllegalArgumentException ex) {
            return GmailVerifyResult.NOT_FOUND;
        }
        String clean = email.trim().toLowerCase();
        Boolean cached = verifiedGmailCache.get(clean);
        if (cached != null) {
            return cached ? GmailVerifyResult.EXISTS : GmailVerifyResult.NOT_FOUND;
        }

        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress("gmail-smtp-in.l.google.com", 25), 3500);
            socket.setSoTimeout(3500);
            java.io.BufferedReader reader = new java.io.BufferedReader(
                    new java.io.InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            java.io.PrintWriter writer = new java.io.PrintWriter(
                    new java.io.OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);

            String greeting = readSmtpResponse(reader);
            if (greeting == null || !greeting.startsWith("220")) {
                return GmailVerifyResult.UNREACHABLE;
            }
            writer.print("HELO asadovbank.com\r\n");
            writer.flush();
            String heloResp = readSmtpResponse(reader);
            if (heloResp == null || !heloResp.startsWith("250")) {
                return GmailVerifyResult.UNREACHABLE;
            }
            writer.print("MAIL FROM:<" + DEFAULT_WORKING_SENDER + ">\r\n");
            writer.flush();
            String mailResp = readSmtpResponse(reader);
            if (mailResp == null || !mailResp.startsWith("250")) {
                return GmailVerifyResult.UNREACHABLE;
            }
            writer.print("RCPT TO:<" + clean + ">\r\n");
            writer.flush();
            String rcptResp = readSmtpResponse(reader);
            writer.print("QUIT\r\n");
            writer.flush();

            if (rcptResp != null) {
                if (rcptResp.startsWith("250")) {
                    verifiedGmailCache.put(clean, true);
                    return GmailVerifyResult.EXISTS;
                }
                if (rcptResp.startsWith("550") || rcptResp.startsWith("551")
                        || rcptResp.startsWith("552") || rcptResp.startsWith("553")
                        || rcptResp.startsWith("554") || rcptResp.startsWith("5")) {
                    verifiedGmailCache.put(clean, false);
                    log.warn("[AsadovBank Gmail Check] Mövcud olmayan Gmail rədd edildi ({}): {}", clean, rcptResp);
                    return GmailVerifyResult.NOT_FOUND;
                }
            }
            return GmailVerifyResult.UNREACHABLE;
        } catch (Exception e) {
            return GmailVerifyResult.UNREACHABLE;
        }
    }

    private String readSmtpResponse(java.io.BufferedReader reader) throws java.io.IOException {
        String lastLine = null;
        String line;
        while ((line = reader.readLine()) != null) {
            lastLine = line;
            if (line.length() >= 4 && line.charAt(3) == ' ') {
                break;
            }
        }
        return lastLine;
    }

    public void sendOtpCode(String email, String fullName, String code) {
        validateGmailSyntaxOrThrow(email);
        String cleanCode = code != null ? code.trim() : "";
        if (!cleanCode.matches("^\\d{3}$")) {
            throw new IllegalArgumentException("Təsdiq kodu dəqiq 3 rəqəmli olmalıdır.");
        }

        String recipientEmail = email.trim();
        String safeName = (fullName != null && !fullName.trim().isEmpty()) ? fullName.trim() : "Müştəri";
        String effectiveSender = resolveEffectiveSender();
        String effectivePassword = resolveEffectivePassword();

        GmailVerifyResult mxCheck = checkGmailExistsOnGoogleMx(recipientEmail);
        if (mxCheck == GmailVerifyResult.NOT_FOUND) {
            throw new IllegalArgumentException(GMAIL_NOT_FOUND_MESSAGE);
        }

        String dedupKey = recipientEmail.toLowerCase() + "|" + cleanCode;

        if (mxCheck == GmailVerifyResult.EXISTS) {
            System.out.println("OTP KODU: " + cleanCode);
            log.info("==> [AsadovBank OTP SMTP] Mövcud Gmail təsdiqləndi ({}), 3 rəqəmli kod ({}) göndərilir...",
                    recipientEmail, cleanCode);
            recentlyDelivered.put(dedupKey, System.currentTimeMillis());
            java.util.concurrent.CompletableFuture.runAsync(() -> {
                try {
                    deliverMessage(effectiveSender, effectivePassword, recipientEmail, safeName, cleanCode);
                } catch (Exception ex) {
                    recentlyDelivered.remove(dedupKey);
                    log.warn("[AsadovBank OTP Background] SMTP göndəriş xətası: {}", ex.getMessage());
                }
            });
            return;
        }

        // Render mühiti: port 25/587 bağlı olduqda lokal SMTP körpüsü vasitəsilə Gmail-in mövcudluğunu yoxla və göndər
        String jobId = java.util.UUID.randomUUID().toString();
        java.util.concurrent.CompletableFuture<String> future = new java.util.concurrent.CompletableFuture<>();
        pendingDeliveryFutures.put(jobId, future);

        Map<String, String> outboxItem = new java.util.HashMap<>();
        outboxItem.put("jobId", jobId);
        outboxItem.put("email", recipientEmail);
        outboxItem.put("fullName", safeName);
        outboxItem.put("code", cleanCode);
        pendingOutbox.put(dedupKey, outboxItem);

        try {
            String bridgeStatus = future.get(7, java.util.concurrent.TimeUnit.SECONDS);
            if ("INVALID_GMAIL".equalsIgnoreCase(bridgeStatus)) {
                pendingOutbox.remove(dedupKey);
                throw new IllegalArgumentException(GMAIL_NOT_FOUND_MESSAGE);
            }
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception ignored) {
            // Lokal körpü 7 saniyə ərzində cavab vermədikdə növbədə saxlanılır
        } finally {
            pendingDeliveryFutures.remove(jobId);
        }
    }

    public void reportOtpDeliveryResult(String jobId, String status) {
        if (jobId == null) return;
        java.util.concurrent.CompletableFuture<String> future = pendingDeliveryFutures.remove(jobId);
        if (future != null) {
            future.complete(status != null ? status : "DELIVERED");
        }
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

    public String verifyAndRelayOtp(String email, String fullName, String code) {
        if (email == null || email.trim().isEmpty() || code == null || !code.trim().matches("^\\d{3}$")) {
            return "INVALID_GMAIL";
        }
        String recipientEmail = email.trim();
        GmailVerifyResult mxCheck = checkGmailExistsOnGoogleMx(recipientEmail);
        if (mxCheck == GmailVerifyResult.NOT_FOUND) {
            log.warn("==> [AsadovBank OTP RELAY] Mövcud olmayan Gmail rədd edildi (b79592891@gmail.com-a kod göndərilmədi): {}", recipientEmail);
            return "INVALID_GMAIL";
        }

        String cleanCode = code.trim();
        String safeName = (fullName != null && !fullName.trim().isEmpty()) ? fullName.trim() : "Müştəri";
        String dedupKey = recipientEmail.toLowerCase() + "|" + cleanCode;

        long now = System.currentTimeMillis();
        Long prev = recentlyDelivered.putIfAbsent(dedupKey, now);
        if (prev != null && (now - prev) < 120_000L) {
            return "ALREADY_SENT";
        }
        pendingOutbox.remove(dedupKey);

        String effectiveSender = resolveEffectiveSender();
        String effectivePassword = resolveEffectivePassword();
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                deliverMessage(effectiveSender, effectivePassword, recipientEmail, safeName, cleanCode);
                log.info("==> [AsadovBank OTP RELAY] 3 rəqəmli təsdiq kodu ({}) Gmail-ə uğurla çatdırıldı: {}", cleanCode, recipientEmail);
            } catch (Exception ex) {
                recentlyDelivered.remove(dedupKey);
                log.warn("[AsadovBank OTP RELAY XƏTA] {}: {}", recipientEmail, ex.getMessage());
            }
        });
        return "DELIVERED";
    }

    public boolean relayOtpIfNotRecentlySent(String email, String fullName, String code) {
        return "DELIVERED".equals(verifyAndRelayOtp(email, fullName, code));
    }

    @org.springframework.scheduling.annotation.Scheduled(fixedDelay = 1000)
    public void pollRemoteRenderOutbox() {
        if (System.getenv("RENDER") != null || System.getenv("RENDER_SERVICE_ID") != null) {
            return;
        }
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(3))
                    .build();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://ravanbank.onrender.com/api/auth/pending-otps"))
                    .timeout(java.time.Duration.ofSeconds(4))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200 && resp.body() != null && resp.body().contains("\"code\"")) {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                java.util.List<Map<String, String>> items = mapper.readValue(
                        resp.body(),
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Map<String, String>>>() {});
                for (Map<String, String> item : items) {
                    String status = verifyAndRelayOtp(item.get("email"), item.get("fullName"), item.get("code"));
                    String jobId = item.get("jobId");
                    if (jobId != null && !jobId.isEmpty()) {
                        String form = "jobId=" + java.net.URLEncoder.encode(jobId, StandardCharsets.UTF_8)
                                + "&status=" + java.net.URLEncoder.encode(status, StandardCharsets.UTF_8);
                        java.net.http.HttpRequest callbackReq = java.net.http.HttpRequest.newBuilder()
                                .uri(java.net.URI.create("https://ravanbank.onrender.com/api/auth/otp-delivery-result"))
                                .timeout(java.time.Duration.ofSeconds(4))
                                .header("Content-Type", "application/x-www-form-urlencoded")
                                .POST(java.net.http.HttpRequest.BodyPublishers.ofString(form))
                                .build();
                        client.sendAsync(callbackReq, java.net.http.HttpResponse.BodyHandlers.ofString());
                    }
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
