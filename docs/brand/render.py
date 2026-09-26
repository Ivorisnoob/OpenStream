"""Renders the release art in this folder to PNG (and JPG where noted) with headless Microsoft Edge.

    python docs/brand/render.py            # everything
    python docs/brand/render.py cover      # only pieces whose name starts with "cover"

Output goes to docs/brand/export/.
"""
import pathlib
import shutil
import subprocess
import sys
import tempfile

from PIL import Image

HERE = pathlib.Path(__file__).resolve().parent
OUT = HERE / "export"
EDGE_CANDIDATES = [
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    shutil.which("msedge") or "",
    shutil.which("google-chrome") or "",
]

# name, page (with query), width, height, scales, also export JPG
# Each piece renders twice: with Japanese accents (the default) and English only ("-en").
BASE = [
    ("cover", "cover.html", 1280, 640, [1, 2], False),
    *[(f"card-{i}", f"card.html?card={i}", 1080, 1350, [1], True) for i in range(1, 7)],
    ("changelog", "changelog.html", 1080, 3056, [1], True),
]
PIECES = [
    (name + ("-en" if lang == "en" else ""), page + ("&" if "?" in page else "?") + f"lang={lang}",
     width, height, scales, jpg)
    for name, page, width, height, scales, jpg in BASE
    for lang in ("ja", "en")
]


def edge() -> str:
    for path in EDGE_CANDIDATES:
        if path and pathlib.Path(path).exists():
            return path
    sys.exit("Microsoft Edge (or Chrome) not found")


def render(name, page, width, height, scale, browser):
    url = (HERE / page.split("?")[0]).as_uri() + ("?" + page.split("?")[1] if "?" in page else "")
    suffix = "" if scale == 1 else f"@{scale}x"
    target = OUT / f"{name}{suffix}.png"
    with tempfile.TemporaryDirectory() as profile:
        subprocess.run([
            browser, "--headless=new", "--disable-gpu", "--hide-scrollbars",
            f"--user-data-dir={profile}", "--allow-file-access-from-files",
            f"--window-size={width},{height}", f"--force-device-scale-factor={scale}",
            "--virtual-time-budget=8000", "--run-all-compositor-stages-before-draw",
            f"--screenshot={target}", url,
        ], check=True, capture_output=True)
    return target


def main():
    only = sys.argv[1] if len(sys.argv) > 1 else ""
    OUT.mkdir(exist_ok=True)
    browser = edge()
    for name, page, width, height, scales, jpg in PIECES:
        if not name.startswith(only):
            continue
        for scale in scales:
            png = render(name, page, width, height, scale, browser)
            image = Image.open(png).convert("RGB")
            image.save(png, optimize=True)
            line = f"{png.name}: {image.size[0]}x{image.size[1]}, {png.stat().st_size // 1024} KB"
            if jpg:
                jpeg = png.with_suffix(".jpg")
                image.save(jpeg, quality=90, optimize=True, progressive=True)
                line += f"; {jpeg.name}: {jpeg.stat().st_size // 1024} KB"
            print(line)


if __name__ == "__main__":
    main()
