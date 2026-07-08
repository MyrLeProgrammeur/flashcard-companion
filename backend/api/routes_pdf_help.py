"""
"Besoin d'aide" floating button on the PDF viewer (Batch 5): a multi-turn
chat grounded in the whole currently-open course PDF, answered via
Infercom DeepSeek-V3.1 — same infra/shape as explain.py's card-explanation
feature, but scoped to one resolved PDF file instead of a card's matched
sources. Every turn re-sends the full conversation; the PDF context stays
in the system message so each follow-up remains grounded.
"""
import hashlib
import json
from pathlib import Path

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from pdf_context import build_context

router = APIRouter()

SYSTEM_PROMPT = (
    "Tu es un tuteur pédagogique niveau Master 2 (statistiques / machine learning). "
    "On te donne un extrait d'un cours source (PDF) et une question posée par "
    "l'étudiant à ce sujet. Réponds de façon rigoureuse et précise. Appuie-toi en "
    "priorité sur l'extrait quand il traite la question. Mais tu as aussi le droit, "
    "et le devoir d'aider : si l'extrait ne suffit pas — par exemple une annale qui "
    "pose une question sans énoncer le théorème demandé, ou une notion seulement "
    "évoquée — réponds quand même à partir de tes propres connaissances plutôt que "
    "de refuser. Signale brièvement quand tu complètes au-delà de l'extrait, mais ne "
    "refuse jamais d'aider sous prétexte que l'extrait ne contient pas la réponse. "
    "IMPÉRATIF — écris TOUTE formule, symbole, variable ou expression mathématique "
    "en LaTeX, jamais en texte brut ni en Unicode : délimiteurs $...$ pour l'inline "
    "(ex. $\\sigma^2$, $x_i$, $\\hat\\beta$) et $$...$$ pour une équation en display. "
    "Cela vaut même pour un simple symbole isolé dans une phrase."
)

# Answer language directive; also folded into the cache key.
LANG_DIRECTIVE = {
    "fr": "Réponds en français.",
    "en": "Answer in English.",
}


class ChatMessage(BaseModel):
    role: str
    content: str


class PdfHelpBody(BaseModel):
    rel_path: str
    question: str | None = None
    messages: list[ChatMessage] | None = None
    lang: str = "fr"


def _resolve_pdf(rel_path: str, pdf_dir: Path) -> Path:
    """Path-traversal/containment guard — mirrors get_course_file in
    routes_pdf.py exactly (kept as a faithful copy per the batch plan
    rather than a shared helper, to avoid touching that module)."""
    pdf_dir = pdf_dir.resolve()
    resolved = (pdf_dir / rel_path).resolve()

    if not resolved.is_relative_to(pdf_dir):
        raise HTTPException(status_code=404, detail="File not found")
    if resolved.suffix.lower() != ".pdf":
        raise HTTPException(status_code=404, detail="File not found")
    if not resolved.is_file():
        raise HTTPException(status_code=404, detail="File not found")
    return resolved


@router.post("/api/courses/help")
def post_pdf_help(body: PdfHelpBody, request: Request):
    # Back-compat: legacy single-shot {question} becomes a one-message list.
    if body.messages:
        messages = body.messages
    elif body.question and body.question.strip():
        messages = [ChatMessage(role="user", content=body.question)]
    else:
        messages = []

    if not messages or not messages[-1].content.strip():
        raise HTTPException(status_code=400, detail="messages must not be empty")

    cfg = request.app.state.cfg
    store = request.app.state.store
    pdf_dir = Path(cfg["paths"]["pdf_dir"])

    resolved = _resolve_pdf(body.rel_path, pdf_dir)

    lang = body.lang if body.lang in LANG_DIRECTIVE else "fr"
    messages_json = json.dumps(
        [{"role": m.role, "content": m.content} for m in messages],
        ensure_ascii=False,
    )
    cache_key = hashlib.sha1(
        f"{body.rel_path}\x1f{lang}\x1f{messages_json}".encode()
    ).hexdigest()

    cached = store.get_pdf_help(cache_key)
    if cached is not None:
        return {**cached, "cached": True}

    max_pdf_context_chars = cfg["explain"]["max_pdf_context_chars"]
    pdf_context = build_context([str(resolved)], max_pdf_context_chars)

    system_prompt = f"{SYSTEM_PROMPT} {LANG_DIRECTIVE[lang]}"
    if pdf_context:
        system_prompt += f"\n\nExtraits du cours source:\n{pdf_context}"
    else:
        system_prompt += "\n\n(Aucun texte n'a pu être extrait de ce PDF — réponds à partir de tes connaissances et dis-le explicitement.)"

    model = cfg["infercom"]["explain_model"]
    response = request.app.state.infercom_client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": system_prompt},
            *[{"role": m.role, "content": m.content} for m in messages],
        ],
    )
    answer = response.choices[0].message.content

    store.save_pdf_help(cache_key, body.rel_path, messages[-1].content, answer, model)
    return {"answer": answer, "model": model, "cached": False}
