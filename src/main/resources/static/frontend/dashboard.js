
document.addEventListener('DOMContentLoaded', () => {
  const triggers = document.querySelectorAll('.user-trigger');
  triggers.forEach(trigger => {
    trigger.addEventListener('click', e => {
      if (window.innerWidth >= 992) return; 
      e.stopPropagation();
      const item = trigger.closest('.user-menu-item');
      const wasOpen = item.classList.contains('open');
      document.querySelectorAll('.user-menu-item.open').forEach(el => el.classList.remove('open'));
      if (!wasOpen) item.classList.add('open');
    });
  });

  document.addEventListener('click', e => {
    if (window.innerWidth >= 992) return;
    if (!e.target.closest('.user-menu-item')) {
      document.querySelectorAll('.user-menu-item.open').forEach(el => el.classList.remove('open'));
    }
  });
});

const dealsList = document.getElementById('deals-list');
const form = document.getElementById('new-deal-form');
const modal = document.getElementById('create-deal-modal');
const dealsMessage = document.getElementById('deals-message');
const API_DEALS = '/api/deals';
const MAX_PHOTO_BYTES = 10 * 1024 * 1024; // 10MB
const REQUEST_TIMEOUT_MS = 30000;

function openModal() {
  if (!modal) return;
  modal.classList.add('active');
  modal.style.visibility = 'visible';
  modal.style.opacity = '1';
}

function closeModal() {
  if (!modal) return;
  modal.classList.remove('active');
  modal.style.visibility = '';
  modal.style.opacity = '';
}

async function loadDeals() {
  dealsList.innerHTML = '';
  if (dealsMessage) dealsMessage.textContent = '';
  try {
    const res = await fetch(API_DEALS, {
      headers: { 'Accept': 'application/json' },
      credentials: 'same-origin'
    });
    if (!res.ok) {
      if (dealsMessage) {
        dealsMessage.textContent = res.status === 401
          ? 'Please log in again to view your deals.'
          : 'Could not load deals.';
      } else {
        dealsList.innerHTML = '<div class="no-deals">Could not load deals</div>';
      }
      return;
    }
    const deals = await res.json();
    if (!Array.isArray(deals) || deals.length === 0) {
      dealsList.innerHTML = '<div class="no-deals">No active deals yet</div>';
      return;
    }
    deals.forEach(deal => {
      const card = document.createElement('div');
      const status = (deal.status || 'Pending Approval');
      const isApproved = status.toLowerCase() === 'approved';
      card.className = `deal-card ${status.toLowerCase().replace(/\s+/g, '-')}`;
      card.dataset.dealId = deal.id;
      card.dataset.status = status;
      card.innerHTML = `
        <div class="deal-title">${deal.title || 'Untitled Deal'}</div>
        <div class="deal-status">${status}</div>
        <div class="deal-value">NGN ${Number(deal.value || 0).toLocaleString()}</div>
        ${isApproved ? `<a class="btn-submit deal-details-link" href="/dashboard/deal/${deal.id}">See Details</a>` : ''}
      `;
      dealsList.appendChild(card);
    });
    makeDealsClickable();
  } catch (e) {
    if (dealsMessage) {
      dealsMessage.textContent = 'Could not load deals.';
    } else {
      dealsList.innerHTML = '<div class="no-deals">Could not load deals</div>';
    }
  }
}

async function saveDeal(formData) {
  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  let res;
  try {
    res = await fetch(API_DEALS, {
      method: 'POST',
      body: formData,
      credentials: 'same-origin',
      signal: controller.signal
    });
  } catch (err) {
    if (err && err.name === 'AbortError') {
      throw new Error('Request timed out. Please try again.');
    }
    throw err;
  } finally {
    clearTimeout(timeout);
  }

  const contentType = res.headers.get('content-type') || '';
  const data = contentType.includes('application/json')
    ? await res.json().catch(() => ({}))
    : { message: (await res.text().catch(() => '')) };

  if (!res.ok) {
    const msg = data && data.message ? data.message : 'Failed to create deal';
    throw new Error(msg);
  }
  await loadDeals();
}

form?.addEventListener('submit', async e => {
  e.preventDefault();

  const formData = new FormData(form);
  const submitBtn = form.querySelector('button[type="submit"]');
  const originalBtnText = submitBtn ? submitBtn.textContent : '';
  const title = formData.get('deal-title');
  const client = formData.get('client-name');
  const value = formData.get('deal-value');
  const photo = formData.get('itemPhoto');

  if (!title || !client || !value) {
    if (dealsMessage) dealsMessage.textContent = 'Please fill all required fields.';
    return;
  }
  if (photo && photo.size && photo.size > MAX_PHOTO_BYTES) {
    if (dealsMessage) dealsMessage.textContent = 'Image is too large. Max 10MB.';
    return;
  }

  try {
    if (submitBtn) {
      submitBtn.disabled = true;
      submitBtn.textContent = 'Saving...';
    }
    await saveDeal(formData);
    closeModal();
    form.reset();
    if (dealsMessage) {
      dealsMessage.textContent = 'Deal created! It is now Pending Approval.';
    }
  } catch (err) {
    if (dealsMessage) dealsMessage.textContent = err.message || 'Failed to create deal';
  } finally {
    if (submitBtn) {
      submitBtn.disabled = false;
      submitBtn.textContent = originalBtnText || 'Lock Deal';
    }
  }
});


document.getElementById('open-create-modal')?.addEventListener('click', openModal);
document.getElementById('close-modal')?.addEventListener('click', closeModal);
document.getElementById('cancel-create')?.addEventListener('click', closeModal);
modal?.addEventListener('click', e => {
  if (e.target === modal) closeModal();
});

loadDeals();

const fileInput = document.getElementById('item-photo');
const uploadArea = document.getElementById('upload-area');
const previewContainer = document.getElementById('preview-container');
const previewImg = document.getElementById('preview-img');
const removeBtn = document.getElementById('remove-preview');

function showPreview(file) {
  if (!file.type.startsWith('image/')) {
    alert('Please select an image file');
    return;
  }

  const reader = new FileReader();
  reader.onload = e => {
    previewImg.src = e.target.result;
    previewContainer.style.display = 'block';
    uploadArea.style.display = 'none';
  };
  reader.readAsDataURL(file);
}

function clearPreview() {
  previewContainer.style.display = 'none';
  uploadArea.style.display = 'block';
  fileInput.value = '';
}


fileInput.addEventListener('change', e => {
  if (e.target.files[0]) {
    showPreview(e.target.files[0]);
  }
});


uploadArea.addEventListener('dragover', e => {
  e.preventDefault();
  uploadArea.classList.add('dragover');
});

uploadArea.addEventListener('dragleave', () => {
  uploadArea.classList.remove('dragover');
});

uploadArea.addEventListener('drop', e => {
  e.preventDefault();
  uploadArea.classList.remove('dragover');
  if (e.dataTransfer.files[0]) {
    fileInput.files = e.dataTransfer.files;
    showPreview(e.dataTransfer.files[0]);
  }
});


removeBtn.addEventListener('click', clearPreview);


const valueInput     = document.getElementById('deal-value');
const weeksSelect    = document.getElementById('weeks');
const customWeeks    = document.getElementById('custom-weeks');
const customGroup    = document.getElementById('custom-weeks-group');
const extraFeeRow    = document.getElementById('extra-fee-row');
const breakdown      = document.getElementById('breakdown');

const displayValue   = document.getElementById('display-value');
const displayService = document.getElementById('display-service-fee');
const displayExtra   = document.getElementById('display-extra-fee');
const displayVat     = document.getElementById('display-vat');
const displayTotal   = document.getElementById('display-total');

const upfrontEl      = document.getElementById('upfront-amount');
const weeklyCountEl  = document.getElementById('weekly-count');
const weeklyAmountEl = document.getElementById('weekly-amount');

function updatePaymentPreview() {
  const value = parseFloat(valueInput.value) || 0;
  if (value < 1000) {
    resetAllDisplays();
    return;
  }

  let weeks = parseInt(weeksSelect.value) || 0;
  let isCustom = weeksSelect.value === 'custom';
  let extraFeePercent = 0;

  if (isCustom) {
    customGroup.style.display = 'block';
    customWeeks.required = true;
    weeks = parseInt(customWeeks.value) || 0;
    if (weeks > 2) extraFeePercent = 0.05; 
  } else {
    customGroup.style.display = 'none';
    customWeeks.required = false;
    customWeeks.value = '';
  }

  if (weeks < 1) {
    resetAllDisplays();
    return;
  }

  const serviceFee = value * 0.05 * weeks;               
  const extraFee   = (value + serviceFee) * extraFeePercent;
  const subTotal   = value + serviceFee + extraFee;
  const vat        = subTotal * 0.075;                    
  const grandTotal = subTotal + vat;

  displayValue.textContent   = 'NGN ' + value.toLocaleString();
  displayService.textContent = 'NGN ' + serviceFee.toLocaleString();
  
  if (extraFeePercent > 0) {
    extraFeeRow.style.display = 'flex';
    displayExtra.textContent  = 'NGN ' + extraFee.toLocaleString();
  } else {
    extraFeeRow.style.display = 'none';
  }

  displayVat.textContent     = 'NGN ' + vat.toLocaleString();
  displayTotal.textContent   = 'NGN ' + grandTotal.toLocaleString();

  const upfront = grandTotal * 0.5;
  const remaining = grandTotal * 0.5;
  const weekly = weeks > 0 ? remaining / weeks : 0;

  upfrontEl.textContent     = 'NGN ' + upfront.toFixed(0).toLocaleString();
  weeklyCountEl.textContent = weeks;
  weeklyAmountEl.textContent = 'NGN ' + weekly.toFixed(0).toLocaleString();

  breakdown.style.display = 'block';
}

function resetAllDisplays() {
  displayValue.textContent = displayService.textContent = 
  displayVat.textContent = displayTotal.textContent = 
  upfrontEl.textContent = weeklyAmountEl.textContent = 'NGN 0';
  
  extraFeeRow.style.display = 'none';
  breakdown.style.display = 'none';
}


valueInput.addEventListener('input', updatePaymentPreview);
weeksSelect.addEventListener('change', updatePaymentPreview);
customWeeks.addEventListener('input', updatePaymentPreview);


updatePaymentPreview();


function makeDealsClickable() {
  const dealsList = document.getElementById('deals-list');
  if (!dealsList) return;

  const dealCards = dealsList.querySelectorAll('.deal-card');

  dealCards.forEach(card => {
    const status = (card.dataset.status || '').toLowerCase();
    const isApproved = status === 'approved';
    if (!isApproved) {
      card.style.cursor = 'default';
      return;
    }
    card.style.cursor = 'pointer';

    card.addEventListener('mouseenter', () => {
      card.style.transform = 'translateY(-3px)';
      card.style.boxShadow = '0 8px 20px rgba(0,0,0,0.08)';
      card.style.transition = 'all 0.18s ease';
    });

    card.addEventListener('mouseleave', () => {
      card.style.transform = '';
      card.style.boxShadow = '';
    });

    card.addEventListener('click', (e) => {
      if (e.target.closest('button, a')) return;

      const titleEl = card.querySelector('.deal-title');
      if (!titleEl) return;

      const titleText = titleEl.textContent.trim();

      const idMatch = titleText.match(/(?:#|Deal\s*#?|Order\s*)?(\d+)/i);
      const dealId = idMatch ? idMatch[1] : card.dataset.dealId;

      if (!dealId) {
        console.warn('Could not extract deal ID from:', titleText);
        return;
      }

      window.location.href = `/dashboard/deal/${dealId}`;
    });
  });
}

renderDeals = (deals) => {
  makeDealsClickable();
};


document.addEventListener('DOMContentLoaded', makeDealsClickable);
