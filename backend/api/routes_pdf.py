"""Course PDF listing + serving. Subject→PDF matching is delegated entirely
to `source_matcher.find_source_pdfs` (subject granularity only — no per-theme
nesting, since the matcher doesn't have that precision to offer)."""
from pathlib import Path

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import FileResponse

import source_matcher

router = APIRouter()


@router.get("/api/courses")
def list_courses(request: Request):
    apkg_dir = request.app.state.cfg["paths"]["apkg_dir"]
    pdf_dir = Path(request.app.state.cfg["paths"]["pdf_dir"])
    import apkg_reader

    cards = apkg_reader.read_all_cards(apkg_dir)
    subjects = sorted({card.subject for card in cards})

    result: dict[str, list[dict]] = {}
    for subject in subjects:
        matches = source_matcher.find_source_pdfs(subject, pdf_dir)
        result[subject] = [
            {
                "filename": Path(m).name,
                "rel_path": Path(m).relative_to(pdf_dir).as_posix(),
            }
            for m in matches
        ]
    return result


@router.get("/api/courses/file")
def get_course_file(path: str, request: Request):
    pdf_dir = Path(request.app.state.cfg["paths"]["pdf_dir"]).resolve()
    resolved = (pdf_dir / path).resolve()

    if not resolved.is_relative_to(pdf_dir):
        raise HTTPException(status_code=404, detail="File not found")
    if resolved.suffix.lower() != ".pdf":
        raise HTTPException(status_code=404, detail="File not found")
    if not resolved.is_file():
        raise HTTPException(status_code=404, detail="File not found")

    return FileResponse(resolved, media_type="application/pdf")
