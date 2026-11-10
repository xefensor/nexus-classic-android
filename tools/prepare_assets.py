#!/usr/bin/env python3
"""Derive small GPU textures from the unchanged AOSP background. Requires Pillow."""
from pathlib import Path
from PIL import Image

assets = Path(__file__).resolve().parents[1] / 'app/src/main/assets'
image = Image.open(assets / 'pyramid_background.png').convert('RGB')
image.crop((476, 392, 490, 406)).save(assets / 'tile.png')
image.resize((60, 50), Image.Resampling.BOX).save(assets / 'lighting.png')
