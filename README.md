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

**Triggers**
- Manual save hotkey, on death, on collection log unlock, on level up, on
  valuable drop (with a configurable gp threshold), on pet, on quest
  completion, on combat task.

**Output**
- **Save folder** — defaults to the RuneLite directory's `instant-replay`
  folder.
- **Chat message on save** — confirms each saved clip in-game.

## Building

```
./gradlew build          # compile + assemble
./gradlew run            # launch a dev client with the plugin side-loaded
```

## License

BSD 2-Clause. See [LICENSE](LICENSE).
