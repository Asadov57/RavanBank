/**
 * Neo-Bank Customer Dashboard - Minimal Vanilla JavaScript
 * Used strictly for UI toggles & interactions (NO data fetching / API calls).
 * Compatible with SSR architectures (Spring Boot + Thymeleaf).
 */

document.addEventListener('DOMContentLoaded', () => {
  // 1. Quick Amount Preset Chips Helper
  const presetChips = document.querySelectorAll('.preset-chip-btn');
  presetChips.forEach(chip => {
    chip.addEventListener('click', (e) => {
      e.preventDefault();
      const targetInputId = chip.getAttribute('data-target');
      const amount = chip.getAttribute('data-amount');
      if (targetInputId && amount) {
        const input = document.getElementById(targetInputId);
        if (input) {
          input.value = amount;
          input.focus();
          
          // Brief pulse highlight for user feedback
          input.style.borderColor = 'var(--primary-500)';
          input.style.boxShadow = '0 0 0 4px rgba(37, 99, 235, 0.2)';
          setTimeout(() => {
            input.style.borderColor = '';
            input.style.boxShadow = '';
          }, 350);
        }
      }
    });
  });

  // 2. Client-side Filter for Recent Transactions Table (Show/Hide DOM Rows only)
  const filterPills = document.querySelectorAll('.filter-pill');
  const txRows = document.querySelectorAll('.transactions-table tbody tr');

  filterPills.forEach(pill => {
    pill.addEventListener('click', () => {
      // Toggle active class
      filterPills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');

      const filterType = pill.getAttribute('data-filter'); // 'all', 'DEPOSIT', 'WITHDRAWAL'

      txRows.forEach(row => {
        const rowType = row.getAttribute('data-type');
        if (filterType === 'all' || rowType === filterType) {
          row.style.display = '';
        } else {
          row.style.display = 'none';
        }
      });
    });
  });

  // 3. Quick Copy Account Number with Visual Feedback
  const copyBtn = document.getElementById('copyAccountBtn');
  if (copyBtn) {
    copyBtn.addEventListener('click', async () => {
      const fullAccountNum = copyBtn.getAttribute('data-account-number') || '402891824892';
      try {
        await navigator.clipboard.writeText(fullAccountNum);
        const originalText = copyBtn.innerHTML;
        copyBtn.innerHTML = `
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg> Copied!
        `;
        copyBtn.style.background = 'rgba(16, 185, 129, 0.3)';
        copyBtn.style.borderColor = 'rgba(16, 185, 129, 0.5)';
        setTimeout(() => {
          copyBtn.innerHTML = originalText;
          copyBtn.style.background = '';
          copyBtn.style.borderColor = '';
        }, 2000);
      } catch (err) {
        console.warn('Clipboard write failed:', err);
      }
    });
  }

  // 4. Dismissible Flash Message Alerts
  const alertCloseButtons = document.querySelectorAll('.alert-close');
  alertCloseButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const alert = btn.closest('.alert-banner');
      if (alert) {
        alert.style.transition = 'opacity 0.2s ease, transform 0.2s ease';
        alert.style.opacity = '0';
        alert.style.transform = 'translateY(-6px)';
        setTimeout(() => alert.remove(), 200);
      }
    });
  });
});
