<!--
    SPDX-FileCopyrightText: Copyright 2024-2025 Arm Limited and/or its affiliates <open-source-office@arm.com>

    SPDX-License-Identifier: Apache-2.0
-->

# Speech to Text library
<!-- TOC -->
* [Speech to Text library](#speech-to-text-library)
  * [Prerequisites](#prerequisites)
  * [Configuration options](#configuration-options)
  * [Quick start](#quick-start)
    * [Neural network](#neural-network)
    * [To Build for Android with KleidiAI kernels](#to-build-for-android-with-kleidiai-kernels)
    * [To Build for Android without KleidiAI Kernels](#to-build-for-android-without-kleidiai-kernels)
      * [To test the above build please do the following:](#to-test-the-above-build-please-do-the-following)
    * [To Build for Linux (aarch64) with KleidiAI and SME kernels](#to-build-for-linux-aarch64_with-kleidiai-and-sme-kernels)
    * [To Build for Linux (aarch64) without KleidiAI Kernels](#to-build-for-linux-aarch64_without-kleidiai-kernels)
    * [To Build for native host](#to-build-for-native-host)
    * [To Build for macOS](#to-build-for-macos)
  * [Building and running tests](#building-and-running-tests)
    * [To Build a test executable](#to-build-a-test-executable)
    * [To Build a JNI lib](#to-build-a-jni-lib)
  * [Known Issues](#known-issues)
  * [Trademarks](#trademarks)
  * [License](#license)
<!-- TOC -->

This repo is designed for building an
[Arm® KleidiAI™](https://www.arm.com/markets/artificial-intelligence/software/kleidi)
enabled STT library using CMake build system. It intends to provide an abstraction for [whisper.cpp](https://github.com/ggml-org/whisper.cpp) framework and it has Arm® KleidiAI™ backend available. In future, we **may** add support for other frameworks and models.

The backend library (selected at CMake configuration stage) is wrapped by this project's thin C++ layer that could be used
directly for testing and evaluations. However, JNI bindings are also provided for developers targeting Android™ based
applications.

## Prerequisites

* CMake 3.27 or above installed
* Android™ NDK v27.1.12297006  (if building for Android™)
* Python 3.9 or above installed, python is used to download test resources and models
* NDK_PATH set to point at the install location of the Android™ NDK
* aarch64 toolchain (Tested with v11.2-2022.02)

## Configuration options

The project is designed to download the required software sources based on user
provided configuration options.

- `STT_DEP_NAME`: Can be `whisper.cpp` only in current implementation. Other options may be added later.
- `WHISPER_SRC_DIR`: Path to the local source directory for the `whisper.cpp` dependency.
- `WHISPER_GIT_URL`: Git repository URL used to clone the `whisper.cpp` dependency.
- `WHISPER_GIT_TAG`: Specific git tag to use for the `whisper.cpp` dependency.

## Quick start

By default, the JNI builds are enabled, and Arm® KleidiAI™ kernels are enabled on arm64/aarch64.
To disable these, configure with: `-DGGML_CPU_KLEIDIAI=OFF`.

### Neural network

This project uses the **ggml-base.en model** as its default network. The model is not quantized.
To strike a balance between computational efficiency and model performance, you can use the **Q4_0 quantization format**.
To quantize your model, you can use the [whisper.cpp quantize tool](https://github.com/ggml-org/whisper.cpp/tree/v1.7.4?tab=readme-ov-file#quantization).

- You can access the model from [Hugging Face](https://huggingface.co/ggerganov/whisper.cpp/blob/main/ggml-base.en.bin).
- The default model configuration is declared in the [`requirements.json`](scripts/py/requirements.json) file.

However, any model supported by the backend library could be used.
> **NOTE**: Currently only Q4_0 models are accelerated by Arm® KleidiAI™ kernels in whisper.cpp.

### To Build for Android with KleidiAI kernels

```shell
cmake -B build \
    -DCMAKE_TOOLCHAIN_FILE=${NDK_PATH}/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-33 \
    -DCMAKE_C_FLAGS=-march=armv8.2a+i8mm+dotprod+fp16 \
    -DCMAKE_CXX_FLAGS=-march=armv8.2a+i8mm+dotprod+fp16 \
    -DBUILD_SHARED_LIBS=false \
    -DTEST_DATA_DIR="/data/local/tmp" \
    -DTEST_MODELS_DIR="/data/local/tmp" \
    -DGGML_OPENMP=OFF

cmake --build ./build
```

### To Build for Android without KleidiAI Kernels

```shell
cmake -B build \
    -DCMAKE_TOOLCHAIN_FILE=${NDK_PATH}/build/cmake/android.toolchain.cmake \
    -DANDROID_ABI=arm64-v8a \
    -DANDROID_PLATFORM=android-33 \
    -DCMAKE_C_FLAGS=-march=armv8.2a+i8mm+dotprod+fp16 \
    -DCMAKE_CXX_FLAGS=-march=armv8.2a+i8mm+dotprod+fp16 \
    -DBUILD_SHARED_LIBS=true \
    -DGGML_CPU_KLEIDIAI=OFF \
    -DTEST_DATA_DIR="/data/local/tmp" \
    -DTEST_MODELS_DIR="/data/local/tmp" \
    -DGGML_OPENMP=OFF

cmake --build ./build
```

#### To test the above build please do the following:

Firstly push the newly built test executable:
```shell
adb push build/bin/stt-cpp-tests /data/local/tmp
```

Next push the newly built libs:
```shell
adb push build/lib/* /data/local/tmp
```

Next you will need to shell to your device:
```shell
adb shell
cd data/local/tmp
export LD_LIBRARY_PATH=./
```

Finally just run the test executable:
```shell
./stt-cpp-tests
```

### To Build for Linux (aarch64) with KleidiAI and SME kernels

To build with SME kernels, ensure `GGML_CPU_ARM_ARCH` is set with needed feature flags as below:
```shell
cmake -B build \
    --preset=elinux-aarch64-release-with-tests \
    -DGGML_CPU_ARM_ARCH=armv8.2-a+dotprod+i8mm+sve+sme+fp16 \
    -DGGML_CPU_KLEIDIAI=ON \
    -DBUILD_EXECUTABLE=ON

cmake --build ./build
```

Once built, a standalone application can be executed to get performance.

Set `GGML_KLEIDIAI_SME=1` to enable the use of SME kernels during execution:

```shell
GGML_KLEIDIAI_SME=1 ./build/bin/whisper-cli -m resources_downloaded/models/model.bin /path/to/audio/audiofile.wav
```

To run without invoking SME kernels, set `GGML_KLEIDIAI_SME=0` during execution:

```shell
GGML_KLEIDIAI_SME=0 ./build/bin/whisper-cli -m resources_downloaded/models/model.bin /path/to/audio/audiofile.wav
```
### To Build for Linux (aarch64) without KleidiAI Kernels

```shell
cmake -B build \
    -DCMAKE_TOOLCHAIN_FILE=cmake/toolchains/aarch64.cmake \
    -DCMAKE_C_FLAGS=-march=armv8.2-a+dotprod+i8mm+fp16 \
    -DCMAKE_CXX_FLAGS=-march=armv8.2-a+dotprod+i8mm+fp16 \
    -DGGML_CPU_KLEIDIAI=OFF
cmake --build ./build
```

### To Build for native host

```shell
cmake -B build --preset=native-release-with-tests
cmake --build ./build
```

### To Build for macOS
To build for the CPU backend on macOS®, you can use the native CMake toolchain. 
However, additional flags are required to explicitly disable the default backends:
```shell
cmake -B build --preset=native-release-with-tests \
    -DCMAKE_C_FLAGS=-march=armv8.2-a+dotprod+i8mm+fp16 \
    -DCMAKE_CXX_FLAGS=-march=armv8.2-a+dotprod+i8mm+fp16 \
    -DGGML_METAL=OFF \
    -DGGML_BLAS=OFF
cmake --build ./build
```

## Building and running tests

To build and test for native host machine:

```shell
cmake -B build --preset=native-release-with-tests
cmake --build ./build
ctest --test-dir ./build
```

### To Build a test executable

Add the option:
```
-DBUILD_EXECUTABLE=true
```

to any of the build commands above. For example

  ```shell
cmake -B build \
    -DCMAKE_TOOLCHAIN_FILE=scripts/cmake/toolchains/aarch64.cmake \
    -DCMAKE_C_FLAGS=-march=armv8.2-a+dotprod+i8mm+fp16 \
    -DCMAKE_CXX_FLAGS=-march=armv8.2-a+dotprod+i8mm+fp16 \
    -DBUILD_EXECUTABLE=true
cmake --build ./build
```

This will produce an executable, which you can use to test your build under :
```
/build/bin/whisper-cli
```

You can run this executable and test an audio file using the following:
```
./whisper-cli -m resources_downloaded/models/model.bin /path/to/audio/audiofile.wav
```

### To Build a JNI lib

Add the options:
```
-DBUILD_JNI_LIB=true
```

to run the sample test `WhisperTestApp.java` run the following commands post-build 
```
ctest --test-dir ./build
```

## Known Issues
**Transcription speed** - We are working on improving this, approximately 2x slower than expected currently

## Trademarks

* Arm® and KleidiAI™ are registered trademarks or trademarks of Arm® Limited (or its subsidiaries) in the US and/or
  elsewhere.
* Android™ is a trademark of Google LLC.
* macOS® is a trademark of Apple Inc.

## License

This project is distributed under the software licenses in [LICENSES](LICENSES) directory.
