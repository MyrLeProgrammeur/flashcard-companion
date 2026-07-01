# CLAUDE.md — projet `flashcard-companion`

App Android qui remplace AnkiDroid comme client de review pour les decks générés par
le projet sœur `flashcard-pipeline`, avec un bouton d'explication IA groundée dans le
PDF source. Voir `README.md` pour le démarrage rapide.

## Contexte à charger avant d'agir

- `flashcard-pipeline` (`../flashcard-pipeline`) est **read-only** depuis ce repo :
  ce projet ne doit jamais écrire dans ses `.apkg`, son `themes.json` ou son état.
- Le GUID stable `sha1(subject\x1ftheme\x1fquestion)` (voir `db_writer.py` du
  pipeline) est la clé de jointure — jamais `notes.id`.

## Règles

- Le backend tourne **sur le tel via Termux**, pas sur le PC.
- La clé Infercom (`INFERCOM_API_KEY`) vit uniquement dans `backend/.env` **sur le
  tel**, hors de tout dossier Syncthing — jamais commit, jamais sur le PC.
- L'état SRS (`backend/srs_store.db`) est propre à cette app, jamais écrit dans les
  `.apkg` synced (évite les conflits Syncthing).
- Matching PDF↔carte = heuristique MVP (voir `source_matcher.py`) — pas garanti,
  fallback explicite vers explication depuis la carte seule.
- **Jamais de trailer `Co-Authored-By: Claude`** — nulle part (commits, PR, artefacts).

## Workflow

- Dev/itération UI : `uvicorn main:app --reload` en local, ouvrir `localhost:8420`
  dans un navigateur PC — pas besoin du tel pour ça.
- Tests unitaires : `pytest backend/tests/`.
- Manips sur le tel (install Termux, APK, vérif chemins Syncthing) = session dédiée,
  câble ou wireless debugging. Claude ne pousse jamais d'APK (installé par Matheo).
