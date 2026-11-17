#!/usr/bin/env bash
# Every design state (design `states[]`), one qa_shot each, into $SH (default ../spatial-app-factory/apps/panel-fit/
# build/shots). Run after tools/relaunch.sh has reached PF-STATE S01 / S02. States change through the debug
# broadcast, which goes through the same events as a press (AGENTS.md). Camera framing (the specimens sit right of the
# window) is the harness' job: rotate with tools/harness/uia_cam.ps1 before the specimen shots.
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
export SH="${SH:-$ROOT/../spatial-app-factory/apps/panel-fit/build/shots}"
Q="bash $ROOT/tools/qa_shot.sh"
# overlays are saved with the layout: switch them off so every state is shot as designed (S10 turns them on)
for k in reach neck ruler; do MSYS_NO_PATHCONV=1 adb -s ${DEV:-emulator-5560} shell "am broadcast -a io.embodit.panelfit.DEBUG -p io.embodit.panelfit --es cmd 'overlay $k 0'" >/dev/null 2>&1; done
$Q - S02-empty "deleteAll" "state S02"
$Q - S01 "state S01"
$Q - S02 "state S02"
$Q - S03 "add T1"
$Q - S04 "state S04"
$Q - S04-dist05 "place --ef dist 0.5 --ef yaw 24 --ef elev -10"
$Q - S04-dist30 "place --ef dist 3.0 --ef yaw 24 --ef elev -10"
$Q - S04-page2 "adjustPage 1"
$Q - S04none "state S04:none"
$Q - S06 "state S04" "drag --ef x 0.05 --ef y 0 --ef z 0"
$Q - S06-release "release"
$Q - S07 "state S07"
$Q - S07none "state S07:none"
$Q - S08 "state S08"
$Q - S08-page2 "cardPage 1"
$Q - S08none "state S08:none"
# the save result stays 6 s in the hint row: the emulator goes to 60 fps BEFORE the save, then a short capture
# a specimen selected on the card page first (S09 is a save from S08)
$Q - S08-before-save "state S08" "cardPage 0" "saveFail --es where none"
for k in "S09-public|save" "S09-private|saveFail --es where public" "S09-none|saveFail --es where all"; do
  bash "$ROOT/tools/harness/emu.sh" fast on >/dev/null 2>&1; sleep 3; PRE=0.3 SETTLE=1.5 $Q - "${k%%|*}" "${k#*|}"; sleep 6
done
$Q - S10 "saveFail --es where none" "state S10"
for k in reach neck ruler; do MSYS_NO_PATHCONV=1 adb -s ${DEV:-emulator-5560} shell "am broadcast -a io.embodit.panelfit.DEBUG -p io.embodit.panelfit --es cmd 'overlay $k 0'" >/dev/null 2>&1; done
$Q - S11 "state S11"
$Q - S12 "state S12"
$Q - S13 "state S13"
$Q - S13-general "settingsPage 1"
$Q - S13b-quit "state S13b:quit"
$Q - S13b-clear "state S13b:clear"
MSYS_NO_PATHCONV=1 adb -s ${DEV:-emulator-5560} shell rm -f /sdcard/Android/data/io.embodit.panelfit/files/import/*.png
$Q - S15-empty "state S15"
$Q - S15error "state S15:error"
$Q - S16 "state S16"
$Q - S16-off "blocked --ei on 0"
$Q - S17-S02 "lang en" "state S02"
$Q - S17-S04 "state S04"
$Q - S17-S08 "state S08"
$Q - S17-S13 "state S13"
$Q - S19 "lang zh" "state S19"
$Q - S02full "deleteAll" "state S02:full"
