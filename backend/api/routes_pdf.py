"""Course PDF listing + serving. Subject→PDF matching is delegated entirely
to `source_matcher.find_source_pdfs` (subject granularity only — no per-theme
nesting, since the matcher doesn't have that precision to offer)."""
from pathlib import Path

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import FileResponse

import source_matcher
from grouping import apply_groups, group_path

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
        matches = source_matcher.find_source_pdfs([subject], pdf_dir)
        result[subject] = [
            {
                "filename": Path(m).name,
                "rel_path": Path(m).relative_to(pdf_dir).as_posix(),
            }
            for m in matches
        ]
    return result


@router.get("/api/courses/tree")
def courses_tree(request: Request):
    """Recursive directory tree under `pdf_dir`, mirroring the real
    `Cours/<Matière>/<sub-folder?>/file.pdf` layout — independent of
    `source_matcher`'s subject-level matching in `/api/courses`. Only `.pdf`
    files are listed; dotfiles/dirs (Syncthing markers like `.stfolder`,
    `.stfolder.removed-*`) are skipped. Empty folders (no PDF anywhere in
    their subtree) are dropped. Every node carries `rel_path` (posix,
    relative to `pdf_dir`) so a leaf's `rel_path` can be handed straight to
    `/api/courses/file?path=`."""
    pdf_dir = Path(request.app.state.cfg["paths"]["pdf_dir"])

    def walk(dir_path: Path) -> list[dict]:
        if not dir_path.is_dir():
            return []
        out = []
        for entry in sorted(dir_path.iterdir(), key=lambda p: p.name.lower()):
            if entry.name.startswith("."):
                continue
            if entry.is_dir():
                children = walk(entry)
                if not children:
                    continue
                out.append(
                    {
                        "name": entry.name,
                        "is_file": False,
                        "rel_path": entry.relative_to(pdf_dir).as_posix(),
                        "children": children,
                    }
                )
            elif entry.is_file() and entry.suffix.lower() == ".pdf":
                out.append(
                    {
                        "name": entry.name,
                        "is_file": True,
                        "rel_path": entry.relative_to(pdf_dir).as_posix(),
                        "children": [],
                    }
                )
        return out

    def make_group(name: str, children: list) -> dict:
        # `rel_path` is virtual here; `/api/courses/file` rejects it, which is
        # correct — a folder is not a document.
        return {
            "name": name,
            "is_file": False,
            "is_group": True,
            "rel_path": group_path(name),
            "children": sorted(children, key=lambda c: c["name"].casefold()),
        }

    store = request.app.state.store
    return apply_groups(walk(pdf_dir), store.get_deck_groups(), make_group)


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
