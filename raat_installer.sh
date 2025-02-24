#!/bin/bash

set -e  # Stop the script on error

echo "RAAT Installer: Automatic APK and server installation on Arch Linux"

# 1. Check that the system is Arch Linux
if ! grep -q "Arch" /etc/os-release; then
    echo "Error: This script supports only Arch Linux"
    exit 1
fi

#2. Install dependencies (Android SDK, Gradle, JDK, AUR helper)
echo "Installing dependencies..."
sudo pacman -S --needed --noconfirm \
    jdk-openjdk gradle android-sdk android-platform-tools git yay

# 3. Clone and build the Android client (from RAAT-main)
echo "📲 Cloning the client-side (Android)..."
rm -rf ~/raat-client
git clone https://github.com/Student-Team-Projects/RAAT-main.git ~/raat-client
cd ~/raat-client

echo "⚙️ Building the APK..."
./gradlew assembleRelease

APK_PATH=$(find app/build/outputs/apk/release -name "*.apk" | head -n 1)
if [[ -f "$APK_PATH" ]]; then
    echo "APK successfully built: $APK_PATH"
else
    echo "Error: APK was not built!"
    exit 1
fi
cd ~  # Return to the home directory

# 4. Install the server-side (`raat-server`) via AUR
echo "Installing the server-side..."
yay -S --noconfirm raat-server

# 5. Final instructions for the user
echo "Installation completed!"
echo "To start the server, run: raat-server"
echo "To install the APK on Android, connect your phone and run:"
echo "  adb install $APK_PATH"
