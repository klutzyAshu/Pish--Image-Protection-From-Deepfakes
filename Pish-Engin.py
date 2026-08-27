import os
import sys
import platform
import argparse
from pathlib import Path

import torch
import torch.nn as nn
import torch.nn.functional as F
from torchvision import transforms
from PIL import Image
from tqdm import tqdm


def get_downloads_folder() -> Path:
    if os.name == "nt":
        try:
            import winreg
            sub_key = r"SOFTWARE\Microsoft\Windows\CurrentVersion\Explorer\Shell Folders"
            downloads_guid = "{374DE290-123F-4565-9164-39C4925E467B}"
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, sub_key) as key:
                return Path(winreg.QueryValueEx(key, downloads_guid)[0])
        except Exception:
            return Path.home() / "Downloads"
    return Path.home() / "Downloads"


def get_optimal_device() -> torch.device:
    if torch.cuda.is_available():
        return torch.device("cuda")
    if hasattr(torch.backends, "mps") and torch.backends.mps.is_available():
        return torch.device("mps")
    return torch.device("cpu")


def describe_platform(device: torch.device) -> str:
    system = platform.system()
    os_label = {"Windows": "Windows", "Darwin": "macOS", "Linux": "Linux"}.get(system, system)
    if device.type == "cuda":
        gpu_label = f"NVIDIA GPU ({torch.cuda.get_device_name(0)})"
    elif device.type == "mps":
        gpu_label = "Apple Silicon GPU (Metal/MPS)"
    else:
        gpu_label = "CPU (no GPU acceleration detected - this will be slower)"
    return f"{os_label} | {gpu_label}"


def sanitize_path(raw_path: str) -> Path:
    cleaned = raw_path.strip().strip("'\"").strip("& '").strip()
    return Path(cleaned).expanduser().resolve()


def interactive_picture_prompt() -> Path:
    print("=" * 60)
    print("  PISH-ENGIN : SELECT IMAGE TO PROTECT")
    print("=" * 60)
    print(" [1] Enter or paste the file path")
    print(" [2] Drag and drop an image file into this terminal")
    print(" [3] Type 'browse' to open the system file picker")
    print(" [q] Exit")
    print("-" * 60)

    while True:
        try:
            choice = input("Enter image path or option > ").strip()
        except (KeyboardInterrupt, EOFError):
            print("\n[!] Exiting.")
            sys.exit(0)

        if choice.lower() in ("q", "quit", "exit"):
            sys.exit(0)

        if choice.lower() == "browse":
            try:
                import tkinter as tk
                from tkinter import filedialog
                root = tk.Tk()
                root.withdraw()
                root.attributes("-topmost", True)
                selected = filedialog.askopenfilename(
                    title="Select Photo to Protect",
                    filetypes=[("Image Files", "*.jpg *.jpeg *.png *.webp *.bmp")]
                )
                root.destroy()
                if selected:
                    choice = selected
                else:
                    continue
            except Exception:
                print("[!] System picker unavailable. Enter path manually.")
                continue

        if not choice:
            continue

        target_path = sanitize_path(choice)
        if not target_path.is_file():
            print(f"[x] File does not exist: {target_path}")
            continue

        try:
            with Image.open(target_path) as img:
                img.verify()
            print(f"[OK] Target loaded: {target_path.name}")
            return target_path
        except Exception:
            print(f"[x] Invalid image format: {target_path}")


def build_texture_mask(img_01: torch.Tensor, low: float = 0.35, high: float = 1.0) -> torch.Tensor:
    gray = 0.299 * img_01[:, 0:1] + 0.587 * img_01[:, 1:2] + 0.114 * img_01[:, 2:3]
    sobel_x = torch.tensor([[-1., 0., 1.], [-2., 0., 2.], [-1., 0., 1.]],
                            device=img_01.device).view(1, 1, 3, 3)
    sobel_y = sobel_x.transpose(2, 3)
    gx = F.conv2d(gray, sobel_x, padding=1)
    gy = F.conv2d(gray, sobel_y, padding=1)
    edge = torch.sqrt(gx ** 2 + gy ** 2 + 1e-8)
    edge = F.avg_pool2d(edge, 5, stride=1, padding=2)
    edge = (edge - edge.min()) / (edge.max() - edge.min() + 1e-8)
    mask = low + (high - low) * edge
    return mask.repeat(1, 3, 1, 1)


def eot_transform(img_pm1: torch.Tensor) -> torch.Tensor:
    x = img_pm1
    choice = torch.randint(0, 4, (1,)).item()
    if choice == 0:
        h, w = x.shape[-2:]
        scale = torch.empty(1).uniform_(0.5, 0.9).item()
        small = F.interpolate(x, scale_factor=scale, mode="bilinear", align_corners=False)
        x = F.interpolate(small, size=(h, w), mode="bilinear", align_corners=False)
    elif choice == 1:
        levels = 32.0
        x01 = (x + 1) / 2
        x01 = torch.round(x01 * levels) / levels
        x01 = x01 + (torch.rand_like(x01) - 0.5) * (1.0 / levels)
        x = x01 * 2 - 1
    elif choice == 2:
        b = torch.empty(1).uniform_(-0.05, 0.05).item()
        c = torch.empty(1).uniform_(0.9, 1.1).item()
        x = torch.clamp(x * c + b, -1, 1)
    else:
        k = torch.ones(3, 1, 3, 3, device=x.device) / 9.0
        x = F.conv2d(x, k, padding=1, groups=3)
    return torch.clamp(x, -1, 1)


class PishShieldV2:
    VAE_REPOS = [
        "stabilityai/sd-vae-ft-mse",
        "stabilityai/stable-diffusion-2-1",
        "madebyollin/sdxl-vae-fp16-fix",
    ]

    def __init__(self, device: torch.device = None, use_face_loss: bool = False):
        from diffusers import AutoencoderKL

        self.device = device or get_optimal_device()
        print(f"[*] Platform: {describe_platform(self.device)}")
        print("[*] Loading VAE ensemble (first run downloads weights, be patient)...")

        self.vaes = []
        for repo in self.VAE_REPOS:
            try:
                kwargs = {"torch_dtype": torch.float32}
                if "stable-diffusion-2-1" in repo:
                    kwargs["subfolder"] = "vae"
                vae = AutoencoderKL.from_pretrained(repo, **kwargs).to(self.device)
                vae.eval()
                for p in vae.parameters():
                    p.requires_grad = False
                self.vaes.append(vae)
                print(f"    [OK] {repo}")
            except Exception as e:
                print(f"    [skip] {repo} unavailable ({e})")

        if not self.vaes:
            raise RuntimeError("No VAE could be loaded. Check your internet connection.")

        self.to_tensor = transforms.ToTensor()
        self.to_pil = transforms.ToPILImage()
        self.loss_fn = nn.MSELoss()

        self.use_face_loss = use_face_loss
        self.face_model = None
        self.mtcnn = None
        if use_face_loss:
            try:
                from facenet_pytorch import MTCNN, InceptionResnetV1
                self.mtcnn = MTCNN(image_size=160, margin=0, device=self.device)
                self.face_model = InceptionResnetV1(pretrained="vggface2").eval().to(self.device)
                for p in self.face_model.parameters():
                    p.requires_grad = False
                print("    [OK] Face-embedding model (facenet) loaded")
            except Exception as e:
                print(f"    [skip] Face-embedding attack unavailable ({e})")
                self.use_face_loss = False

    def _multi_vae_loss(self, adv_pm1: torch.Tensor, target_latents: list) -> torch.Tensor:
        total = 0.0
        for vae, target_latent in zip(self.vaes, target_latents):
            latent = vae.encode(adv_pm1).latent_dist.sample()
            total = total + self.loss_fn(latent, target_latent)
        return total / len(self.vaes)

    def protect(self, input_path: Path, output_path: Path, eps: float, alpha: float,
                steps: int, eot_prob: float = 0.5):
        output_path.parent.mkdir(parents=True, exist_ok=True)

        orig_img = Image.open(input_path).convert("RGB")
        w, h = orig_img.size
        max_dim = 768
        if max(w, h) > max_dim:
            orig_img.thumbnail((max_dim, max_dim), Image.Resampling.LANCZOS)
            w, h = orig_img.size
        w, h = (w // 8) * 8, (h // 8) * 8
        orig_img = orig_img.resize((w, h), Image.Resampling.LANCZOS)

        orig01 = self.to_tensor(orig_img).unsqueeze(0).to(self.device)
        orig_pm1 = orig01 * 2.0 - 1.0

        mask = build_texture_mask(orig01)
        eps_map = eps * mask

        target_pm1 = torch.zeros_like(orig_pm1)
        target_latents = []
        with torch.no_grad():
            for vae in self.vaes:
                target_latents.append(vae.encode(target_pm1).latent_dist.sample())

        orig_face_embedding = None
        if self.use_face_loss:
            with torch.no_grad():
                face = self.mtcnn(orig_img)
                if face is not None:
                    orig_face_embedding = self.face_model(face.unsqueeze(0).to(self.device)).detach()
                else:
                    print("    [!] No face detected - skipping face-embedding term for this image")

        adv_pm1 = orig_pm1.clone().detach()
        adv_pm1 = adv_pm1 + torch.empty_like(adv_pm1).uniform_(-1, 1) * eps_map
        adv_pm1 = torch.clamp(adv_pm1, -1.0, 1.0)

        for _ in tqdm(range(steps), desc="Pish-Engin ensemble attack", unit="step"):
            adv_pm1.requires_grad = True

            if torch.rand(1).item() < eot_prob:
                attack_input = eot_transform(adv_pm1)
            else:
                attack_input = adv_pm1

            loss = self._multi_vae_loss(attack_input, target_latents)

            if self.use_face_loss and orig_face_embedding is not None:
                face_input = F.interpolate(attack_input, size=(160, 160),
                                            mode="bilinear", align_corners=False)
                cur_embedding = self.face_model(face_input)
                face_loss = -F.pairwise_distance(cur_embedding, orig_face_embedding).mean()
                loss = loss + 0.5 * face_loss

            loss.backward()

            with torch.no_grad():
                grad = adv_pm1.grad.sign()
                adv_pm1 = adv_pm1 - alpha * eps_map * grad
                perturbation = torch.clamp(adv_pm1 - orig_pm1, -eps_map, eps_map)
                adv_pm1 = torch.clamp(orig_pm1 + perturbation, -1.0, 1.0).detach()

        final01 = torch.clamp((adv_pm1.squeeze(0).cpu() + 1.0) / 2.0, 0.0, 1.0)
        out_img = self.to_pil(final01)
        out_img.save(str(output_path), format="PNG")
        print(f"\n[OK] Protected photo saved:")
        print(f"    {output_path.resolve()}\n")
        print("[!] Reminder: this raises the cost of AI misuse, it does not")
        print("    guarantee protection against every current/future model.")


def main():
    parser = argparse.ArgumentParser(prog="Pish-Engin", description="Pish-Engin: Multi-Model Anti-AI Image Cloak")
    parser.add_argument("-i", "--input", type=str, default=None, help="Input photo path")
    parser.add_argument("-o", "--output", type=str, default=None, help="Output path")
    parser.add_argument("-s", "--strength", choices=["low", "medium", "high"], default="medium")
    parser.add_argument("--steps", type=int, default=40, help="Optimization steps (default: 40)")
    parser.add_argument("--face", action="store_true", help="Also attack face-recognition embeddings (deepfake/face-swap defense)")
    parser.add_argument("--eot-prob", type=float, default=0.5, help="Probability of applying EOT transform each step")
    args = parser.parse_args()

    input_path = sanitize_path(args.input) if args.input else interactive_picture_prompt()

    if args.output:
        output_path = sanitize_path(args.output)
    else:
        downloads_dir = get_downloads_folder()
        output_path = downloads_dir / f"{input_path.stem}_pish_engin.png"

    strength_config = {
        "low": 0.03,
        "medium": 0.06,
        "high": 0.10,
    }
    eps = strength_config[args.strength]
    alpha = eps / 4.0

    engine = PishShieldV2(use_face_loss=args.face)
    engine.protect(input_path, output_path, eps, alpha, args.steps, eot_prob=args.eot_prob)


if __name__ == "__main__":
    main()
