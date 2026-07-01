from fastapi import APIRouter, HTTPException, Request
from pydantic import BaseModel

router = APIRouter()


class SettingsBody(BaseModel):
    again_days: float | None = None
    hard_days: float | None = None
    good_days: float | None = None
    easy_days: float | None = None
    easy_bonus: float | None = None


def _validate(updates: dict) -> None:
    for key in ("again_days", "hard_days", "good_days", "easy_days"):
        if key in updates and updates[key] < 0:
            raise HTTPException(status_code=400, detail=f"{key} must be >= 0")
    if "easy_bonus" in updates and updates["easy_bonus"] < 1:
        raise HTTPException(status_code=400, detail="easy_bonus must be >= 1")


@router.get("/api/settings")
def get_settings(request: Request):
    return request.app.state.store.get_settings()


@router.put("/api/settings")
def put_settings(body: SettingsBody, request: Request):
    updates = {k: v for k, v in body.model_dump().items() if v is not None}
    _validate(updates)
    return request.app.state.store.save_settings(updates)
