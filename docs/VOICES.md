# Voices on the watch (P8)

WGO speaks with the watch's own text-to-speech engines. What the owner's Galaxy Watch5 Pro has (WgoVoice dump,
2026-11-12; `adb logcat -s WgoVoice` while the voice picker is open shows the same list):

| engine | Spanish voices installed | notes |
|---|---|---|
| Samsung TTS (`com.samsung.SMT`, default) | es-US female, es-ES female | each also listed as `<locale>-default`, the same speaker (the "two voices that sound the same") |
| Speech Services by Google (`com.google.android.tts`) | es-US: 4 speakers (`esc`, `esd`, `esf`, `sfb`); es-ES: 5 (`eea`, `eec`, `eed`, `eee`, `eef`) | all offline; each also has a `-network` twin; Google does not say which are male or female |

No Colombian (es-CO) voice exists in either engine. The picker shows up to 6 different voices, with "mujer" /
"hombre" only when the engine states it; for Google voices listen with **PROBAR** to find a male one.

## Getting more voices (nothing is installed by the app)

- In WGO: Settings > **MÁS VOCES** opens the chosen engine's own voice-download screen (Samsung's language list or
  Google's "Install voice data"). Pick a language there; the engine downloads it over Wi-Fi.
- On the watch without WGO: Settings > General > Accessibility (or "Text-to-speech" on some builds) lists the engines
  and their languages; Samsung voices come from the Galaxy Store, Google's from the Play Store app on the watch
  ("Speech Services by Google", keep it updated).
- A different engine: install it from the Play Store on the watch; WGO lists every installed engine automatically
  (Settings > MOTOR DE VOZ appears as soon as there is more than one).

After installing, open WGO's voice picker again: new voices appear at once (the picker reads the engines each time).
