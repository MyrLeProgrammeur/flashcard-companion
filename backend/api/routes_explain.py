from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

import apkg_reader
from explain import explain_card

router = APIRouter()


class ExplainFeedback(BaseModel):
    vote: int
    lang: str = "fr"


class ExplainBody(BaseModel):
    critique: str | None = None


@router.post("/api/cards/{guid}/explain")
def post_explain(
    guid: str,
    request: Request,
    force: bool = False,
    lang: str = "fr",
    body: ExplainBody | None = None,
):
    cfg = request.app.state.cfg
    store = request.app.state.store
    client = request.app.state.infercom_client

    apkg_dir = cfg["paths"]["apkg_dir"]
    card = None
    for c in apkg_reader.read_all_cards(apkg_dir):
        if c.guid == guid:
            card = c
            break
    if card is None:
        raise HTTPException(status_code=404, detail="Card not found")

    result = explain_card(
        client=client,
        model=cfg["infercom"]["explain_model"],
        card=card,
        pdf_dir=cfg["paths"]["pdf_dir"],
        store=store,
        max_pdf_context_chars=cfg["explain"]["max_pdf_context_chars"],
        force=force,
        lang=lang,
        critique=body.critique if body else None,
    )
    return result


@router.post("/api/cards/{guid}/explain/feedback")
def post_explain_feedback(guid: str, body: ExplainFeedback, request: Request):
    """Pure telemetry: log a 👍/👎 vote on the displayed explanation. Never
    calls the AI — a cached explanation is enough (works even pill-red)."""
    cfg = request.app.state.cfg
    store = request.app.state.store

    if body.vote not in (-1, 1):
        raise HTTPException(status_code=400, detail="vote must be -1 or +1")

    apkg_dir = cfg["paths"]["apkg_dir"]
    card = None
    for c in apkg_reader.read_all_cards(apkg_dir):
        if c.guid == guid:
            card = c
            break
    if card is None:
        raise HTTPException(status_code=404, detail="Card not found")

    cache_guid = f"{guid}\x1f{body.lang}"
    cached = store.get_explanation(cache_guid)
    if cached is not None:
        model = cached["model"]
        grounded = 1 if cached["source_files"] else 0
    else:
        model = cfg["infercom"]["explain_model"]
        grounded = None

    return store.save_explain_feedback(
        guid=guid,
        lang=body.lang,
        model=model,
        vote=body.vote,
        grounded=grounded,
        deck_name=card.deck_name,
        surface="explain",
    )
