#!/bin/bash

set -e  # Stop the script on error

# Updating system(could be necessary to find packages)
# sudo pacman -Syu

# Refreshing mirrors and databases
sudo pacman -S reflector
sudo reflector --latest 10 --sort rate --save /etc/pacman.d/mirrorlist
sudo pacman -Syyu

# Installing Java SDK
sudo pacman -S jdk11-openjdk

export JAVA_HOME=/usr/lib/jvm/java-11-openjdk
java -version

# Installing AUR helper
sudo pacman -S --needed base-devel git
git clone https://aur.archlinux.org/yay.git
cd yay
makepkg -si

# Installing Android SDK
yay -S android-studio

# Set path to the installed SDK, needed to build the application
export ANDROID_HOME=~/Android/Sdk # placed here by default
# export ANDROID_HOME=/opt/android-sdk # Could land here

# Download the application repo
rm -rf RAAT
git clone https://github.com/Student-Team-Projects/RAAT.git
cd RAAT

# Building the application
./gradlew assembleRelease

# Install the server-side (`raat-server`) via AUR
echo "Installing the server-side..."
yay -S --noconfirm raat-server
