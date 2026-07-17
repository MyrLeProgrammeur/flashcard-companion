#!/data/data/com.termux/files/usr/bin/bash
# Repairs .venv after a Termux Python minor-version bump. No-op (~0.3s) when
# the venv is healthy, so it is safe to call on every boot and every start.
#
# Why this exists (2026-07-16 outage): `pkg upgrade` swapped python3.13 for
# python3.14 and deleted the old binary. `.venv/bin/python` is a symlink to
# $PREFIX/bin/python — NOT a versioned path — so it silently followed to the
# new interpreter while every package stayed in .venv/lib/python3.13/. The
# venv stopped being recognised as a venv at all (its site-packages left
# sys.path entirely) and uvicorn died at every boot with ModuleNotFoundError.
#
# Renaming lib/python3.13 -> lib/python3.14 is NOT a fix: pydantic_core, jiter
# and _yaml ship compiled .so files tagged for the old ABI (cp313 vs cp314).
# The venv genuinely has to be rebuilt, which recompiles those three from
# source via Rust (minutes of CPU — the pip wheel cache does not help across a
# version bump, since its keys carry the ABI tag).
#
# Contract: exit 0 = .venv is usable NOW. exit 1 = it is not, and the caller
# must not start the backend. The working .venv is never removed until a
# replacement has been built AND verified, so a failed repair is a no-op.
#
# HTTP checks go through python urllib, never curl — curl is broken on this
# Termux (openssl/ngtcp2 skew, see notify-due.sh's header).

set -uo pipefail

# Resolved from this script's own path, not hardcoded: start.sh invokes it by
# relative path and the boot script by absolute one, and both must agree.
BACKEND="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VENV="$BACKEND/.venv"
STAGING="$BACKEND/.venv.new"
LOG="$HOME/venv-repair.log"
LOCK="$HOME/.venv-repair.lock"

# The runtime imports the backend actually needs. Deliberately includes the
# three compiled packages (pydantic via pydantic_core, openai via jiter, yaml
# via _yaml) — they are what an ABI mismatch breaks first.
IMPORT_CHECK='import uvicorn, fastapi, pydantic, openai, yaml, pypdf, dotenv'

log() { echo "$(date -Iseconds) ensure-venv: $*" >>"$LOG"; }

notify() {
  # id keeps repair notifications replacing each other instead of stacking.
  termux-notification \
    --id flashcard-venv \
    --title "Flashcards — $1" \
    --content "$2" \
    2>>"$LOG" || log "termux-notification failed: $1 / $2"
}

venv_ok() {
  [ -x "$VENV/bin/python" ] || return 1
  "$VENV/bin/python" -c "$IMPORT_CHECK" >/dev/null 2>&1
}

have_network() {
  python3 - <<'PYEOF' >/dev/null 2>&1
import sys, urllib.request
try:
    urllib.request.urlopen("https://pypi.org/simple/", timeout=8)
except Exception:
    sys.exit(1)
PYEOF
}

# ---------------------------------------------------------------- fast path

if venv_ok; then
  exit 0
fi

# ------------------------------------------------------------------ repair

# Serialise against a concurrent repair (boot script + a manual start.sh can
# race). mkdir is atomic; a lock older than 40min is assumed dead and stolen,
# since the longest legitimate rebuild observed is ~10min.
if ! mkdir "$LOCK" 2>/dev/null; then
  if [ -n "$(find "$LOCK" -maxdepth 0 -mmin +40 2>/dev/null)" ]; then
    log "stealing stale lock"
    rmdir "$LOCK" 2>/dev/null
    mkdir "$LOCK" 2>/dev/null || { log "lock contended, giving up"; exit 1; }
  else
    log "another repair holds the lock — not starting a second one"
    exit 1
  fi
fi
trap 'rmdir "$LOCK" 2>/dev/null' EXIT

sys_ver="$(python3 -c 'import sys; print("%d.%d" % sys.version_info[:2])' 2>/dev/null)"
log "venv unusable (system python is ${sys_ver:-unknown}) — rebuilding"
notify "réparation en cours" "Python ${sys_ver:-?} : reconstruction de l'environnement, quelques minutes."

# pip needs HTTPS to PyPI and wifi is often not up yet this early after boot.
# ~5min of patience, then give up rather than destroy anything.
for _ in $(seq 1 20); do
  have_network && break
  sleep 15
done

if ! have_network; then
  log "no network — aborting, existing .venv left untouched"
  notify "réparation reportée" "Pas de réseau. Relance : backend/termux/ensure-venv.sh"
  exit 1
fi

rm -rf "$STAGING"

# Build into staging and verify there. $VENV is not touched until this whole
# block has succeeded — that is the entire safety property of this script.
if ! python3 -m venv "$STAGING" >>"$LOG" 2>&1; then
  log "venv creation failed"
  notify "réparation échouée" "Création du venv impossible. Voir $LOG"
  rm -rf "$STAGING"
  exit 1
fi

"$STAGING/bin/pip" install --upgrade pip -q >>"$LOG" 2>&1 || true

if ! "$STAGING/bin/pip" install -r "$BACKEND/requirements.txt" >>"$LOG" 2>&1; then
  log "pip install failed"
  notify "réparation échouée" "pip install a échoué. Voir $LOG"
  rm -rf "$STAGING"
  exit 1
fi

if ! "$STAGING/bin/python" -c "$IMPORT_CHECK" >>"$LOG" 2>&1; then
  log "staging venv installed but imports fail — not promoting it"
  notify "réparation échouée" "Le nouvel environnement ne s'importe pas. Voir $LOG"
  rm -rf "$STAGING"
  exit 1
fi

# Promote. Keep exactly one previous venv for rollback; older ones are dead
# weight (~55MB each) and would otherwise pile up one per Python bump.
rm -rf "$BACKEND"/.venv.broken.* 2>/dev/null
if [ -e "$VENV" ]; then
  mv "$VENV" "$BACKEND/.venv.broken.$(date +%Y%m%d-%H%M%S)" || {
    log "could not move the old venv aside"
    notify "réparation échouée" "Impossible de déplacer l'ancien venv. Voir $LOG"
    exit 1
  }
fi
mv "$STAGING" "$VENV" || { log "could not promote staging venv"; exit 1; }

# pip baked the staging path ($STAGING/bin/python) into every console-script
# shebang under bin/ — uvicorn, pip, fastapi, … After the move those point at
# a path that no longer exists ("bad interpreter"), so .venv/bin/uvicorn is
# dead even though imports work. Rewrite them to the final path. Guards:
#   [ -L ] skips bin/python* (symlinks to the system interpreter — sed -i
#          would follow the link and rewrite the real binary), and
#   grep -I  edits only text wrappers that actually mention the staging path,
#          never a binary. The verify step below reruns through .venv/bin/uvicorn
#          precisely so a future shebang regression can't pass unnoticed again.
for f in "$VENV"/bin/*; do
  [ -L "$f" ] && continue
  [ -f "$f" ] || continue
  grep -Iq -- "$STAGING" "$f" 2>/dev/null || continue
  sed -i "s|$STAGING|$VENV|g" "$f"
done

if ! "$VENV/bin/uvicorn" --version >/dev/null 2>&1; then
  log "promoted venv but .venv/bin/uvicorn is not runnable — check shebangs in $LOG"
  notify "réparation incomplète" "uvicorn ne se lance pas après reconstruction. Voir $LOG"
  exit 1
fi

log "rebuilt for python ${sys_ver:-unknown}"
notify "réparation réussie" "Environnement reconstruit pour Python ${sys_ver:-?}."
exit 0
