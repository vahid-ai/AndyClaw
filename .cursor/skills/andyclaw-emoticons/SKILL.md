---
name: andyclaw-emoticons
description: ASCII emoticons for the dGEN1 terminal display. Use when working on terminal text display, emoticon rendering, agent reactions, setTerminalText, or any UI code involving the dGEN1 back-screen. Reference these codes when the agent needs to show mood-appropriate reactions.
---

# AndyClaw Emoticons

ASCII emoticons ported from the Chad app for use on the dGEN1 terminal status bar (428x142 px back-screen).

## Source File

`app/src/main/java/org/ethereumphone/andyclaw/led/Emoticons.kt`

## Reactive Usage Guide

The on-device AI agent uses `setTerminalText` to display emoticons as reactions. When writing code that triggers terminal display, pick emoticons that match context:

| Context | Emoticon | Code |
|---------|----------|------|
| Success / task complete | cheer | `※\(^o^)/※` |
| Failure / error | cry | `(╥﹏╥)` |
| Greeting / hello | wave | `( * ^ *) ノシ` |
| Thinking / processing | loading | `███▒▒▒▒▒▒▒` |
| Uncertainty / unknown | shrug | `¯\_(ツ)_/¯` |
| Celebration / victory | victory | `(๑•̀ㅂ•́)ง✧` |
| Sadness | sad | `ε(´סּ︵סּ`)з` |
| Excitement | excited | `(ﾉ◕ヮ◕)ﾉ*:・ﾟ✧` |
| Frustration / rage | rage | `t(ಠ益ಠt)` |
| Cool / confident | dealwithit | `(⌐■_■)` |
| Happy | happy | `٩( ๑╹ ꇴ╹)۶` |
| Disapproval / meh | meh | `ಠ_ಠ` |
| Fun / dance | dance | `ᕕ(⌐■_■)ᕗ ♪♬` |
| Flip out | tableflip | `(ノ ゜Д゜)ノ ︵ ┻━┻` |
| Affirmative / yes | yeah | `(•̀ᴗ•́)و ̑̑` |

## Full Emoticon List

```
(afraid) (ㆆ _ ㆆ)
(angel) ☜(⌒▽⌒)☞
(angry) •`_´•
(awkward) •͡˘㇁•͡˘
(bat) /|\ ^._.^ /|\
(bigheart) ❤
(bitcoin) ₿
(bored) (-_-)
(chad) (▀̿Ĺ̯▀̿ ̿)
(check) ✔
(cheer) ※\(^o^)/※
(cry) (╥﹏╥)
(dab) ヽ( •_)ᕗ
(dance) ᕕ(⌐■_■)ᕗ ♪♬
(dead) x⸑x
(dealwithit) (⌐■_■)
(depressed) (︶︹︶)
(dunno) ¯\(°_o)/¯
(evil) ψ(｀∇´)ψ
(excited) (ﾉ◕ヮ◕)ﾉ*:・ﾟ✧
(facepalm) (－‸ლ)
(fight) (ง •̀_•́)ง
(happy) ٩( ๑╹ ꇴ╹)۶
(heart) ♥
(help) \(°Ω°)/
(loading) ███▒▒▒▒▒▒▒
(lol) L(° O °L)
(meh) ಠ_ಠ
(nice) ( ͡° ͜ °)
(point) (☞ﾟヮﾟ)☞
(rage) t(ಠ益ಠt)
(sad) ε(´סּ︵סּ`)з
(shrug) ¯\_(ツ)_/¯
(skull) ☠
(smile) ツ
(star) ★
(strong) ᕙ(⇀‸↼‶)ᕗ
(surprised) (๑•́ ヮ •̀๑)
(tableflip) (ノ ゜Д゜)ノ ︵ ┻━┻
(tears) (ಥ﹏ಥ)
(victory) (๑•̀ㅂ•́)ง✧
(wave) ( * ^ *) ノシ
(yay) \( ﾟヮﾟ)/
(yeah) (•̀ᴗ•́)و ̑̑
```

See `Emoticons.kt` for the complete list of 200+ emoticons.

## Architecture

- **`Emoticons.kt`** — data object with `AVAILABLE_EMOTICONS` (full list) and `TERMINAL_SAFE` (subset verified to render on the 428x142 display)
- **`TerminalTextSkill.kt`** — builtin native skill exposing `setTerminalText` and `clearTerminalText` tools to the on-device AI agent
- **`LedMatrixController.kt`** — bridges to `TerminalSDK.showText()` for hardware display via `setTerminalText()` and `clearTerminalText()` suspend functions
