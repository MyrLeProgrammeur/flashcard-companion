import json
import sqlite3
import sys
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))

from apkg_reader import read_cards  # noqa: E402

MODEL_ID = 1699000000000

SCHEMA_SQL = """
CREATE TABLE col (
    id INTEGER PRIMARY KEY, crt INTEGER NOT NULL, mod INTEGER NOT NULL,
    scm INTEGER NOT NULL, ver INTEGER NOT NULL, dty INTEGER NOT NULL,
    usn INTEGER NOT NULL, ls INTEGER NOT NULL, conf TEXT NOT NULL,
    models TEXT NOT NULL, decks TEXT NOT NULL, dconf TEXT NOT NULL,
    tags TEXT NOT NULL
);
CREATE TABLE notes (
    id INTEGER PRIMARY KEY, guid TEXT NOT NULL, mid INTEGER NOT NULL,
    mod INTEGER NOT NULL, usn INTEGER NOT NULL, tags TEXT NOT NULL,
    flds TEXT NOT NULL, sfld TEXT NOT NULL, csum INTEGER NOT NULL,
    flags INTEGER NOT NULL, data TEXT NOT NULL
);
CREATE TABLE cards (
    id INTEGER PRIMARY KEY, nid INTEGER NOT NULL, did INTEGER NOT NULL,
    ord INTEGER NOT NULL, mod INTEGER NOT NULL, usn INTEGER NOT NULL,
    type INTEGER NOT NULL, queue INTEGER NOT NULL, due INTEGER NOT NULL,
    ivl INTEGER NOT NULL, factor INTEGER NOT NULL, reps INTEGER NOT NULL,
    lapses INTEGER NOT NULL, left INTEGER NOT NULL, odue INTEGER NOT NULL,
    odid INTEGER NOT NULL, flags INTEGER NOT NULL, data TEXT NOT NULL
);
"""


def _model_json():
    return {
        str(MODEL_ID): {
            "id": MODEL_ID,
            "flds": [
                {"name": "Front", "ord": 0},
                {"name": "Back", "ord": 1},
                {"name": "Note", "ord": 2},
            ],
        }
    }


def build_fixture_apkg(apkg_path: Path):
    """Hand-build a minimal .apkg matching flashcard-pipeline's db_writer.py schema."""
    tmp_db = apkg_path.with_suffix(".anki2.tmp")
    conn = sqlite3.connect(tmp_db)
    conn.executescript(SCHEMA_SQL)

    decks = {
        "1": {"id": 1, "name": "Default"},
        "2": {"id": 2, "name": "Statistical Inference"},
        "3": {"id": 3, "name": "Statistical Inference::Confidence Intervals"},
    }
    conn.execute(
        "INSERT INTO col VALUES (1,0,0,0,11,0,-1,0,'{}',?,?,'{}','')",
        (json.dumps(_model_json()), json.dumps(decks)),
    )

    notes = [
        (1, "guid-a", MODEL_ID, "Q1\x1fA1\x1fnote1", "Q1"),
        (2, "guid-b", MODEL_ID, "Q2\x1fA2\x1f", "Q2"),
    ]
    for nid, guid, mid, flds, sfld in notes:
        conn.execute(
            "INSERT INTO notes VALUES (?,?,?,0,-1,'',?,?,0,0,'')",
            (nid, guid, mid, flds, sfld),
        )

    cards = [
        (1, 1, 3, 0, 0, -1, 0, 0, 1, 0, 2500, 0, 0, 0, 0, 0, 0, ""),
        (2, 2, 3, 0, 0, -1, 0, 0, 2, 0, 2500, 0, 0, 0, 0, 0, 0, ""),
    ]
    for card in cards:
        conn.execute(
            "INSERT INTO cards VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            card,
        )

    conn.commit()
    conn.close()

    with zipfile.ZipFile(apkg_path, "w") as zf:
        zf.write(tmp_db, "collection.anki2")
        zf.writestr("media", "{}")
    tmp_db.unlink()


def test_read_cards_extracts_fields_and_deck_hierarchy(tmp_path):
    apkg_path = tmp_path / "Statistical_Inference.apkg"
    build_fixture_apkg(apkg_path)

    cards = read_cards(apkg_path)

    assert len(cards) == 2
    by_guid = {c.guid: c for c in cards}

    assert by_guid["guid-a"].front == "Q1"
    assert by_guid["guid-a"].back == "A1"
    assert by_guid["guid-a"].note == "note1"
    assert by_guid["guid-a"].subject == "Statistical Inference"
    assert by_guid["guid-a"].theme == "Confidence Intervals"

    assert by_guid["guid-b"].front == "Q2"
    assert by_guid["guid-b"].note == ""
