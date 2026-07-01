/* Réglages SM-2: charge/sauvegarde les 5 knobs via GET/PUT /api/settings.
   Champs = again_days, hard_days, good_days, easy_days, easy_bonus. */

const el = (id) => document.getElementById(id);

const FIELDS = ["again_days", "hard_days", "good_days", "easy_days", "easy_bonus"];

el("back-btn").innerHTML = icon("chevronLeft");

async function loadSettings() {
  try {
    const res = await fetch("/api/settings", { cache: "no-store" });
    const data = await res.json();
    for (const f of FIELDS) {
      if (data[f] !== undefined && data[f] !== null) el(f).value = data[f];
    }
  } catch {
    el("status").textContent = "Impossible de charger les réglages.";
    el("status").classList.add("error");
  }
}

el("settings-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const status = el("status");
  status.classList.remove("error", "ok");
  status.textContent = "Enregistrement…";

  const body = {};
  for (const f of FIELDS) body[f] = parseFloat(el(f).value);

  try {
    const res = await fetch("/api/settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!res.ok) throw new Error("bad status");
    status.textContent = "Enregistré.";
    status.classList.add("ok");
  } catch {
    status.textContent = "Échec de l'enregistrement.";
    status.classList.add("error");
  }
});

loadSettings();
