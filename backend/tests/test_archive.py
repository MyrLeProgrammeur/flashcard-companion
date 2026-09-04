"""Archiving a subject: it leaves the home screen and every due queue, and it
stays in the stats. Same harness as test_deck_groups.py."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent))

from test_apkg_reader import build_fixture_apkg  # noqa: E402
from test_explain_feedback import _make_client  # noqa: E402

SUBJECT_A = "Statistical Inference"
SUBJECT_B = "Time Series"


def _setup(tmp_path, monkeypatch):
    """Two subjects, so archiving one still leaves something on the home screen."""
    main_module, client = _make_client(tmp_path, monkeypatch)
    apkg_dir = Path(main_module.app.state.cfg["paths"]["apkg_dir"])
    build_fixture_apkg(apkg_dir / "a.apkg")
    build_fixture_apkg(
        apkg_dir / "b.apkg", subject=SUBJECT_B, theme="ARMA", guid_prefix="ts"
    )
    return main_module, client


def _names(client):
    return [n["name"] for n in client.get("/api/tree").json()]


def _archived(client):
    return client.get("/api/archive").json()["subjects"]


def _set_archived(client, subject, archived):
    return client.put(
        f"/api/archive/subject/{subject}", json={"archived": archived}
    )


def test_nothing_is_archived_by_default(tmp_path, monkeypatch):
    _, client = _setup(tmp_path, monkeypatch)

    assert _archived(client) == []
    assert sorted(_names(client)) == [SUBJECT_A, SUBJECT_B]


def test_archiving_then_unarchiving_a_subject_round_trips(tmp_path, monkeypatch):
    _, client = _setup(tmp_path, monkeypatch)

    r = _set_archived(client, SUBJECT_A, True)
    assert r.status_code == 200
    assert r.json() == {"subject": SUBJECT_A, "archived": True}
    assert _archived(client) == [SUBJECT_A]
    assert _names(client) == [SUBJECT_B]

    r = _set_archived(client, SUBJECT_A, False)
    assert r.status_code == 200
    assert _archived(client) == []
    assert sorted(_names(client)) == [SUBJECT_A, SUBJECT_B]


def test_archiving_is_idempotent(tmp_path, monkeypatch):
    _, client = _setup(tmp_path, monkeypatch)

    _set_archived(client, SUBJECT_A, True)
    _set_archived(client, SUBJECT_A, True)
    assert _archived(client) == [SUBJECT_A]

    _set_archived(client, SUBJECT_B, False)  # never archived: a no-op, not an error
    assert _archived(client) == [SUBJECT_A]


def test_archiving_a_group_archives_all_its_subjects(tmp_path, monkeypatch):
    """The one-gesture case: file both subjects under "M1", archive the folder."""
    _, client = _setup(tmp_path, monkeypatch)
    for subject in (SUBJECT_A, SUBJECT_B):
        client.put(f"/api/deck-groups/subject/{subject}", json={"group": "M1"})

    r = client.put("/api/archive/group/M1", json={"archived": True})

    assert r.status_code == 200
    assert r.json() == {
        "group": "M1",
        "archived": True,
        "subjects": sorted([SUBJECT_A, SUBJECT_B]),
    }
    assert _archived(client) == sorted([SUBJECT_A, SUBJECT_B])
    # apply_groups drops a folder whose every child vanished: no empty shell.
    assert client.get("/api/tree").json() == []


def test_unarchiving_a_group_restores_the_folder_as_it_was(tmp_path, monkeypatch):
    main_module, client = _setup(tmp_path, monkeypatch)
    for subject in (SUBJECT_A, SUBJECT_B):
        client.put(f"/api/deck-groups/subject/{subject}", json={"group": "M1"})
    client.put("/api/archive/group/M1", json={"archived": True})

    # The mapping is untouched while archived — that is what makes the restore exact.
    assert main_module.app.state.store.get_deck_groups() == {
        SUBJECT_A: "M1",
        SUBJECT_B: "M1",
    }

    r = client.put("/api/archive/group/M1", json={"archived": False})

    assert r.status_code == 200
    assert _archived(client) == []
    roots = client.get("/api/tree").json()
    assert [n["name"] for n in roots] == ["M1"]
    folder = roots[0]
    assert folder["is_group"] is True
    assert sorted(c["name"] for c in folder["children"]) == sorted([SUBJECT_A, SUBJECT_B])


def test_archiving_an_unknown_group_is_a_404(tmp_path, monkeypatch):
    _, client = _setup(tmp_path, monkeypatch)

    assert client.put("/api/archive/group/Ghost", json={"archived": True}).status_code == 404


def test_archived_subject_leaves_the_tree_and_both_due_endpoints(tmp_path, monkeypatch):
    """The real reason to archive: no more review, no more daily notification."""
    _, client = _setup(tmp_path, monkeypatch)
    before = client.get("/api/due/count").json()["due"]
    a_due = len(client.get("/api/due", params={"path": SUBJECT_A}).json())
    assert before > 0 and a_due > 0

    _set_archived(client, SUBJECT_A, True)

    assert SUBJECT_A not in _names(client)
    assert client.get("/api/due/count").json()["due"] == before - a_due
    assert [c["subject"] for c in client.get("/api/due").json()] == [SUBJECT_B] * a_due
    # Even asked for by its exact path, an archived subject yields nothing.
    assert client.get("/api/due", params={"path": SUBJECT_A}).json() == []
    assert client.get("/api/due/count", params={"path": SUBJECT_A}).json() == {"due": 0}


def test_archived_subject_leaves_the_flat_deck_endpoints(tmp_path, monkeypatch):
    _, client = _setup(tmp_path, monkeypatch)

    _set_archived(client, SUBJECT_A, True)

    assert SUBJECT_A not in client.get("/api/decks").json()
    assert all(
        not s["path"].startswith(SUBJECT_A) for s in client.get("/api/subjects").json()
    )


def test_stats_keep_the_archived_subject(tmp_path, monkeypatch):
    """History is history: /api/stats/* must not be filtered by the archive."""
    _, client = _setup(tmp_path, monkeypatch)
    guid = client.get("/api/due", params={"path": SUBJECT_A}).json()[0]["guid"]
    client.post(f"/api/cards/{guid}/review", json={"quality": 5, "time_spent_ms": 1200})

    before = client.get("/api/stats/overview").json()
    assert before["total_reviews"] == 1

    _set_archived(client, SUBJECT_A, True)

    assert client.get("/api/stats/overview").json() == before
    assert [c["guid"] for c in client.get("/api/stats/cards").json()] == [guid]
    assert len(client.get("/api/stats/export").json()) == 1
