(function () {
  function disableAutofillEverywhere() {
    document.querySelectorAll('form').forEach(function (form) {
      form.setAttribute('autocomplete', 'off');
    });

    document.querySelectorAll('input, textarea').forEach(function (el) {
      var type = (el.getAttribute('type') || '').toLowerCase();
      if (
        type === 'hidden' ||
        type === 'checkbox' ||
        type === 'radio' ||
        type === 'submit' ||
        type === 'button' ||
        type === 'reset' ||
        type === 'file'
      ) {
        return;
      }
      el.setAttribute('aria-autocomplete', 'none');
      if (type === 'password') {
        el.setAttribute('autocomplete', 'new-password');
      } else {
        el.setAttribute('autocomplete', 'off');
      }
    });
  }

  function setupScrollAndTabPreservation() {
    var path = window.location.pathname || '/';
    var keyY = 'bank_preserve_scroll_y';
    var keyPath = 'bank_preserve_scroll_path';
    var keyTs = 'bank_preserve_scroll_ts';

    // Restore position if returning from a form submit on the same path
    try {
      var savedPath = sessionStorage.getItem(keyPath);
      var savedY = sessionStorage.getItem(keyY);
      var savedTs = Number(sessionStorage.getItem(keyTs) || '0');
      if (savedPath === path && savedY !== null && Date.now() - savedTs < 20000) {
        var y = parseInt(savedY, 10);
        sessionStorage.removeItem(keyY);
        sessionStorage.removeItem(keyPath);
        sessionStorage.removeItem(keyTs);

        if (path.indexOf('/admin/security') !== -1) {
          var tabBtn = sessionStorage.getItem('sec_active_tab_btn');
          var tabSec = sessionStorage.getItem('sec_active_tab_sec');
          if (tabBtn && tabSec && typeof window.switchSecTab === 'function') {
            window.switchSecTab(tabBtn, tabSec);
          }
        }

        if (!isNaN(y) && y > 0) {
          if ('scrollRestoration' in history) {
            history.scrollRestoration = 'manual';
          }
          var applyScroll = function () {
            window.scrollTo({ top: y, left: 0, behavior: 'instant' });
          };
          applyScroll();
          requestAnimationFrame(applyScroll);
          setTimeout(applyScroll, 60);
        }
      }
    } catch (e) {}

    // Save position before non-logout form submission
    document.addEventListener('submit', function (e) {
      var form = e.target;
      if (!form || form.tagName !== 'FORM') return;
      var action = (form.getAttribute('action') || '').toLowerCase();
      if (action.indexOf('/logout') !== -1) return;
      try {
        sessionStorage.setItem(keyY, String(window.scrollY || window.pageYOffset || 0));
        sessionStorage.setItem(keyPath, path);
        sessionStorage.setItem(keyTs, String(Date.now()));
      } catch (err) {}
    }, true);
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', function () {
      disableAutofillEverywhere();
      setupScrollAndTabPreservation();
    });
  } else {
    disableAutofillEverywhere();
    setupScrollAndTabPreservation();
  }
})();
