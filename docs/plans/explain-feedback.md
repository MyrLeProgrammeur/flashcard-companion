# Plan — Feedback 👍/👎 sur l'explication IA
> Executor: Sonnet or Opus + subagents. Apply the decisions, do not re-litigate them. Unresolved doubt → stop and ask.

## Goal & scope
Ajouter un vote 👍/👎 sur l'explication IA affichée dans la sheet de review, en **télémétrie pure** (aucune action déclenchée par un 👎). But réel : collecter un signal permettant plus tard (feature stats) de repérer les explications faibles, notamment corréler les 👎 avec le fallback card-only (matching PDF qui échoue).

Périmètre strict : **explication seule** (`/explain`). Le pdf-help, le fix "bouton mort" offline, la régénération sur 👎, la persistance inter-ouvertures et l'exploitation en stats sont **hors lot**.

## Settled decisions
- **Modèle de données = log append-only** (calqué sur `review_log`), pas un état écrasable. Plusieurs votes possibles par (guid, lang).
- **Télémétrie pure** : un vote logge, point. Accusé de réception UI ("Merci, c'est noté"), rien d'autre.
- **Instantané stocké** : `guid, lang, model, vote, grounded, deck_name, surface, created_at`. On stocke `deck_name` (évite le point de douleur de `review_log` qui n'a que le guid) et `surface` (=`'explain'` pour l'instant, anticipe pdf-help sans migration).
- **Dérivation autoritative côté backend (option A)** : l'endpoint prend seulement `{vote, lang}`. Il retrouve `deck_name` par scan `.apkg` (pattern `_find_card`), et lit l'explication cachée `get_explanation(guid\x1flang)` pour en tirer `model` et `grounded = len(source_files) > 0`. Le client ne fait confiance à rien. Fallback si pas de cache : `model` depuis `cfg["infercom"]["explain_model"]`, `grounded = null`.
- **Encodage vote** : entier `+1` / `-1`. Validation : hors {-1, +1} → 400 (comme `post_review` sur `quality`). Guid inconnu → 404.
- **Réponse endpoint** : l'instantané stocké (`{vote, grounded, model, deck_name, created_at}`) — utile pour vérifier à l'œil que `grounded` est bien résolu depuis le cache.
- **UI** : verrou intra-ouverture (1er clic fige les deux boutons, surligne le choisi, micro-copy). Pas de changement d'avis dans la même ouverture. Pas de persistance inter-ouvertures (chaque ouverture repart neutre). Boutons dans le `sheet-foot`.
- **i18n** fr/en pour labels + confirmation.
- **Tests** : store (1 test : écrit tous les champs + append-only), endpoint (200 + instantané / 400 vote invalide / 404 guid inconnu / grounded+model depuis cache plein / grounded=null + model config sur cache vide). Pas de test front.

## Open questions
none

## Batches (independent)

### Batch 1 — Schéma + persistance
- Files: `backend/srs_store.py`
- Actions :
  - Ajouter à `SCHEMA_SQL` la table :
    ```sql
    CREATE TABLE IF NOT EXISTS explain_feedback (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        guid TEXT, lang TEXT, model TEXT,
        vote INTEGER,        -- +1 / -1
        grounded INTEGER,    -- 1 = PDF-grounded, 0 = card-only, NULL = cache absent
        deck_name TEXT,
        surface TEXT,        -- 'explain'
        created_at TEXT
    );
    ```
  - Méthode `save_explain_feedback(self, guid, lang, model, vote, grounded, deck_name, surface="explain")` → INSERT, `created_at = _now_iso()`. Append-only (jamais d'UPDATE/DELETE), calquée sur `log_review` (`srs_store.py:129`).
  - Vérifier que `get_explanation(cache_guid)` expose bien `source_files` et `model` (utilisé par le batch 2) ; sinon, exposer ce qu'il faut sans casser l'appelant existant (`explain.py:44`).
- Constraints : suivre le style de `log_review` et des tables existantes ; `source_files` est stocké sérialisé (voir `save_explanation`/`get_explanation`) — le batch 2 en déduit `grounded`.
- Verify: `pytest backend/tests/ -k feedback`
- Done when: un test store insère un feedback complet et vérifie 2 lignes après 2 votes sur le même (guid, lang).

### Batch 2 — Endpoint
- Files: `backend/api/routes_explain.py` (même router que `/explain`)
- Actions :
  - `POST /api/cards/{guid}/explain/feedback`, body Pydantic `{vote: int, lang: str = "fr"}`.
  - Valider `vote ∈ {-1, +1}` sinon `HTTPException(400)`.
  - Retrouver la carte par guid (scan `apkg_reader.read_all_cards`, pattern de `routes_explain.py:17` / `_find_card`) → `deck_name` ; 404 si absente.
  - `cache_guid = f"{guid}\x1f{lang}"` ; `cached = store.get_explanation(cache_guid)`.
    - Si présent : `model = cached["model"]`, `grounded = 1 if cached["source_files"] else 0`.
    - Sinon : `model = cfg["infercom"]["explain_model"]`, `grounded = None`.
  - `store.save_explain_feedback(guid, lang, model, vote, grounded, deck_name, "explain")`.
  - Retourner `{vote, grounded, model, deck_name, created_at}`.
- Constraints : réutiliser `request.app.state.{cfg,store}` comme `post_explain` ; ne PAS appeler l'IA (le feedback marche même pill rouge si un cache existe).
- Verify: `pytest backend/tests/ -k feedback`
- Done when: POST valide → 200 + instantané correct ; cache plein → `grounded=1`/`model` du cache ; cache vide → `grounded=null`/`model` config ; vote invalide → 400 ; guid inconnu → 404.

### Batch 3 — UI sheet
- Files: `backend/static/app.js`, `backend/static/review.html` (markup sheet), `backend/static/i18n.js`, `backend/static/style.css` si besoin
- Actions :
  - Deux boutons 👍/👎 dans le `sheet-foot` (près de `app.js:167`). Au retour de `/explain` (`app.js:139-173`), `guid`+`lang` (`getLang()`) sont déjà en scope → les capturer pour le POST.
  - Au clic : `POST /api/cards/${guid}/explain/feedback` body `{vote, lang}`. Puis figer les deux boutons, surligner le choisi, afficher la micro-copy de confirmation.
  - Réinitialiser l'état neutre à chaque `openSheet()`/nouvelle explication (pas de persistance inter-ouvertures).
  - i18n : clés fr/en pour les labels 👍/👎 et le "Merci, c'est noté".
- Constraints : vanilla JS, pas de build ; suivre le style des handlers existants (`app.js`) et le rendu du `sheet-foot`. Aucune régression sur le flux explain actuel.
- Verify: manuel — `uvicorn main:app --reload`, `localhost:8420`, ouvrir une explication, voter, vérifier la requête réseau (200 + instantané) et le verrou. Optionnel : vérif CDP sur le WebView phone (cf. mémoire projet) si doute rendu.
- Done when: un vote part au backend, l'UI se verrouille avec confirmation, et rouvrir la sheet repart neutre.

## Execution
- Un subagent par batch ; commit + `/clear` entre les batches.
- Ordre imposé : Batch 1 → 2 → 3 (le 2 dépend du schéma, le 3 de l'endpoint).
- Batches 1-2 : backend, testables isolément. Batch 3 : front, vérif manuelle.

## Known pitfalls
- **`grounded` = le vrai enjeu.** Il DOIT venir du cache d'explication (`source_files`), pas d'une ré-inférence. C'est le point que les tests doivent verrouiller (cache plein vs vide).
- Le cache `explain_cache` est keyé sur `guid\x1flang` (`explain.py:42`), **pas** sur le guid seul ni sur le modèle : une explication d'un ancien `explain_model` reste servie jusqu'à un `force`. Donc le `model` stocké avec le feedback = celui qui a réellement produit le texte affiché (repris du cache), correct par construction avec l'option A.
- Le feedback suit toujours un `/explain` affiché → la ligne `explain_cache` existe (le `save_explanation` tourne avant le retour, `explain.py:75`). Le fallback cache-vide est défensif, il ne devrait pas se déclencher en usage normal.
- Ne JAMAIS écrire dans les `.apkg` (règle projet) — tout va dans `companion_state.db`.
- Pas de trailer `Co-Authored-By: Claude` (règle projet, nulle part).
- `deck_name` n'est PAS dans le payload `/api/due` (seulement `subject`) — d'où la dérivation backend par scan. Ne pas tenter de le lire depuis `queue[idx]`.
