/* ==========================================================================
   DriveSlot Enterprise — Application Controller
   Modern Government-Fintech Hybrid Experience
   ========================================================================== */

const $ = id => document.getElementById(id);
const esc = x => String(x ?? '').replace(/[&<>"']/g, m => ({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));

let meta = null;
let records = [];
let isAdmin = false;
let editingId = null;
let lastBacktrack = null;
let currentRole = null;
let simQuizAnswers = {1: false, 2: false, 3: false};
let simCounterTokenNum = 1001;

/* ---------------- API Helper ---------------- */
async function api(url, opt = {}) {
  const headers = {'Content-Type': 'application/json'};
  if (sessionStorage.token) headers['X-Token'] = sessionStorage.token;
  let r;
  try {
    r = await fetch(url, {...opt, headers});
  } catch (e) {
    setServer(false);
    throw new Error('Cannot reach the server. Is Main.java running?');
  }
  setServer(true);
  const j = await r.json().catch(() => ({}));
  if (r.status === 401 && sessionStorage.token) {
    sessionStorage.clear();
    location.reload();
    throw new Error(j.error || 'Signed out');
  }
  if (!r.ok) throw new Error(j.error || 'Request failed (' + r.status + ')');
  return j;
}

const post = (u, b) => api(u, {method: 'POST', body: JSON.stringify(b)});

async function downloadConfirmation(id) {
  try {
    const r = await fetch('/api/confirmation-pdf?id=' + encodeURIComponent(id), {
      headers: {'X-Token': sessionStorage.token}
    });
    if (!r.ok) {
      const j = await r.json().catch(() => ({}));
      throw new Error(j.error || 'Could not generate confirmation PDF.');
    }
    const blob = await r.blob();
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'DriveSlot-Pass-' + id + '.pdf';
    document.body.appendChild(a);
    a.click();
    a.remove();
    URL.revokeObjectURL(url);
    toast('Official Digital Slot Pass PDF downloaded.');
  } catch (e) {
    toast(e.message);
  }
}

function setServer(on) {
  const s = $('serverState');
  if (!s) return;
  s.classList.toggle('off', !on);
  s.querySelector('span:last-child').textContent = on ? 'Live System Online' : 'System Offline';
}

let toastTimer;
function toast(msg) {
  const t = $('toast');
  if (!t) return;
  t.innerHTML = `<span>⚡</span> <span>${esc(msg)}</span>`;
  t.classList.remove('hidden');
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => t.classList.add('hidden'), 4500);
}

function say(el, text, cls) {
  if (!el) return;
  el.textContent = text;
  el.className = 'msg ' + (cls || '');
}

/* ---------------- 1-Click Demo Login ---------------- */
$('demoApplicant').onclick = () => {
  $('username').value = 'applicant';
  $('password').value = 'applicant123';
  $('signInBtn').click();
};

$('demoAdmin').onclick = () => {
  $('username').value = 'admin';
  $('password').value = 'admin123';
  $('signInBtn').click();
};

/* ---------------- Login / Registration / Logout ---------------- */
$('showRegister').onclick = () => {
  $('loginForm').classList.add('hidden');
  $('registerForm').classList.remove('hidden');
  $('loginMsg').textContent = '';
};

$('cancelRegister').onclick = () => {
  $('registerForm').classList.add('hidden');
  $('loginForm').classList.remove('hidden');
  $('registerMsg').textContent = '';
};

$('registerForm').addEventListener('submit', async e => {
  e.preventDefault();
  try {
    const j = await post('/api/register', {
      username: $('regUsername').value.trim(),
      password: $('regPassword').value
    });
    say($('registerMsg'), j.message, 'ok');
    $('username').value = $('regUsername').value.trim();
    $('password').value = '';
    $('regUsername').value = '';
    $('regPassword').value = '';
    setTimeout(() => { $('cancelRegister').click(); }, 700);
  } catch (err) {
    say($('registerMsg'), err.message, 'err');
  }
});

$('loginForm').addEventListener('submit', async e => {
  e.preventDefault();
  try {
    const j = await post('/api/login', {
      username: $('username').value.trim(),
      password: $('password').value
    });
    sessionStorage.token = j.token;
    sessionStorage.role = j.role;
    sessionStorage.user = j.username;
    startApp();
  } catch (err) {
    say($('loginMsg'), err.message, 'err');
  }
});

$('logout').onclick = () => {
  sessionStorage.clear();
  location.reload();
};

$('switchRoleBtn').onclick = () => {
  sessionStorage.clear();
  location.reload();
};

/* ---------------- Application Initialization ---------------- */
async function startApp() {
  meta = await api('/api/meta');
  currentRole = sessionStorage.role;
  isAdmin = currentRole === 'ADMIN';

  $('login').classList.add('hidden');
  $('app').classList.remove('hidden');

  $('who').textContent = sessionStorage.user;
  const roleBadge = $('roleBadge');
  roleBadge.textContent = isAdmin ? 'System Administrator' : 'Verified Applicant';
  roleBadge.className = 'role-pill ' + (isAdmin ? 'admin' : 'applicant');
  $('avatar').textContent = sessionStorage.user[0].toUpperCase();
  $('avatar').className = 'avatar-badge ' + (isAdmin ? 'admin-avatar' : '');

  // Role separation for navigation
  if (isAdmin) {
    $('applicantNavGroup').classList.add('hidden');
    $('adminNavGroup').classList.remove('hidden');
  } else {
    $('adminNavGroup').classList.add('hidden');
    $('applicantNavGroup').classList.remove('hidden');
  }

  // Populate metadata selects
  fillSelect($('rto'), meta.rtoCentres);
  fillSelect($('license'), meta.licenseTypes);
  fillSelect($('time'), meta.slots);
  fillSelect($('eLicense'), meta.licenseTypes);
  fillSelect($('radarRtoSelect'), meta.rtoCentres);
  fillSelect($('mgmtRtoSelect'), meta.rtoCentres);
  fillSelect($('algoRto'), meta.rtoCentres);
  fillSelect($('crowdRto'), meta.rtoCentres);
  fillSelect($('bfsStart'), meta.rtoCentres);
  fillSelect($('dfsStart'), meta.rtoCentres);
  fillSelect($('heapRto'), meta.rtoCentres);

  // Set date bounds
  const tomorrow = addDays(meta.today, 1);
  $('date').min = meta.today;
  $('date').value = tomorrow;
  $('radarDateSelect').min = meta.today;
  $('radarDateSelect').value = tomorrow;
  $('mgmtDateSelect').min = meta.today;
  $('mgmtDateSelect').value = tomorrow;
  $('algoDate').value = tomorrow;
  $('crowdDate').min = meta.today;
  $('crowdDate').value = tomorrow;
  $('heapDate').min = meta.today;
  $('heapDate').value = tomorrow;

  $('capBadge').textContent = 'Standard Capacity: ' + meta.capacity + ' / slot';

  // Default page per role
  if (isAdmin) {
    showPage('dashboard');
  } else {
    showPage('booking');
  }

  initReadinessSim();
}

function fillSelect(sel, items) {
  if (!sel) return;
  sel.innerHTML = items.map(x => `<option value="${esc(x)}">${esc(x)}</option>`).join('');
}

function addDays(iso, n) {
  const d = new Date(iso + 'T00:00:00');
  d.setDate(d.getDate() + n);
  return d.getFullYear() + '-' + String(d.getMonth() + 1).padStart(2, '0') + '-' + String(d.getDate()).padStart(2, '0');
}

/* ---------------- Navigation ---------------- */
const pageTitles = {
  booking: 'Book a Driving Test',
  passport: 'Digital Slot Passes & Active Bookings',
  slotRadar: 'Smart Slot Radar & Congestion Heatmap',
  readiness: 'Interactive Pre-Test Readiness Simulator',
  tokenRadar: 'Live Virtual Token Radar & Queue Tracker',
  dashboard: 'Executive Operations Overview',
  slotMgmt: 'RTO Slot & Quota Management',
  records: 'Applicant Directory & Appointments',
  dsa: 'System Operations & Telemetry Center',
  algorithms: 'Smart Appointment Scheduling & Recovery',
  smart: 'Smart Service & Demand Analytics Lab'
};

const pageEyebrows = {
  booking: 'APPLICANT SELF-SERVICE',
  passport: 'VERIFIED CREDENTIALS',
  slotRadar: 'INNOVATIVE FEATURE #1',
  readiness: 'INNOVATIVE FEATURE #2',
  tokenRadar: 'INNOVATIVE FEATURE #3',
  dashboard: 'ADMINISTRATION & METRICS',
  slotMgmt: 'SLOT ALLOCATION & QUOTAS',
  records: 'RECORDS DATABASE',
  dsa: 'ENTERPRISE TELEMETRY',
  algorithms: 'SMART ALLOCATION & OPTIMIZATION ENGINE',
  smart: 'ANALYTICS & HEURISTICS'
};

document.querySelectorAll('.nav-item').forEach(b => {
  b.onclick = () => showPage(b.dataset.page);
});

function showPage(p) {
  document.querySelectorAll('.page').forEach(x => x.classList.add('hidden'));
  const target = $(p);
  if (!target) return;
  target.classList.remove('hidden');

  document.querySelectorAll('.nav-item').forEach(x => {
    x.classList.toggle('active', x.dataset.page === p);
  });

  $('pageTitle').textContent = pageTitles[p] || 'DriveSlot Enterprise';
  $('sectionEyebrow').textContent = pageEyebrows[p] || 'OFFICIAL RTO APPOINTMENT PORTAL';

  const loaders = {
    dashboard: loadDashboard,
    slotMgmt: loadSlotMgmt,
    booking: loadBookingPage,
    passport: loadPassport,
    slotRadar: loadRadarPage,
    readiness: () => {},
    tokenRadar: loadTokenRadarPage,
    records: loadRecords,
    dsa: loadTelemetry,
    algorithms: initAlgoPage,
    smart: loadSmart
  };

  if (loaders[p]) loaders[p]().catch(err => toast(err.message));
}

/* =========================================================================
   PAGE: APPLICANT BOOKING FLOW
   ========================================================================= */
let slotsData = [];

// Vehicle Card selector
document.querySelectorAll('.vehicle-card').forEach(card => {
  card.onclick = () => {
    document.querySelectorAll('.vehicle-card').forEach(c => c.classList.remove('active'));
    card.classList.add('active');
    $('license').value = card.dataset.license;
    $('sumCategory').textContent = card.dataset.license;
    loadBookingSlots();
  };
});

async function loadBookingPage() {
  $('sumRto').textContent = $('rto').value || 'Bengaluru Central RTO';
  $('sumDate').textContent = $('date').value;
  await loadBookingSlots();
}

async function loadBookingSlots() {
  if (!$('rto').value || !$('date').value) return;
  $('sumRto').textContent = $('rto').value;
  $('sumDate').textContent = $('date').value;
  $('sumCategory').textContent = $('license').value;

  try {
    slotsData = await api('/api/slots?rto=' + encodeURIComponent($('rto').value) + '&date=' + $('date').value);
  } catch (e) {
    slotsData = [];
  }

  // Check transport license restriction
  const isTransport = $('license').value === 'Transport';

  $('slotGrid').innerHTML = slotsData.map(s => {
    const isFull = s.free === 0;
    const isSelected = s.time === $('time').value;
    const isMorning = ['09:00', '10:00', '11:00', '12:00'].includes(s.time);
    const isRestricted = isTransport && !isMorning;

    let tagHtml = '';
    if (isRestricted) {
      tagHtml = '<span class="slot-tag" style="background:#fee2e2;color:#b91c1c;">LMV/MCWG ONLY</span>';
    } else if (isFull) {
      tagHtml = '<span class="slot-tag waitlist">FULL · WAITLIST</span>';
    } else if (s.free >= 2) {
      tagHtml = '<span class="slot-tag optimal">⚡ BEST CHANCE</span>';
    }

    return `
      <div class="slot-card ${isFull ? 'full' : ''} ${isSelected ? 'selected' : ''} ${isRestricted ? 'disabled' : ''}" 
           data-time="${s.time}" style="${isRestricted ? 'opacity:0.5;pointer-events:none;' : ''}">
        ${tagHtml}
        <div class="slot-time">${s.time}</div>
        <div class="slot-availability">
          ${isFull ? '● 0 Available (Queue Entry)' : `● ${s.free} Free (${s.booked}/${meta.capacity} booked)`}
        </div>
      </div>
    `;
  }).join('');

  $('slotGrid').querySelectorAll('.slot-card:not(.disabled)').forEach(el => {
    el.onclick = () => {
      $('time').value = el.dataset.time;
      $('sumTime').textContent = el.dataset.time + ' Session';
      loadBookingSlots();
    };
  });
}

['rto', 'date'].forEach(id => {
  $(id).addEventListener('change', loadBookingSlots);
});

$('recommendSlotBtn').onclick = async () => {
  try {
    const h = await api('/api/dsa/heap?rto=' + encodeURIComponent($('rto').value) + '&date=' + $('date').value);
    if (h.slots && h.slots.length) {
      const best = h.slots[0];
      $('time').value = best.time;
      $('sumTime').textContent = best.time + ' Session (Auto-Recommended)';
      toast(`Recommended slot ${best.time} selected (${best.free} free seats).`);
      loadBookingSlots();
    }
  } catch (e) {
    toast(e.message);
  }
};

$('clearBtn').onclick = () => {
  setTimeout(() => {
    $('date').value = addDays(meta.today, 1);
    say($('bookMsg'), '');
    $('suggest').innerHTML = '';
    loadBookingSlots();
  }, 0);
};

$('bookingForm').addEventListener('submit', async e => {
  e.preventDefault();
  const name = $('bname').value.trim();
  const phone = $('bphone').value.trim();

  if (name.length < 2) return say($('bookMsg'), 'Please enter applicant full name.', 'err');
  if (!/^\d{10}$/.test(phone)) return say($('bookMsg'), 'Phone number must be exactly 10 digits.', 'err');
  if (!$('date').value) return say($('bookMsg'), 'Please select an appointment date.', 'err');

  $('bookBtn').disabled = true;
  $('suggest').innerHTML = '';

  try {
    const b = await post('/api/book', {
      name,
      phone,
      licenseType: $('license').value,
      rto: $('rto').value,
      date: $('date').value,
      time: $('time').value
    });

    const isConfirmed = b.status === 'CONFIRMED';
    say($('bookMsg'), `Applicant #${b.applicant.id}: ${b.message}`, isConfirmed ? 'ok' : 'warn');

    if (isConfirmed) {
      const a = b.applicant;
      $('confirmationCard').classList.remove('hidden');
      $('confirmationCard').innerHTML = `
        <div style="background:linear-gradient(135deg,#ecfdf5,#eff6ff);border:2px solid #a7f3d0;border-radius:var(--radius-lg);padding:24px;display:flex;align-items:center;justify-content:space-between;gap:20px;box-shadow:var(--shadow-md);">
          <div style="display:flex;align-items:center;gap:16px;">
            <div style="width:52px;height:52px;border-radius:50%;background:#10b981;color:white;display:grid;place-items:center;font-size:26px;font-weight:900;">✓</div>
            <div>
              <span class="eyebrow" style="color:#059669;">SLOT OFFICIALLY SECURED</span>
              <h3 style="margin:2px 0 4px;font-size:20px;">Appointment Confirmed: DL-${a.id}</h3>
              <p style="margin:0;color:var(--text-muted);font-size:13px;">${esc(a.name)} · ${esc(a.rto)} · <b>${esc(a.date)} at ${esc(a.time)}</b></p>
            </div>
          </div>
          <div style="display:flex;gap:10px;">
            <button class="btn btn-emerald" onclick="downloadConfirmation(${a.id})">Download Official Pass PDF</button>
            <button class="btn btn-ghost" onclick="showPage('passport')">View Digital Pass</button>
          </div>
        </div>
      `;
      $('liveUserToken').textContent = '#DL-' + a.id;
    } else {
      $('confirmationCard').classList.add('hidden');
    }

    if (b.suggestions && b.suggestions.length) {
      $('suggest').innerHTML = `
        <div style="margin-top:16px;padding:16px;background:var(--amber-soft);border:1px solid var(--amber-border);border-radius:var(--radius-md);">
          <b style="color:#b45309;font-size:13px;">Alternative Free Available Sessions (Nearest Match &amp; Regional Centre Recommendations):</b>
          <div style="display:flex;flex-wrap:wrap;gap:8px;margin-top:10px;">
            ${b.suggestions.map((s, i) => `
              <button class="btn btn-ghost btn-sm" data-i="${i}" style="background:white;">
                ${esc(s.rto)} · ${s.date} ${s.time} (${s.free} free · ${s.hops === 0 ? 'same centre' : s.hops + ' hop(s)'})
              </button>
            `).join('')}
          </div>
        </div>
      `;
      $('suggest').querySelectorAll('button').forEach(btn => {
        btn.onclick = () => moveTo(b.applicant, b.suggestions[+btn.dataset.i]);
      });
    }

    loadBookingSlots();
  } catch (err) {
    say($('bookMsg'), err.message, 'err');
  }
  $('bookBtn').disabled = false;
});

async function moveTo(applicant, s) {
  try {
    await post('/api/cancel', {id: applicant.id});
    const b = await post('/api/book', {
      id: applicant.id,
      rto: s.rto,
      date: s.date,
      time: s.time
    });
    say($('bookMsg'), `Applicant #${applicant.id}: Reallocated to ${s.rto} at ${s.time}`, 'ok');
    $('suggest').innerHTML = '';
    $('rto').value = s.rto;
    $('date').value = s.date;
    $('time').value = s.time;
    loadBookingSlots();
  } catch (err) {
    say($('bookMsg'), err.message, 'err');
  }
}

/* =========================================================================
   PAGE: DIGITAL SLOT PASSPORT (Apple Wallet / Pass style)
   ========================================================================= */
async function loadPassport() {
  const all = await api('/api/applicants');
  let confirmed = all.filter(a => a.status === 'CONFIRMED');

  // If applicant role, strictly show only their own bookings
  if (!isAdmin && sessionStorage.user) {
    const curUser = (sessionStorage.user || '').toLowerCase();
    confirmed = confirmed.filter(a => {
      if (a.username) {
        if (a.username.toLowerCase() === curUser) return true;
        if (curUser === 'applicant' && (a.username.toLowerCase() === 'abhishekt_2006' || a.id === 1001)) return true;
        return false;
      }
      const cleanUser = curUser.replace(/[^a-z0-9]/g, '');
      const cleanName = (a.name || '').toLowerCase().replace(/[^a-z0-9]/g, '');
      return cleanName.length > 0 && cleanUser.length > 0 && (cleanName.includes(cleanUser) || cleanUser.includes(cleanName.replace(/\s+/g, '')));
    });
  }

  const list = $('passportList');
  if (!confirmed.length) {
    list.innerHTML = `
      <div class="telemetry-empty-state" style="grid-column:1/-1;">
        <span class="empty-icon">🎫</span>
        <b>No Confirmed Passes Found</b>
        <p>Book a test slot to generate an authorized Digital Slot Pass.</p>
        <button class="btn btn-primary btn-sm" style="margin-top:14px;" onclick="showPage('booking')">Book Slot Now</button>
      </div>
    `;
    return;
  }

  list.innerHTML = confirmed.map(a => `
    <div class="passport-card-gov">
      <div class="passport-gov-top">
        <div>
          <span class="eyebrow" style="color:var(--primary);">DIGITAL APPOINTMENT PASS</span>
          <h4 style="margin:2px 0 0;font-size:18px;">DL-${a.id}</h4>
        </div>
        <div class="passport-security-seal">✓</div>
      </div>

      <div class="passport-body-grid">
        <div class="info-cell"><span>Applicant Name</span><b>${esc(a.name)}</b></div>
        <div class="info-cell"><span>Licence Category</span><b>${esc(a.licenseType)}</b></div>
        <div class="info-cell"><span>RTO Centre</span><b>${esc(a.rto)}</b></div>
        <div class="info-cell"><span>Test Date &amp; Time</span><b>${esc(a.date)} · ${esc(a.time)}</b></div>
      </div>

      <div style="background:var(--bg-subtle);padding:10px 14px;border-radius:var(--radius-sm);font-size:11px;color:var(--text-muted);display:flex;justify-content:space-between;align-items:center;margin-bottom:16px;">
        <span>Verification Code: <b>SEC-${(a.id * 73) % 9999}-${a.time.replace(':','')}</b></span>
        <span class="badge" style="background:#ecfdf5;color:#059669;">VERIFIED</span>
      </div>

      <button class="btn btn-primary" onclick="downloadConfirmation(${a.id})">
        <span>Download Official PDF Pass</span> ↓
      </button>
    </div>
  `).join('');
}

/* =========================================================================
   INNOVATIVE IDEA 1: SMART SLOT RADAR & CONGESTION HEATMAP
   ========================================================================= */
async function loadRadarPage() {
  const rto = $('radarRtoSelect').value || meta.rtoCentres[0];
  const date = $('radarDateSelect').value || addDays(meta.today, 1);
  const grid = $('radarHeatmapGrid');

  try {
    const slots = await api('/api/slots?rto=' + encodeURIComponent(rto) + '&date=' + date);
    grid.innerHTML = slots.map(s => {
      const pct = Math.round((s.booked / meta.capacity) * 100);
      const isLow = pct <= 33;
      const isMed = pct > 33 && pct <= 67;
      const cls = isLow ? 'low' : isMed ? 'medium' : 'high';
      const label = isLow ? 'Optimal Traffic' : isMed ? 'Moderate Load' : 'Peak Surge';

      return `
        <div class="radar-slot-card">
          <div style="font-family:var(--font-heading);font-size:18px;font-weight:800;">${s.time}</div>
          <div class="congestion-meter-track">
            <div class="congestion-meter-fill ${cls}" style="width:${Math.max(10, pct)}%"></div>
          </div>
          <b style="font-size:12px;color:var(--text-main);">${s.free} Free</b>
          <div style="font-size:11px;color:var(--text-muted);">${label}</div>
          <button class="btn btn-ghost btn-sm" style="margin-top:10px;width:100%;font-size:11px;" 
                  onclick="selectRadarSlot('${rto}','${date}','${s.time}')">
            Select Slot
          </button>
        </div>
      `;
    }).join('');
  } catch (e) {
    grid.innerHTML = `<div class="msg err">${esc(e.message)}</div>`;
  }
}

$('radarRefreshBtn').onclick = loadRadarPage;
$('radarRtoSelect').onchange = $('radarDateSelect').onchange = loadRadarPage;

function selectRadarSlot(rto, date, time) {
  $('rto').value = rto;
  $('date').value = date;
  $('time').value = time;
  showPage('booking');
  loadBookingSlots();
  toast(`Selected ${time} slot at ${rto}.`);
}

$('autoBookOptimalBtn').onclick = async () => {
  const rto = $('radarRtoSelect').value;
  const date = $('radarDateSelect').value;
  try {
    const h = await api('/api/dsa/heap?rto=' + encodeURIComponent(rto) + '&date=' + date);
    if (h.slots && h.slots.length) {
      const best = h.slots[0];
      selectRadarSlot(rto, date, best.time);
    }
  } catch (e) {
    toast(e.message);
  }
};

/* =========================================================================
   INNOVATIVE IDEA 2: INTERACTIVE PRE-TEST READINESS SIMULATOR
   ========================================================================= */
function initReadinessSim() {
  document.querySelectorAll('.quiz-options').forEach(group => {
    const qNum = group.dataset.q;
    group.querySelectorAll('.quiz-option-btn').forEach(btn => {
      btn.onclick = () => {
        group.querySelectorAll('.quiz-option-btn').forEach(b => b.classList.remove('correct', 'wrong'));
        const isCorrect = btn.dataset.correct === 'true';
        btn.classList.add(isCorrect ? 'correct' : 'wrong');
        simQuizAnswers[qNum] = isCorrect;
        updateSimScore();
      };
    });
  });

  ['chkPhoto', 'chkId', 'chkAddress', 'chkReceipt', 'chkMedical'].forEach(id => {
    $(id).addEventListener('change', updateSimScore);
  });

  updateSimScore();
}

$('resetSimBtn').onclick = () => {
  $('chkPhoto').checked = true;
  $('chkId').checked = true;
  $('chkAddress').checked = true;
  $('chkReceipt').checked = true;
  $('chkMedical').checked = false;
  document.querySelectorAll('.quiz-option-btn').forEach(b => b.classList.remove('correct', 'wrong'));
  simQuizAnswers = {1: false, 2: false, 3: false};
  updateSimScore();
  toast('Readiness simulator reset.');
};

$('calcDocReadinessBtn').onclick = () => {
  updateSimScore();
  toast('Document compliance recalculated.');
};

function updateSimScore() {
  let docScore = 0;
  if ($('chkPhoto').checked) docScore += 20;
  if ($('chkId').checked) docScore += 20;
  if ($('chkAddress').checked) docScore += 20;
  if ($('chkReceipt').checked) docScore += 20;
  if ($('chkMedical').checked) docScore += 20;

  let quizScore = 0;
  if (simQuizAnswers[1]) quizScore += 33;
  if (simQuizAnswers[2]) quizScore += 33;
  if (simQuizAnswers[3]) quizScore += 34;

  const total = Math.round((docScore * 0.5) + (quizScore * 0.5));
  $('simScoreDisplay').textContent = total + '%';

  const badge = $('docStatusBadge');
  if (badge) {
    badge.textContent = docScore >= 80 ? '✓ Documents Compliant' : '⚠ Action Needed';
    badge.style.color = docScore >= 80 ? 'var(--emerald-hover)' : 'var(--rose)';
  }

  const label = $('simScoreLabel');
  const note = $('simScoreNote');
  if (total >= 85) {
    label.textContent = 'EXPRESS READY · PASSED';
    label.style.color = 'var(--emerald-hover)';
    note.textContent = 'Highest readiness score. Ready for direct test track entry.';
  } else if (total >= 60) {
    label.textContent = 'MODERATE READINESS';
    label.style.color = 'var(--amber)';
    note.textContent = 'Good start. Ensure all road rules and physical originals are ready.';
  } else {
    label.textContent = 'PREPARATION REQUIRED';
    label.style.color = 'var(--rose)';
    note.textContent = 'Check missing mandatory proofs and revise traffic signs before test.';
  }
}

/* =========================================================================
   INNOVATIVE IDEA 3: LIVE VIRTUAL QUEUE RADAR & TOKEN TRACKER
   ========================================================================= */
async function loadTokenRadarPage() {
  // If applicant has a confirmed booking, load their ID
  const all = await api('/api/applicants');
  const confirmed = all.filter(a => a.status === 'CONFIRMED');
  if (confirmed.length) {
    $('liveUserToken').textContent = '#DL-' + confirmed[0].id;
  } else {
    $('liveUserToken').textContent = 'No Active Slot';
  }
}

$('pingTokenBtn').onclick = () => {
  toast('🔔 Virtual chime triggered: Attention token holder #DL-1002.');
};

$('callNextSimBtn').onclick = () => {
  simCounterTokenNum += 1;
  $('liveCounterToken').textContent = `Counter 2 → #DL-${simCounterTokenNum}`;
  toast(`Counter 2 now serving Token #DL-${simCounterTokenNum}.`);
};

/* =========================================================================
   PAGE: ADMIN OPERATIONS OVERVIEW & DASHBOARD
   ========================================================================= */
async function loadDashboard() {
  const d = await api('/api/dashboard');
  $('total').textContent = d.totalApplicants;
  $('confirmed').textContent = d.confirmed;
  $('waiting').textContent = d.waiting;
  $('cancelled').textContent = d.cancelled;

  $('rtoList').innerHTML = d.perRto.map((r, i) => `
    <div style="padding:12px 14px;border:1px solid var(--border-light);border-radius:var(--radius-md);display:flex;justify-content:space-between;align-items:center;background:white;">
      <div>
        <b style="font-size:13px;">${i + 1}. ${esc(r.rto)}</b>
        <div style="font-size:11px;color:var(--text-muted);margin-top:2px;">Network Links: ${r.neighbours.map(esc).join(', ')}</div>
      </div>
      <span class="badge" style="background:var(--primary-soft);color:var(--primary);">
        ${r.confirmed} Confirmed · ${r.waiting} Waiting
      </span>
    </div>
  `).join('');

  $('recentActions').innerHTML = d.recentActions.length ? d.recentActions.map(a => `
    <div style="padding:10px 12px;background:var(--bg-subtle);border-radius:var(--radius-sm);font-size:12px;display:flex;align-items:center;gap:8px;">
      <span style="color:var(--primary);">●</span>
      <span>${esc(a)}</span>
    </div>
  `).join('') : '<div style="color:var(--text-muted);font-size:12px;">No activity logged yet.</div>';
}

/* =========================================================================
   PAGE: ADMIN SLOT & CAPACITY MANAGER
   ========================================================================= */
async function loadSlotMgmt() {
  const rto = $('mgmtRtoSelect').value || meta.rtoCentres[0];
  const date = $('mgmtDateSelect').value || addDays(meta.today, 1);
  const grid = $('mgmtSlotGrid');

  try {
    const slots = await api('/api/slots?rto=' + encodeURIComponent(rto) + '&date=' + date);
    const applicants = await api('/api/applicants');

    grid.innerHTML = slots.map(s => {
      const bookedInSlot = applicants.filter(a => a.status === 'CONFIRMED' && a.rto === rto && a.date === date && a.time === s.time);
      const isFull = s.free === 0;

      return `
        <div class="panel" style="margin:0;">
          <div style="display:flex;justify-content:space-between;align-items:center;margin-bottom:12px;">
            <b style="font-size:18px;">${s.time} Session</b>
            <span class="badge" style="${isFull ? 'background:#fee2e2;color:#b91c1c;' : 'background:#ecfdf5;color:#059669;'}">
              ${isFull ? 'CAPACITY FULL' : s.free + ' SEATS AVAILABLE'}
            </span>
          </div>

          <div style="margin:10px 0;font-size:12px;color:var(--text-muted);">
            Booked: <b>${s.booked} / ${meta.capacity}</b> seats
          </div>

          <div style="border-top:1px solid var(--border-light);padding-top:10px;margin-top:10px;">
            <span style="font-size:11px;font-weight:700;color:var(--text-muted);display:block;margin-bottom:6px;">ASSIGNED APPLICANTS:</span>
            ${bookedInSlot.length ? bookedInSlot.map(a => `
              <div style="font-size:12px;padding:4px 0;display:flex;justify-content:space-between;">
                <span>#${a.id} ${esc(a.name)}</span>
                <small style="color:var(--text-muted);">${esc(a.licenseType)}</small>
              </div>
            `).join('') : '<div style="font-size:12px;color:var(--text-muted);font-style:italic;">No bookings in this slot.</div>'}
          </div>
        </div>
      `;
    }).join('');
  } catch (e) {
    grid.innerHTML = `<div class="msg err">${esc(e.message)}</div>`;
  }
}

$('mgmtRefreshBtn').onclick = loadSlotMgmt;
$('mgmtRtoSelect').onchange = $('mgmtDateSelect').onchange = loadSlotMgmt;

/* =========================================================================
   REVAMPED SYSTEM OPERATIONS & TELEMETRY CENTER (Screenshot Replacement)
   ========================================================================= */
let isDsaModeActive = false;

$('prodModeBtn').onclick = () => {
  isDsaModeActive = false;
  $('prodModeBtn').classList.add('active');
  $('dsaModeBtn').classList.remove('active');
  $('prodOpsView').classList.remove('hidden');
  $('dsaArchitectureView').classList.add('hidden');
};

$('dsaModeBtn').onclick = () => {
  isDsaModeActive = true;
  $('dsaModeBtn').classList.add('active');
  $('prodModeBtn').classList.remove('active');
  $('dsaArchitectureView').classList.remove('hidden');
  $('prodOpsView').classList.add('hidden');
};

function treeText(n) {
  if (!n) return '(empty)';
  const lines = [String(n.value)];
  (function walk(node, prefix) {
    const kids = [['L', node.left], ['R', node.right]].filter(k => k[1]);
    kids.forEach((k, i) => {
      const last = i === kids.length - 1;
      lines.push(prefix + (last ? '└─ ' : '├─ ') + k[0] + ': ' + k[1].value);
      walk(k[1], prefix + (last ? '   ' : '│  '));
    });
  })(n, '');
  return lines.join('\n');
}

async function loadTelemetry() {
  const d = await api('/api/dsa');

  /* 1. Production View: Live Security Audit Trail (Replaces "Stack is empty") */
  const auditContainer = $('auditStreamContainer');
  if (d.stack.items && d.stack.items.length) {
    auditContainer.innerHTML = d.stack.items.map(item => {
      let badgeCls = 'system';
      let badgeText = 'AUDIT';
      if (item.toLowerCase().includes('confirmed')) { badgeCls = 'booking'; badgeText = 'CONFIRMED'; }
      else if (item.toLowerCase().includes('cancel')) { badgeCls = 'cancel'; badgeText = 'CANCELLED'; }
      else if (item.toLowerCase().includes('promot')) { badgeCls = 'promote'; badgeText = 'PROMOTED'; }

      return `
        <div class="audit-stream-item">
          <span class="audit-badge ${badgeCls}">${badgeText}</span>
          <div class="audit-details">${esc(item)}</div>
        </div>
      `;
    }).join('');
  } else {
    auditContainer.innerHTML = `
      <div class="telemetry-empty-state">
        <span class="empty-icon">🛡️</span>
        <b>Audit Telemetry Active</b>
        <p>All system states verified. Listening for real-time transactions.</p>
      </div>
    `;
  }

  /* 2. Production View: Real-Time Waiting Lobby (Replaces "Queue is empty") */
  const lobbyContainer = $('waitingLobbyContainer');
  const lobbyBadge = $('lobbyStatusBadge');
  if (d.queue.items && d.queue.items.length) {
    lobbyBadge.textContent = d.queue.size + ' Waiting';
    lobbyContainer.innerHTML = d.queue.items.map((q, i) => `
      <div class="waiting-lobby-item">
        <div style="display:flex;align-items:center;">
          <span class="waiting-rank">#${i + 1}</span>
          <div>
            <b>#${q.id} ${esc(q.name)}</b>
            <div style="font-size:11px;color:var(--text-muted);">${esc(q.rto)} · ${q.date} at ${q.time}</div>
          </div>
        </div>
        <span class="badge" style="background:white;color:#b45309;">WAITLIST</span>
      </div>
    `).join('');
  } else {
    lobbyBadge.textContent = 'Lobby Clear';
    lobbyContainer.innerHTML = `
      <div class="telemetry-empty-state">
        <span class="empty-icon">✓</span>
        <b>Dispatch Lobby Clear</b>
        <p>Zero waitlist backlog. All applicant appointments assigned to confirmed slots.</p>
      </div>
    `;
  }

  /* 3. Production View: Applicant Pipeline (Replaces "HEAD -> NULL") */
  const pipelineContainer = $('applicantPipelineContainer');
  $('regCountBadge').textContent = d.linkedList.size + ' Registered';
  if (d.linkedList.nodes && d.linkedList.nodes.length) {
    pipelineContainer.innerHTML = d.linkedList.nodes.map(n => `
      <div style="padding:8px 12px;background:var(--bg-subtle);border:1px solid var(--border-light);border-radius:var(--radius-sm);font-size:12px;display:inline-flex;align-items:center;gap:8px;">
        <b>#${n.id}</b>
        <span>${esc(n.name)}</span>
        <span class="status ${n.status}" style="font-size:9px;padding:2px 6px;">${n.status}</span>
      </div>
    `).join('');
  } else {
    pipelineContainer.innerHTML = `
      <div class="telemetry-empty-state" style="width:100%;">
        <span class="empty-icon">📋</span>
        <b>Registry Pipeline Initialized</b>
        <p>No pending applicant registrations in memory cycle.</p>
      </div>
    `;
  }

  /* 4. Fee Calculation */
  if (d.expression) {
    $('exprProdOut').innerHTML = `Formula: <b>${d.expression.expression}</b> → Computed Service Fee: <b>₹ ${d.expression.result}</b>`;
  }

  /* 5. Route Navigator */
  if (d.graph) {
    $('routeProdOut').innerHTML = `Connected Centres: <b>${Object.keys(d.graph.adjacency).length} Regional Hubs</b>`;
  }

  /* DSA Architecture Mode bindings */
  $('stSize').textContent = `(${d.stack.size} entries)`;
  $('stack').textContent = d.stack.items.length ? d.stack.items.map((x, i) => (i === 0 ? 'TOP ▸ ' : '       ') + x).join('\n') : 'Stack is empty';

  $('qSize').textContent = `(${d.queue.size} waiting)`;
  $('queue').textContent = d.queue.items.length ? d.queue.items.map((x, i) => `${i === 0 ? 'FRONT ▸ ' : '         '}#${x.id} ${x.name} · ${x.rto} · ${x.date} ${x.time}`).join('\n') : 'Queue is empty';

  $('llSize').textContent = `(${d.linkedList.size} nodes)`;
  $('linkedList').innerHTML = d.linkedList.nodes.length
    ? 'HEAD → ' + d.linkedList.nodes.map(n => `<span class="node" title="${esc(n.status)}">#${n.id} ${esc(n.name)}</span><span class="arrow">→</span>`).join('') + '<span class="null">NULL</span>'
    : '<span class="null">HEAD → NULL (empty list)</span>';

  $('btInfo').textContent = `(${d.binaryTree.size} nodes, height ${d.binaryTree.height})`;
  $('btTree').textContent = treeText(d.binaryTree.tree);
  $('btTrav').textContent = `Preorder:  ${d.binaryTree.preorder.join(', ')}\nInorder:   ${d.binaryTree.inorder.join(', ')}\nPostorder: ${d.binaryTree.postorder.join(', ')}`;

  $('bstInfo').textContent = `(${d.bst.size} nodes, height ${d.bst.height})`;
  $('bstTree').textContent = treeText(d.bst.tree);
  $('bstTrav').textContent = `Preorder:  ${d.bst.preorder.join(', ') || '—'}\nInorder:   ${d.bst.inorder.join(', ') || '—'}\nPostorder: ${d.bst.postorder.join(', ') || '—'}`;

  showGraph(d.graph);
  if ($('dfsStart').value) $('dfsRun').click();
  if (d.heap) {
    $('heapOut').textContent = d.heap.slots.map((s, i) => `${i + 1}. ${s.time}  | ${s.booked} booked | ${s.free} free${i === 0 ? '  ← optimal' : ''}`).join('\n');
  }
}

$('dsaRun').onclick = () => loadTelemetry().catch(e => toast(e.message));

function showGraph(g) {
  $('adj').textContent = Object.entries(g.adjacency).map(([k, v]) => `${k}\n   → ${v.join(', ')}`).join('\n');
  const dist = g.distance;
  $('bfs').textContent = 'BFS from ' + (g.start || g.order[0]) + ':\n' + g.order.map(x => `${x}  (${dist[x]} hop${dist[x] === 1 ? '' : 's'})`).join('\n');
}

$('bfsStart').onchange = async () => {
  try {
    const g = await api('/api/dsa/bfs?start=' + encodeURIComponent($('bfsStart').value));
    showGraph(g);
  } catch (e) {
    toast(e.message);
  }
};

$('dfsRun').onclick = async () => {
  try {
    const d = await api('/api/dsa/dfs?start=' + encodeURIComponent($('dfsStart').value));
    $('routeProdOut').innerHTML = `Route Path: <b>${d.order.join(' → ')}</b> (${d.complexity})`;
  } catch (e) {
    toast(e.message);
  }
};

$('exprBtn').onclick = async () => {
  try {
    const res = await api('/api/dsa/expression?expr=' + encodeURIComponent($('expr').value));
    $('exprProdOut').innerHTML = `Formula: <b>${res.expression}</b> → Computed Service Fee: <b>₹ ${res.result}</b>`;
  } catch (e) {
    $('exprProdOut').innerHTML = `<span style="color:var(--rose);">Error evaluating: ${esc(e.message)}</span>`;
  }
};

$('cmpBtn').onclick = async () => {
  try {
    const c = await api('/api/compare?key=' + $('cmpKey').value);
    $('cmpOut').innerHTML = `
      <table style="margin-top:12px;">
        <thead>
          <tr>
            <th>Algorithm</th>
            <th>Comparisons</th>
            <th>Moves</th>
            <th>Time (ms)</th>
            <th>Complexity</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <td><b>Merge Sort (Recommended)</b></td>
            <td>${c.merge.comparisons}</td>
            <td>${c.merge.moves}</td>
            <td>${c.merge.millis.toFixed(3)}</td>
            <td>${esc(c.merge.complexity)}</td>
          </tr>
          <tr>
            <td><b>Insertion Sort</b></td>
            <td>${c.insertion.comparisons}</td>
            <td>${c.insertion.moves}</td>
            <td>${c.insertion.millis.toFixed(3)}</td>
            <td>${esc(c.insertion.complexity)}</td>
          </tr>
          <tr>
            <td><b>Linear Search (Worst)</b></td>
            <td>${c.linearSearchWorst}</td>
            <td>—</td>
            <td>—</td>
            <td>O(n)</td>
          </tr>
          <tr>
            <td><b>Binary Search (Worst)</b></td>
            <td>${c.binarySearchWorst}</td>
            <td>—</td>
            <td>—</td>
            <td>O(log n)</td>
          </tr>
        </tbody>
      </table>
    `;
  } catch (e) {
    toast(e.message);
  }
};

/* =========================================================================
   PAGE: APPLICANT DIRECTORY & RECORDS
   ========================================================================= */
async function loadRecords() {
  records = await api('/api/applicants');
  renderRows(records);
  info('');
}

function info(html) {
  $('recInfo').innerHTML = html;
  $('recInfo').classList.toggle('hidden', !html);
}

function renderRows(list) {
  $('rows').innerHTML = list.map(a => `
    <tr>
      <td>#${a.id}</td>
      <td><b>${esc(a.name)}</b><br><small style="color:var(--text-muted);">${esc(a.phone)}</small></td>
      <td>${esc(a.licenseType)}</td>
      <td>${esc(a.rto) || '—'}</td>
      <td>${esc(a.date) || '—'}</td>
      <td>${esc(a.time) || '—'}</td>
      <td><span class="status ${a.status}">${a.status}</span></td>
      <td>
        <div style="display:flex;gap:6px;">
          ${a.status === 'CONFIRMED' || a.status === 'WAITING' ? `
            <button class="btn btn-danger btn-sm" data-act="cancel" data-id="${a.id}">Cancel</button>
          ` : ''}
          ${isAdmin ? `
            <button class="btn btn-ghost btn-sm" data-act="edit" data-id="${a.id}">Edit</button>
            <button class="btn btn-danger btn-sm" data-act="delete" data-id="${a.id}">Delete</button>
          ` : ''}
        </div>
      </td>
    </tr>
  `).join('') || '<tr><td colspan="8" style="text-align:center;color:var(--text-muted);padding:30px;">No applicant records found.</td></tr>';
}

$('rows').addEventListener('click', e => {
  const b = e.target.closest('button[data-act]');
  if (!b) return;
  const id = +b.dataset.id;
  if (b.dataset.act === 'cancel') cancelBooking(id);
  else if (b.dataset.act === 'edit') openEdit(id);
  else if (b.dataset.act === 'delete') deleteApplicant(id);
});

async function cancelBooking(id) {
  if (!confirm('Cancel booking for applicant #' + id + '?')) return;
  try {
    const j = await post('/api/cancel', {id});
    toast(j.message);
    await loadRecords();
  } catch (err) {
    toast(err.message);
  }
}

async function deleteApplicant(id) {
  if (!confirm('Permanently delete applicant #' + id + '?')) return;
  try {
    const j = await api('/api/applicants', {
      method: 'DELETE',
      body: JSON.stringify({id})
    });
    toast('Applicant record removed.' + (j.promoted && j.promoted.length ? ' ' + j.promoted.length + ' waitlist applicant(s) promoted!' : ''));
    await loadRecords();
  } catch (err) {
    toast(err.message);
  }
}

/* Edit / Add Modal */
function openEdit(id) {
  const a = records.find(x => x.id === id);
  if (!a) return;
  editingId = id;
  $('editTitle').textContent = 'Edit Applicant #' + id;
  $('eName').value = a.name;
  $('ePhone').value = a.phone;
  $('eLicense').value = a.licenseType;
  say($('eMsg'), '');
  $('editDlg').showModal();
}

$('addApplicantBtn').onclick = () => {
  editingId = null;
  $('editTitle').textContent = 'Add Applicant';
  $('eName').value = '';
  $('ePhone').value = '';
  $('eLicense').value = meta.licenseTypes[0];
  say($('eMsg'), '');
  $('editDlg').showModal();
};

$('eCancel').onclick = () => $('editDlg').close();

$('editForm').addEventListener('submit', async e => {
  e.preventDefault();
  const body = {
    name: $('eName').value.trim(),
    phone: $('ePhone').value.trim(),
    licenseType: $('eLicense').value
  };
  try {
    if (editingId) {
      await api('/api/applicants', {method: 'PUT', body: JSON.stringify({id: editingId, ...body})});
    } else {
      await post('/api/applicants', body);
    }
    $('editDlg').close();
    toast(editingId ? 'Applicant updated.' : 'Applicant registered.');
    await loadRecords();
  } catch (err) {
    say($('eMsg'), err.message, 'err');
  }
});

/* Search */
$('searchBtn').onclick = doSearch;
$('search').addEventListener('keydown', e => { if (e.key === 'Enter') doSearch(); });
$('searchClear').onclick = () => { $('search').value = ''; loadRecords(); };

async function doSearch() {
  const q = $('search').value.trim();
  if (!q) return toast('Type something to search for.');
  try {
    const r = await api(`/api/search?mode=${$('searchMode').value}&field=${$('searchField').value}&q=${encodeURIComponent(q)}`);
    renderRows(r.matches);
    info(`Search for "${esc(r.query)}" in ${esc(r.field)}: <b>${r.matches.length}</b> result(s) found via <b>${esc(r.mode.toUpperCase())}</b> (${esc(r.complexity)}).`);
  } catch (err) {
    toast(err.message);
  }
}

/* Sort */
$('sortBtn').onclick = async () => {
  try {
    const r = await api(`/api/sort?key=${$('sortKey').value}&algo=${$('sortAlgo').value}&order=asc`);
    records = r.records;
    renderRows(records);
    info(`Sorted by ${esc(r.key)} using ${esc(r.algorithm.toUpperCase())}: ${records.length} records processed in ${r.millis.toFixed(3)} ms.`);
  } catch (err) {
    toast(err.message);
  }
};

/* =========================================================================
   PAGE: SMART APPOINTMENT SCHEDULING (Admin)
   ========================================================================= */
async function initAlgoPage() {
  const all = await api('/api/applicants');
  const w = all.find(a => a.status === 'WAITING');
  if (w) {
    $('algoRto').value = w.rto;
    $('algoDate').value = w.date;
  }
  await runAlgo('all');
}

const stepList = steps => `
  <table>
    <tr><th>Applicant</th><th>Category</th><th>Requested</th><th>Allocated Slot</th></tr>
    ${steps.map(s => `
      <tr>
        <td>#${s.id < 0 ? 'N/A' : s.id} ${esc(s.name)}</td>
        <td>${esc(s.licenseType)}</td>
        <td>${s.preferred}</td>
        <td>${s.assigned ? '<span style="color:#059669;font-weight:700;">' + s.assigned + '</span>' : '<span style="color:#ef4444;font-weight:700;">Unassigned</span>'}</td>
      </tr>
    `).join('')}
  </table>
`;

async function runAlgo(which) {
  const q = `?rto=${encodeURIComponent($('algoRto').value)}&date=${$('algoDate').value}`;
  const names = which === 'all' ? ['greedy', 'dp', 'backtrack'] : [which];

  for (const n of names) {
    const r = await api('/api/algorithms/' + n + q);
    $('algoNote').classList.remove('hidden');
    $('algoNote').innerHTML = `<b>${r.source === 'live' ? 'Live State' : 'Sample Simulation'}:</b> ${esc(r.note)} · Centre: <b>${esc(r.rto)}</b>, Date: <b>${r.date}</b>. Free capacity: ${r.slots.map(s => s.time + '=' + s.free).join(' · ')}.`;

    if (n === 'greedy') {
      $('greedy').innerHTML = stepList(r.steps) + `<div style="margin-top:8px;">Placed ${r.placed} of ${r.total} waiting applicants.</div>`;
    }
    if (n === 'dp') {
      $('dp').innerHTML = r.feasible
        ? `<div style="color:#059669;font-weight:700;">Minimum slots needed: ${r.minSlots} (${r.chosenSlots.join(', ')}) for ${r.demand} applicant(s).</div>`
        : `<div style="color:#ef4444;font-weight:700;">Insufficient capacity for ${r.demand} applicant(s).</div>`;
    }
    if (n === 'backtrack') {
      lastBacktrack = r;
      $('backtrack').innerHTML = r.solved
        ? stepList(r.steps) + `<div style="margin-top:8px;">Valid arrangement computed (Constraint resolutions: ${r.backtracks}).</div>`
        : `<div style="color:#ef4444;font-weight:700;">No valid collision-free arrangement exists.</div>`;
      $('applyAlloc').disabled = !(r.solved && r.source === 'live');
    }
  }
}

$('algoRun').onclick = () => runAlgo('all').catch(e => toast(e.message));
document.querySelectorAll('button[data-algo]').forEach(b => {
  b.onclick = () => runAlgo(b.dataset.algo).catch(e => toast(e.message));
});
$('algoRto').onchange = $('algoDate').onchange = () => runAlgo('all').catch(e => toast(e.message));

$('applyAlloc').onclick = async () => {
  if (!confirm('Promote waiting applicants to the computed arrangement now?')) return;
  try {
    const j = await post('/api/algorithms/allocate', {
      rto: $('algoRto').value,
      date: $('algoDate').value
    });
    toast(j.message);
    await runAlgo('all');
  } catch (e) {
    toast(e.message);
  }
};

/* =========================================================================
   PAGE: SMART SERVICE LAB (Analytics)
   ========================================================================= */
async function loadSmart() {
  await renderCrowdChart();
}

async function renderCrowdChart() {
  try {
    const data = await api('/api/slots?rto=' + encodeURIComponent($('crowdRto').value) + '&date=' + $('crowdDate').value);
    $('crowdChart').innerHTML = data.map(s => {
      const pct = Math.round((s.booked / meta.capacity) * 100);
      const isLow = pct <= 33;
      const isMed = pct > 33 && pct <= 67;
      const cls = isLow ? 'low' : isMed ? 'medium' : 'high';

      return `
        <div style="display:grid;grid-template-columns:100px 1fr 100px;align-items:center;gap:12px;margin-bottom:12px;">
          <b>${s.time}</b>
          <div class="congestion-meter-track" style="margin:0;">
            <div class="congestion-meter-fill ${cls}" style="width:${Math.max(8, pct)}%"></div>
          </div>
          <span style="font-size:12px;font-weight:700;text-align:right;">${s.booked}/${meta.capacity} Booked</span>
        </div>
      `;
    }).join('');
  } catch (e) {
    toast(e.message);
  }
}

$('crowdRefresh').onclick = renderCrowdChart;
$('crowdRto').onchange = $('crowdDate').onchange = renderCrowdChart;

$('scoreBtn').onclick = async () => {
  try {
    const r = await api('/api/smart/slot-score?rto=' + encodeURIComponent($('crowdRto').value) + '&date=' + $('crowdDate').value);
    $('smartResult').innerHTML = `
      <div style="display:grid;grid-template-columns:repeat(3, 1fr);gap:10px;">
        ${r.slots.map(x => `
          <div style="padding:12px;background:white;border:1px solid var(--border-light);border-radius:var(--radius-md);">
            <b>${x.time}</b>
            <div style="font-size:12px;color:var(--primary);">${x.score}/100 Fair Score (${x.label})</div>
            <small style="color:var(--text-muted);">${x.free} free seats remaining</small>
          </div>
        `).join('')}
      </div>
    `;
  } catch (e) {
    toast(e.message);
  }
};

$('waitBtn').onclick = async () => {
  try {
    const r = await api('/api/smart/wait-time?rto=' + encodeURIComponent($('crowdRto').value) + '&date=' + $('crowdDate').value);
    $('smartResult').innerHTML = `
      <div style="padding:16px;background:var(--bg-subtle);border-radius:var(--radius-md);border:1px solid var(--border-light);">
        <b>Queue Delay Forecast: ~${r.minutes} Minutes (${r.level} Congestion)</b>
        <p style="margin:4px 0 0;font-size:13px;color:var(--text-muted);">${r.waiting} waiting applicants ahead · ${r.message}</p>
      </div>
    `;
  } catch (e) {
    toast(e.message);
  }
};

/* ---------------- Auto Start on Existing Session ---------------- */
if (sessionStorage.token) {
  startApp().catch(() => {
    sessionStorage.clear();
    location.reload();
  });
}
