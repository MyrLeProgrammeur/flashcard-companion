const params = new URLSearchParams(window.location.search);
const subject = params.get("subject");
const theme = params.get("theme");

document.getElementById("deck-title").textContent = `${subject} — ${theme || "(général)"}`;

let queue = [];
let current = null;

async function loadQueue() {
  const res = await fetch(
    `/api/decks/${encodeURIComponent(subject)}/${encodeURIComponent(theme)}/due?limit=50`
  );
  queue = await res.json();
  nextCard();
}

function nextCard() {
  resetCardView();
  if (queue.length === 0) {
    document.getElementById("card-box").classList.add("hidden");
    document.querySelector(".action-buttons").classList.add("hidden");
    document.getElementById("empty-state").classList.remove("hidden");
    return;
  }
  current = queue.shift();
  document.getElementById("front").textContent = current.front;
  document.getElementById("back").textContent = current.back;
  document.getElementById("note").textContent = current.note || "";
}

function resetCardView() {
  document.getElementById("back-section").classList.add("hidden");
  document.getElementById("explanation-section").classList.add("hidden");
  document.getElementById("rating-buttons").classList.add("hidden");
  document.getElementById("explanation-text").textContent = "";
  document.getElementById("explanation-sources").textContent = "";
}

document.getElementById("btn-show").addEventListener("click", () => {
  document.getElementById("back-section").classList.remove("hidden");
  document.getElementById("rating-buttons").classList.remove("hidden");
});

document.getElementById("btn-explain").addEventListener("click", async () => {
  if (!current) return;
  const btn = document.getElementById("btn-explain");
  btn.disabled = true;
  btn.textContent = "Génération...";
  try {
    const res = await fetch(`/api/cards/${current.guid}/explain`, { method: "POST" });
    const data = await res.json();
    document.getElementById("explanation-text").textContent = data.explanation;
    document.getElementById("explanation-sources").textContent =
      data.source_files.length > 0
        ? `Sources: ${data.source_files.map((f) => f.split("/").pop()).join(", ")}`
        : "Aucune source PDF trouvée — explication basée sur la carte seule.";
    document.getElementById("explanation-section").classList.remove("hidden");
  } finally {
    btn.disabled = false;
    btn.textContent = "Détaille / explique en profondeur";
  }
});

async function rate(quality) {
  if (!current) return;
  await fetch(`/api/cards/${current.guid}/review`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ quality }),
  });
  nextCard();
}

document.getElementById("btn-again").addEventListener("click", () => rate(1));
document.getElementById("btn-hard").addEventListener("click", () => rate(3));
document.getElementById("btn-good").addEventListener("click", () => rate(4));
document.getElementById("btn-easy").addEventListener("click", () => rate(5));

loadQueue();
