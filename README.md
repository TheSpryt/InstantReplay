# Instant Replay

A ShadowPlay-style instant replay plugin for [RuneLite](https://runelite.net/).

Instant Replay continuously keeps the last few seconds of gameplay in a rolling
in-memory buffer and automatically saves an MP4 clip of the moments leading up
to (and just after) configurable in-game events — deaths, collection log
unlocks, level ups, valuable drops, pets and more. You can also bind a hotkey to
save a clip on demand.

## How it works

- Frames are sampled from the client at your chosen framerate via RuneLite's
  `DrawManager` and held in a rolling buffer as JPEG-compressed bytes, so memory
  stays bounded even with several seconds retained.
- When a trigger fires, the buffered lead-up is combined with a short
  post-event tail and encoded to an `.mp4` on a background thread using
  [JCodec](http://jcodec.org/) — a pure-Java H.264 encoder, so **no native
  binaries or external processes are required**.

## Configuration

**Recording**
- **Clip length** — total clip duration (default 15s).
- **Post-event padding** — seconds recorded after the event; the remainder is
  the lead-up (default 2s).
- **Framerate** — frames per second to capture and encode (default 30).
- **Resolution** — vertical resolution; the client is downscaled to this height
  and never upscaled (default 720p).
- **JPEG buffer quality** — trade memory use against clip quality.
- **Draw cursor** — overlay a marker at the mouse position (the OS cursor is
  not part of captured frames, so it is drawn by the plugin).

**Triggers**
- Manual save hotkey, on death, on collection log unlock, on level up, on
  valuable drop (with a configurable gp threshold), on pet, on quest
  completion, on combat task.

**Output**
- **Save folder** — defaults to the RuneLite directory's `instant-replay`
  folder.
- **Chat message on save** — confirms each saved clip in-game.
- **Show status overlay** — a small on-screen indicator showing when the plugin
  is armed, actively recording a clip, or has just saved one.

## Usage

1. Enable **Instant Replay** in the RuneLite plugin list.
2. Open the plugin's config panel and pick which events should save a clip
   (and, optionally, set a **Manual save hotkey** for on-demand capture).
3. Play normally. When a trigger fires, the plugin captures the surrounding
   seconds and writes an `.mp4` to your save folder. The status overlay flashes
   green and — if enabled — a chat message confirms the save.

Clips are named `<timestamp>_<reason>.mp4` (for example
`2026-06-23_18-30-05_death.mp4`), so they sort chronologically and are easy to
find after a session.

## Building

```
./gradlew build          # compile + assemble
./gradlew run            # launch a dev client with the plugin side-loaded
```

## Changelog

### Unreleased
- Status overlay showing armed / recording / saved state, with a brief flash
  when a clip is written (toggle under **Output**).
- Optional cursor marker drawn into saved clips.

### 1.0.0
- Initial release: rolling in-memory buffer with pure-Java H.264/MP4 encoding,
  automatic triggers (death, collection log, level up, valuable drop, pet,
  quest, combat task) and a manual save hotkey.

## License

BSD 2-Clause. See [LICENSE](LICENSE).
