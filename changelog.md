# Changelog

## Unreleased
- Added **Click Indicator** (Bot window): reads clicks from the loaded `.nxr` and draws them falling on the left side during Playback. Two styles: **Line** and **Follow Zone**. Custom colors, position, size, speed and a green **Perfect** on every hit
- Added a **Red** theme (red panel, card and box sprites) and made it the default on Android; Windows keeps Aurora
- Added a language selector (English / Indonesian) in Settings for both the Table and Panel layouts
- Whole interface, popups, status messages and macro errors are now English by default
- Refreshed the look: new navy and teal palette for every sprite in `resources/`, new logo, matching colors in the Table layout
- Rewrote the project docs; Markdown files are English only from now on

## v0.8.0
- New layout: **Table** (draggable windows with a header per group). The old layout is still available as **Panel**
- Table has no popups: settings, macro actions, descriptions and errors all stay inline or in the status bar
- Windows are packed automatically, the board scrolls, drag a title to reorder, tap a title to collapse
- Windows (Win64) support, the Tab key opens and closes the menu
- Platform specific code moved to `ui/mobile/` and `ui/windows/`
- Layout is switched from Settings
- Added README, about, changelog, HOW_TO_WORK and a logo

## v0.7.2
- Fix: Stats HUD overlapped the bot toolbar on small screens
- Fix: Wave Slide kept clicking after leaving a wave section
- Auto Clicker Super mode accepts fractional CPS

## v0.7.1
- Fix: Straight Wave drifted by one pixel after a portal
- Fix: Click effect toggle was ignored by Straight Ship
- Stats popup checkboxes line up on narrow panels

## v0.7.0
- Utils tab: Straight Ship, Straight Wave, Auto Clicker and Wave Slide
- Auto Clicker has a Normal mode (hold and release in ticks) and a Super mode (CPS, no upper limit)
- Stats HUD with selectable entries
- Click rate (CPS) and click effects are configurable per util

## v0.6.3
- Fix: playback injected input after the player had died
- Fix: stray release events were recorded without a matching press
- Macro picker keeps its scroll position after a rename

## v0.6.2
- Fix: tapping the logo with a popup open also pressed the buttons behind it
- Fix: sliders and the scrollbar reacted to touches that landed on a popup above them
- Menu remembers the last open tab

## v0.6.1
- Fix: restarting a recording with only a few inputs overwrote a long attempt
- Finishing a level now locks the macro until Record is switched on again
- Bot toolbar (clock, pause, step) moved under the stats text

## v0.6.0
- Recording keeps the best attempt instead of the last one
- Inputs are ignored after the player dies
- Scrollbar uses its own sprites
- Settings: clock size, stepper size and logo size sliders
- HUD shows the bot state (REC / PLAY with the input index)

## v0.5.2
- Fix: crash when a macro file was shorter than its header claimed
- Fix: macros saved from practice mode started one frame late

## v0.5.1
- Fix: PracticeFix restored the wrong frame after a checkpoint respawn
- Playback no longer depends on the TPS Bypass setting, it uses the macro TPS

## v0.5.0
- Macro format NXR3 stores a state sample per frame, which prevents playback desync
- PracticeFix stores progress per checkpoint
- Older NXR1 and NXR2 files are still readable

## v0.4.4
- Fix: Particle Fixed did not reset the leftover delta on the first attempt
- Fix: CBF was not restored after leaving a level in Record mode

## v0.4.3
- Bot speed is set from a popup instead of an inline field (the inline field opened the keyboard by itself)
- Fix: clock button stayed visible when the bot was disabled

## v0.4.2
- Fix: renaming a macro to its own name reported an error
- Macro names are limited to 32 characters (letters, digits, `_` and `-`)

## v0.4.1
- Fix: deleting the selected macro left it loaded
- Delete asks for confirmation

## v0.4.0
- Macro management: create, load, rename, save and delete from the Bot tab
- CBF Bypass: pauses Click Between Frames while the bot runs
- Particle Fixed: drops leftover physics delta at the start of every attempt
- Clock button and frame stepper inside levels while recording
- NXR2: macros remember their level and creation time

## v0.3.3
- Fix: recorded macros started one tick early at high TPS
- Fix: playback ignored the player 2 button

## v0.3.2
- Macro files are written through a temporary file, so a failed save no longer destroys the old one
- Broken entries in a macro are dropped instead of rejecting the whole file

## v0.3.1
- Fix: TPS popup accepted values above the limit
- Fix: speed audio stayed pitched after switching Speedhack off

## v0.3.0
- TPS Bypass: physics at a configurable TPS, separate from FPS
- Bot: Record and Playback with `.nxr` macros (NXR1)
- Macro picker popup

## v0.2.2
- Speedhack range raised from 100x to 1000x
- Fix: menu animations slowed down together with the game

## v0.2.1
- Fix: speed text field lost focus after every digit
- Audio pitch option for Speedhack

## v0.2.0
- Speedhack with a slider and a manual entry popup
- Clock button next to the pause button
- Four color themes and a font selector

## v0.1.2
- Fix: menu opened behind other popups
- Fix: overlay logo could be dragged off screen

## v0.1.1
- Fix: settings were not saved when the game was closed from the pause menu
- Fix: Noclip stayed on after switching Safe Mode back on

## v0.1.0
- First release
- Menu with a draggable overlay logo
- Safe Mode, Noclip and persistent settings
- UI scale, opacity and text scale
