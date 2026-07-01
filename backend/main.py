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
app.include_router(routes_review.router)
app.include_router(routes_explain.router)
app.include_router(routes_settings.router)
app.include_router(routes_stats.router)
app.include_router(routes_exams.router)
app.include_router(routes_pdf.router)
app.include_router(routes_pdf_help.router)


@app.get("/api/health")
def health():
    checks = {}

    try:
        app.state.store.conn.execute("SELECT 1").fetchone()
        checks["db"] = "ok"
    except Exception as exc:
        checks["db"] = f"error: {exc}"

    checks["infercom_api_key"] = "ok" if os.environ.get("INFERCOM_API_KEY") else "missing"

    if all(v == "ok" for v in checks.values()):
        return {"status": "ok"}
    return {"status": "degraded", "checks": checks}


app.mount("/", StaticFiles(directory=Path(__file__).parent / "static", html=True), name="static")
