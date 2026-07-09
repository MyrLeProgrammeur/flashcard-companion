"""
FastAPI app entrypoint. Bound to 127.0.0.1 only — no LAN exposure, no auth
needed (loopback trust). Run with:
    uvicorn main:app --reload --host 127.0.0.1 --port 8420
"""
import os
from pathlib import Path

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.staticfiles import StaticFiles
from openai import OpenAI

from api import (
    routes_deck_groups,
    routes_decks,
    routes_exams,
    routes_explain,
    routes_pdf,
    routes_pdf_help,
    routes_review,
    routes_settings,
    routes_stats,
)
from config import load_config
from srs_store import SrsStore

load_dotenv(Path(__file__).parent / ".env")

app = FastAPI(title="flashcard-companion")

cfg = load_config()
app.state.cfg = cfg
app.state.store = SrsStore(Path(cfg["paths"]["data_dir"]) / "companion_state.db")
app.state.infercom_client = OpenAI(
    base_url=cfg["infercom"]["base_url"],
    api_key=os.environ["INFERCOM_API_KEY"],
)

app.include_router(routes_decks.router)
app.include_router(routes_deck_groups.router)
app.include_router(routes_review.router)
app.include_router(routes_explain.router)
app.include_router(routes_settings.router)
app.include_router(routes_stats.router)
app.include_router(routes_exams.router)
app.include_router(routes_pdf.router)
app.include_router(routes_pdf_help.router)


@app.middleware("http")
async def no_store(request, call_next):
    # Single-user loopback server behind an Android WebView: the WebView caches
    # static assets heuristically (no CDN, no revalidation) and will otherwise
    # serve a stale UI after a redeploy. Force revalidation on every request.
    response = await call_next(request)
    response.headers["Cache-Control"] = "no-store"
    return response


@app.get("/api/health")
def health():
    # The header pill reflects reachability of the AI backend (Infercom) — i.e.
    # real internet + a valid key. If this endpoint answers at all, the local
    # server is already up; the frontend derives "backend up" from that and only
    # uses `ai` below to color the pill. A cheap models.list() probe: no token
    # cost, short timeout so a dead link can't hang the poll.
    ai = "unreachable"
    try:
        app.state.infercom_client.with_options(timeout=5.0).models.list()
        ai = "ok"
    except Exception:
        ai = "unreachable"

    return {"status": "ok" if ai == "ok" else "degraded", "ai": ai}


app.mount("/", StaticFiles(directory=Path(__file__).parent / "static", html=True), name="static")
