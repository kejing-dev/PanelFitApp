#!/usr/bin/env bash
# Turn the emulator camera by DEG degrees of yaw (positive = right) and print the app's head yaw afterwards.
#   tools/turn.sh 25        (needs camera_rotation_button On: tools/relaunch.sh sets it)
# Right-button drags of the emulator window turn the camera, 0.0274 deg per framebuffer px measured on panel-fit 2026-11-03 (HD 2560 frame, emu.sh
# 2160 coordinates; factory memory). Chunks <= 600 px, then the app's `hmd` line is read back.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEV=${DEV:-emulator-5560}
PKG=io.embodit.panelfit
H="bash $ROOT/tools/harness/emu.sh"
deg=${1:-25}
px=$(python -c "print(round(abs($deg)/0.0274))")
sign=$(python -c "print(1 if $deg>0 else -1)")
$H fast on >/dev/null 2>&1; sleep 1
while [ "$px" -gt 0 ]; do
  step=$(( px > 600 ? 600 : px )); px=$(( px - step ))
  x2=$(( 1080 + sign * step ))
  $H drag 1080 1080 $x2 1080 -Button right -NoFast -Steps 10 -StepMs 25 >/dev/null 2>&1
  sleep 0.5
done
adb -s $DEV logcat -c
MSYS_NO_PATHCONV=1 adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd hmd" >/dev/null
for i in 1 2 3 4 5 6; do L=$(adb -s $DEV logcat -d -s PanelFit:V | grep "CMD head=" | tail -1); [ -n "$L" ] && break; sleep 1; done
echo "$L" | grep -o "fwd=([^)]*)" | python -c "import sys,math,re; v=[float(x) for x in re.findall(r'-?[0-9.]+', sys.stdin.read())]; print('yaw_right_deg=%.1f' % math.degrees(math.atan2(v[0], -v[2]))) if len(v)>=3 else print('no hmd line')"
