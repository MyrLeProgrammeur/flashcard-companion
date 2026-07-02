"""
"Besoin d'aide" floating button on the PDF viewer (Batch 5): a single
stateless question grounded in the whole currently-open course PDF,
answered via Infercom DeepSeek-V3.1 — same infra/shape as explain.py's
card-explanation feature, but scoped to one resolved PDF file instead of
a card's matched sources.
"""
import hashlib
from pathlib import Path

from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from pdf_context import build_context

router = APIRouter()

SYSTEM_PROMPT = (
    "Tu es un tuteur pédagogique niveau Master 2 (statistiques / machine learning). "
    "On te donne un extrait d'un cours source (PDF) et une question posée par "
    "l'étudiant à ce sujet. Réponds de façon rigoureuse et précise, en t'appuyant "
    "uniquement sur cet extrait. Si l'extrait ne contient pas de quoi répondre à la "
    "question, dis-le explicitement plutôt que d'inventer une réponse."
)

# Answer language directive; also folded into the cache key.
LANG_DIRECTIVE = {
    "fr": "Réponds en français.",
    "en": "Answer in English.",
}


class PdfHelpBody(BaseModel):
    rel_path: str
    question: str
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
    if not body.question.strip():
        raise HTTPException(status_code=400, detail="question must not be empty")

    cfg = request.app.state.cfg
    store = request.app.state.store
    pdf_dir = Path(cfg["paths"]["pdf_dir"])

    resolved = _resolve_pdf(body.rel_path, pdf_dir)

    lang = body.lang if body.lang in LANG_DIRECTIVE else "fr"
    cache_key = hashlib.sha1(
        f"{body.rel_path}\x1f{body.question}\x1f{lang}".encode()
    ).hexdigest()

    cached = store.get_pdf_help(cache_key)
    if cached is not None:
        return {**cached, "cached": True}

    max_pdf_context_chars = cfg["explain"]["max_pdf_context_chars"]
    pdf_context = build_context([str(resolved)], max_pdf_context_chars)

    user_prompt = f"Question: {body.question}\n"
    if pdf_context:
        user_prompt += f"\nExtraits du cours source:\n{pdf_context}"
    else:
        user_prompt += "\n(Aucun texte n'a pu être extrait de ce PDF — réponds à partir de tes connaissances et dis-le explicitement.)"

    model = cfg["infercom"]["explain_model"]
    response = request.app.state.infercom_client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": f"{SYSTEM_PROMPT} {LANG_DIRECTIVE[lang]}"},
            {"role": "user", "content": user_prompt},
        ],
    )
    answer = response.choices[0].message.content

    store.save_pdf_help(cache_key, body.rel_path, body.question, answer, model)
    return {"answer": answer, "model": model, "cached": False}
