# flashcard-companion

App compagnon Android qui remplace AnkiDroid comme client de review pour les decks
générés par [`flashcard-pipeline`](../flashcard-pipeline), avec un bouton d'explication
IA (Infercom, DeepSeek-V3.1) groundée dans le PDF source de la carte.

- `backend/` — serveur FastAPI, tourne **sur le tel via Termux**, lit les `.apkg`
  synced par Syncthing (read-only), possède son propre état SRS (SM-2), sert l'UI web.
- `android/` — coquille Kotlin WebView minimale, pointe sur `http://127.0.0.1:8420`.

Voir le plan complet : `~/.claude/plans/sorted-noodling-pelican.md` (ou son
équivalent archivé) pour le contexte de conception et les risques ouverts.

## Démarrage rapide (dev PC)

```bash
cd backend
python -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env   # renseigner INFERCOM_API_KEY
uvicorn main:app --reload --port 8420
```

Ouvrir `http://localhost:8420/` dans un navigateur pour itérer sur l'UI sans tel.

## Déploiement sur le tel (Termux)

```bash
pkg install python
pip install -r backend/requirements.txt
bash backend/termux/start.sh
```

Voir `backend/termux/boot/` pour l'auto-start au reboot (Termux:Boot, optionnel).
