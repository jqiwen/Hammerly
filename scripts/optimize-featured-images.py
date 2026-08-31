from pathlib import Path
import sys

from PIL import Image, ImageOps


OUTPUT_NAMES = {
    "tent.jpg": "big-agnes-copper-spur-tent.webp",
    "macbook.jpg": "macbook-pro-m3.webp",
    "backpack.jpg": "osprey-atmos-backpack.webp",
    "iphone.jpg": "iphone-15-pro.webp",
    "gaming-pc.jpg": "rtx-4080-gaming-pc.webp",
}


def main() -> None:
    if len(sys.argv) != 3:
        raise SystemExit("usage: optimize-featured-images.py SOURCE_DIR OUTPUT_DIR")

    source_dir = Path(sys.argv[1]).resolve(strict=True)
    output_dir = Path(sys.argv[2]).resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    for source_name, output_name in OUTPUT_NAMES.items():
        with Image.open(source_dir / source_name) as source:
            normalized = ImageOps.exif_transpose(source).convert("RGB")
            optimized = ImageOps.fit(
                normalized,
                (960, 720),
                method=Image.Resampling.LANCZOS,
                centering=(0.5, 0.5),
            )
            optimized.save(
                output_dir / output_name,
                "WEBP",
                quality=78,
                method=6,
                optimize=True,
            )


if __name__ == "__main__":
    main()
