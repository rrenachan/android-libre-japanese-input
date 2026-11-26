# Libre Japanese Input for Android

![Screenshot](screenshot.png)

## What is this?

Libre Japanese Input is a WIP restoration and continuation of the standalone
[Google Japanese Input/mozc](https://github.com/google/mozc) app which has been
discontinued in favor of the monolithic gBoard app which requires an internet
connection. Unfortunately, its most recent, several-year old build has become
increasingly incompatible with recent devices and versions of Android,
motivating the creation of this project.

This project is not affiliated with Google.

## What works/What's been done already?

- Software text entry. Kana input and henkan work.

## What doesn't work/What still needs to be done?

- Lots of layout issues. Keyboard key text sizes are either too small or too big
  in some places. Occasional tofus rendered.
- Included emoji/emoticon sets are outdated. Emoji and symbol history are not
  saved.
- Cannot import or export user dictionaries.
- Hardware keyboard input needs testing.

## Development

First, ensure you have initialized all submodules.

```shell
git submodule update --init --recursive
```

The Libre Japanese Input dev environment is provided as a Nix flake and is very
easy to build with [Nix](https://nixos.org/).

```shell
nix develop
./gradlew installDebug
```

## Building the mozc JNI library and dataset

The best way to do this is via the [Docker setup described in the mozc
repo](https://github.com/google/mozc/blob/master/docs/build_mozc_in_docker.md).
Libre Japanese Input depends on the exact revision
`ddd9730b068387631e3b4d212314ef0ed93befe0`. Specific instructions are provided
below.

> Note (verified 2026-11-25 on WSL Ubuntu 24.04 + Docker Desktop): the upstream
> Mozc Dockerfile no longer builds as-is. The Bazel apt key URL returns 404,
> Bazel 9.x / 7.x are incompatible with rules_apple 1.1.3, `gcc` is missing
> from the image, and the Japan Post zip-code URLs return HTML instead of a
> zip. Apply the included patch before building the Docker image:
>
> ```shell
> git -C third_party/mozc apply ../../mozc-docker.patch
> ```
>
> The patch switches Bazel to Bazelisk 1.25.0 at `/usr/local/bin/bazel`, adds
> `gcc g++`, pins `USE_BAZEL_VERSION=6.4.0`, and switches the zip-code URLs
> to the GitHub mirror `hiroyuki-komatsu/japanpost_zipcode` at commit
> `33524763837473258e7ba2f14b17fc3a70519831`.

```shell
# Clone and setup Libre Japanese Input.
git clone https://github.com/elizagamedev/android-libre-japanese-input.git
cd android-libre-japanese-input
git submodule update --init --recursive

# Create Docker image.
cd third_party/mozc/docker/ubuntu22.04
docker build --rm --tag mozc_ubuntu22.04 .
docker create --interactive --tty --name mozc_build mozc_ubuntu22.04

# Build everything.
docker start mozc_build
docker exec -it mozc_build bash
# Check out the exact version of mozc that LJI uses.
git checkout ddd9730b068387631e3b4d212314ef0ed93befe0
git submodule deinit -f .
git submodule update --init --recursive
export USE_BAZEL_VERSION=6.4.0
bazel build --noenable_bzlmod package --config oss_android -c opt
bazel build --noenable_bzlmod //data_manager/oss:mozc_dataset_for_oss --config linux
exit

# Extract artifacts.
cd ../../../../app/src/main
find -L /home/mozc_builder/work/mozc/src/bazel-out -name "native_libs.zip" -o -name "mozc.data"
docker cp mozc_build:/home/mozc_builder/work/mozc/src/bazel-out/k8-opt/bin/android/jni/native_libs.zip .
docker cp mozc_build:/home/mozc_builder/work/mozc/src/bazel-out/k8-fastbuild/bin/data_manager/oss/mozc.data assets
ln -s jniLibs lib
unzip native_libs.zip
rm -f lib native_libs.zip

# Clean up Docker.
docker stop mozc_build
docker rm mozc_build
docker image rm mozc_ubuntu22.04
