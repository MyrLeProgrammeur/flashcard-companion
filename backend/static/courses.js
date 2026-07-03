/* Cours: charge /api/courses/tree (arbre récursif du vrai dossier pdf_dir,
   Cours/<Matière>/<sous-dossier?>/fichier.pdf) et l'affiche en drill-down,
   même pattern que l'arbre de decks sur l'accueil (index.html + /api/tree).
   Clic sur un dossier -> descend d'un niveau ; clic sur un PDF ->
   pdf-viewer.html?path=... (page ajoutée en Batch 4). */

const el = (id) => document.getElementById(id);

el("back-btn").innerHTML = icon("chevronLeft");

let TREE = [];
let stack = []; // current path, array of folder names

function glyphFor(name) {
  const words = name.trim().split(/\s+/);
  if (words.length >= 2) return (words[0][0] + words[1][0]).toUpperCase();
  return (name.trim()[0] || "?").toUpperCase();
}

function nodesAt(st) {
  let nodes = TREE;
  for (const seg of st) {
    const n = nodes.find((x) => x.name === seg && !x.is_file);
    if (!n) return [];
    nodes = n.children;
  }
  return nodes;
}

function renderLevel() {
  const nodes = nodesAt(stack);

  // breadcrumb
  const crumbs = el("crumbs");
  if (stack.length === 0) {
    crumbs.classList.add("hidden");
  } else {
    crumbs.classList.remove("hidden");
    crumbs.innerHTML = "";
    const mk = (label, depth) => {
      const s = document.createElement("span");
      s.className = "crumb";
      s.textContent = label;
      s.onclick = () => {
        stack = stack.slice(0, depth);
        history.pushState({ stack }, "", location.href);
        renderLevel();
      };
      return s;
    };
    crumbs.appendChild(mk(t("home.crumbRoot"), 0));
    stack.forEach((seg, i) => {
      const sep = document.createElement("span");
      sep.className = "crumb-sep";
      sep.textContent = "›";
      crumbs.appendChild(sep);
      crumbs.appendChild(mk(seg, i + 1));
    });
  }

  // title + subtitle
  el("courses-title").textContent = stack.length ? stack[stack.length - 1] : t("courses.title");
  el("courses-sub").textContent = stack.length ? "" : t("courses.sub");

  const list = el("courses-list");
  list.innerHTML = "";
  if (!nodes.length) {
    list.innerHTML = `<p class="empty-note">${t("courses.noPdf")}</p>`;
    return;
  }

  for (const node of nodes) {
    const row = document.createElement(node.is_file ? "a" : "button");
    row.className = "deck-row";
    if (node.is_file) {
      row.href = `/pdf-viewer.html?path=${encodeURIComponent(node.rel_path)}`;
    } else {
      row.type = "button";
      row.onclick = () => {
        stack = stack.concat(node.name);
        history.pushState({ stack }, "", location.href);
        renderLevel();
      };
    }
    row.innerHTML =
      `<span class="deck-glyph">${glyphFor(node.name)}</span>` +
      `<span class="deck-main"><span class="deck-name">${node.name}</span></span>` +
      `<span class="deck-chevron">${icon("chevronRight", "icon")}</span>`;
    list.appendChild(row);
  }
}

async function loadCourses() {
  try {
    TREE = await (await fetch("/api/courses/tree", { cache: "no-store" })).json();
  } catch {
    el("courses-list").innerHTML = `<p class="empty-note">${t("courses.loadError")}</p>`;
    return;
  }
  stack = [];
  history.replaceState({ stack: [] }, "", location.href);
  renderLevel();
}

window.addEventListener("popstate", (event) => {
  stack = (event.state && event.state.stack) || [];
  renderLevel();
});

loadCourses();
