#!/bin/bash

# Script to generate app icon and splash screen from a square input image
# Usage: ./generate_assets.sh input_image.png

set -e

if [ $# -ne 1 ]; then
    echo "Usage: $0 <input_image.png>"
    echo "Input image should be square (e.g., 1024x1024)"
    exit 1
fi

INPUT_IMAGE="$1"

if [ ! -f "$INPUT_IMAGE" ]; then
    echo "Error: Input file '$INPUT_IMAGE' not found"
    exit 1
fi

# Check if ImageMagick is installed
if ! command -v magick &> /dev/null; then
    echo "Error: ImageMagick is not installed. Please install it first."
    exit 1
fi

echo "Generating assets from: $INPUT_IMAGE"

# Get the dimensions of the input image
DIMENSIONS=$(identify -format "%wx%h" "$INPUT_IMAGE")
WIDTH=$(echo $DIMENSIONS | cut -d'x' -f1)
HEIGHT=$(echo $DIMENSIONS | cut -d'x' -f2)

if [ "$WIDTH" != "$HEIGHT" ]; then
    echo "Warning: Input image is not square ($DIMENSIONS). Results may be unexpected."
fi

# Define paths
RES_DIR="app/src/main/res"
DRAWABLE_DIR="$RES_DIR/drawable"

# Create directories if they don't exist
mkdir -p "$DRAWABLE_DIR"

# Icon sizes for different densities (Android Wear OS launcher icons)
# Wear OS uses smaller icon sizes than regular Android
declare -A ICON_SIZES=(
    ["mipmap-mdpi"]=48
    ["mipmap-hdpi"]=72
    ["mipmap-xhdpi"]=96
    ["mipmap-xxhdpi"]=144
    ["mipmap-xxxhdpi"]=192
)

echo ""
echo "Generating launcher icons..."

for density in "${!ICON_SIZES[@]}"; do
    size=${ICON_SIZES[$density]}
    output_dir="$RES_DIR/$density"
    mkdir -p "$output_dir"

    # Generate round icon (Wear OS primarily uses round icons)
    echo "  - $density: ${size}x${size} (ic_launcher_round.png)"
    magick "$INPUT_IMAGE" -resize "${size}x${size}" \
        \( +clone -threshold -1 -negate -fill white -draw "circle $((size/2)),$((size/2)) $((size/2)),0" \) \
        -alpha off -compose copy_opacity -composite \
        "$output_dir/ic_launcher_round.png"

    # Also generate square icon for compatibility
    echo "  - $density: ${size}x${size} (ic_launcher.png)"
    magick "$INPUT_IMAGE" -resize "${size}x${size}" "$output_dir/ic_launcher.png"
done

echo ""
echo "Generating splash screen..."

# Generate splash screen (1024x1024 with circular mask)
# This creates a 720x720 image centered in a 1024x1024 canvas with circular crop
magick "$INPUT_IMAGE" -resize 720x720^ -gravity center -extent 1024x1024 \
    \( +clone -threshold -1 -negate -fill white -draw "circle 512,512 512,152" \) \
    -alpha off -compose copy_opacity -composite \
    "$DRAWABLE_DIR/splash_screen.png"

echo "  - drawable/splash_screen.png (1024x1024)"

echo ""
echo "✓ Asset generation complete!"
echo ""
echo "Generated files:"
echo "  - Launcher icons in $RES_DIR/mipmap-*/"
echo "  - Splash screen in $DRAWABLE_DIR/"
