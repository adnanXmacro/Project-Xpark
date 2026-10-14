# AGENTS.md

## Building this Android app (offline environment)

This project is a native Android app (Kotlin, Gradle Kotlin DSL, AGP 8.6.1).
The container ships with **no JDK and no Android SDK**, so a fresh session must
bootstrap them before running Gradle. Everything below is already installed in
the environment once these steps have been run.

### 1. Toolchain locations (already installed)

- JDK 17: `/usr/lib/jvm/java-17-openjdk-amd64`
- Android SDK: `/opt/android-sdk` (platform-tools, platforms;android-35, build-tools;35.0.0)

If missing, reinstall:

```bash
export DEBIAN_FRONTEND=noninteractive
apt-get update -qq
apt-get install -y -qq openjdk-17-jdk-headless
```

```bash
mkdir -p /opt/android-sdk/cmdline-tools
curl -sSL -o /tmp/cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
unzip -q /tmp/cmdtools.zip -d /opt/android-sdk/cmdline-tools
mv /opt/android-sdk/cmdline-tools/cmdline-tools /opt/android-sdk/cmdline-tools/latest
export ANDROID_HOME=/opt/android-sdk
yes | /opt/android-sdk/cmdline-tools/latest/bin/sdkmanager --licenses
/opt/android-sdk/cmdline-tools/latest/bin/sdkmanager "platform-tools" "platforms;android-35" "build-tools;35.0.0"
```

### 2. Build

The release signing config requires `keystore/keystore.properties` (present in
the repo) or `ANDROID_KEYSTORE_*` env vars. Gradle evaluates this at
configuration time, so it must resolve even for `assembleDebug`.

Memory is tight (~8 GB, no swap), so constrain the build:

```bash
export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
export ANDROID_HOME=/opt/android-sdk
export ANDROID_SDK_ROOT=/opt/android-sdk
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew assembleDebug \
  -Dorg.gradle.jvmargs="-Xmx2048m -XX:MaxMetaspaceSize=512m" \
  -Dkotlin.compiler.execution.strategy=in-process \
  --max-workers=1 --stacktrace
```

Output: `app/build/outputs/apk/debug/app-debug.apk`.

A full clean debug build takes ~12 minutes. `BUILD SUCCESSFUL` is the pass
signal. Transient `MessageIOException ... Unexpected type tag` lines from the
Gradle daemon and the many `D8: Unexpected error during rewriting of Kotlin
metadata` warnings are benign and pre-existing; the build still succeeds.

### 3. Verify an APK

```bash
/opt/android-sdk/build-tools/35.0.0/aapt2 dump badging app/build/outputs/apk/debug/app-debug.apk | head -3
```

### 4. CI

`.github/workflows/build.yml` runs `./gradlew assembleDebug` then
`assembleRelease` on GitHub-hosted runners with the SDK preinstalled.
