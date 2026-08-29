# Pish-Engin GUI

A sleek, modern JavaFX desktop interface for Pish-Engin.py. Drag-and-drop an
image, pick a protection strength, hit Protect, and get a before/after preview.

Tested: compiled clean and launched with zero runtime errors on a live
display during development (screenshots verified layout, styling, and
control behavior).

## Requirements

- Java 17+ (JDK, not just JRE)
- Maven
- Python 3 with Pish-Engin.py's own requirements installed (see the main
  project's requirements.txt) — the GUI calls Pish-Engin.py as a subprocess,
  it doesn't reimplement the AI logic in Java.
- Place `Pish-Engin.py` in the same folder you run the GUI from (already
  included in this folder).

## Run it

```bash
mvn clean javafx:run
```

This works identically on **Windows, macOS, and Linux** — Maven resolves
the correct native JavaFX binaries for your OS automatically.

## Build a standalone jar

```bash
mvn clean package
```

## About "mobile"

JavaFX itself doesn't run natively on Android or iOS — this GUI is a
**desktop app** (Windows/macOS/Linux), and its layout is responsive down to
small/tablet-sized windows. Getting the *exact same* codebase onto a phone
as a true native app requires a separate toolchain (Gluon Mobile, which
compiles JavaFX apps to native ARM binaries for Android/iOS via GraalVM) —
that's a real, but separate, additional project on top of this one, since it
needs its own build service/licensing and device testing that can't be done
in this environment. If you want, I can scaffold that Gluon Mobile version
as a next step.

## How it works

The GUI is a thin, good-looking front end. All the actual image protection
(the VAE ensemble attack, perceptual masking, EOT robustness, face-embedding
attack) still happens in `Pish-Engin.py`, which the GUI launches as a
background process and streams progress from, so nothing about the AI logic
itself changed — you're just controlling it with buttons and sliders instead
of the command line.
