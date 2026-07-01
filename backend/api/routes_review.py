from datetime import datetime, timezone

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

import apkg_reader
from srs import review, settings_from_dict

router = APIRouter()


def _find_card(apkg_dir: str, guid: str) -> apkg_reader.CardRecord:
    for card in apkg_reader.read_all_cards(apkg_dir):
        if card.guid == guid:
            return card
    raise HTTPException(status_code=404, detail="Card not found")


@router.get("/api/cards/{guid}")
def get_card(guid: str, request: Request):
    apkg_dir = request.app.state.cfg["paths"]["apkg_dir"]
    store = request.app.state.store

    card = _find_card(apkg_dir, guid)
    state = store.get_state(guid)
    return {
        "guid": card.guid,
        "front": card.front,
        "back": card.back,
        "note": card.note,
        "deck_name": card.deck_name,
        "reps": state.reps,
        "interval_days": state.interval_days,
        "due_at": state.due_at.isoformat() if state.due_at else None,
    }


class ReviewBody(BaseModel):
    quality: int
    time_spent_ms: int | None = None


@router.post("/api/cards/{guid}/review")
def post_review(guid: str, body: ReviewBody, request: Request):
    if not 0 <= body.quality <= 5:
        raise HTTPException(status_code=400, detail="quality must be 0-5")

    apkg_dir = request.app.state.cfg["paths"]["apkg_dir"]
    store = request.app.state.store

    _find_card(apkg_dir, guid)  # 404 if the card doesn't exist
    current_state = store.get_state(guid)
    settings = settings_from_dict(store.get_settings())
    new_state = review(current_state, body.quality, datetime.now(timezone.utc), settings)
    store.save_state(guid, new_state)
    store.log_review(
        guid,
        body.quality,
        body.time_spent_ms,
        current_state.interval_days,
        new_state.interval_days,
        current_state.reps,
        new_state.reps,
    )

    return {
        "guid": guid,
        "reps": new_state.reps,
        "interval_days": new_state.interval_days,
        "due_at": new_state.due_at.isoformat() if new_state.due_at else None,
    }
