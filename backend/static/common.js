/* Shared front-end helpers: theme, backend health, icons, markdown.
   Loaded by index.html and review.html. Loopback, single-user, no build step. */

/* ---------- inline SVG icons (simple, stroke-based) ---------- */
const ICONS = {
  spark: '<path d="M12 2c.4 4.5 1.5 5.6 6 6-4.5.4-5.6 1.5-6 6-.4-4.5-1.5-5.6-6-6 4.5-.4 5.6-1.5 6-6z" fill="currentColor" stroke="none"/>',
  doc: '<path d="M6 3h8l4 4v14H6z" fill="none" stroke="currentColor" stroke-linejoin="round"/><path d="M14 3v4h4" fill="none" stroke="currentColor" stroke-linejoin="round"/>',
  alert: '<path d="M12 4l9 16H3z" fill="none" stroke="currentColor" stroke-linejoin="round"/><path d="M12 10v4M12 17v.5" stroke="currentColor" stroke-linecap="round"/>',
  info: '<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor"/><path d="M12 11v5M12 8v.5" stroke="currentColor" stroke-linecap="round"/>',
  refresh: '<path d="M20 12a8 8 0 1 1-2.3-5.6M20 4v4h-4" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"/>',
  serverOff: '<rect x="4" y="5" width="16" height="6" rx="2" fill="none" stroke="currentColor"/><rect x="4" y="13" width="16" height="6" rx="2" fill="none" stroke="currentColor"/><path d="M4 4l16 16" stroke="currentColor" stroke-linecap="round"/>',
  chevronLeft: '<path d="M15 5l-7 7 7 7" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"/>',
  chevronRight: '<path d="M9 5l7 7-7 7" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"/>',
  close: '<path d="M6 6l12 12M18 6L6 18" stroke="currentColor" stroke-linecap="round"/>',
  sun: '<circle cx="12" cy="12" r="4" fill="none" stroke="currentColor"/><path d="M12 3v2M12 19v2M3 12h2M19 12h2M5.6 5.6l1.4 1.4M17 17l1.4 1.4M18.4 5.6L17 7M7 17l-1.4 1.4" stroke="currentColor" stroke-linecap="round"/>',
  moon: '<path d="M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z" fill="none" stroke="currentColor" stroke-linejoin="round"/>',
};

function icon(name, cls = "icon") {
  return `<svg class="${cls}" viewBox="0 0 24 24" width="20" height="20" fill="none">${ICONS[name] || ""}</svg>`;
}

/* ---------- theme ---------- */
const THEME_KEY = "fc-theme";

function currentTheme() {
  return localStorage.getItem(THEME_KEY) || "light";
}
function applyTheme(t) {
  document.documentElement.setAttribute("data-theme", t);
  localStorage.setItem(THEME_KEY, t);
  document.querySelectorAll("[data-theme-icon]").forEach((el) => {
    el.innerHTML = icon(t === "dark" ? "sun" : "moon");
  });
}
function toggleTheme() {
  applyTheme(currentTheme() === "dark" ? "light" : "dark");
}
// apply immediately to avoid a flash
applyTheme(currentTheme());

/* ---------- backend health ---------- */
async function pingHealth() {
  try {
    const res = await fetch("/api/health", { cache: "no-store" });
    return res.ok;
  } catch {
    return false;
  }
}

function renderHealthPill(el, online) {
  el.classList.toggle("offline", !online);
  el.innerHTML = `<span class="dot"></span>${online ? "En ligne" : "Hors ligne"}`;
}

/* Polls /api/health; calls cb(online) on every change (and once at start). */
function startHealthPoll(cb, intervalMs = 10000) {
  let last = null;
  const tick = async () => {
    const online = await pingHealth();
    if (online !== last) {
      last = online;
      cb(online);
    }
  };
  tick();
  return setInterval(tick, intervalMs);
}

/* ---------- minimal, safe markdown ---------- */
function escapeHtml(s) {
  return s.replace(/[&<>]/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;" }[c]));
}

function renderMarkdown(src) {
  if (!src) return "";
  const lines = src.replace(/\r\n/g, "\n").split("\n");
  let html = "";
  let inList = false, inCode = false, para = [];

  const flushPara = () => {
    if (para.length) {
      html += `<p>${inlineMd(para.join(" "))}</p>`;
      para = [];
    }
  };
  const closeList = () => { if (inList) { html += "</ul>"; inList = false; } };

  for (const raw of lines) {
    const line = raw.trimEnd();
    if (line.startsWith("```")) {
      flushPara();
      if (inCode) { html += "</code></pre>"; inCode = false; }
      else { closeList(); html += "<pre><code>"; inCode = true; }
      continue;
    }
    if (inCode) { html += escapeHtml(raw) + "\n"; continue; }

    if (!line.trim()) { flushPara(); closeList(); continue; }

    const h = line.match(/^(#{1,6})\s+(.*)$/);
    if (h) { flushPara(); closeList(); html += `<h3>${inlineMd(h[2])}</h3>`; continue; }

    const li = line.match(/^\s*[-*]\s+(.*)$/);
    if (li) {
      flushPara();
      if (!inList) { html += "<ul>"; inList = true; }
      html += `<li>${inlineMd(li[1])}</li>`;
      continue;
    }
    closeList();
    para.push(line.trim());
  }
  flushPara();
  closeList();
  if (inCode) html += "</code></pre>";
  return html;
}

function inlineMd(s) {
  let t = escapeHtml(s);
  t = t.replace(/`([^`]+)`/g, "<code>$1</code>");
  t = t.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  t = t.replace(/(^|[^*])\*([^*]+)\*/g, "$1<em>$2</em>");
  return t; // $...$ / \(...\) left intact for KaTeX auto-render
}

/* ---------- KaTeX (self-hosted, loaded only where needed) ---------- */
function renderMath(elOrId) {
  const el = typeof elOrId === "string" ? document.getElementById(elOrId) : elOrId;
  if (!el || !window.renderMathInElement) return;
  try {
    window.renderMathInElement(el, {
      delimiters: [
        { left: "$$", right: "$$", display: true },
        { left: "\\[", right: "\\]", display: true },
        { left: "\\(", right: "\\)", display: false },
        { left: "$", right: "$", display: false },
      ],
      throwOnError: false,
      ignoredTags: ["script", "noscript", "style", "textarea", "pre", "code", "option"],
    });
  } catch (e) { /* leave raw on failure */ }
}
