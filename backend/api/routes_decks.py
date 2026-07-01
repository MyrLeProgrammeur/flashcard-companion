from datetime import datetime, timezone

from fastapi import APIRouter, Request

router = APIRouter()


@router.get("/api/decks")
def list_decks(request: Request):
    apkg_dir = request.app.state.cfg["paths"]["apkg_dir"]
    store = request.app.state.store
    import apkg_reader

    cards = apkg_reader.read_all_cards(apkg_dir)
    now = datetime.now(timezone.utc)

    tree: dict[str, dict[str, dict]] = {}
    for card in cards:
        subject_node = tree.setdefault(card.subject, {})
        theme_node = subject_node.setdefault(card.theme, {"card_count": 0, "due_count": 0})
        theme_node["card_count"] += 1

        state = store.get_state(card.guid)
        if state.due_at is not None and state.due_at <= now:
            theme_node["due_count"] += 1

    return tree


@router.get("/api/decks/{subject}/{theme}/due")
def due_cards(subject: str, theme: str, request: Request, limit: int = 20):
    apkg_dir = request.app.state.cfg["paths"]["apkg_dir"]
    store = request.app.state.store
    import apkg_reader

    now = datetime.now(timezone.utc)
    cards = [
        c
        for c in apkg_reader.read_all_cards(apkg_dir)
        if c.subject == subject and c.theme == theme
    ]

    due = []
    for card in cards:
        state = store.get_state(card.guid)
        if state.due_at is not None and state.due_at <= now:
            due.append((state.due_at, card))

    due.sort(key=lambda pair: pair[0])
    return [
        {"guid": c.guid, "front": c.front, "back": c.back, "note": c.note}
        for _, c in due[:limit]
    ]
