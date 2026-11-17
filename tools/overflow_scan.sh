#!/usr/bin/env bash
# Overflow scan (taste: no clipped text): every page / sub-page / confirmation in Chinese and English at 60 fps, then
# the `OVERFLOW <panel>/<control>` lines PfText logs when a text needs more lines or width than it was given.
#   tools/overflow_scan.sh  -> prints the visited states and every distinct OVERFLOW line (none = pass)
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DEV=${DEV:-emulator-5560}
PKG=io.embodit.panelfit
H="bash $ROOT/tools/harness/emu.sh"
cmd() { MSYS_NO_PATHCONV=1 adb -s $DEV shell "am broadcast -a $PKG.DEBUG -p $PKG --es cmd '$1'" >/dev/null 2>&1; sleep "${2:-1.2}"; }
adb -s $DEV logcat -c
$H fast on >/dev/null 2>&1
for lang in zh en; do
  cmd "lang $lang"
  cmd "deleteAll"; cmd "state S02"; cmd "state S02:full" 3
  cmd "state S04"; cmd "adjustPage 1"; cmd "adjustPage 0"; cmd "state S04:none"
  cmd "state S07"; for g in 0 1 2 3 4; do cmd "group $g"; done; cmd "state S07:none"
  cmd "state S08"; cmd "cardPage 1"; cmd "cardPage 0"; cmd "state S08:none"
  cmd "state S08"; cmd "save" 2; cmd "saveFail --es where public" 2; cmd "saveFail --es where all" 2; cmd "saveFail --es where none"
  cmd "state S11" 2; cmd "state S12"; cmd "state S13"; cmd "settingsPage 1"; cmd "state S13b:quit"; cmd "state S13b:clear"
  cmd "state S15"; cmd "state S15:error"; cmd "state S16"; cmd "blocked --ei on 0"; cmd "state S19" 2
  cmd "state S04"; cmd "stick --ef x 0 --ef y 0.8 --ef dur 2" 3; cmd "drag --ef x 0.05 --ef y 0 --ef z 0"; cmd "release"
  $H fast on >/dev/null 2>&1
done
cmd "lang zh"; cmd "deleteAll"
$H fast off >/dev/null 2>&1
echo "states visited: $(adb -s $DEV logcat -d -s PanelFit:V | grep -c 'CMD .* ok ->')"
echo "overflow lines: $(adb -s $DEV logcat -d -s PanelFit:V | grep -c 'OVERFLOW')"
adb -s $DEV logcat -d -s PanelFit:V | grep -o 'OVERFLOW.*' | sort | uniq -c | head -40
