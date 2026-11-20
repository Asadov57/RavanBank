

(function initBankErrorModal() {
  let _retryFn = () => {};

  function ensureModalExists() {
    let overlay = document.getElementById('errorModalOverlay');
    if (!overlay) {
      overlay = document.createElement('div');
      overlay.className = 'error-modal-overlay';
      overlay.id = 'errorModalOverlay';
      overlay.setAttribute('role', 'dialog');
      overlay.setAttribute('aria-modal', 'true');
      overlay.setAttribute('aria-labelledby', 'errorModalTitle');
      overlay.innerHTML = `
        <div class="error-modal">
          <button class="error-modal-close" id="errorModalCloseBtn" aria-label="Close">&times;</button>
          <div class="error-modal-icon-wrap" aria-hidden="true">
            <svg width="32" height="32" viewBox="0 0 24 24" fill="none" stroke="currentColor"
                 stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
              <circle cx="12" cy="12" r="10"></circle>
              <line x1="12" y1="8" x2="12" y2="12"></line>
              <line x1="12" y1="16" x2="12.01" y2="16"></line>
            </svg>
          </div>
          <h2 class="error-modal-title" id="errorModalTitle">Əməliyyat uğursuz oldu</h2>
          <p class="error-modal-message" id="errorModalMessage">Məlumatlar tam daxil edilməyib</p>
          <span class="error-modal-code" id="errorModalCode" style="display:none;"></span>
          <div class="error-modal-actions">
            <button class="error-modal-btn-dismiss" id="errorModalDismissBtn">Bağla</button>
            <button class="error-modal-btn-retry" id="errorModalRetryBtn">Yenidən cəhd et</button>
          </div>
        </div>
      `;
      document.body.appendChild(overlay);
    }
    return overlay;
  }

  function openModal(message, code) {
    const overlay = ensureModalExists();
    const msgEl = overlay.querySelector('#errorModalMessage');
    const codeEl = overlay.querySelector('#errorModalCode');
    const closeBtn = overlay.querySelector('#errorModalCloseBtn');

    if (msgEl) {
      msgEl.textContent = message || 'Məlumatlar tam daxil edilməyib';
    }
    if (codeEl) {
      if (code) {
        codeEl.textContent = 'Xəta kodu: ' + code;
        codeEl.style.display = 'block';
      } else {
        codeEl.style.display = 'none';
      }
    }

    overlay.classList.add('is-open');
    document.body.style.overflow = 'hidden';
    setTimeout(() => { if (closeBtn) closeBtn.focus(); }, 150);
  }

  function closeModal() {
    const overlay = document.getElementById('errorModalOverlay');
    if (overlay) {
      overlay.classList.remove('is-open');
      document.body.style.overflow = '';
    }
  }

  window.BankErrorModal = {
    show: (message, code) => openModal(message, code),
    hide: closeModal,
    setRetryFn: (fn) => { if (typeof fn === 'function') _retryFn = fn; }
  };

  document.addEventListener('click', (e) => {
    if (e.target.matches('#errorModalCloseBtn') || e.target.matches('#errorModalDismissBtn')) {
      closeModal();
      if (typeof _retryFn === 'function') _retryFn();
    } else if (e.target.matches('#errorModalRetryBtn')) {
      closeModal();
      setTimeout(() => { if (typeof _retryFn === 'function') _retryFn(); }, 150);
    } else if (e.target.matches('#errorModalOverlay')) {
      closeModal();
    }
  });

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape') {
      const overlay = document.getElementById('errorModalOverlay');
      if (overlay && overlay.classList.contains('is-open')) closeModal();
    }
  });

  document.addEventListener('invalid', (e) => {
    e.preventDefault();
    window.BankErrorModal.show('Məlumatlar tam daxil edilməyib');
    if (e.target) {
      e.target.classList.add('input-error-highlight');
      window.BankErrorModal.setRetryFn(() => {
        e.target.focus();
        setTimeout(() => e.target.classList.remove('input-error-highlight'), 2000);
      });
    }
  }, true);

  document.addEventListener('submit', (e) => {
    const form = e.target;
    if (!form || !form.elements) return;

    let hasEmptyRequired = false;
    let firstInvalidField = null;

    for (let i = 0; i < form.elements.length; i++) {
      const el = form.elements[i];
      if (!el || el.disabled || el.type === 'hidden' || el.type === 'submit' || el.type === 'button') continue;

      const isRequired = el.hasAttribute('required') || el.required;
      const val = el.value !== undefined ? String(el.value).trim() : '';

      if (isRequired && val === '') {
        hasEmptyRequired = true;
        firstInvalidField = el;
        break;
      }

      if (el.type === 'number') {
        const num = parseFloat(el.value);
        const minAttr = el.getAttribute('min');
        const minVal = minAttr ? parseFloat(minAttr) : 0;
        if (isRequired && (isNaN(num) || (num < minVal))) {
          hasEmptyRequired = true;
          firstInvalidField = el;
          break;
        }
      }

      if (el.tagName === 'SELECT' && isRequired) {
        if (!el.value || el.value === '' || el.value === 'null') {
          hasEmptyRequired = true;
          firstInvalidField = el;
          break;
        }
      }
    }

    if (hasEmptyRequired) {
      e.preventDefault();
      e.stopPropagation();
      window.BankErrorModal.show('Məlumatlar tam daxil edilməyib');
      if (firstInvalidField) {
        firstInvalidField.classList.add('input-error-highlight');
        window.BankErrorModal.setRetryFn(() => {
          firstInvalidField.focus();
          setTimeout(() => firstInvalidField.classList.remove('input-error-highlight'), 2000);
        });
      }
      return false;
    }
  }, true);

  document.addEventListener('DOMContentLoaded', () => {
    const bodyError = document.body.getAttribute('data-error');
    if (bodyError && bodyError.trim() !== '' && bodyError !== 'false') {
      setTimeout(() => window.BankErrorModal.show(bodyError), 150);
      return;
    }
    const params = new URLSearchParams(window.location.search);
    if (params.has('errorMessage')) {
      const msg = params.get('errorMessage');
      if (msg && msg.trim() !== '') {
        setTimeout(() => window.BankErrorModal.show(msg), 150);
      }
    }
  });
})();

document.addEventListener('DOMContentLoaded', () => {

  const currencySelect = document.getElementById('currencySelect');
  const currencyPrefix = document.getElementById('currencyPrefix');
  const currencySymbols = {
    'USD': '$',
    'EUR': '€',
    'GBP': '£',
    'AZN': '₼',
    'JPY': '¥',
    'CAD': '$',
    'CHF': 'Fr.'
  };

  if (currencySelect && currencyPrefix) {
    currencySelect.addEventListener('change', () => {
      const selected = currencySelect.value;
      currencyPrefix.textContent = currencySymbols[selected] || '$';
    });
  }

  const searchInput = document.getElementById('adminSearchInput');
  const tableRows = () => document.querySelectorAll('.admin-data-table tbody tr');

  function applyFilters() {
    const query = searchInput ? searchInput.value.toLowerCase().trim() : '';
    const activeTab = document.querySelector('.filter-tab.active');
    const filterKey = activeTab ? activeTab.getAttribute('data-status') : 'all';

    const rows = tableRows();
    let visibleCount = 0;

    rows.forEach(row => {
      if (row.id === 'noAccountsRow') return;

      const rowStatus = row.getAttribute('data-status') || '';
      const isEligible = row.getAttribute('data-transfer-eligible') === 'true';
      const textContent = row.textContent.toLowerCase();

      let matchesFilter = false;
      if (filterKey === 'all') {
        matchesFilter = true;
      } else if (filterKey === 'TRANSFER_ELIGIBLE') {
        matchesFilter = isEligible;
      } else {
        matchesFilter = (rowStatus.toUpperCase() === filterKey.toUpperCase());
      }

      const matchesSearch = (!query || textContent.includes(query));

      if (matchesFilter && matchesSearch) {
        row.style.display = '';
        visibleCount++;
      } else {
        row.style.display = 'none';
      }
    });

    let emptyRow = document.getElementById('noAccountsRow');
    if (visibleCount === 0 && rows.length > 0) {
      if (!emptyRow) {
        emptyRow = document.createElement('tr');
        emptyRow.id = 'noAccountsRow';
        emptyRow.innerHTML = `<td colspan="6" style="text-align: center; padding: 2rem; color: #94a3b8;">No matching accounts found for the selected filter.</td>`;
        const tbody = document.querySelector('.admin-data-table tbody');
        if (tbody) tbody.appendChild(emptyRow);
      }
      emptyRow.style.display = '';
    } else if (emptyRow) {
      emptyRow.style.display = 'none';
    }

    const blockedNoticeBanner = document.getElementById('blockedNoticeBanner');
    if (blockedNoticeBanner) {
      blockedNoticeBanner.style.display = (filterKey === 'BLOCKED') ? 'flex' : 'none';
    }
  }

  if (searchInput) {
    searchInput.addEventListener('input', applyFilters);
  }

  const filterTabs = document.querySelectorAll('.filter-tab');
  filterTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      filterTabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      applyFilters();
    });
  });

  const blockedKpiCard = document.getElementById('blockedKpiCard');
  if (blockedKpiCard) {
    blockedKpiCard.addEventListener('click', () => {
      const blockedTab = document.getElementById('blockedFilterTab');
      if (blockedTab) {
        filterTabs.forEach(t => t.classList.remove('active'));
        blockedTab.classList.add('active');
        applyFilters();
        const heading = document.getElementById('accountsTableHeading');
        if (heading) {
          heading.scrollIntoView({ behavior: 'smooth' });
        }
      }
    });
  }

  const vaultKpiCard = document.getElementById('vaultKpiCard');
  if (vaultKpiCard) {
    vaultKpiCard.addEventListener('click', () => {
      const vaultSec = document.getElementById('vaultSection');
      if (vaultSec) {
        vaultSec.scrollIntoView({ behavior: 'smooth' });
      }
    });
  }

  applyFilters();

  const alertDismissButtons = document.querySelectorAll('.alert-dismiss-btn');
  alertDismissButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      const alert = btn.closest('.admin-alert');
      if (alert) {
        alert.style.transition = 'opacity 0.2s ease, transform 0.2s ease';
        alert.style.opacity = '0';
        alert.style.transform = 'translateY(-6px)';
        setTimeout(() => alert.remove(), 200);
      }
    });
  });

  const depositAccountSelect = document.getElementById('depositAccountSelect');
  const depositAmountInput = document.getElementById('depositAmountInput');
  const depositPrevCustomer = document.getElementById('depositPrevCustomer');
  const depositPrevCurrentBalance = document.getElementById('depositPrevCurrentBalance');
  const depositPrevAmount = document.getElementById('depositPrevAmount');
  const depositPrevNewBalance = document.getElementById('depositPrevNewBalance');
  const depositPreviewCard = document.getElementById('depositPreviewCard');

  function updateDepositPreview() {
    if (!depositAccountSelect) return;
    const selectedOption = depositAccountSelect.options[depositAccountSelect.selectedIndex];

    if (!selectedOption || !selectedOption.value) {
      if (depositPreviewCard) depositPreviewCard.style.display = 'none';
      return;
    }

    if (depositPreviewCard) depositPreviewCard.style.display = 'block';

    const customerName = selectedOption.getAttribute('data-customer') || selectedOption.text.split('•')[0].trim();
    const currBalance = parseFloat(selectedOption.getAttribute('data-balance')) || 0;
    const addAmount = parseFloat(depositAmountInput ? depositAmountInput.value : 0) || 0;
    const newBalance = currBalance + addAmount;

    if (depositPrevCustomer) depositPrevCustomer.textContent = customerName;
    if (depositPrevCurrentBalance) {
      depositPrevCurrentBalance.textContent = '$' + currBalance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    if (depositPrevAmount) {
      depositPrevAmount.textContent = '+$' + addAmount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    if (depositPrevNewBalance) {
      depositPrevNewBalance.textContent = '$' + newBalance.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
  }

  if (depositAccountSelect) {
    depositAccountSelect.addEventListener('change', updateDepositPreview);
  }
  if (depositAmountInput) {
    depositAmountInput.addEventListener('input', updateDepositPreview);
  }

  const depositPresetChips = document.querySelectorAll('.preset-chip-btn[data-target="depositAmountInput"]');
  depositPresetChips.forEach(chip => {
    chip.addEventListener('click', (e) => {
      e.preventDefault();
      const amount = chip.getAttribute('data-amount');
      if (depositAmountInput && amount) {
        depositAmountInput.value = amount;
        depositAmountInput.dispatchEvent(new Event('input', { bubbles: true }));
        depositAmountInput.focus();
      }
    });
  });

  document.addEventListener('click', (e) => {
    const depositBtn = e.target.closest('.action-deposit');
    if (!depositBtn) return;

    e.preventDefault();
    const accNum = depositBtn.getAttribute('data-acc-num');
    if (!accNum || !depositAccountSelect) return;

    for (let i = 0; i < depositAccountSelect.options.length; i++) {
      if (depositAccountSelect.options[i].value === accNum) {
        depositAccountSelect.selectedIndex = i;
        break;
      }
    }

    updateDepositPreview();

    const depositSection = document.getElementById('adminDepositSection');
    if (depositSection) {
      depositSection.scrollIntoView({ behavior: 'smooth' });
      depositSection.classList.add('input-error-highlight');
      setTimeout(() => depositSection.classList.remove('input-error-highlight'), 1200);
    }
    if (depositAmountInput) {
      setTimeout(() => depositAmountInput.focus(), 400);
    }
  });

  window.updateCreditOperation = function(action) {
    const form = document.getElementById('vaultCreditForm');
    if (form) form.action = action;
    const rateGroup = document.getElementById('interestRateGroup');
    const calcBox = document.getElementById('creditCalculationBox');
    const isTransfer = action && action.includes('transfer');
    if (rateGroup) rateGroup.style.display = isTransfer ? 'none' : '';
    const rateInput = document.getElementById('creditInterestInput');
    if (rateInput) rateInput.required = !isTransfer;
    if (calcBox) calcBox.style.display = isTransfer ? 'none' : 'flex';
  };

  const creditAmountInput = document.getElementById('creditAmountInput');
  const creditInterestInput = document.getElementById('creditInterestInput');
  const calcInterestAmount = document.getElementById('calcInterestAmount');
  const calcTotalRepayment = document.getElementById('calcTotalRepayment');

  function updateCreditPreview() {
    const amount = parseFloat(creditAmountInput ? creditAmountInput.value : 0) || 0;
    const rate = parseFloat(creditInterestInput ? creditInterestInput.value : 0) || 0;
    const interest = (amount * rate) / 100;
    const total = amount + interest;
    if (calcInterestAmount) {
      calcInterestAmount.textContent = '$' + interest.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
    if (calcTotalRepayment) {
      calcTotalRepayment.textContent = '$' + total.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }
  }

  if (creditAmountInput) creditAmountInput.addEventListener('input', updateCreditPreview);
  if (creditInterestInput) creditInterestInput.addEventListener('input', updateCreditPreview);
  updateCreditPreview();

  initRegistrationWebSocket();

  function initRegistrationWebSocket() {
    if (typeof SockJS === 'undefined' || typeof Stomp === 'undefined') {
      return;
    }

    try {
      const socket = new SockJS('/ws');
      const stompClient = Stomp.over(socket);
      stompClient.debug = () => {};

      stompClient.connect({}, () => {
        stompClient.subscribe('/topic/registrations', (message) => {
          try {
            const data = JSON.parse(message.body);
            displayRegistrationToast(data);
            handleNewCustomerLiveUpdate(data);
          } catch (e) {
            console.error('Error handling WebSocket message:', e);
          }
        });
      }, () => {
        setTimeout(initRegistrationWebSocket, 5000);
      });
    } catch (err) {
      console.error('Failed to initialize WebSocket:', err);
    }
  }

  function displayRegistrationToast(data) {
    let container = document.getElementById('liveNotificationsContainer');
    if (!container) {
      container = document.createElement('div');
      container.id = 'liveNotificationsContainer';
      container.className = 'live-notifications-toast-stack';
      document.body.appendChild(container);
    }

    const toast = document.createElement('div');
    toast.className = 'live-toast-item';
    toast.setAttribute('role', 'alert');

    const username = data.username || 'New Customer';
    const initialBalance = data.balance ? ('$' + parseFloat(data.balance).toFixed(2)) : '$600.00';
    const accNumber = data.accountNumber || 'Assigned';

    toast.innerHTML = `
      <div class="live-toast-icon">
        <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round">
          <path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path>
          <circle cx="9" cy="7" r="4"></circle>
          <line x1="19" y1="8" x2="19" y2="14"></line>
          <line x1="22" y1="11" x2="16" y2="11"></line>
        </svg>
      </div>
      <div class="live-toast-content">
        <div class="live-toast-title">
          <span>New Customer Registered</span>
          <span class="live-toast-badge">LIVE</span>
        </div>
        <div class="live-toast-body">
          <strong>${escapeHtml(username)}</strong> joined the system.<br>
          <span style="color: #60a5fa; font-size: 0.775rem;">Account: ${escapeHtml(accNumber)} &bull; Balance: ${initialBalance} USD</span>
        </div>
      </div>
      <button type="button" class="live-toast-close" title="Dismiss">&times;</button>
    `;

    toast.querySelector('.live-toast-close').addEventListener('click', () => {
      dismissToast(toast);
    });

    container.appendChild(toast);
    setTimeout(() => { dismissToast(toast); }, 7000);
  }

  function dismissToast(toast) {
    if (!toast || !toast.parentNode) return;
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(60px)';
    setTimeout(() => {
      if (toast.parentNode) toast.remove();
    }, 300);
  }

  function handleNewCustomerLiveUpdate(data) {
    const activeKpi = document.querySelector('.kpi-card:nth-child(2) .kpi-value');
    if (activeKpi) {
      const curr = parseInt(activeKpi.textContent.replace(/[^0-9]/g, '')) || 0;
      activeKpi.textContent = (curr + 1).toLocaleString();
    }

    const todayKpi = document.querySelector('.kpi-card:nth-child(4) .kpi-value');
    if (todayKpi) {
      const currToday = parseInt(todayKpi.textContent.replace(/[^0-9]/g, '')) || 0;
      todayKpi.textContent = '+' + (currToday + 1);
    }

    const tbody = document.querySelector('.admin-data-table tbody');
    if (tbody && data.username) {
      const initials = (data.username.length >= 2 ? data.username.substring(0, 2).toUpperCase() : 'CU');
      const balanceVal = data.balance ? parseFloat(data.balance).toFixed(2) : '600.00';
      const currencyVal = data.currency || 'USD';
      const accNum = data.accountNumber || ('4098 ' + Math.floor(1000 + Math.random()*9000) + ' ' + Math.floor(1000 + Math.random()*9000) + ' ' + Math.floor(1000 + Math.random()*9000));

      const tr = document.createElement('tr');
      tr.setAttribute('data-status', 'ACTIVE');
      tr.setAttribute('data-transfer-eligible', 'true');
      tr.style.animation = 'slideInRight 0.4s ease forwards';
      tr.style.backgroundColor = 'rgba(59, 130, 246, 0.08)';

      tr.innerHTML = `
        <td><span class="acc-id-badge" style="color: #60a5fa; border-color: rgba(59, 130, 246, 0.4);">${escapeHtml(accNum)}</span></td>
        <td>
          <div class="customer-cell">
            <div class="customer-avatar-initials" style="background: linear-gradient(135deg, #2563eb, #1d4ed8);">${initials}</div>
            <div class="customer-info">
              <span class="customer-full-name">${escapeHtml(data.username)}</span>
              <span class="customer-type-meta">Retail Checking &bull; Real-Time</span>
            </div>
          </div>
        </td>
        <td style="text-align: right;">
          <div class="balance-cell">
            <span class="balance-num-formatted">$${Number(balanceVal).toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2})}</span>
            <span class="currency-tag-inline">${escapeHtml(currencyVal)}</span>
          </div>
        </td>
        <td style="text-align: center;">
          <span class="status-pill status-active">
            <span class="status-dot-static"></span>
            <span>ACTIVE</span>
          </span>
        </td>
        <td style="text-align: center;">
          <span class="eligibility-pill eligible">
            <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5"><polyline points="20 6 9 17 4 12"></polyline></svg>
            Eligible
          </span>
        </td>
        <td style="text-align: right;">
          <div class="table-actions-cell" style="justify-content: flex-end;">
            <button type="button" class="action-btn action-deposit" data-acc-num="${escapeHtml(accNum)}" title="Balansa depozit yüklə">+ Deposit</button>
          </div>
        </td>
      `;

      const emptyRow = document.getElementById('noAccountsRow');
      if (emptyRow) emptyRow.style.display = 'none';

      tbody.insertBefore(tr, tbody.firstChild);
      applyFilters();

      if (depositAccountSelect) {
        const opt = document.createElement('option');
        opt.value = accNum;
        opt.setAttribute('data-customer', data.username);
        opt.setAttribute('data-balance', balanceVal);
        opt.textContent = `${data.username} • ${accNum} (Cari Balans: $${Number(balanceVal).toLocaleString('en-US', {minimumFractionDigits: 2, maximumFractionDigits: 2})})`;
        depositAccountSelect.appendChild(opt);
      }
    }
  }

  function escapeHtml(str) {
    if (!str) return '';
    return String(str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#039;');
  }

  document.addEventListener('click', async (e) => {
    const copyBtn = e.target.closest('.copy-small-btn');
    if (copyBtn) {
      e.preventDefault();
      const val = copyBtn.getAttribute('data-copy');
      if (val) {
        try {
          await navigator.clipboard.writeText(val);
          const origHtml = copyBtn.innerHTML;
          copyBtn.innerHTML = '✓';
          copyBtn.style.color = '#34d399';
          setTimeout(() => {
            copyBtn.innerHTML = origHtml;
            copyBtn.style.color = '';
          }, 1500);
        } catch (err) {
          console.warn('Clipboard write failed:', err);
        }
      }
    }
  });

  const toggleAllPasswordsBtn = document.getElementById('toggleAllPasswordsBtn');
  let passwordsHidden = false;
  if (toggleAllPasswordsBtn) {
    toggleAllPasswordsBtn.addEventListener('click', () => {
      passwordsHidden = !passwordsHidden;
      const pwdEls = document.querySelectorAll('.pwd-text-val');
      pwdEls.forEach(el => {
        if (!el.getAttribute('data-original')) {
          el.setAttribute('data-original', el.textContent.trim());
        }
        const orig = el.getAttribute('data-original');
        el.textContent = passwordsHidden ? '••••••••' : orig;
      });
      const txt = document.getElementById('toggleAllPasswordsText');
      if (txt) {
        txt.textContent = passwordsHidden ? 'Şifrələri Göstər' : 'Şifrələri Gizlət';
      }
    });
  }

});
