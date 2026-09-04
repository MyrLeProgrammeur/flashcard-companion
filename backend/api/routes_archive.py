"""Archive / unarchive whole subjects — see `archive.py` for what archiving
means. Nothing here writes a deck, a card or the SRS history: archiving only
adds a row to `archived_subject`, and unarchiving removes it.

The group endpoint is the ergonomic one ("archive M1 in one gesture"): it
resolves the display folder to its member subjects and archives each of them,
leaving the `deck_group` mapping intact so unarchiving restores the folder as
it was.
"""
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from grouping import subjects_in_group

router = APIRouter()


class ArchiveFlag(BaseModel):
    archived: bool


@router.get("/api/archive")
def list_archived(request: Request):
    """The archived subjects, by deck-tree name."""
    return {"subjects": request.app.state.store.get_archived_subjects()}


@router.put("/api/archive/subject/{subject}")
def set_subject_archived(subject: str, body: ArchiveFlag, request: Request):
    request.app.state.store.set_subject_archived(subject, body.archived)
    return {"subject": subject, "archived": body.archived}


@router.put("/api/archive/group/{group}")
def set_group_archived(group: str, body: ArchiveFlag, request: Request):
    """Apply to every subject filed under the display folder `group`. 404 when
    no subject is filed there — an empty folder does not exist as such."""
    store = request.app.state.store
    members = sorted(subjects_in_group(store.get_deck_groups(), group))
    if not members:
        raise HTTPException(status_code=404, detail="folder not found")
    for subject in members:
        store.set_subject_archived(subject, body.archived)
    return {"group": group, "archived": body.archived, "subjects": members}
