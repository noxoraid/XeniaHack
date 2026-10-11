<p align="center"><img src="logo.png" width="96" alt="XENIA"></p>

# XENIA

A gameplay toolkit for **Geometry Dash 2.2081** built on Geode SDK 5.10.1. It runs on **Android** and **Windows**.

It started as a small noclip and speedhack menu and grew into a macro bot with its own file format (`.nxr`). It is meant for showcasing levels: any run that uses a cheat is dropped, so nothing reaches your progress or the leaderboards.

## Features

- Noclip, Speedhack (0.1x to 1000x) and TPS Bypass
- Macro bot: record and play back `.nxr` files, with a speed clock and frame stepper in the level
- Straight Ship, Straight Wave, Auto Clicker and Wave Slide
- Click Indicator: clicks from the `.nxr` fall on the left side (Line or Follow Zone) with a green Perfect on every hit
- Stats HUD (FPS, TPS, frame, inputs, CPS, position and more)
- Two menu layouts, adjustable UI scale, opacity, fonts and logo size
- English and Indonesian interface (Settings, Language)

Every cheat is only active while **Safe Mode is OFF**.

## Menus

On Android, tap the **N** logo to open the menu. On Windows, press **Tab** or click the logo. Switch layouts and languages in Settings.

## Building

You can also let GitHub Actions do it. The workflow in `.github/workflows/build.yml` builds Windows and Android64 and uploads the `.geode` file as an artifact. Pushing a `v*` tag creates a release.

## Layout of the source

```text
src/xenia/
  core/       state, theme, language helper, entry point
  gameplay/   physics and collision hooks
  bot/        macro recording and playback
  ui/
    menu/     Panel layout
    table/    Table layout
    mobile/   Android profile
    windows/  Windows profile (Tab key)
    widgets/  popups/  hud/  overlay/
```