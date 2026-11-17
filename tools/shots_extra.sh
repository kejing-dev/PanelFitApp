#!/usr/bin/env bash
# The design states shots_all.sh cannot take from the default view (QA review of build 1): S00 (first 2 s of a start),
# S05 (stick held), S06 drag near a frame + snap, S08 with T1 at 0.9 m, S10 one overlay at a time, S14 (head inside a
# specimen), S15 with two pictures, S18 (back from HOME). Run after tools/relaunch.sh. Overlays are switched off first:
# they are saved with the layout, so earlier runs leave them on.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEV=${DEV:-emulator-5560}
PKG=io.embodit.panelfit
export SH="${SH:-$ROOT/../spatial-app-factory/apps/panel-fit/build/shots}"
Q="bash $ROOT/tools/qa_shot.sh"
H="bash $ROOT/tools/harness/emu.sh"
cmd() { MSYS_NO_PATHCONV=1 adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd '$1'" >/dev/null 2>&1; sleep "${2:-1}"; }
cap() { mkdir -p "$SH/emu"; before=$(ls -1 "$SH/emu" | wc -l); adb -s $DEV emu screenrecord screenshot "$(cygpath -w "$SH/emu")" >/dev/null 2>&1
  for i in $(seq 1 12); do sleep 0.5; [ "$(ls -1 "$SH/emu" | wc -l)" -gt "$before" ] && break; done; sleep 0.5
  newest=$(ls -1t "$SH/emu"/*.png 2>/dev/null | head -1); [ -n "$newest" ] && mv -f "$newest" "$SH/$1.png" && echo "OK $SH/$1.png" || echo "capture FAILED $1"; }
off() { cmd "overlay reach 0"; cmd "overlay neck 0"; cmd "overlay ruler 0"; }

off
# S10: one overlay at a time (frames are a setting, on by default)
cmd "deleteAll"; cmd "state S04"
$Q - S10-reach "overlay reach 1"
$Q - S10-neck "overlay reach 0" "overlay neck 1"
$Q - S10-frames "overlay neck 0"
# S05: the stick is held for 12 s, the shot lands inside it
$Q - S05-held "state S04" "stick --ef x 0 --ef y 0.6 --ef dur 12"
sleep 6
# S15 with two pictures (copied from the factory's own images)
for f in "$ROOT/../spatial-app-factory/apps/panel-fit/icon.png" "$ROOT/../spatial-app-factory/apps/panel-fit/build/icons-contact.png"; do
  MSYS_NO_PATHCONV=1 adb -s $DEV push "$(cygpath -m "$f")" /sdcard/Android/data/$PKG/files/import/ >/dev/null 2>&1
done
MSYS_NO_PATHCONV=1 adb -s $DEV shell rm -f /sdcard/Android/data/$PKG/files/import/broken.png
SETTLE=14 $Q - S15-two "state S15"   # the import list is a new glass page: give the half-drawn frame time to heal

# specimen shots: look right, where the specimens are
bash "$ROOT/tools/turn.sh" 30
cmd "deleteAll"
$Q - S08-T1-090 "state S08" "place --ef dist 0.9 --ef yaw 25 --ef elev -10" "cardPage 0"
$Q - S10-ruler "state S04" "overlay ruler 1"
cmd "overlay ruler 0"
# qa_shot sends its commands back to back: the place has to land before the drag starts
cmd "place --ef dist 1.0 --ef yaw 34 --ef elev -10" 2
$Q - S06-near-frame "drag --ef x -0.12 --ef y 0 --ef z 0"
$Q - S06-snapped "release"
# S14: the head override puts the eye 5 cm in front of the selected specimen (the camera itself stays)
DUMP=$(cmd "panels" 1; adb -s $DEV logcat -d -s PanelFit:V | grep "PANELS id" | tail -1)
C=$(echo "$DUMP" | grep -o "center=([^)]*)" | tr -d 'center=()' )
X=$(echo "$C" | cut -d, -f1 | tr -d ' '); Y=$(echo "$C" | cut -d, -f2 | tr -d ' '); Z=$(echo "$C" | cut -d, -f3 | tr -d ' ')
read EX EZ <<<"$(python -c "x,z=$X,$Z; import math; d=math.hypot(x,z); print(x*(1-0.05/d), z*(1-0.05/d))")"
$Q - S14 "head --ef x $EX --ef y $Y --ef z $EZ --ef yaw 0"
cmd "unfreeze"
bash "$ROOT/tools/turn.sh" -30

# S18: out to HOME with an intent (not the HOME key), back in, shot inside the 3 s "布局已恢复"
adb -s $DEV shell am start -a android.intent.action.MAIN -c android.intent.category.HOME >/dev/null 2>&1; sleep 4
$H fast on >/dev/null 2>&1; sleep 2
adb -s $DEV shell am start -n $PKG/.platform.LaunchActivity >/dev/null 2>&1; sleep 1.5; cap S18
adb -s $DEV logcat -d -s PanelFit:V | grep -E "PF-STATE S" | tail -2 | cut -c40-160

# S00: the first second of a cold start
adb -s $DEV shell am force-stop $PKG; sleep 3
$H fast on >/dev/null 2>&1; sleep 2
adb -s $DEV logcat -c
# debug builds hold the preparing page 12 s when files/hold_prep exists (PfScene.holdPrepUntil)
adb -s $DEV shell run-as $PKG touch files/hold_prep
adb -s $DEV shell am start -n $PKG/.platform.LaunchActivity >/dev/null 2>&1; sleep 3
$H fast on >/dev/null 2>&1; sleep 4; cap S00   # fast on after the start: the window draws only at 60 fps
adb -s $DEV shell run-as $PKG rm -f files/hold_prep
adb -s $DEV logcat -d -s PanelFit:V | grep -E "PF-STATE (init|S0)" | head -3 | cut -c40-160
$H fast off >/dev/null 2>&1
