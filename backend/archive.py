"""Archived subjects — the filter that decides what the app still *offers*.

Archiving is not deletion and not a folder: it hides a subject from the home
tree and drops it from every due queue, so a finished year stops generating
review notifications, while `review_log` (and therefore every /api/stats/*
aggregate) keeps every card it ever held. Unarchiving restores the subject
exactly where it was, folder included — `deck_group` is never touched.

Membership is stored under the deck-tree subject name and matched through
`grouping.normalise`, for the same reason folders are: the two trees do not
always spell a subject the same way (`Foundations of ML` vs `Foundations_of_ML`).
"""
from grouping import normalise


def archived_deck_filter(store):
    """Predicate over a full Anki deck name (`subject::theme::…`): True when
    its top-level subject is archived. One place, so the tree and both due
    endpoints can never disagree on what "archived" hides."""
    archived = {normalise(s) for s in store.get_archived_subjects()}

    def is_archived(deck_name: str) -> bool:
        return normalise(deck_name.split("::")[0]) in archived

    return is_archived
