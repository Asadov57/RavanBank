/**
 * AsadovBank Global UI Helper:
 * 1) Disables browser saved-word autocomplete popups on all inputs/forms/textareas.
 * 2) Submits inter-panel feedback messages (/panel-messages/send) in-place via AJAX without page jump.
 * 3) Preserves exact scroll position and active tab across all form submissions so the page never jumps back to top.
 */
(function () {
  'use strict';

  const SCROLL_PATH_KEY = 'agy_keep_scroll_path';
  const SCROLL_Y_KEY = 'agy_keep_scroll_y';
  const SEC_TAB_BTN_KEY = 'agy_sec_active_tab_btn';
  const SEC_TAB_SEC_KEY = 'agy_sec_active_tab_sec';

  // 1. Disable browser autocomplete / saved history dropdowns on every form & field
  function disableAutocompleteOnElement(root) {
    if (!root || !root.querySelectorAll) return;

    const forms = root.tagName === 'FORM' ? [root] : Array.from(root.querySelectorAll('form'));
    forms.forEach(form => {
      form.setAttribute('autocomplete', 'off');
    });

    const fields = root.querySelectorAll('input, textarea');
    fields.forEach(el => {
      const type = (el.getAttribute('type') || 'text').toLowerCase();
      if (type === 'hidden' || type === 'submit' || type === 'button' || type === 'checkbox' || type === 'radio' || type === 'file') {
        return;
      }

      el.setAttribute('autocorrect', 'off');
      el.setAttribute('autocapitalize', 'off');
      el.setAttribute('spellcheck', 'false');
      el.setAttribute('aria-autocomplete', 'none');
      el.setAttribute('data-lpignore', 'true');
      el.setAttribute('data-1p-ignore', 'true');
      el.setAttribute('data-form-type', 'other');

      if (el.tagName === 'TEXTAREA') {
        el.setAttribute('autocomplete', 'off');
        return;
      }

      if (type === 'password') {
        el.setAttribute('autocomplete', 'new-password');
      } else {
        // 'one-time-code' prevents Chromium from showing saved word history popups
        el.setAttribute('autocomplete', 'one-time-code');
      }

      // Guard against Chromium saved-history dropdown popup on focus
      if (!el.dataset.agyNoAutofillBound && !el.hasAttribute('readonly')) {
        el.dataset.agyNoAutofillBound = '1';
        if (document.activeElement !== el) {
          el.readOnly = true;
        }
        const unlock = () => {
          if (el.readOnly) {
            el.readOnly = false;
          }
        };
        el.addEventListener('pointerdown', unlock, { passive: true });
        el.addEventListener('mousedown', unlock, { passive: true });
        el.addEventListener('touchstart', unlock, { passive: true });
        el.addEventListener('focus', unlock, { passive: true });
        el.addEventListener('keydown', unlock, { passive: true });
        el.addEventListener('blur', () => {
          setTimeout(() => {
            if (document.activeElement !== el) {
              el.readOnly = true;
            }
          }, 120);
        }, { passive: true });
      }
    });
  }

  function unlockAllInputsInForm(form) {
    if (!form || !form.querySelectorAll) return;
    form.querySelectorAll('input, textarea').forEach(el => {
      if (el.dataset && el.dataset.agyNoAutofillBound === '1') {
        el.readOnly = false;
      }
    });
  }

  // Helper to render panel badge HTML matching the existing tables
  function renderPanelBadge(panel) {
    const p = (panel || '').toUpperCase();
    if (p === 'ADMIN') {
      return '<span style="background: rgba(245, 158, 11, 0.18); color: #fbbf24; padding: 2px 8px; border-radius: 6px; font-size: 0.72rem; font-weight: 700; display: inline-block;">Admin Panel</span>';
    }
    if (p === 'SECURITY') {
      return '<span style="background: rgba(56, 189, 248, 0.18); color: #38bdf8; padding: 2px 8px; border-radius: 6px; font-size: 0.72rem; font-weight: 700; display: inline-block;">Security Paneli</span>';
    }
    if (p === 'INQUIRY') {
      return '<span style="background: rgba(168, 85, 247, 0.18); color: #c084fc; padding: 2px 8px; border-radius: 6px; font-size: 0.72rem; font-weight: 700; display: inline-block;">Sorğu Paneli</span>';
    }
    if (p === 'TREASURY') {
      return '<span style="background: rgba(16, 185, 129, 0.18); color: #34d399; padding: 2px 8px; border-radius: 6px; font-size: 0.72rem; font-weight: 700; display: inline-block;">Xəzinədar Paneli</span>';
    }
    return '<span>' + escapeHtml(p) + '</span>';
  }

  function escapeHtml(str) {
    return String(str == null ? '' : str)
      .replace(/&/g, '&amp;')
      .replace(/</g, '&lt;')
      .replace(/>/g, '&gt;')
      .replace(/"/g, '&quot;')
      .replace(/'/g, '&#39;');
  }

  function showInlineFormStatus(form, text, isError) {
    let box = form.querySelector('.agy-inline-msg-status');
    if (!box) {
      box = document.createElement('div');
      box.className = 'agy-inline-msg-status';
      box.style.marginTop = '0.75rem';
      box.style.padding = '0.65rem 0.95rem';
      box.style.borderRadius = '10px';
      box.style.fontSize = '0.84rem';
      box.style.fontWeight = '700';
      form.appendChild(box);
    }
    if (isError) {
      box.style.background = 'rgba(239, 68, 68, 0.16)';
      box.style.border = '1px solid rgba(239, 68, 68, 0.4)';
      box.style.color = '#fca5a5';
    } else {
      box.style.background = 'rgba(16, 185, 129, 0.16)';
      box.style.border = '1px solid rgba(16, 185, 129, 0.4)';
      box.style.color = '#34d399';
    }
    box.textContent = (isError ? '❌ ' : '✅ ') + text;
    box.style.display = 'block';
    clearTimeout(box._hideTimer);
    box._hideTimer = setTimeout(() => {
      box.style.display = 'none';
    }, 4500);
  }

  // 2. Intercept /panel-messages/send forms so sending a message stays 100% in place without page reload/jump
  function bindPanelMessageAjaxForms(root) {
    if (!root || !root.querySelectorAll) return;
    const forms = root.querySelectorAll('form[action*="/panel-messages/send"]');
    forms.forEach(form => {
      if (form.dataset.agyAjaxPanelMsgBound === '1') return;
      form.dataset.agyAjaxPanelMsgBound = '1';

      form.addEventListener('submit', async function (e) {
        e.preventDefault();
        unlockAllInputsInForm(form);

        const submitBtn = form.querySelector('button[type="submit"]');
        const prevDisabled = submitBtn ? submitBtn.disabled : false;
        if (submitBtn) submitBtn.disabled = true;

        try {
          const formData = new FormData(form);
          const csrfInput = form.querySelector('input[name="_csrf"]');
          const headers = {
            'X-Requested-With': 'XMLHttpRequest',
            'Accept': 'application/json'
          };
          if (csrfInput && csrfInput.value) {
            headers['X-CSRF-TOKEN'] = csrfInput.value;
          }

          const res = await fetch(form.getAttribute('action') || '/panel-messages/send', {
            method: 'POST',
            headers: headers,
            body: new URLSearchParams(formData)
          });

          const data = await res.json();
          if (!res.ok || !data || !data.success) {
            showInlineFormStatus(form, (data && data.message) || 'Mesaj göndərilərkən xəta baş verdi.', true);
            return;
          }

          // Clear subject & message inputs in place
          const subjEl = form.querySelector('[name="subject"]');
          const msgEl = form.querySelector('[name="message"]');
          if (subjEl) subjEl.value = '';
          if (msgEl) msgEl.value = '';

          showInlineFormStatus(form, data.statusMessage || 'Mesaj uğurla göndərildi.', false);

          // Prepend row into the sibling table's tbody
          const container = form.closest('section, .panel-card, .inq-table-card, #panelMessagesSection') || form.parentElement;
          const tbody = container ? container.querySelector('table tbody') : null;
          if (tbody) {
            const emptyCell = tbody.querySelector('td[colspan]');
            if (emptyCell && emptyCell.parentElement) {
              emptyCell.parentElement.remove();
            }
            const tr = document.createElement('tr');
            const senderTag = data.senderUsername
              ? '<div style="color: #64748b; font-size: 0.75rem; margin-top: 2px;">@' + escapeHtml(data.senderUsername) + '</div>'
              : '';
            tr.innerHTML =
              '<td style="color: #94a3b8; font-size: 0.8rem; white-space: nowrap;">' + escapeHtml(data.createdAt || 'İndi') + '</td>' +
              '<td>' + renderPanelBadge(data.fromPanel) + senderTag + '</td>' +
              '<td>' + renderPanelBadge(data.toPanel) + '</td>' +
              '<td><strong style="color: #fff; font-size: 0.86rem; display: block;">' + escapeHtml(data.subject) + '</strong>' +
              '<div style="color: #cbd5e1; font-size: 0.82rem; white-space: pre-wrap; margin-top: 4px;">' + escapeHtml(data.message) + '</div></td>';
            tbody.insertBefore(tr, tbody.firstChild);
          }
        } catch (err) {
          showInlineFormStatus(form, 'Şəbəkə xətası: ' + (err && err.message ? err.message : 'Yenidən cəhd edin'), true);
        } finally {
          if (submitBtn) submitBtn.disabled = prevDisabled;
        }
      });
    });
  }

  // 3. Preserve scroll position & active Security tab across any standard form submit
  function wrapSecurityTabSwitch() {
    if (typeof window.switchSecTab === 'function' && !window.switchSecTab._agyWrapped) {
      const orig = window.switchSecTab;
      const wrapped = function (btnId, secId) {
        try {
          sessionStorage.setItem(SEC_TAB_BTN_KEY, btnId);
          sessionStorage.setItem(SEC_TAB_SEC_KEY, secId);
        } catch (_) {}
        return orig.apply(this, arguments);
      };
      wrapped._agyWrapped = true;
      window.switchSecTab = wrapped;
    }
  }

  function restoreSecurityTabAndScroll() {
    wrapSecurityTabSwitch();

    const hash = (window.location.hash || '').replace(/^#/, '');
    const hashToBtn = {
      panelMessagesSection: 'tabPanelMessagesBtn',
      registrationsSection: 'tabRegistrationsBtn',
      accountsSection: 'tabAccountsBtn',
      messageSection: 'tabMessageBtn',
      messagesSection: 'tabMessageBtn',
      notificationsSection: 'tabNotificationsBtn',
      loansSection: 'tabLoansBtn',
      employeesSection: 'tabEmployeesBtn'
    };

    if (typeof window.switchSecTab === 'function') {
      if (hash && hashToBtn[hash]) {
        const targetSec = hash === 'messagesSection' ? 'messageSection' : hash;
        window.switchSecTab(hashToBtn[hash], targetSec);
      } else {
        try {
          const savedBtn = sessionStorage.getItem(SEC_TAB_BTN_KEY);
          const savedSec = sessionStorage.getItem(SEC_TAB_SEC_KEY);
          if (savedBtn && savedSec && document.getElementById(savedSec)) {
            window.switchSecTab(savedBtn, savedSec);
          }
        } catch (_) {}
      }
    }

    try {
      const savedPath = sessionStorage.getItem(SCROLL_PATH_KEY);
      const savedY = sessionStorage.getItem(SCROLL_Y_KEY);
      if (savedPath === window.location.pathname && savedY !== null) {
        sessionStorage.removeItem(SCROLL_PATH_KEY);
        sessionStorage.removeItem(SCROLL_Y_KEY);
        const y = parseInt(savedY, 10);
        if (!isNaN(y) && y > 0) {
          if ('scrollRestoration' in history) {
            history.scrollRestoration = 'manual';
          }
          window.scrollTo(0, y);
          requestAnimationFrame(() => window.scrollTo(0, y));
          setTimeout(() => window.scrollTo(0, y), 50);
        }
      }
    } catch (_) {}
  }

  document.addEventListener('submit', function (e) {
    const form = e.target;
    if (!form || form.tagName !== 'FORM') return;
    unlockAllInputsInForm(form);

    const action = (form.getAttribute('action') || '').toLowerCase();
    if (action.includes('/logout') || action.includes('/login') || action.includes('/verify')) {
      return;
    }
    try {
      sessionStorage.setItem(SCROLL_PATH_KEY, window.location.pathname);
      sessionStorage.setItem(SCROLL_Y_KEY, String(window.scrollY || window.pageYOffset || 0));
    } catch (_) {}
  }, true);

  function init() {
    disableAutocompleteOnElement(document);
    bindPanelMessageAjaxForms(document);
    restoreSecurityTabAndScroll();

    if (typeof MutationObserver !== 'undefined' && document.body) {
      const obs = new MutationObserver(mutations => {
        for (const m of mutations) {
          m.addedNodes.forEach(node => {
            if (node && node.nodeType === 1) {
              disableAutocompleteOnElement(node);
              bindPanelMessageAjaxForms(node);
            }
          });
        }
      });
      obs.observe(document.body, { childList: true, subtree: true });
    }
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', init);
  } else {
    init();
  }
  window.addEventListener('load', restoreSecurityTabAndScroll);
})();
