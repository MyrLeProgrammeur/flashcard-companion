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
  settings: '<circle cx="12" cy="12" r="3" fill="none" stroke="currentColor"/><path d="M19.4 13a7.6 7.6 0 0 0 0-2l2-1.5-2-3.5-2.4.6a7.7 7.7 0 0 0-1.7-1l-.4-2.4H9.1l-.4 2.4a7.7 7.7 0 0 0-1.7 1l-2.4-.6-2 3.5 2 1.5a7.6 7.6 0 0 0 0 2l-2 1.5 2 3.5 2.4-.6a7.7 7.7 0 0 0 1.7 1l.4 2.4h5.8l.4-2.4a7.7 7.7 0 0 0 1.7-1l2.4.6 2-3.5-2-1.5z" fill="none" stroke="currentColor" stroke-linejoin="round"/>',
  stats: '<path d="M4 20V10M12 20V4M20 20v-7" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"/>',
  exam: '<rect x="4" y="5" width="16" height="16" rx="2" fill="none" stroke="currentColor"/><path d="M4 9h16M8 3v4M16 3v4" stroke="currentColor" stroke-linecap="round"/><path d="M9 14l2 2 4-4" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"/>',
  menu: '<path d="M4 7h16M4 12h16M4 17h16" stroke="currentColor" stroke-linecap="round"/>',
  home: '<path d="M4 11l8-7 8 7M6 10v9h12v-9" fill="none" stroke="currentColor" stroke-linecap="round" stroke-linejoin="round"/>',
  globe: '<circle cx="12" cy="12" r="9" fill="none" stroke="currentColor"/><path d="M3 12h18M12 3c2.6 2.7 2.6 15.3 0 18M12 3c-2.6 2.7-2.6 15.3 0 18" fill="none" stroke="currentColor"/>',
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
  el.innerHTML = `<span class="dot"></span>${online ? t("health.online") : t("health.offline")}`;
}

/* ---------- hamburger menu (global nav + theme + language) ---------- */
const MENU_LINKS = [
  { href: "/", key: "nav.home", icon: "home" },
  { href: "/courses.html", key: "nav.courses", icon: "doc" },
  { href: "/exams.html", key: "nav.exams", icon: "exam" },
  { href: "/stats.html", key: "nav.stats", icon: "stats" },
  { href: "/settings.html", key: "nav.settings", icon: "settings" },
];

function buildMenu() {
  if (document.getElementById("app-menu")) return;
  const path = location.pathname;
  const isActive = (href) =>
    href === "/" ? (path === "/" || path.endsWith("/index.html")) : path.endsWith(href);

  const links = MENU_LINKS.map((l) =>
    `<a class="menu-item${isActive(l.href) ? " active" : ""}" href="${l.href}">` +
    `${icon(l.icon)}<span>${t(l.key)}</span></a>`
  ).join("");

  const langOpts = SUPPORTED_LANGS.map((l) =>
    `<button class="menu-lang-opt${getLang() === l ? " active" : ""}" data-lang="${l}">${l.toUpperCase()}</button>`
  ).join("");

  const backdrop = document.createElement("div");
  backdrop.className = "menu-backdrop";
  backdrop.id = "menu-backdrop";

  const sheet = document.createElement("nav");
  sheet.className = "menu-sheet";
  sheet.id = "app-menu";
  sheet.innerHTML =
    `<div class="menu-head"><span class="mono">${t("nav.menu")}</span>` +
    `<button class="icon-btn" id="menu-close" aria-label="${t("common.close")}">${icon("close")}</button></div>` +
    `<div class="menu-section">${links}</div>` +
    `<div class="menu-divider"></div>` +
    `<button class="menu-item" id="menu-theme"><span data-theme-icon></span><span>${t("nav.theme")}</span></button>` +
    `<div class="menu-divider"></div>` +
    `<div class="menu-lang-row"><span class="menu-lang-label">${icon("globe")}<span>${t("nav.language")}</span></span>` +
    `<div class="menu-lang">${langOpts}</div></div>`;

  document.body.appendChild(backdrop);
  document.body.appendChild(sheet);
  applyTheme(currentTheme()); // paint the menu's theme icon

  const close = () => { sheet.classList.remove("open"); backdrop.classList.remove("open"); };
  backdrop.addEventListener("click", close);
  sheet.querySelector("#menu-close").addEventListener("click", close);
  sheet.querySelector("#menu-theme").addEventListener("click", toggleTheme);
  sheet.querySelectorAll(".menu-lang-opt").forEach((b) =>
    b.addEventListener("click", () => { setLang(b.getAttribute("data-lang")); location.reload(); })
  );
}

/* Injects the hamburger button into #menu-mount (pages that have one). */
function initMenu() {
  const mount = document.getElementById("menu-mount");
  if (!mount) return;
  buildMenu();
  mount.innerHTML =
    `<button class="menu-btn icon-btn" id="menu-open" aria-label="${t("nav.menu")}">${icon("menu")}</button>`;
  mount.querySelector("#menu-open").addEventListener("click", () => {
    document.getElementById("app-menu").classList.add("open");
    document.getElementById("menu-backdrop").classList.add("open");
  });
}

/* Every page loads common.js: translate static markup + wire the menu. */
window.addEventListener("DOMContentLoaded", () => {
  applyI18n();
  initMenu();
});

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
  // Shrink any display equation wider than its container so it fits the card /
  // sheet / chat width instead of overflowing. Run now and again after fonts
  // settle (KaTeX web-fonts change measured widths once loaded).
  fitDisplayMath(el);
  requestAnimationFrame(() => fitDisplayMath(el));
  if (document.fonts && document.fonts.ready) {
    document.fonts.ready.then(() => fitDisplayMath(el)).catch(() => {});
  }
}

/* Scale each display equation down to fit its container width. Inline math and
   text still wrap normally; only wide \[...\]/$$...$$ blocks are scaled.
   The container width is found by climbing ancestors to the first one that
   actually constrains the equation (content width < the equation's natural
   width) — this is robust to flex-centred boxes (e.g. .card-q) that shrink-wrap
   to the equation and so can't be measured directly. */
function fitDisplayMath(el) {
  if (!el) return;
  el.querySelectorAll(".katex-display").forEach((disp) => {
    const k = disp.querySelector(".katex");
    if (!k) return;
    k.style.transform = "";
    k.style.transformOrigin = "center top";
    disp.style.height = "";
    const natural = k.scrollWidth;
    if (!natural) return;
    let avail = 0;
    for (let p = disp; p && p !== document.body; p = p.parentElement) {
      const cs = getComputedStyle(p);
      const cw = p.clientWidth - parseFloat(cs.paddingLeft || 0) - parseFloat(cs.paddingRight || 0);
      if (cw > 0 && cw < natural) { avail = cw; break; }
    }
    if (!avail) return; // nothing constrains it / it already fits
    const scale = (avail - 2) / natural; // 2px inset so it never kisses the edge
    k.style.display = "inline-block";
    k.style.transform = `scale(${scale})`;
    // transform doesn't shrink the layout box, so collapse the leftover space.
    disp.style.height = k.getBoundingClientRect().height + "px";
  });
}
