#!/usr/bin/env bash
# Real pinch drag on a size label (QA q1-2): no debug drag, the emulator's own gaze + pinch through emu.sh (PostMessage).
# Layout: `state S04` (one T1 specimen 1.0 m, right 25 deg), camera turned 45 deg right after `unfreeze`; the label then
# sits at framebuffer (459, 1491) in emu.sh's 2160-wide frame (screenshots are 2560 wide: x 0.84375).
# Shots: real-drag-0 (before), real-drag-1 (held, 7 s pause halfway, shot after 5 s), real-drag-2 (released), real-drag-3 (3 s later).
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEV=${DEV:-emulator-5560}
PKG=io.embodit.panelfit
H="bash $ROOT/tools/harness/emu.sh"
export SHOTS="${SHOTS:-$ROOT/../spatial-app-factory/apps/panel-fit/build/real-drag}"
mkdir -p "$SHOTS"
cmd() { MSYS_NO_PATHCONV=1 adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd '$1'" >/dev/null 2>&1; sleep "${2:-1.5}"; }
X0=${X0:-459}; Y0=${Y0:-1491}; DX=${DX:-300}
centers() { cmd panels 1; adb -s $DEV logcat -d -s PanelFit:V | grep "PANELS id" | tail -1 | grep -o "center=([^)]*)"; }

cmd deleteAll; cmd "state S04" 3; cmd unfreeze
bash "$ROOT/tools/turn.sh" 45 | tail -1
$H fast on >/dev/null; sleep 3
$H shot real-drag-0 >/dev/null
echo "before   $(centers)"
adb -s $DEV logcat -c
$H drag $X0 $Y0 $((X0 + DX)) $Y0 -Steps 20 -StepMs 60 -HoldMs 400 -PauseAtStep 10 -PauseMs 7000 -EndHoldMs 600 >/dev/null &
sleep 5; $H shot real-drag-1 >/dev/null
wait
$H shot real-drag-2 >/dev/null
echo "released $(centers)"
sleep 3; $H shot real-drag-3 >/dev/null
echo "3 s on   $(centers)"
adb -s $DEV logcat -d -s PanelFit:V | grep -E "HIT test|DRAG" | cut -c7-200
