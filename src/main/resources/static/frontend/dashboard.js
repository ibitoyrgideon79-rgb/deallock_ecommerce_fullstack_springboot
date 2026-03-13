
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
const REQUEST_TIMEOUT_MS = 60000;

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
      credentials: 'include',
      redirect: 'follow',
      cache: 'no-store'
    });
    if (res.redirected || (res.url && res.url.includes('/login'))) {
      if (dealsMessage) dealsMessage.textContent = 'Session expired. Please log in again.';
      return;
    }
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
      const statusLower = status.toLowerCase();
      const isApproved = statusLower === 'approved';
      const isPending = statusLower.includes('pending');
      const isRejected = statusLower === 'rejected';
      const paymentStatus = (deal.paymentStatus || 'NOT_PAID').toLowerCase();
      const isPaymentPending = paymentStatus === 'paid_pending_confirmation';
      const isPaymentConfirmed = paymentStatus === 'paid_confirmed';
      const rejectionReason = deal.rejectionReason && String(deal.rejectionReason).trim()
        ? String(deal.rejectionReason)
        : '';
      card.className = `deal-card ${status.toLowerCase().replace(/\s+/g, '-')}`;
      card.dataset.dealId = deal.id;
      card.dataset.status = status;
      card.innerHTML = `
        <div class="deal-title">${deal.title || 'Untitled Deal'}</div>
        <div class="deal-status">${status}</div>
        <div class="deal-value">NGN ${Number(deal.value || 0).toLocaleString()}</div>
        ${isRejected ? `<div class="deal-status" style="color:#b91c1c;">Rejection reason: ${rejectionReason || 'No reason provided.'}</div>` : ''}
        <div class="deal-actions" style="margin-top:8px; display:flex; gap:8px; flex-wrap:wrap;">
          <a class="btn-submit deal-details-link" href="/dashboard/deal/${deal.id}">See Details</a>
          <a class="btn-submit deal-details-link" href="/dashboard/deal/${deal.id}/track">Track Deal</a>
          ${isApproved && !isPaymentConfirmed && !isPaymentPending ? `<a class="btn-submit" href="/dashboard/deal/${deal.id}/pay">Pay</a>` : ''}
          ${isPaymentPending ? `<span class="deal-status" style="font-weight:600;">Processing</span>` : ''}
          ${isPending ? `<button class="btn-cancel cancel-deal-btn" data-deal-id="${deal.id}" type="button">Cancel Deal</button>` : ''}
          ${isPaymentConfirmed ? `<span class="deal-status" style="font-weight:600;">Paid</span>` : ''}
        </div>
      `;
      dealsList.appendChild(card);
    });
    makeDealsClickable();
    wireCancelButtons();
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
      credentials: 'include',
      redirect: 'follow',
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
  const sellerPhone = formData.get('seller-phone');
  const sellerAddress = formData.get('seller-address');
  const deliveryAddress = formData.get('delivery-address');
  const itemSize = formData.get('item-size');
  const value = formData.get('deal-value');
  const photo = formData.get('itemPhoto');
  const weeks = formData.get('weeks');
  const customWeeksValue = formData.get('customWeeks');

  if (!title || !client || !sellerPhone || !sellerAddress || !deliveryAddress || !itemSize || !value) {
    if (dealsMessage) dealsMessage.textContent = 'Please fill all required fields.';
    return;
  }
  if (!weeks || weeks === '') {
    if (dealsMessage) dealsMessage.textContent = 'Please select the number of weekly installments.';
    return;
  }
  if (weeks === 'custom' && (!customWeeksValue || Number(customWeeksValue) < 3)) {
    if (dealsMessage) dealsMessage.textContent = 'Please enter requested weeks (3 or more).';
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

const termsLink = document.getElementById('terms-link');
const termsModal = document.getElementById('terms-modal');
const termsClose = document.getElementById('terms-close');

termsLink?.addEventListener('click', () => {
  termsModal?.classList.add('active');
});

termsClose?.addEventListener('click', () => {
  termsModal?.classList.remove('active');
});

termsModal?.addEventListener('click', e => {
  if (e.target === termsModal) {
    termsModal.classList.remove('active');
  }
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
const sellerAddressInput = document.getElementById('seller-address');
const deliveryAddressInput = document.getElementById('delivery-address');
const itemSizeInput = document.getElementById('item-size');
const breakdown      = document.getElementById('breakdown');

const displayValue   = document.getElementById('display-value');
const displayService = document.getElementById('display-service-fee');
const displayLogistics = document.getElementById('display-logistics-fee');
const displayUpfrontDue = document.getElementById('display-upfront-due');
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

  if (isCustom) {
    customGroup.style.display = 'block';
    customWeeks.required = true;
    weeks = parseInt(customWeeks.value) || 0;
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
  const vatBase    = serviceFee;
  const vat        = vatBase * 0.075;
  const logistics  = estimateLogisticsFee();
  const upfrontDue = (value * 0.5) + logistics;
  const grandTotal = value + serviceFee + vat + logistics;
  const remaining  = grandTotal - upfrontDue;

  displayValue.textContent   = 'NGN ' + value.toLocaleString();
  displayService.textContent = 'NGN ' + (serviceFee + vat).toLocaleString();
  displayLogistics.textContent = 'NGN ' + logistics.toLocaleString();
  displayUpfrontDue.textContent = 'NGN ' + upfrontDue.toLocaleString();
  displayTotal.textContent   = 'NGN ' + grandTotal.toLocaleString();

  const weekly = weeks > 0 ? remaining / weeks : 0;

  upfrontEl.textContent     = 'NGN ' + upfrontDue.toFixed(0).toLocaleString();
  weeklyCountEl.textContent = weeks;
  weeklyAmountEl.textContent = 'NGN ' + weekly.toFixed(0).toLocaleString();

  breakdown.style.display = 'block';
}

function resetAllDisplays() {
  displayValue.textContent = displayService.textContent = 
  displayTotal.textContent = 
  displayLogistics.textContent = displayUpfrontDue.textContent =
  upfrontEl.textContent = weeklyAmountEl.textContent = 'NGN 0';
  
  breakdown.style.display = 'none';
}


valueInput.addEventListener('input', updatePaymentPreview);
weeksSelect.addEventListener('change', updatePaymentPreview);
customWeeks.addEventListener('input', updatePaymentPreview);
sellerAddressInput?.addEventListener('input', updatePaymentPreview);
deliveryAddressInput?.addEventListener('input', updatePaymentPreview);
itemSizeInput?.addEventListener('change', updatePaymentPreview);


updatePaymentPreview();

function estimateLogisticsFee() {
  const itemSize = (itemSizeInput?.value || '').toLowerCase();
  const sellerAddress = (sellerAddressInput?.value || '').toLowerCase();
  const deliveryAddress = (deliveryAddressInput?.value || '').toLowerCase();

  let baseFee = 5000;
  if (itemSize === 'medium') baseFee = 9000;
  if (itemSize === 'large') baseFee = 15000;

  let distanceFactor = 1.0;
  if (sellerAddress && deliveryAddress) {
    const sellerAbuja = sellerAddress.includes('abuja') || sellerAddress.includes('fct');
    const deliveryAbuja = deliveryAddress.includes('abuja') || deliveryAddress.includes('fct');
    if (sellerAbuja && deliveryAbuja) distanceFactor = 1.0;
    else if (sellerAbuja || deliveryAbuja) distanceFactor = 1.45;
    else distanceFactor = 1.65;
  }

  return Math.round(baseFee * distanceFactor);
}


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

function confirmCancelDialog() {
  return new Promise(resolve => {
    const overlay = document.getElementById('confirm-overlay');
    const yesBtn = document.getElementById('confirm-yes');
    const noBtn = document.getElementById('confirm-no');
    if (!overlay || !yesBtn || !noBtn) {
      const ok = window.confirm('Are you sure you want to cancel this deal?');
      resolve(ok);
      return;
    }
    const cleanup = () => {
      overlay.classList.remove('active');
      yesBtn.removeEventListener('click', onYes);
      noBtn.removeEventListener('click', onNo);
      overlay.removeEventListener('click', onOverlay);
    };
    const onYes = () => { cleanup(); resolve(true); };
    const onNo = () => { cleanup(); resolve(false); };
    const onOverlay = (e) => {
      if (e.target === overlay) {
        cleanup();
        resolve(false);
      }
    };
    yesBtn.addEventListener('click', onYes);
    noBtn.addEventListener('click', onNo);
    overlay.addEventListener('click', onOverlay);
    overlay.classList.add('active');
  });
}

async function handleCancelDealClick(btn) {
  const dealId = btn.getAttribute('data-deal-id');
  if (!dealId) return;
  const ok = await confirmCancelDialog();
  if (!ok) return;
  const originalText = btn.textContent;
  btn.disabled = true;
  btn.textContent = 'Canceling...';
  if (dealsMessage) dealsMessage.textContent = '';
  try {
    const res = await fetch(`${API_DEALS}/${dealId}/cancel`, {
      method: 'POST',
      credentials: 'include',
      redirect: 'follow'
    });
    if (!res.ok) {
      if (dealsMessage) dealsMessage.textContent = 'Failed to cancel deal.';
      btn.disabled = false;
      btn.textContent = originalText;
      return;
    }
    if (dealsMessage) dealsMessage.textContent = 'Deal canceled.';
    await loadDeals();
  } catch (e) {
    if (dealsMessage) dealsMessage.textContent = 'Failed to cancel deal.';
    btn.disabled = false;
    btn.textContent = originalText;
  }
}

function wireCancelButtons() {
  const buttons = document.querySelectorAll('.cancel-deal-btn');
  buttons.forEach(btn => {
    btn.addEventListener('click', () => handleCancelDealClick(btn));
  });
}

dealsList?.addEventListener('click', (e) => {
  const btn = e.target.closest('.cancel-deal-btn');
  if (!btn) return;
  e.preventDefault();
  e.stopPropagation();
  handleCancelDealClick(btn);
});

document.addEventListener('click', (e) => {
  const btn = e.target.closest('.cancel-deal-btn');
  if (!btn) return;
  e.preventDefault();
  e.stopPropagation();
  handleCancelDealClick(btn);
});


document.addEventListener('DOMContentLoaded', makeDealsClickable);
