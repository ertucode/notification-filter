FROM eclipse-temurin:21-jdk-jammy

ARG ANDROID_TOOLS=11076708
ARG ANDROID_API=35
ARG BUILD_TOOLS=35.0.0
ARG LEGACY_BUILD_TOOLS=34.0.0
ARG GRADLE_VERSION=8.10.2

ENV ANDROID_HOME=/opt/android-sdk \
    PATH=/opt/gradle/bin:/opt/android-sdk/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:$PATH

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl unzip \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p "$ANDROID_HOME/cmdline-tools" /opt/gradle \
    && curl -fsSL "https://dl.google.com/android/repository/commandlinetools-linux-${ANDROID_TOOLS}_latest.zip" -o /tmp/android-tools.zip \
    && unzip -q /tmp/android-tools.zip -d /tmp/android-tools \
    && mv /tmp/android-tools/cmdline-tools "$ANDROID_HOME/cmdline-tools/latest" \
    && curl -fsSL "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" -o /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip -d /opt \
    && mv "/opt/gradle-${GRADLE_VERSION}"/* /opt/gradle/ \
    && yes | sdkmanager --licenses >/dev/null \
    && sdkmanager "platform-tools" "platforms;android-${ANDROID_API}" \
        "build-tools;${BUILD_TOOLS}" "build-tools;${LEGACY_BUILD_TOOLS}" \
    && rm -rf /tmp/android-tools* /tmp/gradle.zip

WORKDIR /workspace
CMD ["gradle", "--no-daemon", "assembleDebug"]
