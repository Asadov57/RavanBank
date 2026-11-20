package com.financeapp.controller;

import com.financeapp.model.PanelMessage;
import com.financeapp.repository.PanelMessageRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("/panel-messages")
public class PanelMessageController {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final PanelMessageRepository panelMessageRepository;

    public PanelMessageController(PanelMessageRepository panelMessageRepository) {
        this.panelMessageRepository = panelMessageRepository;
    }

    @PostMapping("/send")
    @PreAuthorize("hasAnyRole('ADMIN', 'ROLE_ADMIN', 'EMPLOYEE', 'ROLE_EMPLOYEE', 'SECURITY', 'ROLE_SECURITY')")
    public Object sendPanelMessage(
            @RequestParam("fromPanel") String fromPanel,
            @RequestParam("toPanel") String toPanel,
            @RequestParam(value = "subject", required = false) String subject,
            @RequestParam("message") String message,
            @RequestParam(value = "redirectTo", defaultValue = "/admin") String redirectTo,
            HttpServletRequest request,
            Principal principal,
            RedirectAttributes redirectAttributes) {

        boolean isAjax = "XMLHttpRequest".equalsIgnoreCase(request.getHeader("X-Requested-With"))
                || (request.getHeader("Accept") != null && request.getHeader("Accept").contains("application/json"));

        String cleanFrom = normalizePanel(fromPanel);
        String cleanTo = normalizePanel(toPanel);
        String sender = principal != null ? principal.getName() : "Sistem";

        if (cleanFrom.equals(cleanTo)) {
            String err = "Mesaj eyni panelə göndərilə bilməz.";
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", err));
            }
            redirectAttributes.addFlashAttribute("errorMessage", err);
            return "redirect:" + safeRedirect(redirectTo, cleanFrom);
        }

        if (message == null || message.trim().isEmpty()) {
            String err = "Mesaj mətni boş ola bilməz.";
            if (isAjax) {
                return ResponseEntity.badRequest().body(Map.of("success", false, "message", err));
            }
            redirectAttributes.addFlashAttribute("errorMessage", err);
            return "redirect:" + safeRedirect(redirectTo, cleanFrom);
        }

        String safeSubject = (subject != null && !subject.trim().isEmpty())
                ? subject.trim()
                : "Daxili Bildiriş";

        PanelMessage pm = new PanelMessage(cleanFrom, cleanTo, sender, safeSubject, message.trim(), null);
        PanelMessage saved = panelMessageRepository.save(pm);

        String okMsg = "Mesaj uğurla " + panelDisplayName(cleanTo) + " panelinə göndərildi.";
        if (isAjax) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("success", true);
            body.put("statusMessage", okMsg);
            body.put("id", saved.getId());
            body.put("fromPanel", cleanFrom);
            body.put("toPanel", cleanTo);
            body.put("senderUsername", sender);
            body.put("subject", safeSubject);
            body.put("message", message.trim());
            body.put("createdAt", saved.getCreatedAt() != null ? saved.getCreatedAt().format(FMT) : "");
            return ResponseEntity.ok(body);
        }

        redirectAttributes.addFlashAttribute("successMessage", okMsg);
        return "redirect:" + safeRedirect(redirectTo, cleanFrom);
    }

    private String normalizePanel(String panel) {
        String p = panel != null ? panel.trim().toUpperCase() : "ADMIN";
        if ("SECURITY".equals(p)) return "SECURITY";
        if ("INQUIRY".equals(p)) return "INQUIRY";
        if ("TREASURY".equals(p)) return "TREASURY";
        return "ADMIN";
    }

    private String panelDisplayName(String panel) {
        if ("SECURITY".equals(panel)) return "Security (Təhlükəsizlik)";
        if ("INQUIRY".equals(panel)) return "Sorğu (Inquiry)";
        if ("TREASURY".equals(panel)) return "Xəzinədar (Treasury)";
        return "Admin Panel";
    }

    private String safeRedirect(String redirectTo, String fromPanel) {
        if (redirectTo != null) {
            if (redirectTo.startsWith("/admin/security")) return "/admin/security#panelMessagesSection";
            if (redirectTo.startsWith("/admin/inquiry")) return "/admin/inquiry#inquiryPanelMessagesSection";
            if (redirectTo.startsWith("/admin/treasury")) return "/admin/treasury#treasuryPanelMessagesSection";
            if (redirectTo.startsWith("/admin")) return "/admin#adminPanelMessagesSection";
        }
        if ("SECURITY".equals(fromPanel)) return "/admin/security#panelMessagesSection";
        if ("INQUIRY".equals(fromPanel)) return "/admin/inquiry#inquiryPanelMessagesSection";
        if ("TREASURY".equals(fromPanel)) return "/admin/treasury#treasuryPanelMessagesSection";
        return "/admin#adminPanelMessagesSection";
    }
}
