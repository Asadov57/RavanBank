/**
 * Banking Admin Dashboard - Minimal Vanilla JavaScript
 * Strictly for UI enhancement & DOM toggles (NO AJAX / fetch calls).
 * 100% compatible with traditional SSR MVC architectures.
 */

document.addEventListener('DOMContentLoaded', () => {

  // 1. Dynamic Currency Symbol Sync in Create Account Form
  const currencySelect = document.getElementById('currencySelect');
  const currencyPrefix = document.getElementById('currencyPrefix');
  const currencySymbols = {
    'USD': '$',
    'EUR': '€',
    'GBP': '£',
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

  // 2. Client-side Search Filter (Instant in-memory DOM filter, No fetch)
  const searchInput = document.getElementById('adminSearchInput');
  const tableRows = document.querySelectorAll('.admin-data-table tbody tr');

  function applyFilters() {
    const query = searchInput ? searchInput.value.toLowerCase().trim() : '';
    const activeTab = document.querySelector('.filter-tab.active');
    const statusFilter = activeTab ? activeTab.getAttribute('data-status') : 'all';

    tableRows.forEach(row => {
      const rowStatus = row.getAttribute('data-status');
      const textContent = row.textContent.toLowerCase();

      const matchesStatus = (statusFilter === 'all' || rowStatus === statusFilter);
      const matchesSearch = (!query || textContent.includes(query));

      if (matchesStatus && matchesSearch) {
        row.style.display = '';
      } else {
        row.style.display = 'none';
      }
    });
  }

  if (searchInput) {
    searchInput.addEventListener('input', applyFilters);
  }

  // 3. Status Tab Filtering (All / Active / Blocked / Pending)
  const filterTabs = document.querySelectorAll('.filter-tab');
  filterTabs.forEach(tab => {
    tab.addEventListener('click', () => {
      filterTabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');
      applyFilters();
    });
  });

  // 4. Dismissible Alert Banners
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

});
