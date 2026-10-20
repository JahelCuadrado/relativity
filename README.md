# Relativity

Makes Minecraft faster where no other mod does. Built to sit next to Sodium, Lithium and the rest of
your performance mods, and to change nothing but speed.

Fabric mod for Minecraft 26.3.

## Features
- **Instant world exit:** leaving a singleplayer world takes you straight back to the menu instead of
  waiting on the "Saving world" screen. The world keeps saving in the background, and if you
  reopen it (or quit the game) before it finishes, Relativity simply waits for the save first.
- **Faster item frames:** bases full of item frames (storage labels, map walls) draw noticeably
  faster. In our tests a wall of 1,000 item frames took about 20% less render time per frame with
  Sodium installed, and about 25% less without it. Clocks, compasses, maps and enchanted items look
  and move exactly as in vanilla.
- **Invisible by design:** every optimization is checked to look and behave exactly like vanilla;
  only the speed changes. Each one can be switched off in the settings.
- **Simple settings screen** with no extra libraries (Mod Menu supported).
- **Lightweight:** under 100 KB.

## Compatibility

Works alongside Sodium, Lithium, C2ME, ImmediatelyFast, EntityCulling, MoreCulling and other
performance mods. With an Iris shader pack active, the item frame optimizations step aside and the
game draws item frames as usual.

## Requirements

Minecraft 26.3 · Fabric Loader 0.19.5+ · Fabric API · Java 25. Mod Menu optional.

## Development

Requires JDK 25. `./gradlew runClient` · `./gradlew build` (compile, format check, JUnit, GameTests)
· `./gradlew spotlessApply`. Pixel-comparison tests: `./gradlew runClientGameTest`. Dev-only
benchmark (never in the jar): `./gradlew runBenchmark -Pprofile=vanilla|pack`.

## License

MIT.
