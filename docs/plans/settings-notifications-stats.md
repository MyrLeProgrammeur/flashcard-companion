# Plan — Réglages SM-2, Notifications, Statistiques poussées
> Executor: Sonnet + subagents. Apply the decisions, do not re-litigate them. Unresolved doubt → stop and ask the user.

## Goal & scope
Add three features to `flashcard-companion` (companion Android/WebView + backend FastAPI sur Termux, dégooglisé) :
- **B — Réglages SM-2** : rendre les intervalles/scores configurables, corriger le « 1 j » dégénéré, et rendre `/api/health` réellement significatif.
- **C — Notifications / rappels quotidiens** : 1 notif/jour à heure fixe si des cartes sont dues, via Termux (pas de FCM).
- **D — Statistiques poussées** : logguer chaque révision (append-only) pour analyser le comportement (temps/carte, % réussite/carte, historique), avec export.

Out of scope : le chantier A (app démarre le backend) est **déjà fait** (MainActivity auto-start + Termux:Boot). Ne pas y toucher.

## Contexte actuel (vérifié cette session — ne pas re-auditer)
- **SRS réel** : `backend/srs.py` implémente un SM-2 « lite ». `review()` (srs.py:24-51) calcule `due_at = now + interval_days`. Persisté en SQLite par `srs_store.py` (schéma `card_state` à srs_store.py:12-21 : `reps, interval_days, ease_factor, due_at, last_reviewed_at, created_at`).
- **Filtrage des dues = temps réel**, pas de cron : chaque route recalcule `due_at <= now` (routes_decks.py `/api/tree`, `/api/due` ; routes_review.py). Fuseaux UTC corrects partout.
- **Paramètres codés en dur** dans srs.py : `QUALITY_AGAIN=1/HARD=3/GOOD=4/EASY=5` (:9-12), 1er intervalle `1.0` (:28,:32), 2e `6.0` (:34), suivants `interval*ease` (:36), ease init `2.5` (:19), formule ease (:38-40), plancher `1.3` (:41). Aucune surface config/API ne les expose.
- **Bug « 1 j » dégénéré** : sur une carte neuve (`reps=0`), Again ET Hard/Good/Easy tombent tous à `interval_days=1.0` → les 4 previews affichent « 1 j ». `_rating_previews` (routes_decks.py) reflète fidèlement ce comportement réel mais trompeur.
- **`/api/health` factice** : `main.py:35-37` renvoie `{"status":"ok"}` en dur — ne vérifie ni la base ni la clé Infercom.
- **Aucune notification/rappel nulle part** (ni Android, ni backend, ni Termux). Le « dû » est purement passif (pull).
- **Log de révision inexistant** : `srs_store.save_state` **écrase** l'état courant (INSERT … ON CONFLICT DO UPDATE, srs_store.py:77-98). Aucun historique des révisions passées n'est conservé → à créer pour les stats.
- **Review POST** : `routes_review.py:42-60`, body `ReviewBody { quality:int 0-5 }` seulement. `app.js` `rate(quality)` POST `{quality}`. Le temps passé n'est ni mesuré ni envoyé.
- **Config** : `backend/config.yaml` (paths, infercom, server, explain) chargé par `config.py`, exposé via `app.state.cfg` (main.py:22-23). Store à `app.state.store`.
- **Front** : UI servie en `backend/static/` — `index.html` (arbre drill-down), `review.html` + `app.js` (session), `common.js` (theme/health/markdown + KaTeX `renderMath`), `style.css` (tokens light/dark). KaTeX self-hosted `static/vendor/katex`, fonts `static/fonts`.
- **Contraintes** : dégooglisé (jamais de FCM/Play Services) ; backend tourne sur Termux ; `termux-api` installé (termux-notification, termux-job-scheduler dispos) ; hiérarchie decks dérivée des noms Anki `::` ; jamais de trailer `Co-Authored-By`.

## Settled decisions
- **C (notifs)** : chemin **Termux** (pas FCM). **1 notification/jour à heure fixe** si `due_count > 0`. Tap sur la notif → **ouvre l'app** (intent launcher `com.matheo.flashcardcompanion`).
- **B (fix 1 j)** : les 4 notes doivent donner des intervalles **distincts dès la 1re révision** (via des intervalles gradués configurables).
- **B (health)** : `/api/health` doit vraiment vérifier (au minimum : base SQLite ouvrable + présence de `INFERCOM_API_KEY`), pas un `ok` en dur.
- **D (stats)** : log **append-only** de chaque révision (jamais écrasé), séparé de `card_state`. Métriques voulues (explicitement demandées par l'utilisateur) : **temps passé par carte**, **si la carte a été réussie**, **% de réussite par carte**, historique complet exploitable + **export** (« je suis mon propre Google »).
- **D (temps)** : le front doit **mesurer le temps par carte** et l'envoyer au POST review.
- **Cible d'exécution** : Sonnet + subagents ; UI en suivant les patterns existants (tokens CSS, `common.js` helpers, pas de framework).

## Open questions (à confirmer avec l'utilisateur avant de coder la partie concernée)
- **B1 — Granularité des intervalles** : le SM-2 actuel est **au jour** (min 1 j). Les previews du design évoquaient des sous-jour (« <1 min », « 6 min »). Introduit-on des **learning steps sous-jour** façon Anki (Again = quelques min même session) ou reste-t-on **au jour** avec des valeurs gradués (ex. Again=remise à 0 / Hard=1 j / Good=3 j / Easy=7 j au 1er passage) ? → **décision produit, demander.**
- **B2 — Quels knobs exposer** dans Réglages : juste les intervalles gradués par note ? + bonus Easy ? + modificateur global d'intervalle ? + ease de départ/plancher ? Garder minimal ou complet ?
- **B3 — Portée des réglages** : global (tous decks) ou par dossier/matière ?
- **B4 — Persistance des réglages** : `config.yaml` (fichier) vs nouvelle table `settings` en base (éditable via API) ? (Une table est plus cohérente avec un écran Réglages en écriture.)
- **C1 — Heure de la notif** : valeur par défaut (ex. 9h ?) et **est-elle réglable** depuis l'écran Réglages ?
- **C2 — Contenu de la notif** : juste le total dû, ou détail par matière ?
- **C3 — Planificateur** : `termux-job-scheduler` (re-enregistrer au boot via le script Termux:Boot existant) — confirmer la fiabilité voulue (l'OS peut décaler l'horaire).
- **D1 — Mesure du temps** : chrono depuis l'affichage du recto jusqu'au clic de note, ou depuis le flip (verso) ? Gérer les pauses/app en arrière-plan ?
- **D2 — Rétention** : le log grossit indéfiniment (ok pour usage perso ?) — pas de purge par défaut.
- **D3 — Visualisations & export** : quelles vues exactes (courbe de rétention, heatmap, top cartes ratées…) et quel format d'export (CSV/JSON/les deux) ? « Comparer avec mes notes » = import de notes externes plus tard (hors scope v1).

## Batches (independent)

### Batch 1 — `/api/health` réel  (indépendant, petit)
- Files: `backend/main.py`.
- Actions: remplacer le handler `health()` (main.py:35-37) par une vérif : ouvrir la base (`app.state.store`) en lecture (petit `SELECT 1`), vérifier `os.environ.get("INFERCOM_API_KEY")` non vide. Retourner `{"status":"ok"}` si tout va bien, sinon `{"status":"degraded","checks":{...}}` avec code 200 (l'UI ne bloque que sur absence de réponse). Ne PAS faire d'appel réseau Infercom (juste présence de clé).
- Constraints: garder la forme `{"status": ...}` (l'UI `common.js` teste `res.ok`).
- Verify: `curl -s 127.0.0.1:8420/api/health` ; couper la clé → `degraded`.
- Done when: health reflète l'état base+clé, l'UI reste « En ligne » tant que FastAPI répond.

### Batch 2 — Réglages SM-2 (backend)  (dépend de B1-B4 tranchés)
- Files: `backend/srs.py`, `backend/srs_store.py` (ou `config.yaml`), nouveau `backend/api/routes_settings.py`, `backend/main.py` (include router).
- Actions: (1) déplacer les constantes SM-2 hors de srs.py vers une source de vérité (table `settings` recommandée, cf. B4) ; `review()` prend les params en argument ou lit un objet settings. (2) Implémenter les **intervalles gradués** (cf. B1) pour que Again/Hard/Good/Easy divergent dès `reps=0` → corrige le « 1 j ». (3) `GET /api/settings` + `PUT /api/settings`. (4) `_rating_previews` (routes_decks.py) doit utiliser les mêmes params.
- Constraints: pas de duplication de la logique SM-2 (une seule fonction) ; migration douce (valeurs par défaut = comportement actuel sauf le fix graduation).
- Verify: `pytest backend/tests/` (adapter `test_srs.py`) ; `curl` GET/PUT settings ; previews d'une carte neuve → 4 valeurs distinctes.
- Done when: changer un intervalle via PUT change les previews et le prochain `due_at`, sans édition de code.

### Batch 3 — Écran Réglages (UI)  (dépend de Batch 2)
- Files: `backend/static/` — nouveau `settings.html` + JS, lien depuis `index.html` (icône roue crantée dans le header), styles dans `style.css` (réutiliser les tokens).
- Actions: écran listant les knobs (cf. B2), lecture `GET /api/settings`, sauvegarde `PUT`. Suivre le style existant (mono labels, cartes surface, dark mode).
- Verify: ouvrir `/settings.html`, modifier, recharger review → previews mis à jour.
- Done when: l'utilisateur règle les intervalles depuis l'app.

### Batch 4 — Log de révision append-only + mesure du temps  (fondation stats)
- Files: `backend/srs_store.py` (nouvelle table `review_log`), `backend/api/routes_review.py` (élargir `ReviewBody`), `backend/static/app.js` (chrono + envoi).
- Actions: (1) table `review_log(id, guid, reviewed_at, quality, time_spent_ms, prev_interval_days, new_interval_days, prev_reps, new_reps)` — **jamais écrasée** (INSERT only). (2) `post_review` insère une ligne à chaque note (en plus de `save_state`). (3) `ReviewBody` accepte `time_spent_ms:int|None`. (4) `app.js` : mesurer le temps (cf. D1) et l'envoyer.
- Constraints: append-only, ne pas toucher `card_state` existant ; rétrocompat (time_spent optionnel).
- Verify: noter quelques cartes → `SELECT count(*) FROM review_log` croît ; `time_spent_ms` non nul.
- Done when: chaque révision laisse une trace immuable avec le temps passé.

### Batch 5 — Analytics (backend)  (dépend de Batch 4)
- Files: nouveau `backend/api/routes_stats.py`, `backend/main.py`.
- Actions: endpoints de lecture agrégée depuis `review_log` : `GET /api/stats/overview` (total révisions, temps total, réussite globale, série par jour), `GET /api/stats/cards` (par carte : nb révisions, % réussite, temps moyen/total, dernière note), `GET /api/stats/export?format=csv|json` (dump brut du log). % réussite = (Good+Easy)/total selon convention à confirmer (D3).
- Verify: `curl` chaque endpoint après quelques révisions.
- Done when: les métriques demandées (temps/carte, % réussite/carte, historique) sont interrogeables + export.

### Batch 6 — Statistiques (UI)  (dépend de Batch 5)
- Files: `backend/static/` — `stats.html` + JS, lien depuis `index.html`, styles.
- Actions: vues (cf. D3) : vue d'ensemble, tableau par carte triable (% réussite, temps), bouton export. Graphes en SVG maison ou lib légère self-hosted (pas de CDN Google). Suivre tokens + dark mode.
- Verify: ouvrir `/stats.html`, données cohérentes avec le log.
- Done when: l'utilisateur visualise et exporte ses données de révision.

### Batch 7 — Notifications quotidiennes (Termux)  (dépend de C1-C3 ; peut défauter)
- Files: nouveau `backend/api/` endpoint `GET /api/due/count` (total dû maintenant) ; nouveau script `backend/termux/notify-due.sh` ; enregistrement via `termux-job-scheduler` (ajouter au script Termux:Boot `~/.termux/boot/start-flashcard-backend.sh`, déjà déployé).
- Actions: (1) `/api/due/count` → `{ "due": N }`. (2) `notify-due.sh` : query l'endpoint (via `python3 urllib`, **pas `curl`** — cassé sur ce Termux), si `due>0` → `termux-notification --title "Flashcards" --content "N cartes à réviser" --action "am start -n com.matheo.flashcardcompanion/.MainActivity"`. (3) planifier 1×/jour à l'heure choisie (C1) via `termux-job-scheduler --script ... --period-ms 86400000` (ou `at`/boucle) ; re-enregistrer au boot. Battery: Termux/Termux:Boot déjà whitelistés.
- Constraints: dégooglisé (termux-notification est local, OK) ; `curl` KO → utiliser Python pour les requêtes HTTP locales.
- Verify: lancer `notify-due.sh` à la main → notif apparaît, tap ouvre l'app.
- Done when: une notif/jour à l'heure fixe rappelle les cartes dues.

## Execution
- Un subagent par batch ; **commit + `/clear` entre chaque batch**.
- Ordre conseillé : 1 → 2 → 3 (réglages) ; 4 → 5 → 6 (stats) ; 7 (notifs) en dernier (dépend de l'heure réglable si B/C liés).
- Après les batches touchant le backend : **déployer sur le tel** (scp vers `~/flashcard-companion/backend/`, redémarrer uvicorn) et vérifier via `adb forward tcp:8420` + navigateur/app. Le repo PC est la source ; le tel est la cible d'exécution.
- Tests : `pytest backend/tests/` ; l'UI se teste en ouvrant `http://127.0.0.1:8420/...` (via `adb forward`).

## Known pitfalls
- **`curl` est cassé sur ce Termux** (skew openssl/ngtcp2) → toute requête HTTP côté Termux se fait en **Python `urllib`**, jamais curl.
- **Termux ne peut pas installer de wheels manylinux** → toute nouvelle dépendance native doit être prébuild Termux (`pkg`) ou pure-python ; `requirements.txt` est déjà réduit à ce qui build (pdfplumber optionnel/lazy).
- **Ne pas dupliquer la logique SM-2** : previews (`_rating_previews`) et `review()` doivent partager la même source de params, sinon divergence UI/réel.
- **Log append-only** : ne jamais faire d'UPDATE/DELETE sur `review_log` ; `card_state` reste l'état courant séparé.
- **`/api/health`** garde la forme `{"status": ...}` et **code 200** même en `degraded` (l'UI ne bloque que si FastAPI ne répond pas).
- **Notif** : le path de l'action tap doit viser `com.matheo.flashcardcompanion/.MainActivity` (exported).
- **Pas de CDN** (Google ou autre) pour d'éventuels graphes : self-host.
- **Jamais de trailer `Co-Authored-By`** (règle projet).
