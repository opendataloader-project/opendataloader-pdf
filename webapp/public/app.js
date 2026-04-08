const form = document.getElementById('uploadForm');
const fileInput = document.getElementById('fileInput');
const fileInfo = document.getElementById('fileInfo');
const convertBtn = document.getElementById('convertBtn');
const statusEl = document.getElementById('status');
const dropZone = document.getElementById('dropZone');

const resultSection = document.getElementById('result');
const resultTitle = document.getElementById('resultTitle');
const markdownOut = document.getElementById('markdownOut');
const copyBtn = document.getElementById('copyBtn');
const downloadBtn = document.getElementById('downloadBtn');

let lastResult = null;

function setStatus(message, kind = '') {
  statusEl.textContent = message;
  statusEl.className = 'status' + (kind ? ' ' + kind : '');
}

function formatBytes(bytes) {
  if (!bytes && bytes !== 0) return '';
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function showFile(file) {
  if (!file) {
    fileInfo.hidden = true;
    convertBtn.disabled = true;
    return;
  }
  fileInfo.hidden = false;
  fileInfo.innerHTML = `<strong>${file.name}</strong> &middot; ${formatBytes(file.size)}`;
  convertBtn.disabled = false;
  setStatus('');
}

fileInput.addEventListener('change', (e) => {
  showFile(e.target.files[0]);
});

['dragenter', 'dragover'].forEach((evt) =>
  dropZone.addEventListener(evt, (e) => {
    e.preventDefault();
    dropZone.querySelector('.drop-label').classList.add('dragover');
  })
);
['dragleave', 'drop'].forEach((evt) =>
  dropZone.addEventListener(evt, (e) => {
    e.preventDefault();
    dropZone.querySelector('.drop-label').classList.remove('dragover');
  })
);
dropZone.addEventListener('drop', (e) => {
  const file = e.dataTransfer.files && e.dataTransfer.files[0];
  if (!file) return;
  if (!/\.pdf$/i.test(file.name) && file.type !== 'application/pdf') {
    setStatus('Only PDF files are accepted.', 'error');
    return;
  }
  const dt = new DataTransfer();
  dt.items.add(file);
  fileInput.files = dt.files;
  showFile(file);
});

form.addEventListener('submit', async (e) => {
  e.preventDefault();
  const file = fileInput.files[0];
  if (!file) return;

  convertBtn.disabled = true;
  setStatus('Converting… (this may take a few seconds for large PDFs)');
  resultSection.hidden = true;

  const formData = new FormData();
  formData.append('pdf', file);

  try {
    const res = await fetch('/api/convert', { method: 'POST', body: formData });
    const payload = await res.json().catch(() => ({}));
    if (!res.ok) {
      throw new Error(payload.error || `Server returned ${res.status}`);
    }
    lastResult = payload;
    markdownOut.textContent = payload.markdown;
    resultTitle.textContent = `Markdown output — ${payload.filename}`;
    resultSection.hidden = false;
    setStatus(`Converted ${file.name} (${formatBytes(file.size)}).`, 'success');
  } catch (err) {
    setStatus(err.message || 'Conversion failed.', 'error');
  } finally {
    convertBtn.disabled = false;
  }
});

copyBtn.addEventListener('click', async () => {
  if (!lastResult) return;
  try {
    await navigator.clipboard.writeText(lastResult.markdown);
    copyBtn.textContent = 'Copied!';
    setTimeout(() => (copyBtn.textContent = 'Copy'), 1500);
  } catch {
    copyBtn.textContent = 'Copy failed';
    setTimeout(() => (copyBtn.textContent = 'Copy'), 1500);
  }
});

downloadBtn.addEventListener('click', () => {
  if (!lastResult) return;
  const blob = new Blob([lastResult.markdown], { type: 'text/markdown;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = lastResult.filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
});
