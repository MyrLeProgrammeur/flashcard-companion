/* Lecteur PDF minimal (Batch 4) — PDF.js core only (pdf.min.mjs +
   pdf.worker.min.mjs), aucune UI Mozilla (pas de viewer.html/css/js).
   Rendu page unique (pas de scroll continu multi-page), fit-to-width,
   pensé pour la WebView Android de l'app. */

import * as pdfjsLib from "/vendor/pdfjs/pdf.min.mjs";

pdfjsLib.GlobalWorkerOptions.workerSrc = "/vendor/pdfjs/pdf.worker.min.mjs";

const el = (id) => document.getElementById(id);

const canvas = el("pdf-canvas");
const ctx = canvas.getContext("2d");
const statusEl = el("pdf-status");
const pageIndicator = el("page-indicator");
const prevBtn = el("prev-btn");
const nextBtn = el("next-btn");

el("back-btn").innerHTML = icon("chevronLeft");
prevBtn.innerHTML = icon("chevronLeft");
nextBtn.innerHTML = icon("chevronRight");

const params = new URLSearchParams(window.location.search);
const relPath = params.get("path") || "";

function filenameFromPath(path) {
  if (!path) return "Cours";
  // URLSearchParams.get() already decodes the query value once.
  const base = path.split("/").pop();
  return base || "Cours";
}

el("pdf-title").textContent = filenameFromPath(relPath);

function showStatus(msg) {
  statusEl.textContent = msg;
  statusEl.classList.remove("hidden");
  canvas.classList.add("hidden");
}

function hideStatus() {
  statusEl.classList.add("hidden");
  canvas.classList.remove("hidden");
}

let pdfDoc = null;
let currentPage = 1;
let renderTask = null;

async function renderPage(num) {
  if (!pdfDoc) return;
  const page = await pdfDoc.getPage(num);

  const viewportUnscaled = page.getViewport({ scale: 1 });
  const containerWidth = el("pdf-viewport").clientWidth;
  const scale = containerWidth / viewportUnscaled.width;
  const viewport = page.getViewport({ scale });

  const outputScale = window.devicePixelRatio || 1;
  canvas.width = Math.floor(viewport.width * outputScale);
  canvas.height = Math.floor(viewport.height * outputScale);
  canvas.style.width = `${Math.floor(viewport.width)}px`;
  canvas.style.height = `${Math.floor(viewport.height)}px`;

  const transform = outputScale !== 1 ? [outputScale, 0, 0, outputScale, 0, 0] : null;

  if (renderTask) {
    try { renderTask.cancel(); } catch { /* ignore */ }
  }
  renderTask = page.render({ canvasContext: ctx, viewport, transform });
  await renderTask.promise;

  currentPage = num;
  pageIndicator.textContent = `${currentPage} / ${pdfDoc.numPages}`;
  prevBtn.disabled = currentPage <= 1;
  nextBtn.disabled = currentPage >= pdfDoc.numPages;
}

prevBtn.addEventListener("click", () => {
  if (currentPage > 1) renderPage(currentPage - 1);
});
nextBtn.addEventListener("click", () => {
  if (pdfDoc && currentPage < pdfDoc.numPages) renderPage(currentPage + 1);
});

let resizeTimer = null;
window.addEventListener("resize", () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(() => renderPage(currentPage), 150);
});

async function load() {
  if (!relPath) {
    showStatus("Aucun cours spécifié.");
    return;
  }
  showStatus("Chargement du PDF…");
  try {
    const url = `/api/courses/file?path=${encodeURIComponent(relPath)}`;
    const loadingTask = pdfjsLib.getDocument(url);
    pdfDoc = await loadingTask.promise;
    hideStatus();
    await renderPage(1);
  } catch (e) {
    showStatus("Impossible de charger ce PDF.");
  }
}

load();
