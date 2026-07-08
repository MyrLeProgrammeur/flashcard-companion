/* Review session: flip, rating (SM-2), and the "Explique en profondeur" sheet.
   Scope is a folder path (?path=a::b::c, empty = review everything).
   Binds to /api/due, /api/cards/{guid}/review, /api/cards/{guid}/explain. */

const params = new URLSearchParams(location.search);
const path = params.get("path") || ""; // empty => all decks

const el = (id) => document.getElementById(id);

let queue = [];
let idx = 0;
let flipped = false;
let cardShownAt = 0;
let coursesData = {}; // subject -> [{filename, rel_path}, ...], fetched once (Batch 6)

/* ---------- static icons ---------- */
el("back-btn").innerHTML = icon("chevronLeft");
el("explain-btn").innerHTML = icon("spark") + " " + t("review.explainBtn");
el("sheet-close").innerHTML = icon("close");

/* ---------- load queue ---------- */
async function loadQueue() {
  el("deck-name").textContent = path ? path.split("::").pop() : t("review.allDecks");
  const [dueRes, coursesRes] = await Promise.all([
    fetch(`/api/due?path=${encodeURIComponent(path)}&limit=50`, { cache: "no-store" }),
    fetch("/api/courses", { cache: "no-store" }),
  ]);
  queue = await dueRes.json();
  try {
    coursesData = await coursesRes.json();
  } catch {
    coursesData = {};
  }
  el("total").textContent = queue.length;
  render();
}

/* ---------- render current card ---------- */
function render() {
  if (idx >= queue.length) return endSession();
  flipped = false;
  el("flip").classList.remove("flipped");
  const c = queue[idx];
  cardShownAt = Date.now();

  el("front").textContent = c.front;
  el("back").innerHTML = c.back; // pipeline fields may carry simple markup
  const note = el("note");
  if (c.note && c.note.trim()) {
    note.innerHTML = c.note;
    note.classList.remove("hidden");
  } else {
    note.classList.add("hidden");
  }
  renderMath(el("front"));
  renderMath(el("back"));
  renderMath(note);

  const p = c.previews || {};
  document.querySelectorAll("[data-iv]").forEach((n) => {
    n.textContent = p[n.dataset.iv] || "";
  });

  el("pos").textContent = idx + 1;
  el("bar").style.width = queue.length ? `${(idx / queue.length) * 100}%` : "0%";

  const sourceLink = el("source-link");
  const matches = coursesData[c.subject];
  if (matches && matches.length) {
    sourceLink.href = `/pdf-viewer.html?path=${encodeURIComponent(matches[0].rel_path)}`;
    sourceLink.innerHTML = icon("doc") + " " + t("review.viewSource");
    sourceLink.classList.remove("hidden");
  } else {
    sourceLink.classList.add("hidden");
  }

  updateFoot();
}

function updateFoot() {
  el("recto-band").classList.toggle("hidden", flipped);
  el("explain-btn").classList.toggle("hidden", !flipped);
  el("rating-row").classList.toggle("hidden", !flipped);
}

function endSession() {
  el("bar").style.width = "100%";
  document.querySelector(".card-flip").classList.add("hidden");
  document.querySelector(".review-foot").classList.add("hidden");
  el("session-end").classList.remove("hidden");
  el("end-sub").textContent = queue.length
    ? t("review.cardsReviewed", {n: queue.length, s: queue.length > 1 ? "s" : ""})
    : t("review.nothingToReview");
}

/* ---------- flip ---------- */
el("flip").addEventListener("click", () => {
  flipped = !flipped;
  el("flip").classList.toggle("flipped", flipped);
  updateFoot();
});

/* ---------- rating ---------- */
async function rate(quality) {
  const c = queue[idx];
  if (!c) return;
  const time_spent_ms = cardShownAt ? Date.now() - cardShownAt : null;
  try {
    await fetch(`/api/cards/${c.guid}/review`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ quality, time_spent_ms }),
    });
  } catch { /* offline handled by poll */ }
  idx += 1;
  render();
}
document.querySelectorAll(".rate").forEach((b) =>
  b.addEventListener("click", () => rate(parseInt(b.dataset.q, 10)))
);

/* ---------- explain sheet ---------- */
function openSheet() { el("sheet-overlay").classList.add("open"); el("sheet").classList.add("open"); }
function closeSheet() { el("sheet-overlay").classList.remove("open"); el("sheet").classList.remove("open"); }
el("sheet-close").addEventListener("click", closeSheet);
el("sheet-overlay").addEventListener("click", closeSheet);

function showSkeleton() {
  el("degr-band").classList.add("hidden");
  el("src-chip").classList.add("hidden");
  el("sheet-foot").textContent = "";
  el("explain-md").innerHTML =
    '<div class="skeleton"><div class="sk-line"></div><div class="sk-line"></div><div class="sk-line short"></div>' +
    '<div class="sk-line" style="margin-top:22px"></div><div class="sk-line"></div><div class="sk-line short"></div></div>';
}

function baseName(path) { return (path || "").split("/").pop(); }

el("explain-btn").addEventListener("click", async () => {
  const c = queue[idx];
  if (!c) return;
  openSheet();
  showSkeleton();
  const t0 = performance.now();
  try {
    const res = await fetch(`/api/cards/${c.guid}/explain?lang=${getLang()}`, { method: "POST" });
    const data = await res.json();
    const ms = Math.round(performance.now() - t0);
    const grounded = Array.isArray(data.source_files) && data.source_files.length > 0;

    const chip = el("src-chip");
    chip.classList.remove("hidden", "pdf", "card-only");
    if (grounded) {
      chip.classList.add("pdf");
      chip.innerHTML = icon("doc") + t("review.groundedIn", {files: data.source_files.map(baseName).join(", ")});
      el("degr-band").classList.add("hidden");
    } else {
      chip.classList.add("card-only");
      chip.innerHTML = icon("alert") + t("review.noSourceChip");
      el("degr-band").innerHTML = icon("info") +
        "<span>" + t("review.noSourceBand") + "</span>";
      el("degr-band").classList.remove("hidden");
    }

    el("explain-md").innerHTML = renderMarkdown(data.explanation || "");
    renderMath(el("explain-md"));
    el("sheet-foot").textContent =
      `${data.model || t("pdf.modelFallback")} · ${data.cached ? t("pdf.cached") : t("pdf.freshGen")} · ${ms} ms`;
  } catch {
    el("explain-md").innerHTML = `<p>${t("review.explainError")}</p>`;
    el("sheet-foot").textContent = t("common.error");
  }
});

/* ---------- health: pill tracks the AI link, redirect only if backend drops ---------- */
startHealthPoll(({ backend, ai }) => {
  const pill = el("health");
  pill.classList.toggle("offline", !ai);
  pill.innerHTML = '<span class="dot"></span>';
  if (!backend) location.href = "/";
});

loadQueue();
