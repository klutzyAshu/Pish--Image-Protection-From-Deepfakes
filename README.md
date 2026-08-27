# Pish-Engin - Multi-Model Anti-AI Image Cloak

Protects a photo against misuse by generative AI (img2img editing, inpainting,
style mimicry) and optionally face-swap deepfake tools, while keeping visible
quality loss to a minimum.

**Honest limits:** this raises the cost/difficulty of automated misuse and
improves survival through social-media re-compression. It is not a
guarantee against every current or future AI model — no public tool
(including Glaze, Fawkes, PhotoGuard, or this one) can promise that.

Runs natively on **Windows, macOS, and Linux** - the script auto-detects
your OS and GPU (NVIDIA CUDA on Windows/Linux, Apple Silicon Metal/MPS on
Mac, or CPU fallback everywhere) and prints what it found on startup.

## Setup

### Windows
```powershell
python -m venv venv
venv\Scripts\activate
pip install -r requirements.txt
```
If you have an NVIDIA GPU and want CUDA acceleration, install the CUDA build
of PyTorch first (see https://pytorch.org/get-started/locally/ for the exact
command matching your CUDA version), then run `pip install -r requirements.txt`
for the rest.

### macOS (Intel or Apple Silicon)
```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```
Apple Silicon (M1/M2/M3/M4) Macs get GPU acceleration automatically via
Metal/MPS - no extra setup needed, the standard PyPI `torch` wheel supports it.
The interactive file-picker's "browse" option uses Tkinter, which ships with
the python.org installer by default.

### Linux
```bash
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt
```
If you have an NVIDIA GPU, install the CUDA build of PyTorch matching your
driver version (see pytorch.org) before installing the rest of the requirements.
If the interactive "browse" file-picker option doesn't open, install Tkinter
separately (`sudo apt install python3-tk` on Debian/Ubuntu, `sudo dnf install
python3-tkinter` on Fedora) - or just type the file path manually instead,
which always works.

First run on any OS will download VAE model weights from Hugging Face (a few
GB) - make sure you have internet access and enough disk space.

## Usage (same command on all three OSes)

```bash
# Basic (medium strength, VAE-ensemble + EOT protection only)
python Pish-Engin.py -i photo.jpg -s medium

# Also disrupt face-swap deepfake tools (needs a detectable face in the photo)
python Pish-Engin.py -i photo.jpg -s high --face --steps 60

# Custom output path
python Pish-Engin.py -i photo.jpg -o protected.png -s low
```

On Windows use `python` (not `python3`) if that's how your install is set up.
If you run it with no arguments, it opens an interactive picker with a manual
path-entry option, drag-and-drop support, and (where Tkinter is available) a
native file browser dialog.

## What changed vs. the original single-VAE script

| Upgrade | What it does | Why it matters |
|---|---|---|
| **Perceptual/texture masking** | Computes a Sobel edge map of your photo and allows more perturbation in textured regions (fur, fabric, background clutter), less in smooth regions (skin, sky) | Same protection strength looks noticeably cleaner to the eye |
| **Ensemble VAE attack** | Attacks 3 different Stable Diffusion VAE variants (SD1.x, SD2.x, SDXL) at once instead of just one | A perturbation that fools 3 models transfers much better to a model you never explicitly attacked |
| **EOT (Expectation over Transformation)** | Randomly simulates resize/re-compression/quantization/brightness jitter during optimization | Perturbation is trained to survive what Instagram/WhatsApp/Facebook do to your image on upload |
| **Optional face-embedding attack (`--face`)** | Adds a second loss term pushing the photo's FaceNet embedding away from the original | VAE attacks mostly stop img2img/inpainting editors; deepfake face-swap tools use face-recognition embeddings instead, a different attack surface this specifically targets |

## Recommended settings for social media / commercial use

- `-s medium --steps 40` is a good default balance of quality vs. protection.
- `-s high --steps 60 --face` if the photo is a portrait and deepfake/face-swap
  is your main concern — expect slightly more visible texture in return for
  stronger, broader protection.
- Always re-check the output image visually before posting; if you can see
  artifacts you're not happy with, drop to `-s low` or reduce `--steps`.
- Keep an unprotected master copy for yourself in a private location — the
  protected version is what you upload publicly, not what you archive.
