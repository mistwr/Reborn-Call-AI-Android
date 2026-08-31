# Reborn Call AI Android

Android-first prototype for a REBORN voice agent on Samsung/Android.

## Goal

Build a single APK that can:

1. Observe carrier call state.
2. Receive privileged call PCM from a local shell-side capture bridge when the device/ROM permits it.
3. Stream remote-party audio to STT.
4. Pass transcripts to the REBORN agent/LLM.
5. Stream TTS back through a pluggable uplink/output adapter.
6. Keep all CRM/business logic separated from the native telephony/audio layer.

## Important technical boundary

A normal Android app cannot directly open `VOICE_CALL`, `VOICE_UPLINK` or `VOICE_DOWNLINK` on stock Android because those paths are protected. This project therefore keeps privileged capture behind a dedicated `ShellCaptureBridge` abstraction. The first implementation target is an embedded/local ADB shell daemon inspired by open-source call-recording projects such as CallVault. Uplink PCM injection is intentionally marked experimental until validated on the target Samsung firmware.

## Modules

- `call/` — carrier call state and dial control.
- `capture/` — PCM capture interface and shell bridge client.
- `agent/` — STT → LLM → TTS orchestration.
- `audio/` — PCM frames, channel splitting and buffering.
- `adb/` — local ADB pairing/daemon lifecycle (next build).

## License note

CallVault is GPL-licensed. Do not copy GPL source into a differently licensed binary without complying with its license. This repository starts with clean interfaces and will keep attribution/license notices for any imported GPL components.

## Build

GitHub Actions builds a debug APK on every push.

Current milestone: **BUILD 1 — Android shell-capture integration skeleton**.
