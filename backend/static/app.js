/* Review session: flip, rating (SM-2), and the "Explique en profondeur" sheet.
   Scope is a folder path (?path=a::b::c, empty = review everything).
   Binds to /api/due, /api/cards/{guid}/review, /api/cards/{guid}/explain. */

const params = new URLSearchParams(location.search);
const path = params.get("path") || ""; // empty => all decks

const el = (id) => document.getElementById(id);

let queue = [];
let idx = 0;
let flipped = false;

/* ---------- static icons ---------- */
el("back-btn").innerHTML = icon("chevronLeft");
el("explain-btn").innerHTML = icon("spark") + " Explique en profondeur";
el("sheet-close").innerHTML = icon("close");

/* ---------- load queue ---------- */
async function loadQueue() {
  el("deck-name").textContent = path ? path.split("::").pop() : "Tous les decks";
  const res = await fetch(`/api/due?path=${encodeURIComponent(path)}&limit=50`, { cache: "no-store" });
  queue = await res.json();
  el("total").textContent = queue.length;
  render();
}

/* ---------- render current card ---------- */
function render() {
  if (idx >= queue.length) return endSession();
  flipped = false;
  el("flip").classList.remove("flipped");
  const c = queue[idx];

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
    ? `${queue.length} carte${queue.length > 1 ? "s" : ""} revue${queue.length > 1 ? "s" : ""}.`
    : "Rien à réviser ici pour le moment.";
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
  try {
    await fetch(`/api/cards/${c.guid}/review`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ quality }),
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
    const res = await fetch(`/api/cards/${c.guid}/explain`, { method: "POST" });
    const data = await res.json();
    const ms = Math.round(performance.now() - t0);
    const grounded = Array.isArray(data.source_files) && data.source_files.length > 0;

    const chip = el("src-chip");
    chip.classList.remove("hidden", "pdf", "card-only");
    if (grounded) {
      chip.classList.add("pdf");
      chip.innerHTML = icon("doc") + `Ancré dans ${data.source_files.map(baseName).join(", ")}`;
      el("degr-band").classList.add("hidden");
    } else {
      chip.classList.add("card-only");
      chip.innerHTML = icon("alert") + "Sans source PDF · carte seule";
      el("degr-band").innerHTML = icon("info") +
        "<span>Aucun PDF source n'a pu être associé à cette carte. L'explication est générée à partir du recto/verso seulement — recoupe avec ton cours.</span>";
      el("degr-band").classList.remove("hidden");
    }

    el("explain-md").innerHTML = renderMarkdown(data.explanation || "");
    renderMath(el("explain-md"));
    el("sheet-foot").textContent =
      `${data.model || "modèle"} · ${data.cached ? "en cache" : "généré à l'instant"} · ${ms} ms`;
  } catch {
    el("explain-md").innerHTML =
      '<p>Impossible de générer l\'explication. Vérifie que le backend et la clé Infercom sont OK.</p>';
    el("sheet-foot").textContent = "erreur";
  }
});

/* ---------- health: redirect to blocking screen if backend drops ---------- */
startHealthPoll((online) => {
  const pill = el("health");
  pill.classList.toggle("offline", !online);
  pill.innerHTML = '<span class="dot"></span>';
  if (!online) location.href = "/";
});

loadQueue();
