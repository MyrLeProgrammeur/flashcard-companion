"""Emit Prompts.kt from the backend's Python sources, so the prompts cannot drift."""
import ast
import pathlib

BACKEND = pathlib.Path(__file__).resolve().parent.parent / "backend"
OUT = (
    pathlib.Path(__file__).resolve().parent.parent
    / "android/app/src/main/java/com/matheo/flashcardcompanion/ai/Prompts.kt"
)


def consts(path):
    """Top-level literal assignments in a module."""
    tree = ast.parse(path.read_text(encoding="utf-8"))
    out = {}
    for node in tree.body:
        if isinstance(node, ast.Assign) and len(node.targets) == 1:
            target = node.targets[0]
            if isinstance(target, ast.Name):
                try:
                    out[target.id] = ast.literal_eval(node.value)
                except ValueError:
                    pass
    return out


explain = consts(BACKEND / "explain.py")
pdf_help = consts(BACKEND / "api" / "routes_pdf_help.py")

EXPLAIN_SYSTEM = explain["SYSTEM_PROMPT"]
PDF_SYSTEM = pdf_help["SYSTEM_PROMPT"]
LANG = explain["LANG_DIRECTIVE"]
assert LANG == pdf_help["LANG_DIRECTIVE"], "the two language dicts diverged"

print(f"explain system: {len(EXPLAIN_SYSTEM)} chars")
print(f"pdf-help system: {len(PDF_SYSTEM)} chars")
print(f"lang directives: {LANG}")


def kt(s):
    """Kotlin string literal body: escape backslash, quote and $ (templates)."""
    return (
        s.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("$", "\\$")
        .replace("\n", "\\n")
    )


body = f'''package com.matheo.flashcardcompanion.ai

/**
 * The AI prompts, generated verbatim from the Python backend
 * (`explain.py`, `api/routes_pdf_help.py`) — they are the product, so they are
 * copied rather than paraphrased.
 *
 * Note the single backslashes in [PDF_HELP_SYSTEM]: the Python literal shows
 * `$\\\\sigma` only because it is a non-raw literal, and the value the model
 * actually receives has one backslash. Doubling them here would quietly weaken
 * the LaTeX instruction.
 *
 * Regenerate with tools/gen_prompts.py.
 */
object Prompts {{

    const val EXPLAIN_SYSTEM = "{kt(EXPLAIN_SYSTEM)}"

    const val PDF_HELP_SYSTEM = "{kt(PDF_SYSTEM)}"

    val LANG_DIRECTIVE = mapOf(
        "fr" to "{kt(LANG["fr"])}",
        "en" to "{kt(LANG["en"])}",
    )

    fun langDirective(lang: String): String = LANG_DIRECTIVE[lang] ?: LANG_DIRECTIVE["fr"]!!

    /** Unknown languages silently become French, as the backend did. */
    fun clampLang(lang: String): String = if (LANG_DIRECTIVE.containsKey(lang)) lang else "fr"

    const val NO_SOURCE_NOTE =
        "{kt("(Aucun extrait de cours source trouvé — explique à partir de la carte seule.)")}"

    const val NO_PDF_TEXT_NOTE =
        "{kt("(Aucun texte n'a pu être extrait de ce PDF — réponds à partir de tes connaissances et dis-le explicitement.)")}"

    const val SOURCE_EXCERPTS_HEADER = "{kt("Extraits du cours source:")}"

    const val CRITIQUE_PREFIX =
        "{kt("L'explication précédente a été jugée insuffisante par l'utilisateur. Ce qui n'allait pas : ")}"

    const val CRITIQUE_SUFFIX =
        "{kt("Réécris une nouvelle explication qui corrige spécifiquement ce point, sans répéter la version précédente.")}"

    fun readingPageNote(page: Int): String =
        "{kt("L'étudiant lit la page ")}" + page +
            "{kt(" de ce cours. Les extraits ci-dessous sont centrés sur cette page ; sa question porte probablement sur ce qui s'y trouve.")}"
}}
'''

OUT.parent.mkdir(parents=True, exist_ok=True)
OUT.write_text(body, encoding="utf-8")
print(f"wrote {OUT}")
