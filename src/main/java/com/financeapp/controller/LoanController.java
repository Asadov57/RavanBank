package com.financeapp.controller;

import com.financeapp.model.*;
import com.financeapp.repository.*;
import com.financeapp.service.AuditLogService;
import com.financeapp.service.BankVaultService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.Principal;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.regex.Pattern;

@Controller
public class LoanController {

    private final LoanApplicationRepository loanApplicationRepository;
    private final CustomerInquiryRepository customerInquiryRepository;
    private final SecurityNotificationRepository securityNotificationRepository;
    private final CustomerMessageRepository customerMessageRepository;
    private final UserRepository userRepository;
    private final AccountRepository accountRepository;
    private final BankVaultService bankVaultService;
    private final AuditLogService auditLogService;
    private final PanelMessageRepository panelMessageRepository;

    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^[A-Za-z0-9]{6,16}$");

    public LoanController(LoanApplicationRepository loanApplicationRepository,
                          CustomerInquiryRepository customerInquiryRepository,
                          SecurityNotificationRepository securityNotificationRepository,
                          CustomerMessageRepository customerMessageRepository,
                          UserRepository userRepository,
                          AccountRepository accountRepository,
                          BankVaultService bankVaultService,
                          AuditLogService auditLogService,
                          PanelMessageRepository panelMessageRepository) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.customerInquiryRepository = customerInquiryRepository;
        this.securityNotificationRepository = securityNotificationRepository;
        this.customerMessageRepository = customerMessageRepository;
        this.userRepository = userRepository;
        this.accountRepository = accountRepository;
        this.bankVaultService = bankVaultService;
        this.auditLogService = auditLogService;
        this.panelMessageRepository = panelMessageRepository;
    }

    @PostMapping("/loan/apply")
    public String applyForLoan(
            @RequestParam("fullName") String fullName,
            @RequestParam("idCardSerial") String idCardSerial,
            @RequestParam(value = "finCode", required = false) String finCode,
            @RequestParam("phoneNumber") String phoneNumber,
            @RequestParam("amount") BigDecimal amount,
            @RequestParam("termMonths") Integer termMonths,
            @RequestParam("monthlyIncome") BigDecimal monthlyIncome,
            @RequestParam(value = "purpose", required = false) String purpose,
            @RequestParam(value = "accountNumber", required = false) String accountNumber,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        if (principal == null) {
            return "redirect:/login";
        }

        try {
            String username = principal.getName();
            User user = userRepository.findFirstByUsernameIgnoreCase(username)
                    .or(() -> userRepository.findFirstByEmailIgnoreCase(username))
                    .orElse(null);

            String canonicalUsername = user != null ? user.getUsername() : username;
            String customerName = (fullName != null && !fullName.trim().isEmpty())
                    ? fullName.trim()
                    : (user != null && user.getFullName() != null ? user.getFullName() : canonicalUsername);

            if (idCardSerial == null || idCardSerial.trim().isEmpty()) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "❌ Şəxsiyyət vəsiqəsinin seriya nömrəsi mütləq daxil edilməlidir! Seriya nömrəsi olmadan kredit müraciəti göndərilə bilməz.");
                return "redirect:/dashboard#loanSection";
            }

            String cleanIdSerial = idCardSerial.trim().toUpperCase().replaceAll("\\s+", "");

            if (cleanIdSerial.matches("^(\\w)\\1+$")) {
                redirectAttributes.addFlashAttribute("errorMessage",
                        "❌ Daxil edilmiş seriya nömrəsi etibarsızdır. Zəhmət olmasa real sənəd seriyanızı daxil edin.");
                return "redirect:/dashboard#loanSection";
            }

            if (cleanIdSerial.startsWith("AA")) {
                if (!cleanIdSerial.matches("^AA\\d{7}$")) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "❌ AA seriyası AA və ardınca dəqiq 7 rəqəmdən ibarət olmalıdır (7 rəqəmdən az və ya çox ola bilməz).");
                    return "redirect:/dashboard#loanSection";
                }
            } else if (cleanIdSerial.startsWith("AZE")) {
                if (!cleanIdSerial.matches("^AZE\\d{8}$")) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "❌ AZE seriyası AZE və ardınca dəqiq 8 rəqəmdən ibarət olmalıdır (8 rəqəmdən az və ya çox ola bilməz).");
                    return "redirect:/dashboard#loanSection";
                }
            } else {
                if (!cleanIdSerial.matches("^[A-Z0-9]{7,10}$") || cleanIdSerial.matches("^\\d+$") || cleanIdSerial.matches("^[A-Z]+$")) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "❌ Xarici vətəndaş sənəd nömrəsi beynəlxalq standartlara uyğun olmalıdır (hərf və rəqəm, 7-10 simvol).");
                    return "redirect:/dashboard#loanSection";
                }
            }

            String cleanFin = (finCode != null && !finCode.trim().isEmpty())
                    ? finCode.trim().toUpperCase().replaceAll("\\s+", "")
                    : (user != null && user.getFinCode() != null ? user.getFinCode() : "");

            if (!cleanFin.isEmpty()) {
                if (cleanFin.length() != 7 || cleanFin.matches("^(\\w)\\1{6}$") || !cleanFin.matches("^[A-Z0-9]{7}$") || cleanFin.contains("O") || cleanFin.contains("I")) {
                    redirectAttributes.addFlashAttribute("errorMessage",
                            "❌ 7 simvollu FİN kod dəqiq 7 simvol olmalı, saxta təkrar simvollardan ibarət olmamalı və O/I hərfləri daxil edilməməlidir.");
                    return "redirect:/dashboard#loanSection";
                }
            }

            if (user != null) {
                boolean userUpdated = false;
                if (user.getIdCardSerial() == null || user.getIdCardSerial().isEmpty()) {
                    user.setIdCardSerial(cleanIdSerial);
                    userUpdated = true;
                }
                if (!cleanFin.isEmpty() && (user.getFinCode() == null || user.getFinCode().isEmpty())) {
                    user.setFinCode(cleanFin);
                    userUpdated = true;
                }
                if (userUpdated) {
                    userRepository.save(user);
                }
            }

            if (amount == null || amount.compareTo(new BigDecimal("100.00")) < 0) {
                redirectAttributes.addFlashAttribute("errorMessage", "❌ Minimum kredit məbləği 100 ₼ olmalıdır.");
                return "redirect:/dashboard#loanSection";
            }
            if (amount.compareTo(new BigDecimal("100000.00")) > 0) {
                redirectAttributes.addFlashAttribute("errorMessage", "❌ Maksimum kredit məbləği 100,000 ₼ həddindədir.");
                return "redirect:/dashboard#loanSection";
            }

            if (termMonths == null || termMonths < 3 || termMonths > 60) {
                redirectAttributes.addFlashAttribute("errorMessage", "❌ Kredit müddəti 3 ilə 60 ay arasında seçilməlidir.");
                return "redirect:/dashboard#loanSection";
            }

            if (monthlyIncome == null || monthlyIncome.compareTo(BigDecimal.ZERO) <= 0) {
                redirectAttributes.addFlashAttribute("errorMessage", "❌ Rəsmi aylıq gəlir mütləq qeyd edilməlidir.");
                return "redirect:/dashboard#loanSection";
            }

            String accNo = (accountNumber != null && !accountNumber.trim().isEmpty()) ? accountNumber.trim() : "";
            if (accNo.isEmpty() && user != null && user.getAccounts() != null && !user.getAccounts().isEmpty()) {
                accNo = user.getAccounts().get(0).getAccountNumber();
            }

            BigDecimal annualRate = new BigDecimal("12.00");
            BigDecimal years = new BigDecimal(termMonths).divide(new BigDecimal("12"), 4, RoundingMode.HALF_UP);
            BigDecimal totalInterest = amount.multiply(annualRate).multiply(years)
                    .divide(new BigDecimal("100"), 2, RoundingMode.HALF_UP);
            BigDecimal totalRepayment = amount.add(totalInterest);
            BigDecimal monthlyPayment = totalRepayment.divide(new BigDecimal(termMonths), 2, RoundingMode.HALF_UP);

            LoanApplication loanApp = new LoanApplication(
                    user != null ? user.getId() : null,
                    canonicalUsername,
                    customerName,
                    cleanIdSerial,
                    phoneNumber != null ? phoneNumber.trim() : "",
                    accNo,
                    amount,
                    termMonths,
                    monthlyIncome,
                    purpose != null && !purpose.trim().isEmpty() ? purpose.trim() : "İstehlak Krediti"
            );
            loanApp.setInterestRate(annualRate);
            loanApp.setMonthlyPayment(monthlyPayment);
            loanApp.setTotalRepayment(totalRepayment);
            loanApp.setStatus("PENDING");
            LoanApplication savedLoan = loanApplicationRepository.save(loanApp);

            String refNo = "CRD-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
            String inquiryMsg = String.format(
                    "Müştəri: %s | Ş/V Seriya: %s | Məbləğ: %s ₼ | Müddət: %d ay | Aylıq Ödəniş: %s ₼ | Toplam: %s ₼ | Aylıq Gəlir: %s ₼ | Məqsəd: %s | Hesab: %s",
                    customerName, cleanIdSerial, amount, termMonths, monthlyPayment, totalRepayment, monthlyIncome, loanApp.getPurpose(), accNo
            );
            CustomerInquiry inquiry = new CustomerInquiry(
                    canonicalUsername,
                    customerName,
                    phoneNumber != null ? phoneNumber.trim() : "",
                    user != null ? user.getEmail() : "",
                    "LOAN_REQUEST",
                    "Kredit Müraciəti: " + amount + " ₼ (" + termMonths + " ay)",
                    inquiryMsg,
                    refNo
            );
            inquiry.setLoanApplicationId(savedLoan.getId());
            inquiry.setStatus("NEW");
            customerInquiryRepository.save(inquiry);

            SecurityNotification notif = new SecurityNotification(
                    "Yeni Kredit Müraciəti",
                    String.format("💳 %s tərəfindən %s ₼ məbləğində kredit müraciəti daxil oldu (Ş/V: %s, Müddət: %d ay). Təsdiq gözləyir.",
                            customerName, amount, cleanIdSerial, termMonths),
                    "LOAN_APPLICATION",
                    canonicalUsername
            );
            securityNotificationRepository.save(notif);

            CustomerMessage msg = new CustomerMessage(
                    canonicalUsername,
                    "AsadovBank Kredit Mərkəzi",
                    "Kredit Müraciətiniz Qəbul Edildi",
                    String.format("Hörmətli %s, %s ₼ məbləğində (%d aylıq) kredit müraciətiniz qeydə alındı və Təhlükəsizlik şöbəsinə göndərildi. Qərar verildikdə dərhal bildiriş alacaqsınız.",
                            customerName, amount, termMonths),
                    "INFO"
            );
            customerMessageRepository.save(msg);

            auditLogService.logAction(canonicalUsername, "SUBMIT_LOAN_APPLICATION", "LOAN",
                    savedLoan.getId().toString(), "Kredit müraciəti təqdim edildi: " + amount + " AZN");

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("✅ Kredit müraciətiniz uğurla qeydə alındı! Məbləğ: %s ₼, Müddət: %d ay. Təhlükəsizlik Mərkəzinin təsdiqindən sonra vəsait dərhal hesabınıza köçürüləcəkdir.",
                            amount, termMonths));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Müraciət göndərilərkən xəta baş verdi: " + e.getMessage());
        }

        return "redirect:/dashboard#loanSection";
    }

    @PostMapping("/admin/loans/approve")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String approveLoan(
            @RequestParam("loanId") Long loanId,
            @RequestParam(value = "note", required = false) String note,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            LoanApplication loan = loanApplicationRepository.findById(loanId)
                    .orElseThrow(() -> new RuntimeException("Kredit müraciəti tapılmadı: " + loanId));

            if ("APPROVED".equalsIgnoreCase(loan.getStatus())) {
                redirectAttributes.addFlashAttribute("errorMessage", "Bu kredit artıq təsdiqlənib.");
                return "redirect:/admin/inquiry";
            }

            String approver = principal != null ? principal.getName() : "Bank Administratoru";

            bankVaultService.issueCustomerCredit(
                    loan.getAccountNumber(),
                    loan.getAmount(),
                    loan.getInterestRate(),
                    "Kredit Təsdiqi • " + loan.getCustomerName() + (note != null && !note.trim().isEmpty() ? " | " + note.trim() : "")
            );

            loan.setStatus("APPROVED");
            loan.setApprovedBy(approver);
            loan.setApprovedAt(LocalDateTime.now());
            loan.setAdminNote(note != null ? note.trim() : "Kredit təhlükəsizlik yoxlamasından keçdi və təsdiqləndi.");
            loan.setUpdatedAt(LocalDateTime.now());
            loanApplicationRepository.save(loan);

            customerInquiryRepository.findAll().stream()
                    .filter(inq -> loan.getId().equals(inq.getLoanApplicationId()))
                    .forEach(inq -> {
                        inq.setStatus("RESOLVED");
                        inq.setAdminReply("Kredit müraciəti təsdiqləndi və vəsait müştərinin balansına köçürüldü.");
                        inq.setRepliedBy(approver);
                        inq.setRepliedAt(LocalDateTime.now());
                        customerInquiryRepository.save(inq);
                    });

            CustomerMessage replyMsg = new CustomerMessage(
                    loan.getUsername(),
                    "AsadovBank Təhlükəsizlik və Kredit İdarəsi",
                    "🎉 Kredit Müraciətiniz Təsdiqləndi!",
                    String.format("Hörmətli %s, %s ₼ məbləğindəki kredit müraciətiniz təsdiqləndi və vəsait dərhal hesabınıza köçürüldü. Aylıq ödəniş: %s ₼. Təbriklər!",
                            loan.getCustomerName(), loan.getAmount(), loan.getMonthlyPayment()),
                    "APPEAL_REPLY"
            );
            customerMessageRepository.save(replyMsg);

            auditLogService.logAction(approver, "APPROVE_LOAN", "LOAN",
                    loan.getId().toString(), "Kredit təsdiqləndi: " + loan.getAmount() + " AZN -> @" + loan.getUsername());

            redirectAttributes.addFlashAttribute("successMessage",
                    String.format("✅ Kredit (#%d) təsdiqləndi! %s ₼ məbləğ müştərinin (%s) hesabına köçürüldü.",
                            loan.getId(), loan.getAmount(), loan.getCustomerName()));

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Kredit təsdiq edilərkən xəta: " + e.getMessage());
        }

        return "redirect:/admin/inquiry";
    }

    @PostMapping("/admin/loans/reject")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String rejectLoan(
            @RequestParam("loanId") Long loanId,
            @RequestParam(value = "reason", required = false) String reason,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        try {
            LoanApplication loan = loanApplicationRepository.findById(loanId)
                    .orElseThrow(() -> new RuntimeException("Kredit müraciəti tapılmadı: " + loanId));

            String rejector = principal != null ? principal.getName() : "Bank Administratoru";
            String rejectReason = (reason != null && !reason.trim().isEmpty())
                    ? reason.trim() : "Məlumatlar bankın daxili kredit siyasətinə uyğun gəlmədi.";

            loan.setStatus("REJECTED");
            loan.setApprovedBy(rejector);
            loan.setAdminNote(rejectReason);
            loan.setUpdatedAt(LocalDateTime.now());
            loanApplicationRepository.save(loan);

            customerInquiryRepository.findAll().stream()
                    .filter(inq -> loan.getId().equals(inq.getLoanApplicationId()))
                    .forEach(inq -> {
                        inq.setStatus("RESOLVED");
                        inq.setAdminReply("Kreditə imtina verildi: " + rejectReason);
                        inq.setRepliedBy(rejector);
                        inq.setRepliedAt(LocalDateTime.now());
                        customerInquiryRepository.save(inq);
                    });

            CustomerMessage replyMsg = new CustomerMessage(
                    loan.getUsername(),
                    "AsadovBank Təhlükəsizlik və Kredit İdarəsi",
                    "Kredit Müraciətiniz Barədə Qərar",
                    String.format("Hörmətli %s, %s ₼ məbləğindəki kredit müraciətinizə hazırda müsbət cavab verilə bilmədi. Səbəb: %s",
                            loan.getCustomerName(), loan.getAmount(), rejectReason),
                    "WARNING"
            );
            customerMessageRepository.save(replyMsg);

            auditLogService.logAction(rejector, "REJECT_LOAN", "LOAN",
                    loan.getId().toString(), "Kredit imtina edildi: " + loan.getAmount() + " AZN -> @" + loan.getUsername());

            redirectAttributes.addFlashAttribute("successMessage",
                    "Kredit müraciəti rədd edildi və müştəriyə ətraflı bildiriş göndərildi.");

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Kredit rədd edilərkən xəta: " + e.getMessage());
        }

        return "redirect:/admin/inquiry";
    }

    @PostMapping("/admin/loans/send-to-security")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String forwardLoanToSecurity(@RequestParam("loanId") Long loanId,
                                        @RequestParam(value = "note", required = false) String note,
                                        Principal principal,
                                        RedirectAttributes redirectAttributes) {
        try {
            LoanApplication loan = loanApplicationRepository.findById(loanId)
                    .orElseThrow(() -> new RuntimeException("Kredit müraciəti tapılmadı."));
            String actor = principal != null ? principal.getName() : "Sorğu Əməkdaşı";
            loan.setStatus("SENT_TO_SECURITY");
            loanApplicationRepository.save(loan);

            customerInquiryRepository.findAll().stream()
                    .filter(i -> loan.getId().equals(i.getLoanApplicationId()))
                    .forEach(inq -> {
                        inq.setStatus("SENT_TO_SECURITY");
                        inq.setForwardedToSecurity(true);
                        inq.setForwardedBy(actor);
                        inq.setForwardedAt(LocalDateTime.now());
                        inq.setSecurityNote(note != null && !note.trim().isEmpty() ? note.trim() : "Kredit yoxlanışı üçün Security şöbəsinə göndərildi.");
                        customerInquiryRepository.save(inq);
                    });

            String msgBody = String.format("Mərhələ 1 -> 2 (Kredit Yoxlanışı)\nMüştəri: %s (@%s)\nMəbləğ: %,.2f ₼ (%d ay)\nQeyd: %s",
                    loan.getCustomerName(), loan.getUsername(), loan.getAmount(), loan.getTermMonths(),
                    note != null && !note.trim().isEmpty() ? note.trim() : "Təhlükəsizlik yoxlanışı tələb olunur.");
            panelMessageRepository.save(new PanelMessage("INQUIRY", "SECURITY", actor,
                    "[Kredit Müraciəti #" + loanId + "] Security Yoxlanışı", msgBody, null));

            redirectAttributes.addFlashAttribute("successMessage",
                    "Kredit müraciəti (#" + loanId + ") yoxlanış üçün Security şöbəsinə göndərildi.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Xəta: " + e.getMessage());
        }
        return "redirect:/admin/inquiry";
    }

    @PostMapping("/admin/loans/send-to-treasury")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE')")
    public String forwardLoanToTreasury(@RequestParam("loanId") Long loanId,
                                        @RequestParam(value = "note", required = false) String note,
                                        Principal principal,
                                        RedirectAttributes redirectAttributes) {
        try {
            LoanApplication loan = loanApplicationRepository.findById(loanId)
                    .orElseThrow(() -> new RuntimeException("Kredit müraciəti tapılmadı."));
            String actor = principal != null ? principal.getName() : "Security";
            loan.setStatus("SENT_TO_TREASURY");
            loanApplicationRepository.save(loan);

            String msgBody = String.format("Mərhələ 2 -> 3 (Təsdiqləndi, Xəzinədar Rəsmiləşdirməsi)\nMüştəri: %s (@%s)\nMəbləğ: %,.2f ₼ (%d ay)\nSecurity Qeydi: %s",
                    loan.getCustomerName(), loan.getUsername(), loan.getAmount(), loan.getTermMonths(),
                    note != null && !note.trim().isEmpty() ? note.trim() : "Security yoxlanışından keçdi. Rəsmiləşdirmə və məbləğ ayırması üçün hazırdır.");
            panelMessageRepository.save(new PanelMessage("SECURITY", "TREASURY", actor,
                    "[Kredit Təsdiqləndi #" + loanId + "] Xəzinədar Ayırması", msgBody, null));

            redirectAttributes.addFlashAttribute("successMessage",
                    "Kredit müraciəti (#" + loanId + ") Security tərəfindən təsdiqləndi və rəsmiləşdirmə üçün Xəzinədar panelinə göndərildi.");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("errorMessage", "Xəta: " + e.getMessage());
        }
        return "redirect:/admin/security";
    }
}

