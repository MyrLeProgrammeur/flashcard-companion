"""
Read-only parsing of .apkg files produced by flashcard-pipeline.

Never opens the zip member in place and never writes back into the .apkg —
the file is extracted to a temp copy first, so there is zero risk of ever
touching the Syncthing-synced file (would otherwise risk a sync conflict).
"""
import json
import sqlite3
import tempfile
import zipfile
from dataclasses import dataclass
from pathlib import Path


@dataclass
class CardRecord:
    guid: str
    deck_name: str
    subject: str
    theme: str
    front: str
    back: str
    note: str


@dataclass
class DeckNode:
    name: str
    subject: str
    theme: str | None
    card_count: int


def list_apkg_files(apkg_dir: str | Path) -> list[Path]:
    apkg_dir = Path(apkg_dir)
    if not apkg_dir.exists():
        return []
    return sorted(apkg_dir.glob("*.apkg"))


def _extract_readonly(apkg_path: Path) -> str:
    """Copy collection.anki2 out of the zip to a temp file, opened read-only."""
    with tempfile.NamedTemporaryFile(suffix=".anki2", delete=False) as tmp:
        with zipfile.ZipFile(apkg_path, "r") as zf:
            tmp.write(zf.read("collection.anki2"))
        return tmp.name


def _fields_order(models_json: dict, mid: int) -> list[str]:
    model = models_json[str(mid)]
    return [f["name"] for f in sorted(model["flds"], key=lambda f: f["ord"])]


def read_cards(apkg_path: Path) -> list[CardRecord]:
    tmp_path = _extract_readonly(apkg_path)
    try:
        conn = sqlite3.connect(f"file:{tmp_path}?mode=ro", uri=True)
        try:
            col_row = conn.execute("SELECT models, decks FROM col").fetchone()
            models_json = json.loads(col_row[0])
            decks_json = json.loads(col_row[1])

            rows = conn.execute(
                """
                SELECT n.guid, n.mid, n.flds, c.did
                FROM notes n
                JOIN cards c ON c.nid = n.id
                """
            ).fetchall()

            records = []
            for guid, mid, flds, did in rows:
                order = _fields_order(models_json, mid)
                values = flds.split("\x1f")
                field_map = dict(zip(order, values))

                deck_name = decks_json.get(str(did), {}).get("name", "")
                parts = deck_name.split("::")
                subject = parts[0] if parts else ""
                theme = parts[1] if len(parts) > 1 else ""

                records.append(
                    CardRecord(
                        guid=guid,
                        deck_name=deck_name,
                        subject=subject,
                        theme=theme,
                        front=field_map.get("Front", ""),
                        back=field_map.get("Back", ""),
                        note=field_map.get("Note", ""),
                    )
                )
            return records
        finally:
            conn.close()
    finally:
        Path(tmp_path).unlink(missing_ok=True)


def read_all_cards(apkg_dir: str | Path) -> list[CardRecord]:
    cards: list[CardRecord] = []
    for apkg_path in list_apkg_files(apkg_dir):
        cards.extend(read_cards(apkg_path))
    return cards


def deck_tree(apkg_dir: str | Path) -> dict[str, dict[str, int]]:
    """subject -> theme -> card count"""
    tree: dict[str, dict[str, int]] = {}
    for card in read_all_cards(apkg_dir):
        tree.setdefault(card.subject, {}).setdefault(card.theme, 0)
        tree[card.subject][card.theme] += 1
    return tree
