/* Statistiques: charge /api/stats/overview + /api/stats/cards, dessine
   la heatmap calendaire (grille de div maison, pas de lib de graphes),
   et rend le tableau par carte triable. Export = liens directs vers
   /api/stats/export?format=csv|json (Content-Disposition déjà géré côté API). */

const el = (id) => document.getElementById(id);

el("back-btn").innerHTML = icon("chevronLeft");

/* ---------- formatting ---------- */

function msToHuman(ms) {
  if (ms === null || ms === undefined || Number.isNaN(ms)) return "—";
  const totalSec = Math.round(ms / 1000);
  const h = Math.floor(totalSec / 3600);
  const m = Math.floor((totalSec % 3600) / 60);
  const s = totalSec % 60;
  if (h > 0) return `${h}h ${m}m`;
  if (m > 0) return `${m}m ${s}s`;
  return `${s}s`;
}

function pct(x) {
  if (x === null || x === undefined || Number.isNaN(x)) return "—";
  return `${(x * 100).toFixed(1)}%`;
}

function formatDateTime(iso) {
  if (!iso) return "—";
  const d = new Date(iso.includes("T") ? iso : iso.replace(" ", "T"));
  if (Number.isNaN(d.getTime())) return iso;
  return d.toLocaleString("fr-FR", { year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit" });
}

/* ---------- overview ---------- */

async function loadOverview() {
  try {
    const res = await fetch("/api/stats/overview", { cache: "no-store" });
    const data = await res.json();
    el("stat-total").textContent = data.total_reviews;
    el("stat-time").textContent = msToHuman(data.total_time_spent_ms);
    el("stat-success").textContent = pct(data.success_rate);
    renderHeatmap(data.per_day || []);
  } catch {
    el("stat-total").textContent = "?";
    el("stat-time").textContent = "?";
    el("stat-success").textContent = "?";
  }
}

/* ---------- heatmap ---------- */

function renderHeatmap(perDay) {
  const wrap = el("heatmap");
  const empty = el("heatmap-empty");
  const extremes = el("heatmap-extremes");
  wrap.innerHTML = "";
  extremes.innerHTML = "";

  if (!perDay.length) {
    empty.classList.remove("hidden");
    return;
  }
  empty.classList.add("hidden");

  const counts = new Map(perDay.map((d) => [d.date, d.count]));
  const dates = perDay.map((d) => new Date(`${d.date}T00:00:00`));
  const start = new Date(Math.min(...dates));
  const end = new Date(Math.max(...dates));

  // align start to Monday
  const startDow = (start.getDay() + 6) % 7;
  start.setDate(start.getDate() - startDow);

  const days = [];
  const cur = new Date(start);
  while (cur <= end) {
    const iso = cur.toISOString().slice(0, 10);
    days.push({ date: iso, count: counts.get(iso) || 0 });
    cur.setDate(cur.getDate() + 1);
  }
  while (days.length % 7 !== 0) {
    const last = new Date(`${days[days.length - 1].date}T00:00:00`);
    last.setDate(last.getDate() + 1);
    days.push({ date: last.toISOString().slice(0, 10), count: 0 });
  }

  const maxCount = Math.max(...perDay.map((d) => d.count));
  const weeks = days.length / 7;
  wrap.style.gridTemplateColumns = `repeat(${weeks}, 12px)`;

  for (const day of days) {
    const cell = document.createElement("div");
    const level = day.count === 0 ? 0 : Math.min(4, Math.ceil((day.count / maxCount) * 4));
    cell.className = `heat-cell level-${level}`;
    cell.title = t("stats.dayCellTitle", {date: day.date, n: day.count, s: day.count !== 1 ? "s" : ""});
    wrap.appendChild(cell);
  }

  let highest = perDay[0];
  let lowest = perDay[0];
  for (const d of perDay) {
    if (d.count > highest.count) highest = d;
    if (d.count < lowest.count) lowest = d;
  }
  extremes.innerHTML =
    `<span class="extreme high">${t("stats.dayHigh")}<b>${highest.date}</b> (${highest.count})</span>` +
    `<span class="extreme low">${t("stats.dayLow")}<b>${lowest.date}</b> (${lowest.count})</span>`;
}

/* ---------- per-card table ---------- */

let cardsData = [];
let sortKey = "guid";
let sortDir = 1; // 1 asc, -1 desc

const COLUMN_RENDER = {
  guid: (v) => v,
  review_count: (v) => v,
  success_rate: (v) => pct(v),
  avg_time_spent_ms: (v) => msToHuman(v),
  total_time_spent_ms: (v) => msToHuman(v),
  last_quality: (v) => (v === null || v === undefined ? "—" : v),
  last_reviewed_at: (v) => formatDateTime(v),
};

function renderCardsTable() {
  const tbody = el("cards-tbody");
  const empty = el("cards-empty");
  tbody.innerHTML = "";

  if (!cardsData.length) {
    empty.classList.remove("hidden");
    return;
  }
  empty.classList.add("hidden");

  const rows = [...cardsData].sort((a, b) => {
    const av = a[sortKey];
    const bv = b[sortKey];
    if (av === null || av === undefined) return 1;
    if (bv === null || bv === undefined) return -1;
    if (av < bv) return -1 * sortDir;
    if (av > bv) return 1 * sortDir;
    return 0;
  });

  for (const row of rows) {
    const tr = document.createElement("tr");
    tr.innerHTML = Object.keys(COLUMN_RENDER)
      .map((key) => `<td>${COLUMN_RENDER[key](row[key])}</td>`)
      .join("");
    tbody.appendChild(tr);
  }

  document.querySelectorAll("#cards-table th").forEach((th) => {
    th.classList.toggle("sorted", th.dataset.key === sortKey);
    th.classList.toggle("desc", th.dataset.key === sortKey && sortDir === -1);
  });
}

async function loadCards() {
  try {
    const res = await fetch("/api/stats/cards", { cache: "no-store" });
    cardsData = await res.json();
    renderCardsTable();
  } catch {
    cardsData = [];
    renderCardsTable();
  }
}

document.querySelectorAll("#cards-table th").forEach((th) => {
  th.addEventListener("click", () => {
    const key = th.dataset.key;
    if (key === sortKey) {
      sortDir *= -1;
    } else {
      sortKey = key;
      sortDir = 1;
    }
    renderCardsTable();
  });
});

loadOverview();
loadCards();
