const profile = {
  id: 'niosh',
  name: 'NIOSH REL',
  criterionDbA: 85,
  exchangeRateDb: 3,
  thresholdDbA: 80,
  referenceHours: 8,
};

const state = {
  stream: null,
  audioContext: null,
  analyser: null,
  source: null,
  running: false,
  startedAt: null,
  lastFrameAt: 0,
  energySum: 0,
  sampleCount: 0,
  points: [],
  maxDbA: 0,
  sessionId: null,
  history: JSON.parse(localStorage.getItem('levelapp-web-history') || '[]'),
};

const liveDbAEl = document.getElementById('liveDbA');
const meterFillEl = document.getElementById('meterFill');
const laeqValueEl = document.getElementById('laeqValue');
const lasMaxValueEl = document.getElementById('lasMaxValue');
const twaValueEl = document.getElementById('twaValue');
const doseValueEl = document.getElementById('doseValue');
const durationValueEl = document.getElementById('durationValue');
const stateValueEl = document.getElementById('stateValue');
const micStateEl = document.getElementById('micState');
const calibrationStateEl = document.getElementById('calibrationState');
const sessionListEl = document.getElementById('sessionList');
const notesEl = document.getElementById('notes');
const startBtn = document.getElementById('startBtn');
const pauseBtn = document.getElementById('pauseBtn');
const stopBtn = document.getElementById('stopBtn');
const exportPdfBtn = document.getElementById('exportPdfBtn');
const shareBtn = document.getElementById('shareBtn');
const chartCanvas = document.getElementById('historyChart');
const ctx = chartCanvas.getContext('2d');

function formatDb(value) {
  if (!Number.isFinite(value)) return '--';
  return `${value.toFixed(1)} dB(A)`;
}

function formatMinutes(ms) {
  const totalSeconds = Math.max(0, ms / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = Math.floor(totalSeconds % 60);
  return `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
}

function dosePercent(levelDbA, durationHours) {
  if (durationHours <= 0 || levelDbA < profile.thresholdDbA) return 0;
  const dose = (durationHours / profile.referenceHours) * Math.pow(2, (levelDbA - profile.criterionDbA) / profile.exchangeRateDb);
  return dose * 100;
}

function twa(levelDbA, durationHours) {
  if (durationHours <= 0) return Number.NEGATIVE_INFINITY;
  const p = dosePercent(levelDbA, durationHours) / 100;
  return p <= 0 ? Number.NEGATIVE_INFINITY : profile.criterionDbA + 10 * Math.log10(p);
}

function updateSummary(levelDbA, durationMs) {
  const durationHours = durationMs / 3600000;
  const currentDose = dosePercent(levelDbA, durationHours);
  const currentTwa = twa(levelDbA, durationHours);

  const percentage = Math.min(100, Math.max(8, (levelDbA / 120) * 100));
  meterFillEl.style.width = `${percentage}%`;

  liveDbAEl.textContent = levelDbA.toFixed(1);
  laeqValueEl.textContent = formatDb(levelDbA);
  lasMaxValueEl.textContent = formatDb(Math.max(levelDbA, state.maxDbA));
  twaValueEl.textContent = Number.isFinite(currentTwa) ? `${currentTwa.toFixed(1)} dB(A)` : '--';
  doseValueEl.textContent = `${currentDose.toFixed(1)} %`;
  durationValueEl.textContent = formatMinutes(durationMs);
  stateValueEl.textContent = state.running ? 'Grabando' : 'En espera';
}

function renderChartToContext(targetCtx, width, height, points) {
  const pad = { top: 18, right: 18, bottom: 32, left: 42 };
  const minY = 40;
  const maxY = 120;
  const minValue = minY;
  const maxValue = maxY;

  targetCtx.clearRect(0, 0, width, height);

  targetCtx.strokeStyle = 'rgba(255,255,255,0.08)';
  targetCtx.lineWidth = 1;

  for (let i = 0; i <= 4; i++) {
    const y = pad.top + (i * (height - pad.top - pad.bottom)) / 4;
    targetCtx.beginPath();
    targetCtx.moveTo(pad.left, y);
    targetCtx.lineTo(width - pad.right, y);
    targetCtx.stroke();
  }

  targetCtx.fillStyle = 'rgba(157,176,194,0.8)';
  targetCtx.font = '12px sans-serif';
  for (let i = 0; i <= 4; i++) {
    const value = maxY - (i * (maxY - minY)) / 4;
    const y = pad.top + (i * (height - pad.top - pad.bottom)) / 4;
    targetCtx.fillText(`${value.toFixed(0)} dB`, 6, y + 4);
  }

  if (!points.length) {
    targetCtx.fillStyle = 'rgba(157,176,194,0.8)';
    targetCtx.font = '14px sans-serif';
    targetCtx.fillText('Sin datos grabados', pad.left, height / 2);
    return;
  }

  const startTime = points[0].timestamp;
  const endTime = points[points.length - 1].timestamp;
  const totalSpan = Math.max(1, endTime - startTime);

  targetCtx.beginPath();
  points.forEach((point, idx) => {
    const x = pad.left + ((point.timestamp - startTime) / totalSpan) * (width - pad.left - pad.right);
    const y = height - pad.bottom - ((point.dbA - minValue) / Math.max(1, maxValue - minValue)) * (height - pad.top - pad.bottom);
    if (idx === 0) targetCtx.moveTo(x, y);
    else targetCtx.lineTo(x, y);
  });
  targetCtx.strokeStyle = '#7cc6ff';
  targetCtx.lineWidth = 2;
  targetCtx.stroke();

  const refY = height - pad.bottom - ((profile.criterionDbA - minY) / (maxY - minY)) * (height - pad.top - pad.bottom);
  targetCtx.strokeStyle = 'rgba(255,200,87,0.95)';
  targetCtx.beginPath();
  targetCtx.moveTo(pad.left, refY);
  targetCtx.lineTo(width - pad.right, refY);
  targetCtx.stroke();

  targetCtx.fillStyle = 'rgba(255,200,87,0.95)';
  targetCtx.font = '12px sans-serif';
  targetCtx.fillText('NIOSH REL — 85 dBA / 8 h', width - 190, refY - 8);

  targetCtx.fillStyle = 'rgba(157,176,194,0.8)';
  targetCtx.font = '11px sans-serif';
  for (let i = 0; i <= 4; i++) {
    const t = (totalSpan * i) / 4;
    const x = pad.left + (i * (width - pad.left - pad.right)) / 4;
    targetCtx.fillText(`${t.toFixed(0)} s`, x - 8, height - 10);
  }

  targetCtx.fillStyle = 'rgba(157,176,194,0.8)';
  targetCtx.fillText('Tiempo (s)', width / 2 - 22, height - 2);
  targetCtx.fillText('dB(A)', 12, 14);
}

function drawHistoryChart() {
  renderChartToContext(ctx, chartCanvas.width, chartCanvas.height, state.points);
}

function updateSessionList() {
  sessionListEl.innerHTML = '';
  if (!state.history.length) {
    const li = document.createElement('li');
    li.innerHTML = '<div class="session-title"><strong>Sin sesiones</strong><span>La sesión se guardará en el historial local del navegador.</span></div><span class="badge">LISTO</span>';
    sessionListEl.appendChild(li);
    return;
  }

  state.history.slice().reverse().forEach((item) => {
    const li = document.createElement('li');
    li.innerHTML = `
      <div class="session-title">
        <strong>${item.title}</strong>
        <span>${item.timestamp} • ${item.laeq.toFixed(1)} dB(A) • ${item.dose.toFixed(1)}% dosis</span>
      </div>
      <span class="badge">${item.profile}</span>
    `;
    sessionListEl.appendChild(li);
  });
}

async function requestMicrophone() {
  if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
    micStateEl.textContent = 'No disponible';
    alert('Este navegador no soporta getUserMedia. La demo web necesitará un navegador con soporte de micrófono.');
    return false;
  }

  try {
    const stream = await navigator.mediaDevices.getUserMedia({
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: false,
      },
    });

    state.stream = stream;
    state.audioContext = new (window.AudioContext || window.webkitAudioContext)();
    state.analyser = state.audioContext.createAnalyser();
    state.analyser.fftSize = 2048;
    state.source = state.audioContext.createMediaStreamSource(stream);
    state.source.connect(state.analyser);
    micStateEl.textContent = 'Activo';
    return true;
  } catch (error) {
    micStateEl.textContent = 'Permiso denegado';
    console.error(error);
    return false;
  }
}

function processAudioFrame() {
  if (!state.running || !state.analyser) return;
  const bufferLength = state.analyser.fftSize;
  const dataArray = new Uint8Array(bufferLength);
  state.analyser.getByteTimeDomainData(dataArray);

  let sumSquares = 0;
  for (let i = 0; i < dataArray.length; i++) {
    const sample = (dataArray[i] - 128) / 128;
    sumSquares += sample * sample;
  }

  const rms = Math.sqrt(sumSquares / dataArray.length);
  const linearPressure = rms * 0.707;
  const dbLinear = 20 * Math.log10(Math.max(linearPressure, 1e-6) / 0.00002);
  // A-weighting compensation used only for the browser demo, not a certified measurement.
  const dbA = dbLinear + 0.25 * Math.log10(Math.max(linearPressure, 1e-6) + 1e-6) * 20;

  const now = performance.now();
  const duration = state.startedAt ? now - state.startedAt : 0;

  state.energySum += Math.pow(10, dbA / 10);
  state.sampleCount += 1;
  state.maxDbA = Math.max(state.maxDbA, dbA);

  state.points.push({
    timestamp: now,
    elapsedSeconds: duration / 1000,
    dbA,
    laeq: dbA,
    dose: dosePercent(dbA, (duration || 1) / 3600000),
    twa: twa(dbA, (duration || 1) / 3600000),
  });

  const smoothedDb = state.points.length ? state.points[state.points.length - 1].dbA : dbA;
  const laeq = 10 * Math.log10((state.energySum / Math.max(1, state.sampleCount)) * 0.01);

  updateSummary(smoothedDb, duration);
  laeqValueEl.textContent = `${laeq.toFixed(1)} dB(A)`;
  lasMaxValueEl.textContent = `${state.maxDbA.toFixed(1)} dB(A)`;

  drawHistoryChart();
  requestAnimationFrame(processAudioFrame);
}

async function startSession() {
  const granted = await requestMicrophone();
  if (!granted) return;

  state.running = true;
  state.startedAt = performance.now();
  state.lastFrameAt = state.startedAt;
  state.energySum = 0;
  state.sampleCount = 0;
  state.maxDbA = 0;
  state.points = [];
  state.sessionId = `ses-${Date.now()}`;
  stateValueEl.textContent = 'Grabando';
  calibrationStateEl.textContent = 'CALIBRACIÓN NO VALIDADA';
  calibrationStateEl.style.background = 'rgba(255,200,87,0.14)';
  calibrationStateEl.style.color = '#ffc857';
  drawHistoryChart();
  requestAnimationFrame(processAudioFrame);
}

function pauseSession() {
  state.running = false;
  stateValueEl.textContent = 'Pausada';
}

function finishSession() {
  state.running = false;
  const durationMs = state.startedAt ? performance.now() - state.startedAt : 0;
  const rawD = state.points.length ? state.points[state.points.length - 1].dbA : 0;
  const finalDose = dosePercent(rawD, durationMs / 3600000);
  const finalTwa = twa(rawD, durationMs / 3600000);

  const session = {
    title: `Sesión ${new Date().toLocaleString()}`,
    timestamp: new Date().toLocaleString(),
    profile: profile.name,
    laeq: state.points.length ? state.points[state.points.length - 1].laeq : 0,
    dose: finalDose,
    twa: Number.isFinite(finalTwa) ? finalTwa : 0,
    notes: notesEl.value || 'Sin notas',
  };

  state.history.push(session);
  localStorage.setItem('levelapp-web-history', JSON.stringify(state.history));
  updateSessionList();

  stateValueEl.textContent = 'Finalizada';
  durationValueEl.textContent = formatMinutes(durationMs);
  if (state.stream) {
    state.stream.getTracks().forEach((track) => track.stop());
    state.stream = null;
  }
  if (state.audioContext) {
    state.audioContext.close();
    state.audioContext = null;
  }
  state.startedAt = null;
  calibrationStateEl.textContent = 'NO CALIBRADO';
  calibrationStateEl.style.background = 'rgba(255,122,122,0.12)';
  calibrationStateEl.style.color = '#ff7a7a';
  micStateEl.textContent = 'Sin acceso';
}

function buildPrintableReport() {
  const existing = document.getElementById('printReport');
  if (existing) existing.remove();

  const report = document.createElement('div');
  report.id = 'printReport';

  const reportCanvas = document.createElement('canvas');
  reportCanvas.width = 1200;
  reportCanvas.height = 360;
  const reportCtx = reportCanvas.getContext('2d');
  renderChartToContext(reportCtx, reportCanvas.width, reportCanvas.height, state.points);

  const lastPoint = state.points[state.points.length - 1] || null;
  const summary = {
    laeq: lastPoint ? lastPoint.laeq : 0,
    dose: lastPoint ? lastPoint.dose : 0,
    twa: lastPoint ? lastPoint.twa : 0,
    max: state.maxDbA,
    duration: state.startedAt ? performance.now() - state.startedAt : 0,
  };

  report.innerHTML = `
    <div class="print-report-shell">
      <div class="print-report-header">
        <div>
          <div class="print-eyebrow">LevelApp • Dosimetría acústica</div>
          <h2>Informe de sesión</h2>
        </div>
        <span class="print-badge">${profile.name}</span>
      </div>

      <div class="print-summary-grid">
        <div><span>Fecha</span><strong>${new Date().toLocaleString()}</strong></div>
        <div><span>LAeq</span><strong>${summary.laeq.toFixed(1)} dB(A)</strong></div>
        <div><span>LASmax</span><strong>${summary.max.toFixed(1)} dB(A)</strong></div>
        <div><span>TWA</span><strong>${Number.isFinite(summary.twa) ? summary.twa.toFixed(1) : '--'} dB(A)</strong></div>
        <div><span>Dosis</span><strong>${summary.dose.toFixed(1)} %</strong></div>
        <div><span>Duración</span><strong>${formatMinutes(summary.duration)}</strong></div>
      </div>

      <div class="print-chart-wrap">
        <canvas id="reportChartCanvas" width="1200" height="360"></canvas>
      </div>

      <div class="print-notes">
        <h3>Notas</h3>
        <p>${(notesEl.value || 'Sin notas').replace(/</g, '&lt;').replace(/\n/g, '<br>')}</p>
      </div>
    </div>
  `;

  document.body.appendChild(report);
  const finalCanvas = document.getElementById('reportChartCanvas');
  const finalCtx = finalCanvas.getContext('2d');
  renderChartToContext(finalCtx, finalCanvas.width, finalCanvas.height, state.points);
  window.print();
  setTimeout(() => report.remove(), 800);
}

function exportPdf() {
  buildPrintableReport();
}

async function shareSession() {
  const summary = `LevelApp — ${profile.name}\nLAeq: ${laeqValueEl.textContent}\nTWA: ${twaValueEl.textContent}\nDosis: ${doseValueEl.textContent}\nPerfil: ${profile.name}`;

  if (navigator.share) {
    try {
      await navigator.share({
        title: 'LevelApp — Resumen de sesión',
        text: summary,
      });
      return;
    } catch (error) {
      console.warn('Share cancelled', error);
    }
  }

  if (navigator.clipboard) {
    await navigator.clipboard.writeText(summary);
    alert('Resumen copiado al portapapeles.');
  } else {
    alert(summary);
  }
}

startBtn.addEventListener('click', startSession);
pauseBtn.addEventListener('click', pauseSession);
stopBtn.addEventListener('click', finishSession);
exportPdfBtn.addEventListener('click', exportPdf);
shareBtn.addEventListener('click', shareSession);

calibrationStateEl.textContent = 'NO CALIBRADO';
calibrationStateEl.style.background = 'rgba(255,122,122,0.12)';
calibrationStateEl.style.color = '#ff7a7a';
updateSessionList();
drawHistoryChart();
