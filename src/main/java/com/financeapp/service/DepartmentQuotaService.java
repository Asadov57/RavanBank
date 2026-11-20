package com.financeapp.service;

import com.financeapp.repository.BankEmployeeRepository;
import org.springframework.stereotype.Service;
import org.springframework.ui.Model;

/**
 * Mərkəzləşdirilmiş ştat limitləri və vəzifə normallaşdırma xidməti.
 * Limitlər: İT Mütəxəssisi=2, Xəzinədar=2, Security=2, Operator=8, Müştəri Xidmətləri=4, İnsan Resursları=2.
 */
@Service
public class DepartmentQuotaService {

    private final BankEmployeeRepository bankEmployeeRepository;

    public DepartmentQuotaService(BankEmployeeRepository bankEmployeeRepository) {
        this.bankEmployeeRepository = bankEmployeeRepository;
    }

    public String normalizeDepartmentName(String raw) {
        if (raw == null) {
            return "Inquiry";
        }
        String d = raw.trim().toLowerCase();
        if (d.contains("security") || d.contains("təhlükə")) {
            return "Security";
        }
        if (d.contains("treasury") || d.contains("xəzinə") || d.contains("xaznadar")) {
            return "Treasury";
        }
        if (d.equals("it") || d.contains("mütəxəssis") || d.contains("infrastruktur")) {
            return "IT";
        }
        if (d.contains("customer service") || d.contains("müştəri xidmətləri")) {
            return "Customer Service";
        }
        if (d.equals("hr") || d.contains("insan resurs")) {
            return "HR";
        }
        return "Inquiry";
    }

    public String getPositionLabel(String normalizedDept) {
        return switch (normalizedDept) {
            case "IT" -> "İT Mütəxəssisi";
            case "Treasury" -> "Xəzinədar";
            case "Security" -> "Security";
            case "Customer Service" -> "Müştəri Xidmətləri";
            case "HR" -> "İnsan Resursları";
            default -> "Operator";
        };
    }

    public int getLimit(String normalizedDept) {
        return switch (normalizedDept) {
            case "IT" -> 2;
            case "Treasury" -> 2;
            case "Security" -> 2;
            case "Customer Service" -> 4;
            case "HR" -> 2;
            default -> 8; // Inquiry / Operator
        };
    }

    public long countOccupied(String normalizedDept) {
        return bankEmployeeRepository.countByDepartmentIgnoreCaseAndApprovalStatus(normalizedDept, "APPROVED");
    }

    public boolean isDepartmentFull(String rawPositionOrDept) {
        String norm = normalizeDepartmentName(rawPositionOrDept);
        return countOccupied(norm) >= getLimit(norm);
    }

    /**
     * Vəzifə üzrə ştat dolubsa "İşçi lazım deyil..." xəta mesajı qaytarır, əks halda null.
     */
    public String checkQuotaError(String rawPositionOrDept, String currentDeptIfSameEmployee) {
        String targetDept = normalizeDepartmentName(rawPositionOrDept);
        if (currentDeptIfSameEmployee != null && targetDept.equalsIgnoreCase(currentDeptIfSameEmployee)) {
            return null;
        }
        long count = countOccupied(targetDept);
        int limit = getLimit(targetDept);
        if (count >= limit) {
            return String.format(
                    "İşçi lazım deyil! '%s' vəzifəsi üzrə ştat sayı tamamdır (%d/%d).",
                    getPositionLabel(targetDept), count, limit
            );
        }
        return null;
    }

    public void populateQuotaAttributes(Model model) {
        long itCount = countOccupied("IT");
        long treasuryCount = countOccupied("Treasury");
        long securityCount = countOccupied("Security");
        long inquiryCount = countOccupied("Inquiry");
        long customerServiceCount = countOccupied("Customer Service");
        long hrCount = countOccupied("HR");

        model.addAttribute("itEmployeesCount", itCount);
        model.addAttribute("treasuryEmployeesCount", treasuryCount);
        model.addAttribute("securityEmployeesCount", securityCount);
        model.addAttribute("inquiryEmployeesCount", inquiryCount);
        model.addAttribute("customerServiceEmployeesCount", customerServiceCount);
        model.addAttribute("hrEmployeesCount", hrCount);

        model.addAttribute("itLimit", getLimit("IT"));
        model.addAttribute("treasuryLimit", getLimit("Treasury"));
        model.addAttribute("securityLimit", getLimit("Security"));
        model.addAttribute("inquiryLimit", getLimit("Inquiry"));
        model.addAttribute("customerServiceLimit", getLimit("Customer Service"));
        model.addAttribute("hrLimit", getLimit("HR"));

        model.addAttribute("itFull", itCount >= getLimit("IT"));
        model.addAttribute("treasuryFull", treasuryCount >= getLimit("Treasury"));
        model.addAttribute("securityFull", securityCount >= getLimit("Security"));
        model.addAttribute("inquiryFull", inquiryCount >= getLimit("Inquiry"));
        model.addAttribute("customerServiceFull", customerServiceCount >= getLimit("Customer Service"));
        model.addAttribute("hrFull", hrCount >= getLimit("HR"));
    }
}
