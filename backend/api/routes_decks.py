from datetime import datetime, timezone

from fastapi import APIRouter, Request

from grouping import (
    apply_groups,
    group_name_from_path,
    group_path,
    normalise,
    subjects_in_group,
)
from srs import (
    QUALITY_AGAIN,
    QUALITY_EASY,
    QUALITY_GOOD,
    QUALITY_HARD,
    SrsSettings,
    review,
    settings_from_dict,
)

router = APIRouter()


def _interval_label(days: float) -> str:
    """Human, compact label for an SM-2 interval (backend is day-granular)."""
    if days < 1:
        return "<1 j"
    if days < 30:
        return f"{round(days)} j"
    if days < 365:
        return f"{round(days / 30)} mois"
    return f"{round(days / 365)} an" + ("s" if round(days / 365) > 1 else "")


def _rating_previews(state, now: datetime, settings: SrsSettings) -> dict:
    """Projected next interval per rating button, computed by srs.review itself
    (same function/settings as the real review — never duplicate SM-2 logic)."""
    return {
        "again": _interval_label(review(state, QUALITY_AGAIN, now, settings).interval_days),
        "hard": _interval_label(review(state, QUALITY_HARD, now, settings).interval_days),
        "good": _interval_label(review(state, QUALITY_GOOD, now, settings).interval_days),
        "easy": _interval_label(review(state, QUALITY_EASY, now, settings).interval_days),
    }


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
    settings = settings_from_dict(store.get_settings())
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
        {
            "guid": c.guid,
            "front": c.front,
            "back": c.back,
            "note": c.note,
            "previews": _rating_previews(store.get_state(c.guid), now, settings),
        }
        for _, c in due[:limit]
    ]


@router.get("/api/tree")
def deck_tree(request: Request):
    """Nested folder tree from the full Anki deck path (`a::b::c`, arbitrary
    depth). Counts are aggregated over every descendant, so a parent shows the
    sum of its subtree — read-only, driven entirely by the pipeline's deck names."""
    apkg_dir = request.app.state.cfg["paths"]["apkg_dir"]
    store = request.app.state.store
    import apkg_reader

    now = datetime.now(timezone.utc)
    tree: dict = {}
    for card in apkg_reader.read_all_cards(apkg_dir):
        segs = [s for s in card.deck_name.split("::") if s] or ["(sans nom)"]
        state = store.get_state(card.guid)
        is_due = state.due_at is not None and state.due_at <= now
        cursor = tree
        for seg in segs:
            node = cursor.setdefault(seg, {"children": {}, "card_count": 0, "due_count": 0})
            node["card_count"] += 1
            if is_due:
                node["due_count"] += 1
            cursor = node["children"]

    def to_list(children: dict, prefix: list[str]) -> list:
        out = []
        for name in sorted(children):
            node = children[name]
            path = prefix + [name]
            kids = to_list(node["children"], path)
            entry = {
                "name": name,
                "path": "::".join(path),
                "card_count": node["card_count"],
                "due_count": node["due_count"],
                "children": kids,
            }
            out.append(entry)

            # This node has cards of its own (deck_name ends here) *and*
            # sub-decks. card_count/due_count already aggregate the full
            # subtree, so the remainder after subtracting the children's
            # totals is exactly the count of cards attached directly to
            # this deck. Expose them as a synthetic leaf row so they stay
            # reachable — otherwise only the sub-decks show up and the
            # direct cards vanish from the UI. Its `path` is the parent's
            # own path, so reviewing it uses the same prefix-scoped query
            # as the parent and necessarily pulls in the sub-decks too;
            # there is no backend concept of "this deck, no descendants".
            if kids:
                direct_cards = node["card_count"] - sum(k["card_count"] for k in kids)
                if direct_cards > 0:
                    direct_due = node["due_count"] - sum(k["due_count"] for k in kids)
                    entry["children"].append(
                        {
                            "name": "",
                            "is_direct": True,
                            "path": "::".join(path),
                            "card_count": direct_cards,
                            "due_count": direct_due,
                            "children": [],
                        }
                    )
        return out

    def make_group(name: str, children: list) -> dict:
        return {
            "name": name,
            "path": group_path(name),
            "is_group": True,
            "card_count": sum(c["card_count"] for c in children),
            "due_count": sum(c["due_count"] for c in children),
            "children": sorted(children, key=lambda c: c["name"].casefold()),
        }

    return apply_groups(to_list(tree, []), store.get_deck_groups(), make_group)


@router.get("/api/subjects")
def list_subjects(request: Request):
    """Flat list of every deck-tree node (all `::` prefixes), for the exam
    subject picker — replaces the old free-text field (typo-prone exact
    match)."""
    apkg_dir = request.app.state.cfg["paths"]["apkg_dir"]
    import apkg_reader

    counts: dict[str, int] = {}
    for card in apkg_reader.read_all_cards(apkg_dir):
        segs = [s for s in card.deck_name.split("::") if s]
        for i in range(1, len(segs) + 1):
            path = "::".join(segs[:i])
            counts[path] = counts.get(path, 0) + 1

    return [
        {"path": path, "depth": path.count("::"), "card_count": count}
        for path, count in sorted(counts.items())
    ]


def _due_cards_in_scope(store, cards, now: datetime, path: str = "") -> list:
    """Shared due-computation: cards from `cards` whose stored state is due
    at `now`, scoped to `path` (empty = everything, else the exact deck or
    any deck nested under it via `path::...`). Returns (due_at, card) pairs,
    sorted. Both `/api/due` and `/api/due/count` go through this single loop
    so the two can never diverge on what "due" means."""

    # A folder is a display node, not a deck prefix: resolve it back to the
    # subjects it holds, otherwise `path` would match no deck_name at all.
    group_name = group_name_from_path(path)
    if group_name is not None:
        members = subjects_in_group(store.get_deck_groups(), group_name)
        normalised_members = {normalise(m) for m in members}

        def in_scope(deck_name: str) -> bool:
            return normalise(deck_name.split("::")[0]) in normalised_members
    else:

        def in_scope(deck_name: str) -> bool:
            return not path or deck_name == path or deck_name.startswith(path + "::")

    due = []
    for card in cards:
        if not in_scope(card.deck_name):
            continue
        state = store.get_state(card.guid)
        if state.due_at is not None and state.due_at <= now:
            due.append((state.due_at, card))

    due.sort(key=lambda pair: pair[0])
    return due


@router.get("/api/due")
def due_by_path(request: Request, path: str = "", limit: int = 50):
    """Due cards for a folder subtree. Empty path = everything (review all).
    Scope = the exact deck OR any deck nested under it (`path::...`)."""
    apkg_dir = request.app.state.cfg["paths"]["apkg_dir"]
    store = request.app.state.store
    import apkg_reader

    now = datetime.now(timezone.utc)
    settings = settings_from_dict(store.get_settings())

    due = _due_cards_in_scope(store, apkg_reader.read_all_cards(apkg_dir), now, path)
    return [
        {
            "guid": c.guid,
            "front": c.front,
            "back": c.back,
            "note": c.note,
            "subject": c.subject,
            "previews": _rating_previews(store.get_state(c.guid), now, settings),
        }
        for _, c in due[:limit]
    ]


@router.get("/api/due/count")
def due_count(request: Request, path: str = ""):
    """Total number of cards due right now (all decks by default) — for the
    Termux daily-notification script (Batch 7). Lightweight: no card
    payloads, just the count. Reuses `_due_cards_in_scope`, the same query
    `/api/due` uses, so the two endpoints can never disagree."""
    apkg_dir = request.app.state.cfg["paths"]["apkg_dir"]
    store = request.app.state.store
    import apkg_reader

    now = datetime.now(timezone.utc)
    due = _due_cards_in_scope(store, apkg_reader.read_all_cards(apkg_dir), now, path)
    return {"due": len(due)}
