/* Cours: charge /api/courses (matière -> liste de PDF matchés par
   source_matcher, granularité matière uniquement — pas d'arbre plus profond),
   affiche une section par matière avec ses PDF en deck-row. Clic sur un PDF
   -> pdf-viewer.html?path=... (page ajoutée en Batch 4). */

const el = (id) => document.getElementById(id);

el("back-btn").innerHTML = icon("chevronLeft");

function glyphFor(name) {
  const words = name.trim().split(/\s+/);
  if (words.length >= 2) return (words[0][0] + words[1][0]).toUpperCase();
  return (name.trim()[0] || "?").toUpperCase();
}

async function loadCourses() {
  const list = el("courses-list");
  list.innerHTML = "";
  let data = {};
  try {
    const res = await fetch("/api/courses", { cache: "no-store" });
    data = await res.json();
  } catch {
    list.innerHTML = `<p class="empty-note">Impossible de charger les cours.</p>`;
    return;
  }

  const subjects = Object.keys(data);
  if (!subjects.length) {
    list.innerHTML = `<p class="empty-note">Aucune matière trouvée.</p>`;
    return;
  }

  for (const subject of subjects) {
    const pdfs = data[subject] || [];

    const heading = document.createElement("h2");
    heading.className = "section-title";
    heading.textContent = subject;
    list.appendChild(heading);

    if (!pdfs.length) {
      const empty = document.createElement("p");
      empty.className = "empty-note";
      empty.textContent = "Aucun PDF trouvé.";
      list.appendChild(empty);
      continue;
    }

    const deckList = document.createElement("div");
    deckList.className = "deck-list";
    for (const pdf of pdfs) {
      const row = document.createElement("a");
      row.className = "deck-row";
      row.href = `/pdf-viewer.html?path=${encodeURIComponent(pdf.rel_path)}`;
      row.innerHTML =
        `<span class="deck-glyph">${glyphFor(subject)}</span>` +
        `<span class="deck-main"><span class="deck-name">${pdf.filename}</span></span>` +
        `<span class="deck-chevron">${icon("chevronRight", "icon")}</span>`;
      deckList.appendChild(row);
    }
    list.appendChild(deckList);
  }
}

loadCourses();
