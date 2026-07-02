/* Lecteur PDF minimal — PDF.js core only (pdf.min.mjs + pdf.worker.min.mjs),
   aucune UI Mozilla (pas de viewer.html/css/js). Rendu continu : toutes les
   pages sont empilées verticalement, fit-to-width, pensé pour la WebView
   Android de l'app. */

import * as pdfjsLib from "/vendor/pdfjs/pdf.min.mjs";

pdfjsLib.GlobalWorkerOptions.workerSrc = "/vendor/pdfjs/pdf.worker.min.mjs";

const el = (id) => document.getElementById(id);

const pagesContainer = el("pdf-pages");
const statusEl = el("pdf-status");
const viewport = el("pdf-viewport");

el("back-btn").innerHTML = icon("chevronLeft");
el("pdf-help-fab").innerHTML = icon("spark");
el("help-sheet-close").innerHTML = icon("close");
el("pdf-help-submit").innerHTML = icon("spark");

const params = new URLSearchParams(window.location.search);
const relPath = params.get("path") || "";

function filenameFromPath(path) {
  if (!path) return t("nav.courses");
  // URLSearchParams.get() already decodes the query value once.
  const base = path.split("/").pop();
  return base || t("nav.courses");
}

el("pdf-title").textContent = filenameFromPath(relPath);

function showStatus(msg) {
  statusEl.textContent = msg;
  statusEl.classList.remove("hidden");
  pagesContainer.classList.add("hidden");
}

function hideStatus() {
  statusEl.classList.add("hidden");
  pagesContainer.classList.remove("hidden");
}

let pdfDoc = null;
let pageCanvases = [];

async function renderPageInto(canvas, page) {
  const viewportUnscaled = page.getViewport({ scale: 1 });
  const containerWidth = viewport.clientWidth - 20; // account for viewport padding
  const scale = containerWidth / viewportUnscaled.width;
  const scaledViewport = page.getViewport({ scale });

  const outputScale = window.devicePixelRatio || 1;
  canvas.width = Math.floor(scaledViewport.width * outputScale);
  canvas.height = Math.floor(scaledViewport.height * outputScale);
  canvas.style.width = `${Math.floor(scaledViewport.width)}px`;
  canvas.style.height = `${Math.floor(scaledViewport.height)}px`;

  const ctx = canvas.getContext("2d");
  const transform = outputScale !== 1 ? [outputScale, 0, 0, outputScale, 0, 0] : null;
  await page.render({ canvasContext: ctx, viewport: scaledViewport, transform }).promise;
}

async function renderAllPages() {
  pagesContainer.innerHTML = "";
  pageCanvases = [];
  for (let num = 1; num <= pdfDoc.numPages; num++) {
    const canvas = document.createElement("canvas");
    canvas.className = "pdf-page-canvas";
    pagesContainer.appendChild(canvas);
    pageCanvases.push(canvas);
    const page = await pdfDoc.getPage(num);
    await renderPageInto(canvas, page);
  }
}

let resizeTimer = null;
window.addEventListener("resize", () => {
  clearTimeout(resizeTimer);
  resizeTimer = setTimeout(async () => {
    if (!pdfDoc) return;
    for (let i = 0; i < pageCanvases.length; i++) {
      const page = await pdfDoc.getPage(i + 1);
      await renderPageInto(pageCanvases[i], page);
    }
  }, 150);
});

async function load() {
  if (!relPath) {
    showStatus(t("pdf.noCourse"));
    return;
  }
  showStatus(t("pdf.loading"));
  try {
    const url = `/api/courses/file?path=${encodeURIComponent(relPath)}`;
    const loadingTask = pdfjsLib.getDocument({ url });
    pdfDoc = await loadingTask.promise;
    hideStatus();
    await renderAllPages();
  } catch (e) {
    showStatus(t("pdf.loadError"));
  }
}

/* ---------- "besoin d'aide" chat sheet (Batch 5, multi-turn) ---------- */
let helpHistory = [];
const transcriptEl = el("help-transcript");

function scrollTranscriptToEnd() {
  transcriptEl.scrollTop = transcriptEl.scrollHeight;
}

function appendUserBubble(content) {
  const bubble = document.createElement("div");
  bubble.className = "chat-bubble chat-bubble-user";
  bubble.textContent = content;
  transcriptEl.appendChild(bubble);
  scrollTranscriptToEnd();
}

function appendAssistantBubble(content) {
  const bubble = document.createElement("div");
  bubble.className = "chat-bubble chat-bubble-assistant md";
  bubble.innerHTML = renderMarkdown(content || "");
  transcriptEl.appendChild(bubble);
  renderMath(bubble);
  scrollTranscriptToEnd();
  return bubble;
}

function appendThinkingBubble() {
  const bubble = document.createElement("div");
  bubble.className = "chat-bubble chat-bubble-assistant chat-bubble-thinking";
  bubble.innerHTML =
    '<div class="skeleton"><div class="sk-line"></div><div class="sk-line"></div><div class="sk-line short"></div></div>';
  transcriptEl.appendChild(bubble);
  scrollTranscriptToEnd();
  return bubble;
}

function resetHelpChat() {
  helpHistory = [];
  transcriptEl.innerHTML = "";
  el("help-sheet-foot").textContent = "";
}

function openHelpSheet() {
  resetHelpChat();
  el("help-sheet-overlay").classList.add("open");
  el("help-sheet").classList.add("open");
}
function closeHelpSheet() {
  el("help-sheet-overlay").classList.remove("open");
  el("help-sheet").classList.remove("open");
}
el("pdf-help-fab").addEventListener("click", () => {
  openHelpSheet();
  el("pdf-help-question").focus();
});
el("help-sheet-close").addEventListener("click", closeHelpSheet);
el("help-sheet-overlay").addEventListener("click", closeHelpSheet);

el("pdf-help-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const questionEl = el("pdf-help-question");
  const question = questionEl.value.trim();
  if (!question || !relPath) return;

  const submitBtn = el("pdf-help-submit");
  submitBtn.disabled = true;
  questionEl.value = "";

  helpHistory.push({ role: "user", content: question });
  appendUserBubble(question);
  const thinkingBubble = appendThinkingBubble();
  el("help-sheet-foot").textContent = "";
  const t0 = performance.now();
  try {
    const res = await fetch("/api/courses/help", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ rel_path: relPath, messages: helpHistory, lang: getLang() }),
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const data = await res.json();
    const ms = Math.round(performance.now() - t0);

    thinkingBubble.remove();
    helpHistory.push({ role: "assistant", content: data.answer || "" });
    appendAssistantBubble(data.answer || "");
    el("help-sheet-foot").textContent =
      `${data.model || t("pdf.modelFallback")} · ${data.cached ? t("pdf.cached") : t("pdf.freshGen")} · ${ms} ms`;
  } catch {
    thinkingBubble.remove();
    helpHistory.pop();
    const errorBubble = document.createElement("div");
    errorBubble.className = "chat-bubble chat-bubble-assistant chat-bubble-error";
    errorBubble.textContent = t("pdf.helpError");
    transcriptEl.appendChild(errorBubble);
    scrollTranscriptToEnd();
    el("help-sheet-foot").textContent = t("common.error");
  } finally {
    submitBtn.disabled = false;
    questionEl.focus();
  }
});

load();
