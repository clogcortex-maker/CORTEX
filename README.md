# CORTEX — On-Device AI Image Generation

CORTEX is a native Android app that generates images from text prompts **entirely on your device** — no server, no API keys, no cloud. It runs Stable Diffusion 1.5 on your phone's GPU via Google MediaPipe's Image Generator task.

## Features

- **Fully offline generation** — after a one-time model download (~2 GB from Hugging Face), everything runs on-device
- **Conversation UI** — chat-style interface; every prompt and image stays in the thread
- **Refine (correct image by prompt)** — regenerate any image guided by its edges, depth, or facial structure: describe the change you want, CORTEX remixes the image accordingly
- **Guide images** — attach any picture from your gallery and guide generation by it
- **Live progress** — watch the diffusion steps appear as the image forms
- **7 themes** — Cortex, AMOLED Black, Ocean, Forest, Sunset, Crimson, Daylight
- **Seeds & steps** — reproducible generations with fixed seeds, quality/speed control
- **Save & share** — export to gallery, share via any app

## Requirements

- Android 7.0+ (API 24+), **arm64** device
- ~8 GB RAM recommended (works on many 6 GB devices; may be killed on lower RAM)
- OpenCL GPU support (virtually all modern arm64 phones)
- ~2.3 GB free storage for model files
- Generation takes roughly 15–60 seconds per image depending on device

## Building

Open the project in Android Studio (or run `./gradlew assembleDebug`). Java 17, AGP 8.0, Kotlin 1.8.

The app downloads model files on first launch:
- Base model: MediaPipe-converted Stable Diffusion 1.5 (fp16) from Hugging Face
- Refine plugins: Canny edge / depth / face-landmark models from Google's model storage

## Licences & attribution

- **Stable Diffusion v1.5** — CreativeML OpenRAIL-M (commercial use permitted; use restrictions apply — see the license). Downloaded from the `na5h13/stable-diffusion-v1-5-mediapipe` mirror of the official `stable-diffusion-v1-5/stable-diffusion-v1-5` weights.
- **MediaPipe Image Generator** — Apache License 2.0 (Google)
- The MediaPipe Image Generator task is no longer actively maintained by Google but still fully functional.
- Images are AI-generated; you are responsible for how you use and share them.
