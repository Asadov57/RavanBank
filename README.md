# Nexus Neo-Bank Banking Templates
### Server-Side Rendered (SSR) MVC Templates (Spring Boot + Thymeleaf / Django / Laravel)

This repository provides two production-grade, responsive templates designed specifically for traditional **Server-Side Rendered (SSR) MVC frameworks** without relying on REST APIs, AJAX, or `fetch()` for state management:

1. **Customer Dashboard (`index.html` / `templates/dashboard.html`)**: Professional corporate blue/indigo light theme.
2. **Admin Portal (`admin.html` / `templates/admin.html`)**: Sleek obsidian slate dark mode banking administration and ledger console.

---

## 📁 Project Structure

```text
Bank Final Ravan/
├── index.html                  <-- Customer Dashboard (Light Mode)
├── admin.html                  <-- Admin Dashboard (Dark Mode)
├── css/
│   ├── styles.css              <-- Customer Design System (Corporate Blue / Indigo)
│   └── admin-dark.css          <-- Admin Dark Design System (Obsidian Slate)
├── js/
│   ├── dashboard.js            <-- Minimal Vanilla JS for Customer UI toggles
│   └── admin.js                <-- Minimal Vanilla JS for Admin search/filters
├── templates/                  <-- Drop-in templates for Spring Boot (src/main/resources/templates)
│   ├── dashboard.html
│   └── admin.html
├── static/                     <-- Drop-in static assets for Spring Boot (src/main/resources/static)
│   ├── css/
│   │   ├── styles.css
│   │   └── admin-dark.css
│   └── js/
│       ├── dashboard.js
│       └── admin.js
└── README.md
```

---

## 🛡️ Admin Dashboard Features (`admin.html`)

### 1. Dark Mode Aesthetic
- **Palette**: Deep Cosmic Slate (`#0a0f1d`), Elevated Obsidian Surface (`#111827`), Inset Inputs (`#0d1424`), Electric Blue (`#3b82f6`), and Indigo (`#6366f1`).
- **Status Badges**: Glow-tinted semantic badges for `ACTIVE` (Emerald), `BLOCKED` (Coral Red), and `PENDING` (Amber).

### 2. Main Data Table (`<table>`)
- Semantic `<table>`, `<thead>`, `<tbody>`, and `<tr>` structure designed for simple looping with server-side template engines:
  ```html
  <tr th:each="account : ${accounts}" th:attr="data-status=${account.status}">
    <td><span class="acc-id-badge" th:text="${account.id}">AC-84920</span></td>
    <td>...<span th:text="${account.customerName}">...</span></td>
    <td style="text-align: right;"><span th:text="${account.balance}">...</span></td>
    <td style="text-align: center;"><span class="status-pill" th:text="${account.status}">ACTIVE</span></td>
    <td>... Actions ...</td>
  </tr>
  ```
- **Columns**: Account ID, Customer Name, Balance, Status, Actions.
- **Client-Side Live Filter (No AJAX)**: Instant text search and status filter tabs (`All`, `Active`, `Blocked`, `Pending`) powered by vanilla DOM visibility toggles.

### 3. Create Account Form
- Standard HTML form submitting via `POST`:
  ```html
  <form action="/admin/accounts/create" th:action="@{/admin/accounts/create}" method="POST" id="createAccountForm">
    <input type="hidden" name="_csrf" th:value="${_csrf?.token}" />

    <!-- Customer Name -->
    <input type="text" name="customerName" required placeholder="e.g. Jonathan R. Vance" />

    <!-- Initial Balance -->
    <input type="number" step="0.01" min="0.00" name="initialBalance" required placeholder="1000.00" />

    <!-- Currency Selector -->
    <select name="currency" required>
      <option value="USD">USD - US Dollar ($)</option>
      <option value="EUR">EUR - Euro (€)</option>
      <option value="GBP">GBP - British Pound (£)</option>
      <option value="JPY">JPY - Japanese Yen (¥)</option>
      <option value="CAD">CAD - Canadian Dollar ($)</option>
      <option value="CHF">CHF - Swiss Franc (Fr.)</option>
    </select>

    <button type="submit">Create Account</button>
  </form>
  ```
- Dynamic currency symbol prefix synchronization in Vanilla JS.

### 4. Table Action Buttons
- **View Link**: Standard HTML hyperlink (`<a>`):
  ```html
  <a th:href="@{/admin/accounts/{id}(id=${account.id})}" href="/admin/accounts/AC-84920" class="action-btn action-view">View</a>
  ```
- **Block / Unblock Form**: Mini HTML form submitting via `POST`:
  ```html
  <form th:action="@{/admin/accounts/{id}/block(id=${account.id})}" action="/admin/accounts/AC-84920/block" method="POST" class="inline-action-form">
    <input type="hidden" name="_csrf" th:value="${_csrf?.token}" />
    <button type="submit" class="action-btn action-block">Block</button>
  </form>
  ```
- **Delete Form**: Mini HTML form submitting via `POST` with standard browser confirmation:
  ```html
  <form th:action="@{/admin/accounts/{id}/delete(id=${account.id})}" action="/admin/accounts/AC-84920/delete" method="POST" class="inline-action-form" onsubmit="return confirm('Are you sure you want to permanently delete this account?');">
    <input type="hidden" name="_csrf" th:value="${_csrf?.token}" />
    <button type="submit" class="action-btn action-delete">Delete</button>
  </form>
  ```

---

## ☕ Spring Boot Admin Controller Sample (`AdminAccountController.java`)

```java
package com.neobank.admin.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.util.List;

@Controller
@RequestMapping("/admin")
public class AdminAccountController {

    private final AdminAccountService accountService;

    public AdminAccountController(AdminAccountService accountService) {
        this.accountService = accountService;
    }

    /**
     * Display the Admin Dashboard
     */
    @GetMapping("/dashboard")
    public String showAdminDashboard(Model model) {
        List<BankAccount> accounts = accountService.getAllAccounts();

        model.addAttribute("accounts", accounts);
        model.addAttribute("totalLiquidityFormatted", accountService.getTotalLiquidityFormatted());
        model.addAttribute("activeAccountsCount", accountService.countActiveAccounts());
        model.addAttribute("blockedAccountsCount", accountService.countBlockedAccounts());
        model.addAttribute("newAccountsToday", accountService.countNewAccountsToday());

        return "admin"; // Resolves to templates/admin.html
    }

    /**
     * Create a new Bank Account via standard HTTP POST
     * Form: <form action="/admin/accounts/create" method="POST">
     */
    @PostMapping("/accounts/create")
    public String createAccount(@RequestParam("customerName") String customerName,
                                @RequestParam("initialBalance") BigDecimal initialBalance,
                                @RequestParam("currency") String currency,
                                RedirectAttributes redirectAttributes) {
        try {
            BankAccount created = accountService.createAccount(customerName, initialBalance, currency);
            redirectAttributes.addFlashAttribute("successMessage", 
                String.format("Account %s successfully created for %s.", created.getId(), customerName));
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Failed to create account: " + ex.getMessage());
        }
        return "redirect:/admin/dashboard";
    }

    /**
     * Block an account via mini POST form
     * Form: <form action="/admin/accounts/{id}/block" method="POST">
     */
    @PostMapping("/accounts/{id}/block")
    public String blockAccount(@PathVariable("id") String accountId,
                               RedirectAttributes redirectAttributes) {
        accountService.setAccountStatus(accountId, "BLOCKED");
        redirectAttributes.addFlashAttribute("successMessage", "Account " + accountId + " has been blocked.");
        return "redirect:/admin/dashboard";
    }

    /**
     * Unblock an account via mini POST form
     * Form: <form action="/admin/accounts/{id}/unblock" method="POST">
     */
    @PostMapping("/accounts/{id}/unblock")
    public String unblockAccount(@PathVariable("id") String accountId,
                                 RedirectAttributes redirectAttributes) {
        accountService.setAccountStatus(accountId, "ACTIVE");
        redirectAttributes.addFlashAttribute("successMessage", "Account " + accountId + " has been restored to active status.");
        return "redirect:/admin/dashboard";
    }

    /**
     * Delete an account via mini POST form
     * Form: <form action="/admin/accounts/{id}/delete" method="POST">
     */
    @PostMapping("/accounts/{id}/delete")
    public String deleteAccount(@PathVariable("id") String accountId,
                                RedirectAttributes redirectAttributes) {
        accountService.deleteAccount(accountId);
        redirectAttributes.addFlashAttribute("successMessage", "Account " + accountId + " has been permanently deleted.");
        return "redirect:/admin/dashboard";
    }
}
```

---

## 🚀 How to Run & Preview

1. **Direct Browser Preview (Natural Templates)**:
   - Double-click [admin.html](file:///c:/Users/user/Desktop/Bank%20Final%20Ravan/admin.html) to view the Admin Dashboard in dark mode.
   - Double-click [index.html](file:///c:/Users/user/Desktop/Bank%20Final%20Ravan/index.html) to view the Customer Dashboard in light mode.
2. **Spring Boot Integration**:
   - Copy `templates/*` into your Spring Boot `src/main/resources/templates/`.
   - Copy `static/*` into your Spring Boot `src/main/resources/static/`.
#   B a n k F r o n t e d  
 