#!/usr/bin/env bash
# Panel stress test (factory rule A4): 30 rounds of page switches in the tool window, select / done, a specimen added
# and deleted (two new attachment panels each time), a resize swap and a language switch; then reports the process is
# alive. Capture a frame with qa_shot.sh afterwards to check the panels still draw. tools/stress_panels.sh [ROUNDS]
set -u
DEV=${DEV:-emulator-5560}
PKG=io.embodit.panelfit
export PATH="$PATH:${LOCALAPPDATA}/Android/Sdk/platform-tools"
rounds=${1:-30}
cmd() { MSYS_NO_PATHCONV=1 adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd '$1'" >/dev/null 2>&1; }
adb -s $DEV logcat -c
cmd "state=S04"; sleep 2
for i in $(seq 1 "$rounds"); do
  cmd "page rate"; sleep 0.3; cmd "page card"; sleep 0.3; cmd "page settings"; sleep 0.3; cmd "page library"; sleep 0.3
  cmd "add T3"; sleep 0.6; cmd "step width 1"; sleep 0.8; cmd "done"; sleep 0.3
  last=$(adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd dump" >/dev/null 2>&1; sleep 0.4; adb -s $DEV logcat -d -s PanelFit:V | grep "DUMP" | tail -1 | grep -o '"id":[0-9]*' | tail -1 | cut -d: -f2)
  [ -n "$last" ] && cmd "select $last" && sleep 0.2 && adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd page --es p list" >/dev/null 2>&1
  [ -n "$last" ] && MSYS_NO_PATHCONV=1 adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd 'ui list.$last.del'" >/dev/null 2>&1 && sleep 0.2 && MSYS_NO_PATHCONV=1 adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd 'ui list.$last.delete'" >/dev/null 2>&1
  sleep 0.4; cmd "lang en"; sleep 0.3; cmd "lang zh"; sleep 0.3
done
echo "rounds=$rounds done"
adb -s $DEV shell pidof $PKG >/dev/null && echo "process alive" || echo "PROCESS DIED"
adb -s $DEV logcat -d 2>/dev/null | grep -E "FATAL EXCEPTION|Process: $PKG" | head -3
echo "panels bound: $(adb -s $DEV logcat -d -s PanelFit:V 2>/dev/null | grep -c 'PANEL bound')  swapped: $(adb -s $DEV logcat -d -s PanelFit:V 2>/dev/null | grep -c 'PANEL swapped')  overflow: $(adb -s $DEV logcat -d -s PanelFit:V 2>/dev/null | grep -c 'OVERFLOW')"
