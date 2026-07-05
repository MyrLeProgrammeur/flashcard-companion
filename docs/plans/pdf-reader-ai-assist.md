# Plan — Lecteur PDF intégré + aide IA groundée

> Executor: Sonnet + subagents. Apply the decisions, do not re-litigate them. Unresolved doubt → stop and ask the user.

## Goal & scope

Ajouter un lecteur PDF intégré à l'app (WebView), pour consulter les cours sources directement, avec un bouton flottant « besoin d'aide » (icône étincelle, style Gemini côté visuel, DeepSeek-V3.1 via Infercom côté moteur) permettant de poser une question groundée sur le PDF actuellement affiché.

**Contexte de la décision** : l'idée de départ était un chatbot générique branché DeepSeek. Elle a été abandonnée au profit de ce lecteur PDF + question groundée, jugé supérieur car le grounding devient exact (le PDF affiché) au lieu de l'heuristique fragile actuelle (`source_matcher.py`, matching par nom de fichier/dossier, seuil `difflib` à 0.6).

C'est un **besoin totalement neuf** — ne remplace aucune consultation existante des cours.

## Contexte actuel (repo `flashcard-companion`)

- **Pas de lecteur PDF dans l'app** aujourd'hui — les PDF sources vivent dans `pdf_dir` (configuré dans `backend/config.yaml`), utilisés uniquement en extraction de texte pour le grounding de `explain.py` (explication de carte à la demande), jamais affichés tels quels.
- **`backend/source_matcher.py`** : matching heuristique sujet de carte → fichier(s) PDF par nom (difflib, seuil 0.6), pas de traçabilité carte→document réelle (limite documentée en tête de fichier).
- **`backend/pdf_context.py`** : construit un contexte texte tronqué (`max_pdf_context_chars`) à partir des PDF matchés, pour l'inclure dans le prompt Infercom.
- **`backend/explain.py`** : pattern d'appel Infercom déjà en place (system prompt, `client.chat.completions.create`, modèle DeepSeek-V3.1) — référence directe pour le nouveau bouton d'aide.
- **Hiérarchie des decks** dérivée des noms Anki `::` dans les `.apkg` (voir `docs/plans/settings-notifications-stats.md` pour le contexte SRS complet, non concerné ici).
- **Header de `backend/static/index.html`** : icônes déjà présentes pour Réglages (gear), Stats (bar-chart), Examens — nouveau pattern d'icône à suivre pour l'entrée « Cours ».
- **Pas de CDN autorisé** (règle projet) — tout rendu (PDF, icônes) doit être self-hosted, comme KaTeX l'est déjà pour les formules.

## Settled decisions

- **Deux points d'accès**, pas trois (le niveau carte seule est jugé redondant) :
  1. **Icône header « Cours »** (pair de Réglages/Stats/Examens) → liste des PDF organisée selon la même hiérarchie de dossiers `::` que les decks → ouvre le PDF choisi dans le lecteur.
  2. **Raccourci contextuel sur l'écran de révision** : un lien « voir le cours source » sur la carte, utilisant le matching existant `source_matcher.py` (subject → PDF), ouvre directement le même lecteur sur le bon PDF.
- **Bouton flottant « besoin d'aide »** en bas à droite du lecteur PDF, icône étincelle (référence visuelle Gemini), pose une question groundée sur le PDF affiché — moteur DeepSeek-V3.1 via Infercom, même infra que `explain.py`.
- **Aucun CDN** — rendu PDF self-hosté.

## Open questions (à trancher avant/pendant l'implémentation — ne pas décider à la place de l'utilisateur)

- **Granularité du grounding** pour le bouton d'aide : tout le PDF, ou seulement la page/section actuellement visible à l'écran ? (Question posée en conversation, jamais répondue.)
- **Modèle d'interaction** : question unique sans historique (stateless, comme `explain_card`) ou conversation multi-tours au sein d'une session de lecture PDF ?
- **Cache des réponses** : les réponses du bouton d'aide sont-elles mises en cache (par PDF + question, comme `explain.py` cache par `card.guid`) ou toujours regénérées ?
- **Bibliothèque de rendu PDF côté front** : à choisir en respectant la contrainte no-CDN (candidate évidente : PDF.js self-hosté, comme KaTeX l'est déjà — à confirmer, pas encore tranché).
- **Un dossier deck peut avoir plusieurs PDF** (ex. version annotée + brute coexistent, cf. dédup par hash de fichier dans `flashcard-pipeline/state.py`) : l'écran « Cours » doit-il lister les deux séparément, ou n'afficher que la plus récente/annotée ?
- **Endpoint de service des PDF** : `pdf_dir` n'est aujourd'hui utilisé que pour extraction de texte côté backend — il faut un nouvel endpoint pour streamer/servir le PDF brut au frontend (nom de route, gestion des chemins, sécurité d'accès aux fichiers — à définir).

## Batches (independent)

### Batch 1 — Choix technique lecteur PDF (bloquant, à trancher avec l'utilisateur avant tout code)
- Files: aucun encore.
- Actions: proposer 1-2 options de rendu PDF self-hosté (poids, compatibilité WebView Android, absence de dépendance CDN), obtenir la décision de l'utilisateur.
- Constraints: pas de CDN.
- Done when: bibliothèque choisie et confirmée par l'utilisateur.

### Batch 2 — Endpoint de service PDF (backend)
- Files: nouveau `backend/api/routes_pdf.py`, `backend/main.py` (include_router).
- Actions: endpoint servant le contenu brut d'un PDF depuis `pdf_dir` (à sécuriser contre path traversal), plus un endpoint de listing des PDF disponibles organisés par dossier `::` (réutiliser la logique de hiérarchie de `routes_decks.py`, ne pas la dupliquer).
- Constraints: ne pas dupliquer la logique d'arborescence des decks.
- Verify: `curl` le endpoint de listing + téléchargement d'un PDF de test.
- Done when: un PDF est récupérable et listable via l'API.

### Batch 3 — Écran « Cours » (UI)
- Files: nouveau `backend/static/courses.html` + JS, icône dans `common.js` (ICONS), lien dans `index.html` header (suivre exactement le pattern Stats/Examens).
- Actions: liste des PDF par dossier `::`, clic → ouvre le lecteur (Batch 4).
- Constraints: suivre les tokens CSS existants, dark mode, pas de CDN.
- Verify: naviguer la liste, ouvrir un PDF de test.
- Done when: l'utilisateur peut parcourir ses cours et en ouvrir un.

### Batch 4 — Lecteur PDF intégré
- Files: nouveau `backend/static/pdf-viewer.html` + JS (dépend du choix du Batch 1).
- Actions: affichage du PDF (bibliothèque choisie), navigation pages, appelé depuis Batch 3 et depuis le raccourci carte (Batch 6).
- Constraints: self-hosté, respecte dark mode si la lib le permet.
- Verify: ouvrir un PDF réel multi-pages, naviguer.
- Done when: un PDF complet est lisible dans l'app.

### Batch 5 — Bouton « besoin d'aide » (backend + UI)
- Files: nouveau `backend/api/routes_pdf_help.py` (ou extension de `routes_pdf.py`), `backend/static/pdf-viewer.html`/JS (bouton flottant + modal de question/réponse).
- Actions: réutiliser le pattern `explain.py` (system prompt, appel Infercom DeepSeek-V3.1, grounding via `pdf_context.py`) pour une question posée sur le PDF affiché — granularité et cache **dépendent des Open Questions ci-dessus, à trancher avant d'écrire ce batch**.
- Constraints: ne pas dupliquer le pattern d'appel Infercom d'`explain.py` — factoriser si pertinent.
- Verify: poser une question sur un PDF de test, réponse groundée reçue.
- Done when: l'utilisateur peut poser une question sur le PDF ouvert et recevoir une réponse pertinente.

### Batch 6 — Raccourci depuis l'écran de révision
- Files: `backend/static/review.html` + `app.js`.
- Actions: lien « voir le cours source » sur la carte, utilisant `source_matcher.py` (déjà existant, ne pas dupliquer), ouvre le lecteur du Batch 4 sur le bon PDF.
- Constraints: ne pas créer de 3e point d'accès distinct — ce lien ouvre le même lecteur que Batch 3/4.
- Verify: depuis une carte en révision, cliquer le lien → bon PDF ouvert.
- Done when: accès en un clic depuis la révision vers le cours source.

## Execution
- Un subagent par batch ; **commit + `/clear` entre chaque batch**.
- Batch 1 est bloquant : ne pas lancer Batch 2+ sans réponse de l'utilisateur sur le choix de bibliothèque.
- Batch 5 dépend des réponses aux Open Questions (granularité, cache, historique) — s'arrêter et demander si non tranché avant ce batch.
- Ordre conseillé : 1 (bloquant) → 2 → 3 → 4 → 6 → 5 (peut suivre 4 en parallèle une fois les open questions tranchées).

## Known pitfalls
- **Pas de CDN** (règle projet) — tout rendu PDF/icônes doit être self-hosté, comme KaTeX (`backend/static/vendor/katex`) l'est déjà.
- **Ne pas dupliquer** : la hiérarchie `::` (déjà dans `routes_decks.py`), le matching subject→PDF (`source_matcher.py`), le pattern d'appel Infercom (`explain.py`) — tous à réutiliser, jamais réécrire.
- **`curl` cassé sur le Termux du tel** — non pertinent ici (pas de script Termux dans ce plan), mais rappel si un futur batch en ajoute un.
- **Jamais de trailer `Co-Authored-By`** (règle projet).
