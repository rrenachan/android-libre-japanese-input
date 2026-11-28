#!/bin/bash
set -e
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export PATH=$JAVA_HOME/bin:$PATH
if [ "$1" = "clean" ]; then
  ./gradlew clean assembleDebug
else
  ./gradlew assembleDebug
fi
adb install -r app/build/outputs/apk/debug/app-debug.apk
