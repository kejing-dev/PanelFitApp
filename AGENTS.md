# Panel Fit 面板试衣间 · AGENTS.md

PICO OS 6 (Spatial SDK 6.1.9), Kotlin + SpatialUI. Put the panel you are about to build in front of you at 1:1 (up to
6 specimens: real SpatialUI attachment panels with PicoTheme type and components), push it away / pull it close with
the stick, read angular size, arc minutes and equivalent dp off its label, check reach, neck and overlaps, rate each
type size, and save a spec card (JSON + Markdown). Design package: `spatial-app-factory/apps/panel-fit/design-package.json`
(rev 3, gate B 2026-11-02). Package `io.embodit.panelfit`, v0.1.0.

## Build

```
# PowerShell (Git Bash fails on gradlew: CRLF)
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat assembleDebug testDebugUnitTest --no-daemon
```

`local.properties` (not in git) needs `sdk.dir`. Unit tests: `app/src/test/.../PanelFitDomainTest.kt` (35).

## Architecture

- `Main.kt`: `DefaultStage` (Mixed, passthrough; manifest `pico.spatial.stage.style=1`) + one Planar `WindowContainer`
  `PanelFitTools` (560 x 680 dp, fixed with `windowConstraints`). The design's tool panel was an attachment panel left
  30 deg; the factory native-window rule (2026-11-02) moved it into a system window (caption-bar drag / close are the
  system's, pages on the native `TabBar`). See "Deviations".
- `ui/pf/PfShared.kt`: the process-level model (`PfViewModel`) shared by the Stage and the window; the window reports
  its lifecycle (`WINDOW ON_CREATE ... ON_DESTROY`). Recall = close, wait for ON_DESTROY, open (native-window-move §3.5).
- `domain/`: plain Kotlin, tested on the JVM. `FitMath` (mm, angle, eq dp, arc minutes, px, FOV share), `Verdicts`,
  `Placement` (placeUpright, body anchor, polar <-> world, arc arrangement, recommended frames, snaps), `StickMap`,
  `Occlusion`, `Report` (readings per specimen), `SpecText` (label lines, card rows, sentence, JSON / MD export),
  `PfReducer` (every state transition), `Copy` (all copy in zh / en).
- Layout is stored relative to the **body anchor** (eye + yaw at start-up or at "全部样张回到面前"): `PolarPose` =
  distance eye -> centre, height above the eye, bearing right of body forward, facing offset, tilt.
- `ui/pf/scene/PfScene.kt` (ECS `PfFrameSystem`): head validity (3 non-zero samples within 1 cm, 2 s fallback -> S16),
  stick (same `StickMap` for the controller and `stick` debug), label drag (I5: a collider 3 cm in front of each size
  label, 1.05 x 1.5 its size, `hit:label<i>`; `detectTapGestures` / `detectSpatialDragGesture` sit on the Stage `SpatialView`, which never
  moves, and take only those colliders; the hand travel turns the pointing line (`DragLine`, as MRI rehearsal); yaw
  frozen while held; a gesture on the label panel itself got nothing from a real pinch, QA #1 q1-2), specimen / label / chip poses every frame, overlays, head events to the model at <= 5 Hz.
- `SpecimenRig`: an attachment panel has a fixed size, so a new size is a new panel (`spec:<id>:<w>x<h>`): bound and
  parked 1/1000 for 6 frames / 0.6 s, shown 2 mm behind the old one, swapped 4 frames after its content composed.
  The Stage declares at most **2 new attachment panels per frame** (`PfScene.declared`).
- `PanelRig` (from the Spatial Ruler / Hazard Hunt): label pool `label0..5` and the `openChip`; its four findings
  (park, stow, compose after two frames, no LookAtComponent) hold here too.
- `OverlayViews`: reach arcs, comfort zone (1.5 m sphere), 100 mm ruler, 3 dashed frames, occlusion masks; all strips
  of ONE shared unit-band mesh, made at most 16 entities per frame, no colliders.
- Tool window pages: `ui/pf/window/MainWindow.kt` (factory template, current page and quit confirmation hoisted into the
  model, bilingual), `PfWindow.kt`, `Pages.kt`. Settings is the template's last tab; quit is its bottom row with an
  inline confirmation; the exit itself runs on the Stage (release, close window, closeStage, finishAndRemoveTask,
  exitProcess).

## Debug broadcast (debug builds only)

```
adb -s emulator-5560 shell "am broadcast -a io.embodit.panelfit.DEBUG -p io.embodit.panelfit --es cmd '<command>'"
```

Extras can be separate (`--es cmd place --ef dist 1.2`) or inside the command string (`--es cmd 'place --ef dist 1.2'`).
Every command goes through the same `PfEvent` as a press (profile D29); each answers with `CMD ... -> <state name>`.

| command | effect |
|---|---|
| `add T1..T8` / `--es t T1` | add from the library (T8 = first picture in files/import/) |
| `select <id>` / `--ei i <id>` (-1 none), `done` | I2 / [完成] |
| `place --ef dist --ef yaw --ef elev --ef facing --ef tilt` | pose of the selected (else last) specimen, through the stick-release path |
| `stick --ef x --ef y --ef dur` | feeds the stick mapping for `dur` s (same function as the controller) |
| `drag --ef x --ef y`, `release` | drags the selected specimen's label: metres of specimen travel (x right, y up) are turned into the hand travel that gives them, converted to view-local pixels and fed to the same gesture handler as a real pinch (`onLabelGesture`); z is ignored (depth is the stick's) |
| `head --ef x --ef y --ef z --ef yaw [--ef pitch]`, `head off`, `unfreeze` | override the head pose (frozen) |
| `overlay reach\|neck\|ruler\|frames 1\|0` | S10 |
| `overlap` | two specimens, the nearer covering the lower part of the farther (S11) |
| `rate bodyMedium ok\|meh\|no` | I8 |
| `save`; `saveFail --es where public\|all\|none` | S09 (three results) |
| `import --es path <name or absolute path>` | T8 from files/import/ (absolute paths are copied there) |
| `fill6`, `deleteAll`, `blocked 1\|0`, `quit`, `quitYes`, `lang zh\|en`, `recall`, `front` | |
| `step dist\|height\|side\|facing\|tilt\|width\|heightDp\|scale 1\|-1`, `surface glass\|solid`, `tiltToEye` | adjust page |
| `page library\|adjust\|rate\|card\|settings\|list\|import`, `adjustPage 0\|1`, `cardPage 0\|1`, `group 0..4`, `settingsPage 0\|1` | navigation |
| `state <S00..S19>[:variant]` (`S02:full`, `S04:none`, `S07:none`, `S08:none`, `S13b:quit`, `S13b:clear`, `S15:error`) | straight to a design state; sensing frozen unless `--ei live 1` |
| `ui <probe name>` | presses a window / panel control (e.g. `tryOne`, `card.T2`, `adjust.done`, `dist.plus`, `card.save`, `list.<id>.del`, `quit`, `quit.yes`, `openPanel`) |
| `dump` (`DUMP {...}`, also files/dump.json), `hmd`, `panels` | probes |

Log prefixes (tag `PanelFit`): `PF-STATE` (state name `S04+S10:T1`, startup 1 / 3 / 5 s placement lines), `PF-METRIC`
(one line per placement / parameter change; `type role=... themeSp renderedSp`), `PF-SAVE`, `PF-IMPORT`, `PF-COLORS`
(resolved theme colours for `tools/contrast.py`), `HEAD`, `WINDOW`, `PANEL bound|swapped|removed`, `STICK`, `DRAG`,
`RESIZE`, `FRONT`, `HIT`, `OVERFLOW <panel>/<control>`.

## Screenshots (QA list)

`tools/shots_all.sh` runs every state through `tools/qa_shot.sh` (HD graphics; `fast on` while capturing):
S00/S02 empty, S01, S02, S02:full, S03, S04 (+ 0.5 m / 3.0 m, page 2), S04:none, S05 (stick), S06 (drag + release),
S07, S07:none, S08 (+ page 2), S08:none, S09 x3, S10, S11, S12, S13 (+ general), S13b quit / clear, S15, S15:error,
S16 on / off, S17 on S02 / S04 / S08 / S13, S19; `tools/shots_extra.sh` adds S00, S05 held, S06 near a frame + snap,
S08 T1 at 0.9 m, S10 one overlay at a time, S14, S15 empty / two pictures, S18. The specimens sit right of the window (the window takes the middle of
the view): rotate the emulator camera with `tools/harness/uia_cam.ps1` for the specimen shots. S18: `am start -a
android.intent.action.MAIN -c android.intent.category.HOME`, then start the app again (not the HOME key).

Other tools: `relaunch.sh [install]`, `cold_start.sh [s]` (ANR watch), `stress_panels.sh [rounds]` (30 rounds of page
switches, add / resize / delete, language), `overflow_scan.sh` (every page in zh and en, prints `OVERFLOW` lines),
`turn.sh <deg>` (turns the emulator camera, 0.0274 deg per fb px measured here, prints the head yaw), `real_drag.sh`
(real pinch drag on a label through `emu.sh drag`, shots before / held / released / 3 s later), `make_icons.py`,
`icon_sheet.py`, `make_icon.py`, `contrast.py`.

Capture notes: `emu.sh` click / drag coordinates are in its 2160-wide framebuffer frame, the HD screenshots are 2560
wide (multiply screenshot x / y by 0.84375). A save result stays 6 s in the hint row, so S09 is shot with `fast on` sent before the save
(`shots_all.sh`). Debug `state` commands freeze head sensing: send `unfreeze` before turning the camera. The system
window's x can be simulated with `adb shell am stack remove <task id of SpatialStubActivity>` (the open chip appears).

## Deviations (also in state.json)

1. Tool panel: system Planar window (560 x 680 dp) instead of an attachment panel left 30 deg 0.8 m (native-window
   rule). The window opens 1.75 m straight ahead and the app cannot place it, so the "tool panel sector" that
   automatic placement and the recommended frames skip is the window's estimated sector: its bearing when it was
   (re)opened, +-15.6 deg (560 dp at 1 mm per dp per metre) + 3 deg. The first specimen therefore lands right of the
   window (about 27 deg), the next ones right then left. After the user drags the window the sector is not tracked.
2. S19 [把工具面板挪开] cannot move a system window: it is [把它挪开] and moves the specimen to the free place on the arc.
3. "叫回工具面板" = close + reopen; the system puts it straight ahead (not left 30 deg).
4. D29: adding from the library and "移到面前" place on the arc from body forward (not the press-time head yaw);
   "全部样张回到面前" waits for the head to rest before it samples.
5. S14 / S19 "no hits": not possible. On the emulator the attachment panel entity has neither an InteractableComponent nor
   a CollisionComponent (`HIT off key=... interactable=false collision=false`), so the faded (30 %) specimen still takes
   the ray. S19 relies on the window's [把它挪开] (moves the specimen to the free place on the arc) and the panel list.
   The 30 % itself is a Compose `alpha(0.3)` on the specimen content: the entity's OpacityControllerComponent did not
   show on attachment panels on the emulator (kept as well).
6. Specimen "solid" surface follows the text colour (dark under light text, light under dark text) for contrast.

## Not verifiable on the emulator

Readability self-rating, k with a real ruler (W / 300), direct touch reach, neck comfort, real stick axes, drag feel
and eye + pinch on the label on the headset (the emulator's own gaze + pinch drag passes: `tools/real_drag.sh`), MediaStore public Download on the headset, FOV / PPD by Build.MODEL.
