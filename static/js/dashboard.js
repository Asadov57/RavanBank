

(function initBankErrorModal() {
  let _retryFn = () => { };

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

    if (form.id === 'transferForm' || form.id === 'unifiedFundsForm') return;

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
        if (isRequired && (isNaN(num) || (minVal > 0 && num < minVal) || num <= 0)) {
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
      const msg = (bodyError === 'true') ? 'İstifadəçi adı və ya şifrə yanlışdır.' : bodyError;
      setTimeout(() => window.BankErrorModal.show(msg), 150);
      return;
    }
    const params = new URLSearchParams(window.location.search);
    if (params.has('errorMessage')) {
      const msg = params.get('errorMessage');
      if (msg && msg.trim() !== '') {
        setTimeout(() => window.BankErrorModal.show(msg), 150);
      }
    } else if (params.get('error') === 'true') {
      setTimeout(() => window.BankErrorModal.show('İstifadəçi adı və ya şifrə yanlışdır.'), 150);
    }
  });
})();

document.addEventListener('DOMContentLoaded', () => {

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
          input.dispatchEvent(new Event('input', { bubbles: true }));
          input.focus();
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

  const filterPills = document.querySelectorAll('.filter-pill');
  const txRows = document.querySelectorAll('.transactions-table tbody tr');

  filterPills.forEach(pill => {
    pill.addEventListener('click', () => {
      filterPills.forEach(p => p.classList.remove('active'));
      pill.classList.add('active');
      const filterType = pill.getAttribute('data-filter');
      txRows.forEach(row => {
        const rowType = row.getAttribute('data-type');
        row.style.display = (filterType === 'all' || rowType === filterType) ? '' : 'none';
      });
    });
  });

  const copyBtn = document.getElementById('copyAccountBtn');
  if (copyBtn) {
    copyBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
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

  const flipContainer = document.getElementById('bankCardFlipContainer');

  if (flipContainer) {
    const handleCardFlip = (e) => {

      if (e.target.closest('#copyAccountBtn') ||
          e.target.closest('#copyBackCardBtn') ||
          e.target.closest('.copy-btn') ||
          e.target.closest('.card-back-copy-btn') ||
          e.target.closest('a') ||
          e.target.closest('input')) {
        return;
      }
      flipContainer.classList.toggle('is-flipped');
    };

    flipContainer.addEventListener('click', handleCardFlip);

    flipContainer.addEventListener('keydown', (e) => {
      if (e.key === 'Enter' || e.key === ' ') {
        e.preventDefault();
        flipContainer.classList.toggle('is-flipped');
      }
    });
  }

  const copyBackBtn = document.getElementById('copyBackCardBtn');
  if (copyBackBtn) {
    copyBackBtn.addEventListener('click', async (e) => {
      e.stopPropagation();
      const rawNum = copyBackBtn.getAttribute('data-copy') || '4098 7056 4547 6397';
      const cleanNum = rawNum.replace(/\s+/g, '');
      try {
        await navigator.clipboard.writeText(cleanNum);
        const originalHtml = copyBackBtn.innerHTML;
        copyBackBtn.innerHTML = `
          <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round">
            <polyline points="20 6 9 17 4 12"></polyline>
          </svg> <span>Kopyalandı!</span>
        `;
        copyBackBtn.style.background = 'rgba(16, 185, 129, 0.35)';
        copyBackBtn.style.borderColor = 'rgba(16, 185, 129, 0.6)';
        setTimeout(() => {
          copyBackBtn.innerHTML = originalHtml;
          copyBackBtn.style.background = '';
          copyBackBtn.style.borderColor = '';
        }, 2000);
      } catch (err) {
        console.warn('Clipboard write failed:', err);
      }
    });
  }

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

  const CURRENCY_RATES = { AZN: 1.0, USD: 1.70, EUR: 1.85 };
  const CURRENCY_SYMBOLS = { AZN: '₼', USD: '$', EUR: '€' };

  function normalizeCur(c) {
    const u = String(c || 'AZN').trim().toUpperCase();
    return (u === 'USD' || u === 'EUR' || u === 'AZN') ? u : 'AZN';
  }

  function convertAmount(amount, fromCur, toCur) {
    const f = normalizeCur(fromCur);
    const t = normalizeCur(toCur);
    if (f === t) return Math.round(amount * 100) / 100;
    const inAzn = amount * CURRENCY_RATES[f];
    return Math.round((inAzn / CURRENCY_RATES[t]) * 100) / 100;
  }

  function calcTransferFee(grossInAccountCur) {
    if (grossInAccountCur <= 0) return 0;
    if (grossInAccountCur <= 10) return 0.02;
    if (grossInAccountCur <= 100) return 0.05;
    return 0.10;
  }

  function formatMoney(val, cur) {
    const sym = CURRENCY_SYMBOLS[normalizeCur(cur)];
    const num = (typeof val === 'number' && !isNaN(val)) ? val : 0;
    return num.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' ' + sym;
  }

  function attachCardMaskPreview(inputEl, previewId) {
    if (!inputEl) return;
    let maskPreviewEl = document.getElementById(previewId);
    if (!maskPreviewEl) {
      maskPreviewEl = document.createElement('div');
      maskPreviewEl.id = previewId;
      maskPreviewEl.style.fontSize = '0.8rem';
      maskPreviewEl.style.color = '#0284c7';
      maskPreviewEl.style.marginTop = '4px';
      maskPreviewEl.style.display = 'none';
      inputEl.parentNode.appendChild(maskPreviewEl);
    }
    inputEl.addEventListener('input', () => {
      const val = inputEl.value;
      const digits = val.replace(/\D/g, '');
      if (digits.length >= 4) {
        if (/^\d+$/.test(val.replace(/\s+/g, '')) && digits.length <= 16) {
          const parts = digits.match(/.{1,4}/g);
          inputEl.value = parts ? parts.join(' ') : digits;
        }
        const last4 = digits.substring(digits.length - 4);
        maskPreviewEl.innerHTML = `<span style="color:#64748b;">Təhlükəsizlik maskası:</span> <strong>**** **** **** ${last4}</strong>`;
        maskPreviewEl.style.display = 'block';
      } else {
        maskPreviewEl.style.display = 'none';
      }
    });
  }

  const unifiedForm = document.getElementById('unifiedFundsForm');
  const opInput = document.getElementById('fundsOperationType');
  const recTypeInput = document.getElementById('fundsRecipientType');
  const opTabBtns = document.querySelectorAll('.op-tab-btn[data-op]');
  const recToggleBtns = document.querySelectorAll('.recipient-toggle-btn[data-scope="funds"]');

  const fundsIndFields = document.getElementById('fundsIndividualFields');
  const fundsCorpFields = document.getElementById('fundsCorporateFields');
  const fundsRecAccount = document.getElementById('fundsRecipientAccount');
  const fundsCompanyName = document.getElementById('fundsCompanyName');

  const fundsCurrencySelect = document.getElementById('fundsCurrency');
  const fundsCurrencyPrefix = document.getElementById('fundsCurrencyPrefix');
  const fundsAmountInput = document.getElementById('fundsAmount');
  const fundsDescriptionInput = document.getElementById('fundsDescription');

  const fundsHeaderIcon = document.getElementById('fundsHeaderIcon');
  const fundsCardTitle = document.getElementById('fundsCardTitle');
  const fundsCardSubtitle = document.getElementById('fundsCardSubtitle');
  const fundsRecTypeLabel = document.getElementById('fundsRecipientTypeLabel');
  const fundsIndLabel = document.getElementById('fundsIndividualLabel');
  const fundsIndHint = document.getElementById('fundsIndividualHint');
  const fundsAmountLabel = document.getElementById('fundsAmountLabel');
  const fundsFeeHint = document.getElementById('fundsFeeHint');
  const fundsLimitInfo = document.getElementById('fundsLimitInfo');
  const calcGrossLabel = document.getElementById('calcGrossLabel');
  const calcGrossPreview = document.getElementById('calcGrossPreview');
  const calcAznPreview = document.getElementById('calcAznPreview');
  const calcFeePreview = document.getElementById('calcFeePreview');
  const calcTotalPreview = document.getElementById('calcTotalPreview');
  const fundsSubmitBtn = document.getElementById('fundsSubmitBtn');
  const fundsSubmitLabel = document.getElementById('fundsSubmitLabel');
  const fundsFootnote = document.getElementById('fundsFootnote');

  const transferConfirmOverlay = document.getElementById('transferConfirmOverlay');
  const confirmTransferBtn = document.getElementById('confirmTransferBtn');
  const cancelTransferBtn = document.getElementById('cancelTransferBtn');
  const transferConfirmCloseBtn = document.getElementById('transferConfirmCloseBtn');
  const confirmRecipientEl = document.getElementById('confirmRecipient');
  const confirmAmountEl = document.getElementById('confirmAmount');
  const confirmFeeEl = document.getElementById('confirmFee');
  const confirmTotalEl = document.getElementById('confirmTotal');

  let unifiedConfirmed = false;

  function highlightFieldError(field, msg) {
    window.BankErrorModal.show(msg);
    if (field) {
      field.classList.add('input-error-highlight');
      window.BankErrorModal.setRetryFn(() => {
        field.focus();
        setTimeout(() => field.classList.remove('input-error-highlight'), 2000);
      });
    }
  }

  function updateFundsCalculations() {
    if (!unifiedForm) return;
    const isTransfer = (opInput ? opInput.value : 'TRANSFER') === 'TRANSFER';
    const selectedCur = normalizeCur(fundsCurrencySelect ? fundsCurrencySelect.value : 'AZN');
    const accountCur = normalizeCur(unifiedForm.getAttribute('data-account-currency') || 'AZN');
    const sym = CURRENCY_SYMBOLS[selectedCur];

    if (fundsCurrencyPrefix) fundsCurrencyPrefix.textContent = sym;

    document.querySelectorAll('.funds-preset').forEach(btn => {
      const rawAmt = parseFloat(btn.getAttribute('data-amount')) || 0;
      const formattedAmt = rawAmt >= 1000
        ? rawAmt.toLocaleString('en-US', { maximumFractionDigits: 0 })
        : String(Math.round(rawAmt));
      btn.textContent = '+' + sym + formattedAmt;
    });

    const gross = parseFloat(fundsAmountInput ? fundsAmountInput.value : 0) || 0;
    const aznEq = convertAmount(gross, selectedCur, 'AZN');
    const senderGross = convertAmount(gross, selectedCur, accountCur);
    const fee = isTransfer ? calcTransferFee(senderGross) : 0;
    const total = gross > 0 ? Math.round((senderGross + fee) * 100) / 100 : 0;

    if (calcGrossPreview) calcGrossPreview.textContent = formatMoney(gross > 0 ? gross : 0, selectedCur);
    if (calcAznPreview) calcAznPreview.textContent = formatMoney(gross > 0 ? aznEq : 0, 'AZN');
    if (calcFeePreview) calcFeePreview.textContent = formatMoney(fee, accountCur);
    if (calcTotalPreview) calcTotalPreview.textContent = formatMoney(total, accountCur);

    if (fundsLimitInfo) {
      if (isTransfer) {
        fundsLimitInfo.textContent = `Gündəlik limit: 50,000.00 ${sym}/gün • Komissiya (hesab valyutası ${accountCur}): ≤10: 0.02 • ≤100: 0.05 • >100: 0.10`;
      } else {
        fundsLimitInfo.textContent = `Gündəlik limit: 25,000.00 ${sym}/gün • Çıxarış / ödəniş komissiyası: 0.00 (Komissiyasız) • Köçürmə komissiyası: ≤10: 0.02 • ≤100: 0.05 • >100: 0.10`;
      }
    }
  }

  function syncUnifiedFormState() {
    if (!unifiedForm) return;
    const op = opInput ? opInput.value : 'TRANSFER';
    const isTransfer = op === 'TRANSFER';
    const mode = recTypeInput ? recTypeInput.value : 'INDIVIDUAL';
    const isCorp = mode === 'CORPORATE';

    opTabBtns.forEach(b => {
      const active = b.getAttribute('data-op') === op;
      b.classList.toggle('active', active);
      b.setAttribute('aria-selected', active ? 'true' : 'false');
    });

    recToggleBtns.forEach(b => {
      b.classList.toggle('active', b.getAttribute('data-mode') === mode);
    });

    const transferAction = unifiedForm.getAttribute('data-transfer-action') || '/account/transfer';
    const withdrawAction = unifiedForm.getAttribute('data-withdraw-action') || '/account/withdraw';
    unifiedForm.setAttribute('action', isTransfer ? transferAction : withdrawAction);

    if (fundsIndFields) fundsIndFields.classList.toggle('is-visible', !isCorp);
    if (fundsCorpFields) fundsCorpFields.classList.toggle('is-visible', isCorp);

    if (fundsRecAccount) {
      fundsRecAccount.disabled = isCorp;
      fundsRecAccount.required = !isCorp && isTransfer;
    }
    if (fundsCompanyName) {
      fundsCompanyName.disabled = !isCorp;
      fundsCompanyName.required = isCorp;
    }

    if (fundsCardTitle) {
      fundsCardTitle.textContent = isTransfer ? 'Pul Göndərmə (Transfer)' : 'Nağd Çıxarış / Ödəniş (Withdraw Funds)';
    }
    if (fundsCardSubtitle) {
      fundsCardSubtitle.textContent = isTransfer
        ? 'Şəxslərə və ya Şirkətlərə təhlükəsiz pul köçürməsi'
        : 'Şəxslərə və ya Şirkətlərə ödəniş / nağd çıxarış';
    }
    if (fundsHeaderIcon) {
      if (isTransfer) {
        fundsHeaderIcon.style.background = 'rgba(99, 102, 241, 0.16)';
        fundsHeaderIcon.style.color = '#6366f1';
      } else {
        fundsHeaderIcon.style.background = 'rgba(16, 185, 129, 0.16)';
        fundsHeaderIcon.style.color = '#059669';
      }
    }
    if (fundsRecTypeLabel) {
      fundsRecTypeLabel.textContent = isTransfer ? 'Təyinat Növü (Kimə göndərilir?)' : 'Təyinat Növü (Çıxarış / Ödəniş kimə?)';
    }
    if (fundsIndLabel) {
      fundsIndLabel.textContent = isTransfer
        ? 'Alıcı Hesab No və ya @İstifadəçi adı'
        : 'Şəxsə aid Alıcı Hesab No və ya @İstifadəçi adı (İxtiyari)';
    }
    if (fundsIndHint) {
      fundsIndHint.textContent = isTransfer
        ? 'Məs: 4098 7056 ... və ya @username'
        : 'ATM/Nağd çıxarışda boş qala bilər';
    }
    if (fundsAmountLabel) {
      fundsAmountLabel.textContent = isTransfer ? 'Köçürmə Məbləği' : 'Çıxarış / Ödəniş Məbləği';
    }
    if (fundsFeeHint) {
      fundsFeeHint.textContent = isTransfer
        ? '≤10: 0.02 • ≤100: 0.05 • >100: 0.10'
        : 'Komissiyasız • ≤10: 0.02 • ≤100: 0.05 • >100: 0.10';
    }
    if (fundsAmountInput) {
      fundsAmountInput.min = isTransfer ? '0.01' : '1.00';
      fundsAmountInput.max = isTransfer ? '50000.00' : '25000.00';
    }
    if (calcGrossLabel) {
      calcGrossLabel.textContent = isTransfer ? 'Köçürüləcək məbləğ:' : 'Çıxarış / Ödəniş məbləği:';
    }
    if (fundsSubmitBtn) {
      fundsSubmitBtn.classList.toggle('is-transfer', isTransfer);
      fundsSubmitBtn.classList.toggle('is-withdraw', !isTransfer);
    }
    if (fundsSubmitLabel) {
      fundsSubmitLabel.textContent = isTransfer ? 'Pul Göndər / Transfer' : 'Çıxarış Et / Withdraw';
    }
    if (fundsFootnote) {
      fundsFootnote.textContent = isTransfer
        ? '256-bit SSL encrypted • Instant transfer settlement'
        : 'Zero withdrawal fees • Instant settlement available';
    }

    updateFundsCalculations();
  }

  function closeTransferConfirmModal() {
    unifiedConfirmed = false;
    if (transferConfirmOverlay) {
      transferConfirmOverlay.classList.remove('is-open');
      document.body.style.overflow = '';
    }
  }

  if (unifiedForm) {
    opTabBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        if (opInput) opInput.value = btn.getAttribute('data-op') || 'TRANSFER';
        syncUnifiedFormState();
      });
    });

    recToggleBtns.forEach(btn => {
      btn.addEventListener('click', () => {
        if (recTypeInput) recTypeInput.value = btn.getAttribute('data-mode') || 'INDIVIDUAL';
        syncUnifiedFormState();
      });
    });

    if (fundsCurrencySelect) {
      fundsCurrencySelect.addEventListener('change', updateFundsCalculations);
    }
    if (fundsAmountInput) {
      fundsAmountInput.addEventListener('input', updateFundsCalculations);
    }

    attachCardMaskPreview(fundsRecAccount, 'recipientCardMaskPreview');
    syncUnifiedFormState();

    unifiedForm.addEventListener('submit', (e) => {
      if (unifiedConfirmed) {
        unifiedConfirmed = false;
        return;
      }

      const op = opInput ? opInput.value : 'TRANSFER';
      const isTransfer = op === 'TRANSFER';
      const mode = recTypeInput ? recTypeInput.value : 'INDIVIDUAL';
      const isCorp = mode === 'CORPORATE';
      const selectedCur = normalizeCur(fundsCurrencySelect ? fundsCurrencySelect.value : 'AZN');
      const accountCur = normalizeCur(unifiedForm.getAttribute('data-account-currency') || 'AZN');
      const availBalanceAttr = unifiedForm.getAttribute('data-account-balance');
      const availBalance = availBalanceAttr !== null && availBalanceAttr !== '' ? parseFloat(availBalanceAttr) : NaN;

      const companyVal = fundsCompanyName ? fundsCompanyName.value.trim() : '';
      const recAccVal = fundsRecAccount ? fundsRecAccount.value.trim() : '';
      const gross = parseFloat(fundsAmountInput ? fundsAmountInput.value : 0) || 0;

      if (isCorp) {
        if (!companyVal) {
          e.preventDefault();
          e.stopImmediatePropagation();
          highlightFieldError(fundsCompanyName, 'Şirkət adı / Müəssisə daxil edilməlidir');
          return;
        }
      } else if (isTransfer && !recAccVal) {
        e.preventDefault();
        e.stopImmediatePropagation();
        highlightFieldError(fundsRecAccount, 'Alıcı hesab nömrəsi və ya @istifadəçi adı daxil edilməlidir');
        return;
      }

      if (gross <= 0) {
        e.preventDefault();
        e.stopImmediatePropagation();
        highlightFieldError(fundsAmountInput, isTransfer ? 'Köçürmə məbləği müsbət olmalıdır' : 'Çıxarış məbləği müsbət olmalıdır');
        return;
      }
      if (!isTransfer && gross < 1) {
        e.preventDefault();
        e.stopImmediatePropagation();
        highlightFieldError(fundsAmountInput, 'Minimum çıxarış məbləği 1.00 olmalıdır');
        return;
      }
      const maxLim = isTransfer ? 50000 : 25000;
      if (gross > maxLim) {
        e.preventDefault();
        e.stopImmediatePropagation();
        highlightFieldError(fundsAmountInput, `Maksimum gündəlik limit ${maxLim.toLocaleString('en-US')} ${CURRENCY_SYMBOLS[selectedCur]} təşkil edir`);
        return;
      }

      const senderGross = convertAmount(gross, selectedCur, accountCur);
      const fee = isTransfer ? calcTransferFee(senderGross) : 0;
      const total = Math.round((senderGross + fee) * 100) / 100;

      if (!isNaN(availBalance) && (total - availBalance) > 0.0001) {
        e.preventDefault();
        e.stopImmediatePropagation();
        highlightFieldError(
          fundsAmountInput,
          `Kifayət qədər balans yoxdur / Insufficient balance! Tələb olunan: ${formatMoney(total, accountCur)} • Mövcud balans: ${formatMoney(availBalance, accountCur)}`
        );
        return;
      }

      if (!isTransfer) {
        // Withdraw is validated cleanly and submits directly
        return;
      }

      // Transfer shows confirmation modal
      e.preventDefault();
      e.stopImmediatePropagation();

      const recipientDisplay = isCorp ? companyVal : recAccVal;

      if (confirmRecipientEl) confirmRecipientEl.textContent = recipientDisplay || '—';
      if (confirmAmountEl) confirmAmountEl.textContent = formatMoney(gross, selectedCur);
      if (confirmFeeEl) confirmFeeEl.textContent = formatMoney(fee, accountCur);
      if (confirmTotalEl) confirmTotalEl.textContent = formatMoney(total, accountCur);

      if (transferConfirmOverlay) {
        transferConfirmOverlay.classList.add('is-open');
        document.body.style.overflow = 'hidden';
        if (confirmTransferBtn) setTimeout(() => confirmTransferBtn.focus(), 100);
      } else {
        const agreed = window.confirm(
          "PUL KÖÇÜRMƏSİNİN TƏSDİQİ:\n\n" +
          "Qəbuledici: " + recipientDisplay + "\n" +
          "Köçürüləcək məbləğ: " + formatMoney(gross, selectedCur) + "\n" +
          "Komissiya haqqı: " + formatMoney(fee, accountCur) + "\n" +
          "Balansdan ümumi çıxılacaq: " + formatMoney(total, accountCur) + "\n\n" +
          "Əməliyyatı təsdiq edirsiniz?"
        );
        if (agreed) {
          unifiedConfirmed = true;
          HTMLFormElement.prototype.submit.call(unifiedForm);
        }
      }
    });
  }

  if (confirmTransferBtn) {
    confirmTransferBtn.addEventListener('click', () => {
      unifiedConfirmed = true;
      if (transferConfirmOverlay) {
        transferConfirmOverlay.classList.remove('is-open');
        document.body.style.overflow = '';
      }
      if (unifiedForm) {
        HTMLFormElement.prototype.submit.call(unifiedForm);
      }
    });
  }

  if (cancelTransferBtn) {
    cancelTransferBtn.addEventListener('click', closeTransferConfirmModal);
  }

  if (transferConfirmCloseBtn) {
    transferConfirmCloseBtn.addEventListener('click', closeTransferConfirmModal);
  }

  if (transferConfirmOverlay) {
    transferConfirmOverlay.addEventListener('click', (e) => {
      if (e.target === transferConfirmOverlay) {
        closeTransferConfirmModal();
      }
    });
  }

  document.addEventListener('keydown', (e) => {
    if (e.key === 'Escape' && transferConfirmOverlay && transferConfirmOverlay.classList.contains('is-open')) {
      closeTransferConfirmModal();
    }
  });
});
