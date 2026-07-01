"""
Builds the "explain in depth" prompt and calls Infercom's DeepSeek-V3.1
(reasoning group), grounded in matched source PDF text when available.
Falls back to explaining from the card's own fields if no PDF match is
found — the button must never hard-fail.
"""
from openai import OpenAI

from apkg_reader import CardRecord
from pdf_context import build_context
from source_matcher import find_source_pdfs
from srs_store import SrsStore

SYSTEM_PROMPT = (
    "Tu es un tuteur pédagogique niveau Master 2 (statistiques / machine learning). "
    "On te donne une flashcard (question/réponse) et, si disponible, des extraits du "
    "cours source. Donne une explication approfondie et rigoureuse de la notion "
    "derrière la carte : intuition, définitions précises, dérivation ou preuve si "
    "pertinent, pièges classiques. Si aucun extrait de cours n'est fourni, explique "
    "à partir de la carte seule et dis-le explicitement."
)


def explain_card(
    client: OpenAI,
    model: str,
    card: CardRecord,
    pdf_dir: str,
    store: SrsStore,
    max_pdf_context_chars: int,
    force: bool = False,
) -> dict:
    if not force:
        cached = store.get_explanation(card.guid)
        if cached is not None:
            return {**cached, "cached": True}

    source_files = store.get_source_match(card.subject)
    if source_files is None:
        source_files = find_source_pdfs(card.subject, pdf_dir)
        store.save_source_match(card.subject, source_files)

    pdf_context = build_context(source_files, max_pdf_context_chars) if source_files else ""

    user_prompt = f"Question: {card.front}\nRéponse: {card.back}\n"
    if card.note:
        user_prompt += f"Note: {card.note}\n"
    if pdf_context:
        user_prompt += f"\nExtraits du cours source:\n{pdf_context}"
    else:
        user_prompt += "\n(Aucun extrait de cours source trouvé — explique à partir de la carte seule.)"

    response = client.chat.completions.create(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_prompt},
        ],
    )
    explanation = response.choices[0].message.content

    store.save_explanation(card.guid, explanation, source_files, model)
    return {
        "explanation": explanation,
        "source_files": source_files,
        "model": model,
        "cached": False,
    }
