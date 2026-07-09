import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent.parent))
sys.path.insert(0, str(Path(__file__).parent))

from test_apkg_reader import build_fixture_apkg  # noqa: E402
from test_explain_feedback import _make_client  # noqa: E402


def _setup(tmp_path, monkeypatch):
    main_module, client = _make_client(tmp_path, monkeypatch)
    build_fixture_apkg(Path(main_module.app.state.cfg["paths"]["apkg_dir"]) / "f.apkg")
    return main_module, client


def _tree(client):
    return client.get("/api/tree").json()


def _names(nodes):
    return [n["name"] for n in nodes]


def _subjects(client):
    return _names(_tree(client))


def test_no_groups_means_the_tree_is_unchanged(tmp_path, monkeypatch):
    _, client = _setup(tmp_path, monkeypatch)

    roots = _tree(client)

    assert roots
    assert all(not n.get("is_group") for n in roots)


def test_filing_a_subject_nests_it_under_a_folder(tmp_path, monkeypatch):
    _, client = _setup(tmp_path, monkeypatch)
    subject = _subjects(client)[0]

    r = client.put(f"/api/deck-groups/subject/{subject}", json={"group": "Master 1"})
    assert r.status_code == 200

    roots = _tree(client)
    assert "Master 1" in _names(roots)
    assert subject not in _names(roots)  # it moved, it is not duplicated

    folder = next(n for n in roots if n["name"] == "Master 1")
    assert folder["is_group"] is True
    assert folder["path"] == "@group:Master 1"
    assert _names(folder["children"]) == [subject]


def test_folder_counts_aggregate_its_subjects(tmp_path, monkeypatch):
    _, client = _setup(tmp_path, monkeypatch)
    before = {n["name"]: n for n in _tree(client)}
    subject = next(iter(before))

    client.put(f"/api/deck-groups/subject/{subject}", json={"group": "Master 1"})

    folder = next(n for n in _tree(client) if n["name"] == "Master 1")
    assert folder["card_count"] == before[subject]["card_count"]
    assert folder["due_count"] == before[subject]["due_count"]


def test_reviewing_a_folder_scopes_to_its_subjects(tmp_path, monkeypatch):
    """`@group:` is not a deck prefix — /api/due must resolve it or return nothing."""
    _, client = _setup(tmp_path, monkeypatch)
    subject = _subjects(client)[0]
    client.put(f"/api/deck-groups/subject/{subject}", json={"group": "Master 1"})

    in_folder = client.get("/api/due", params={"path": "@group:Master 1"}).json()
    in_subject = client.get("/api/due", params={"path": subject}).json()

    assert in_folder, "a folder holding a due subject must yield its due cards"
    assert [c["guid"] for c in in_folder] == [c["guid"] for c in in_subject]


def test_reviewing_an_empty_folder_yields_nothing(tmp_path, monkeypatch):
    _, client = _setup(tmp_path, monkeypatch)

    assert client.get("/api/due", params={"path": "@group:Ghost"}).json() == []


def test_dissolving_a_folder_returns_subjects_to_the_root(tmp_path, monkeypatch):
    _, client = _setup(tmp_path, monkeypatch)
    subject = _subjects(client)[0]
    client.put(f"/api/deck-groups/subject/{subject}", json={"group": "Master 1"})

    r = client.delete("/api/deck-groups/Master 1")
    assert r.status_code == 200

    roots = _names(_tree(client))
    assert subject in roots
    assert "Master 1" not in roots


def test_a_subject_lives_in_at_most_one_folder(tmp_path, monkeypatch):
    main_module, client = _setup(tmp_path, monkeypatch)
    subject = _subjects(client)[0]

    client.put(f"/api/deck-groups/subject/{subject}", json={"group": "Master 1"})
    client.put(f"/api/deck-groups/subject/{subject}", json={"group": "Master 2"})

    assert main_module.app.state.store.get_deck_groups() == {subject: "Master 2"}
    assert "Master 1" not in _names(_tree(client))


def test_unfiling_sends_a_subject_back_to_the_root(tmp_path, monkeypatch):
    _, client = _setup(tmp_path, monkeypatch)
    subject = _subjects(client)[0]
    client.put(f"/api/deck-groups/subject/{subject}", json={"group": "Master 1"})

    client.put(f"/api/deck-groups/subject/{subject}", json={"group": None})

    assert subject in _names(_tree(client))


def test_rename_and_bad_names(tmp_path, monkeypatch):
    _, client = _setup(tmp_path, monkeypatch)
    subject = _subjects(client)[0]
    client.put(f"/api/deck-groups/subject/{subject}", json={"group": "Master 1"})

    assert client.patch("/api/deck-groups/Master 1", json={"name": "M1"}).status_code == 200
    assert "M1" in _names(_tree(client))

    assert client.patch("/api/deck-groups/M1", json={"name": "  "}).status_code == 400
    assert client.patch("/api/deck-groups/M1", json={"name": "a::b"}).status_code == 400
    assert client.patch("/api/deck-groups/nope", json={"name": "x"}).status_code == 404
    assert client.delete("/api/deck-groups/nope").status_code == 404


def test_group_membership_survives_the_underscore_mismatch(tmp_path, monkeypatch):
    """The deck is `Foundations of ML`, its course folder `Foundations_of_ML`.
    One filing must group both trees."""
    main_module, client = _setup(tmp_path, monkeypatch)
    store = main_module.app.state.store
    store.set_deck_group("Foundations of ML", "Master 1")

    pdf_dir = Path(main_module.app.state.cfg["paths"]["pdf_dir"])
    (pdf_dir / "Foundations_of_ML").mkdir()
    (pdf_dir / "Foundations_of_ML" / "chap1.pdf").write_bytes(b"%PDF-1.4\n%%EOF\n")
    (pdf_dir / "Time Series").mkdir()
    (pdf_dir / "Time Series" / "chap1.pdf").write_bytes(b"%PDF-1.4\n%%EOF\n")

    roots = client.get("/api/courses/tree").json()

    assert "Master 1" in _names(roots)
    assert "Time Series" in _names(roots)      # ungrouped, stays at the root
    folder = next(n for n in roots if n["name"] == "Master 1")
    assert folder["is_group"] is True
    assert _names(folder["children"]) == ["Foundations_of_ML"]
