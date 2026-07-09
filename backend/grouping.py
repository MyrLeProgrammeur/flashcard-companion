"""Display-only folders over the two trees the app shows.

The home screen's tree comes from Anki deck names, the Courses screen's from
the real directory layout under `pdf_dir`. Both are read-only: nothing here
renames a deck or touches a folder on disk, so GUIDs — and with them the whole
SRS history — are unaffected. Clearing `deck_group` restores the flat view.

The two trees do not always spell a subject the same way: the pipeline turns
underscores into spaces for the deck name but the source folder keeps them
(`Foundations of ML` vs `Foundations_of_ML`). Membership is therefore stored
under the deck-tree name — the one the user sees when they file the subject —
and the Courses tree resolves through `normalise`.
"""

GROUP_PREFIX = "@group:"


def normalise(name: str) -> str:
    return name.replace("_", " ").strip().casefold()


def group_path(group_name: str) -> str:
    """Path handed to the front for a folder node. Deliberately not a legal
    Anki deck path, so it can never collide with a real deck prefix."""
    return f"{GROUP_PREFIX}{group_name}"


def group_name_from_path(path: str) -> str | None:
    return path[len(GROUP_PREFIX):] if path.startswith(GROUP_PREFIX) else None


def subjects_in_group(groups: dict[str, str], group_name: str) -> set[str]:
    return {subject for subject, g in groups.items() if g == group_name}


def apply_groups(roots: list[dict], groups: dict[str, str], make_group) -> list[dict]:
    """Nest the root entries whose name is filed under a folder, leaving the
    rest where they are. Ungrouped subjects stay at the root on purpose: a
    freshly synced `.apkg` shows up next to the folders, unsorted, and the
    user files it when they feel like it.

    `make_group(name, children)` builds the folder node, since the two trees
    have different node shapes. Folders and loose subjects are sorted together
    by name, so a folder is not visually privileged over a subject.
    """
    by_normalised = {normalise(subject): g for subject, g in groups.items()}

    grouped: dict[str, list[dict]] = {}
    out: list[dict] = []
    for entry in roots:
        group_name = by_normalised.get(normalise(entry["name"]))
        if group_name is None:
            out.append(entry)
        else:
            grouped.setdefault(group_name, []).append(entry)

    # A folder whose every subject has vanished (deck deleted, PDF folder
    # removed) simply does not appear — no empty shell, no error.
    out.extend(make_group(name, children) for name, children in grouped.items())
    out.sort(key=lambda e: e["name"].casefold())
    return out
