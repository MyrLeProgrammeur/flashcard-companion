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

# Answer language directive appended to the system prompt (and folded into the
# cache key so switching language doesn't serve a stale-language explanation).
LANG_DIRECTIVE = {
    "fr": "Réponds en français.",
    "en": "Answer in English.",
}


def explain_card(
    client: OpenAI,
    model: str,
    card: CardRecord,
    pdf_dir: str,
    store: SrsStore,
    max_pdf_context_chars: int,
    force: bool = False,
    lang: str = "fr",
) -> dict:
    lang = lang if lang in LANG_DIRECTIVE else "fr"
    cache_guid = f"{card.guid}\x1f{lang}"
    if not force:
        cached = store.get_explanation(cache_guid)
        if cached is not None:
            return {**cached, "cached": True}

    # Match against every deck-path segment, not just the first (::-separated)
    # one, so an added level (e.g. "M1::Stats") doesn't break matching.
    segments = [s for s in card.deck_name.split("::") if s]
    source_files = store.get_source_match(card.deck_name)
    if source_files is None:
        source_files = find_source_pdfs(segments, pdf_dir)
        store.save_source_match(card.deck_name, source_files)

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
            {"role": "system", "content": f"{SYSTEM_PROMPT} {LANG_DIRECTIVE[lang]}"},
            {"role": "user", "content": user_prompt},
        ],
    )
    explanation = response.choices[0].message.content

    store.save_explanation(cache_guid, explanation, source_files, model)
    return {
        "explanation": explanation,
        "source_files": source_files,
        "model": model,
        "cached": False,
    }
