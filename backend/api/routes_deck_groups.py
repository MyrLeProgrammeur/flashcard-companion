"""Create/rename/dissolve the display-only folders of the home screen, and
file subjects into them. Purely presentational: see `grouping.py`. Nothing
here reads or writes a deck, a card or a PDF.
"""
from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

from grouping import GROUP_PREFIX

router = APIRouter()


class SubjectGroup(BaseModel):
    group: str | None = None  # None = back to the root


class GroupRename(BaseModel):
    name: str


def _clean_name(name: str) -> str:
    name = name.strip()
    if not name:
        raise HTTPException(status_code=400, detail="folder name must not be empty")
    if "::" in name or name.startswith(GROUP_PREFIX):
        raise HTTPException(status_code=400, detail="illegal folder name")
    return name


@router.get("/api/deck-groups")
def list_deck_groups(request: Request):
    """subject -> folder name. Subjects absent from this map sit at the root."""
    return request.app.state.store.get_deck_groups()


@router.put("/api/deck-groups/subject/{subject}")
def set_subject_group(subject: str, body: SubjectGroup, request: Request):
    group = _clean_name(body.group) if body.group is not None else None
    request.app.state.store.set_deck_group(subject, group)
    return {"subject": subject, "group": group}


@router.patch("/api/deck-groups/{group_name}")
def rename_deck_group(group_name: str, body: GroupRename, request: Request):
    new_name = _clean_name(body.name)
    moved = request.app.state.store.rename_deck_group(group_name, new_name)
    if not moved:
        raise HTTPException(status_code=404, detail="folder not found")
    return {"name": new_name, "subjects": moved}


@router.delete("/api/deck-groups/{group_name}")
def delete_deck_group(group_name: str, request: Request):
    """Dissolve the folder. Its subjects return to the root; no card is touched."""
    freed = request.app.state.store.delete_deck_group(group_name)
    if not freed:
        raise HTTPException(status_code=404, detail="folder not found")
    return {"dissolved": group_name, "subjects": freed}
