from fastapi import APIRouter, Request

import apkg_reader
from explain import explain_card

router = APIRouter()


@router.post("/api/cards/{guid}/explain")
def post_explain(guid: str, request: Request, force: bool = False):
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
        from fastapi import HTTPException

        raise HTTPException(status_code=404, detail="Card not found")

    result = explain_card(
        client=client,
        model=cfg["infercom"]["explain_model"],
        card=card,
        pdf_dir=cfg["paths"]["pdf_dir"],
        store=store,
        max_pdf_context_chars=cfg["explain"]["max_pdf_context_chars"],
        force=force,
    )
    return result
