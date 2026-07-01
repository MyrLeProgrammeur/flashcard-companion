#!/data/data/com.termux/files/usr/bin/bash
# Termux:Boot script — scaffolded from day one but NOT required for v1.
# Copy to ~/.termux/boot/ once Termux:Boot is installed and confirmed working
# (plan §3.3 step 3, deferred). Without this, the manual `start.sh` path
# (or the app's RUN_COMMAND best-effort trigger) is the guaranteed way to
# bring the backend up after a reboot.
bash ~/flashcard-companion/backend/termux/start.sh &
