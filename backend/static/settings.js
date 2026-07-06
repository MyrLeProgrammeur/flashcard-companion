/* SM-2 settings + notifications: loads/saves the knobs via GET/PUT
   /api/settings. Fields = again_days, hard_days, good_days, easy_days,
   easy_bonus, notify_hour (daily Termux reminder hour, Batch 7). */

const el = (id) => document.getElementById(id);

const FIELDS = ["again_days", "hard_days", "good_days", "easy_days", "easy_bonus", "notify_hour"];

el("back-btn").innerHTML = icon("chevronLeft");

async function loadSettings() {
  try {
    const res = await fetch("/api/settings", { cache: "no-store" });
    const data = await res.json();
    for (const f of FIELDS) {
      if (data[f] !== undefined && data[f] !== null) el(f).value = data[f];
    }
  } catch {
    el("status").textContent = t("settings.loadError");
    el("status").classList.add("error");
  }
}

el("settings-form").addEventListener("submit", async (e) => {
  e.preventDefault();
  const status = el("status");
  status.classList.remove("error", "ok");
  status.textContent = t("settings.saving");

  const body = {};
  for (const f of FIELDS) body[f] = parseFloat(el(f).value);

  try {
    const res = await fetch("/api/settings", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(body),
    });
    if (!res.ok) throw new Error("bad status");
    status.textContent = t("settings.saved");
    status.classList.add("ok");
  } catch {
    status.textContent = t("settings.saveFailed");
    status.classList.add("error");
  }
});

loadSettings();
