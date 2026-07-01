/* Examens (Batch 8): petit formulaire pour créer une entrée subject_grades
   (matière + date approx. des résultats), et affichage de la corrélation
   par matière — note / % réussite / temps investi côte à côte, sans score
   composite (GET/POST/PUT /api/exams). La note se saisit en tapant dans le
   tableau puis en quittant le champ (PUT immédiat, best-effort). */

const el = (id) => document.getElementById(id);

el("back-btn").innerHTML = icon("chevronLeft");

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

async function loadExams() {
  const tbody = el("exams-tbody");
  const empty = el("exams-empty");
  tbody.innerHTML = "";

  let rows = [];
  try {
    const res = await fetch("/api/exams", { cache: "no-store" });
    rows = await res.json();
  } catch {
    rows = [];
  }

  if (!rows.length) {
    empty.classList.remove("hidden");
    return;
  }
  empty.classList.add("hidden");

  for (const row of rows) {
    const tr = document.createElement("tr");
    tr.innerHTML = `
      <td>${row.deck_path}</td>
      <td>${row.expected_results_date}</td>
      <td><input class="settings-input" style="width:48px" type="number" step="0.01"
                  value="${row.grade ?? ""}" data-id="${row.id}"></td>
      <td>${pct(row.success_rate)}</td>
      <td>${msToHuman(row.total_time_spent_ms)}</td>
    `;
    tbody.appendChild(tr);
  }

  tbody.querySelectorAll("input[data-id]").forEach((input) => {
    input.addEventListener("change", async () => {
      const grade = parseFloat(input.value);
      if (Number.isNaN(grade)) return;
      try {
        await fetch(`/api/exams/${input.dataset.id}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ grade }),
        });
      } catch {
        // best-effort; the value stays in the input, user can retry
      }
    });
  });
}

el("exam-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const status = el("form-status");
  status.classList.remove("error", "ok");
  status.textContent = "Ajout…";

  const body = {
    deck_path: el("deck_path").value.trim(),
    expected_results_date: el("expected_results_date").value,
  };

  try {
    const res = await fetch("/api/exams", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!res.ok) throw new Error("bad status");
    status.textContent = "Ajouté.";
    status.classList.add("ok");
    el("exam-form").reset();
    loadExams();
  } catch {
    status.textContent = "Échec de l'ajout.";
    status.classList.add("error");
  }
});

loadExams();
