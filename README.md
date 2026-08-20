# EyeRule2020

Short description: Android app for EyeRule features and demos.

## Screenshots & GIFs

Place screenshots or demo GIFs in `docs/screenshots/` and reference them from this README.

Example Markdown to embed a screenshot:

![Screenshot example](docs/screenshots/screenshot1.png)

Example Markdown to embed an animated demo (GIF):

![Demo example](docs/screenshots/demo.gif)

### Recommended workflow

- Record a short video from the device:

```bash
adb shell screenrecord /sdcard/demo.mp4
adb pull /sdcard/demo.mp4
```

- Convert to optimized GIF (recommended) using `ffmpeg` + `gifsicle`:

```bash
ffmpeg -i demo.mp4 -vf "fps=15,scale=640:-1:flags=lanczos" -y temp.gif
gifsicle -O3 --colors 256 temp.gif > docs/screenshots/demo.gif
rm temp.gif
```

Alternatively, use an MP4 for higher quality; GitHub will render MP4 links in the README.

### File naming

- Use descriptive names like `screenshot-login.png`, `screenshot-main.png`, `demo-flow.gif`.
- Keep GIFs short (3–8s) and under ~5MB for good performance on GitHub.

After adding images, run:

```bash
git add docs/screenshots demo.gif README.md
git commit -m "Add screenshots and README guidance"
git push
```
# 20-20-20

An Android app for the 20-20-20 eye rule: every 20 minutes, look at something
20 feet away for 20 seconds. The app shows a fullscreen overlay on top of
whatever you're doing when it's time for a break.

Interval and break length are both adjustable. No account, no ads, no
network access beyond the optional support link.

## Features

- Fullscreen break overlay, works on top of any app
- Adjustable interval and break length
- Persistent notification with a live countdown
- Optional sound on break
- Optional "unskippable" mode (no Skip button, ignores back button)
- Optional timer reset when you unlock your phone
- Survives reboots if it was running

## Building

Requires JDK 17, the Android SDK (platform 34, build-tools 34.0.0), and
Gradle. No Android Studio needed.

```
gradle assembleDebug
```

The APK ends up at `app/build/outputs/apk/debug/app-debug.apk`.

## Permissions

- `SYSTEM_ALERT_WINDOW` - draws the break screen over other apps
- `SCHEDULE_EXACT_ALARM` - keeps the interval accurate
- `POST_NOTIFICATIONS` - shows the countdown notification
- `RECEIVE_BOOT_COMPLETED` - resumes the schedule after a restart

## License

MIT, see `LICENSE`.
