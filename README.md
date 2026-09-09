# 2c client web

Coming soon. The web client is actively being worked on and will be published when it is ready.

# 2c client android

2c client android is an independent, third-party twocents client built specifically for Android. It is not an official twocents product, is not affiliated with, maintained by, sponsored by, or endorsed by twocents, and its developers have no role in operating the twocents service.

The application was built substantially with AI-generated code under human direction, testing, and design feedback. Expect rough edges while it remains in active development, and review the source before trusting it with your account.

The goal is a more polished Android experience: lightweight, fast, data-conscious, responsive, reactive, and intuitive, while retaining the visual identity and functionality people expect from twocents.

## Current status

Latest Android release: **v0.1.6**. See the [release notes](https://github.com/fr0meup/2c-client-reworked/releases/tag/v0.1.6) for details.

The Android client supports the functionality of the official twocents application with the current exceptions of creating transactions and budgets. Those features are not currently available to the developer in the EU, which means their behavior and API contracts cannot yet be tested or implemented reliably. They will be added as soon as access makes a correct implementation possible.

Existing transaction and budget content may still be displayed where the available response data permits it.

The app is under active development. Bugs, incomplete edge cases, and upstream API changes are possible.

## Download and install

If you do not want to build the application yourself, download the latest signed Android APK here:

### [Download the latest 2c client android APK](https://github.com/fr0meup/2c-client-reworked/releases/latest/download/2c-client-android.apk)

The download link always targets the `2c-client-android.apk` asset attached to the newest GitHub Release. This application is for Android only; an APK cannot be installed on iPhone or iPad.

To install it:

1. Download the APK on your Android device.
2. Open the downloaded file from your browser or file manager.
3. If Android blocks it, allow **Install unknown apps** for the browser or file manager you used.
4. Return to the APK and tap **Install**.
5. For later updates, download the newest APK and install it over the existing app. Starting with v0.1.3, the app checks GitHub when opened or brought to the foreground and offers an update when available. You can also use **Settings → Check for updates**. Downloads and installation start only when you choose them.

Android may display a warning because the application is distributed directly through GitHub rather than an app store. Before installing, confirm that the download came from this repository’s Releases page and compare its SHA-256 checksum with the checksum published in the release notes.

Do not uninstall the app when updating unless the release notes explicitly require it. Installing a newer APK over the existing installation preserves local settings and data as long as both APKs use the same application ID and release-signing key.

## Main features

- Feeds and topics, including new, hot, following, polls, picks, and topic feeds.
- Posts, nested comments, replies, quoting, voting, bookmarks, polls, Likert posts, links, images, GIFs, and video.
- Profiles, aliases, following, user activity, net-worth history, and leaderboards.
- Notifications with direct navigation to the relevant post, comment, profile, room, or message.
- Rooms, direct messages, replies, reactions, typing indicators, media, unread markers, custom rooms, and room discovery.
- Native Android push notifications, launcher badges, deep links, media viewing, downloads, and app settings.
- Portable local import and export for supported app data, preferences, complete search indexes, drafts with their media attachments, GIF collections, and statistics.

## Additional client features

### Advanced search

Advanced search maintains a persistent local post index and can search and filter across considerably more data than a normal feed. Results can be narrowed by people, topic, date range, post attributes, gender, age, net worth, votes, and other available metadata. Incremental scans look for newer posts instead of rebuilding a completed index every time.

### Custom GIF library

Save, favorite, organize, bulk-import, preview, and reuse your own GIFs and images from the mobile composer. Saved originals are retained locally outside the temporary image cache, with their URLs kept as a fallback. Removing an item removes its saved file too. App-data exports include downloaded GIF originals as well as library entries.

### Link previews

Web links display cached preview cards in posts, comments, profile comments, and chats. Cards use available page metadata for a title, description, and image, with a clickable URL fallback. Duplicate destinations are combined; X posts and direct media retain their dedicated rendering. Requests and cache sizes are bounded to limit data use. Some sites block previews or require login, and oversized preview images fall back to text cards.

### Followers discovery

twocents does not expose a complete follower-list endpoint. The client can build a candidate list from users visible in rooms, DMs, leaderboards, following data, and the local search index, then check those candidates to discover who follows you. This is a best-effort scan, can take several minutes, may be incomplete, and may temporarily encounter service rate limits.

Follow notifications also add people to the saved followers list and retain their UUIDs for future scans. Rescanning checks whether they still follow you, including people who later unfollow. These lists are included in app-data backups.

### Drafts

Save unfinished posts locally and return to them later, including supported post options and media attachments. Portable `.2cbackup` exports store draft media as raw files alongside the backup manifest, allowing it to survive uninstalling and reinstalling the app.

### Custom group chats

Create custom rooms and group DMs, join supported rooms, and share invite links from within the Android client.

### Mentions

Mention people while writing posts, comments, and chat messages. Type **@** to choose someone you follow, search by their assigned nickname, or enter a user UUID to find someone outside your following list. Mentions render as clickable names or net worths that open the person's profile.

### Mobile picks cards

Picks include a probability-history graph, resolution status, compact Yes/No choices, and average-net-worth results. Swipe horizontally on the graph to inspect historical probabilities; vertical swipes scroll the feed. Average net worth and vote percentages stay hidden until you vote or the pick resolves. Graph probabilities and voter percentages are distinct measurements.

### Local activity statistics

The sidebar records available daily and weekly activity such as upvotes and follower changes. The history is reconciled against current account totals where possible and can be preserved through data export and import.

### Privacy and feed controls

- **Mute users:** hide someone’s posts from regular feeds without blocking them.
- **Show verified accounts only:** filter supported feeds to verified accounts while leaving saved bookmarks accessible.
- **Appear offline:** disconnect live presence, typing, notification, and chat sockets while retaining manual refresh and REST-based message sending.
- **Wi-Fi-only automatic media:** reduce mobile-data usage for automatic media preparation while keeping manual playback available.

### Self-follow and your own nickname

In **Edit profile**, optionally follow yourself and choose a nickname. Self-follow is off by default unless you already follow yourself. You can change the nickname or turn self-follow off there, then select **Save changes**. Nickname changes may take time to appear throughout the app; refresh or reopen the affected page if needed.

Following yourself also adds you to your saved Followers list without a scan. Turning it off removes your entry; the saved list is retained across restarts and included in data backups.

### Automatic self-upvotes

Optionally upvote your own newly created posts and comments automatically. This behavior is enabled by default and can be disabled in Settings.

### Better interaction navigation

Open the profile associated with an interaction directly from posts, comments, notifications, rooms, and supported activity surfaces. Notification actors remain navigable after a notification has been marked as read.

### Notification read controls

Mark notifications as read directly from the notification page without opening their destination, or mark all currently visible notification categories as read at once.

### ZWJ text tool

The composer includes an optional ZWJ tool that inserts Unicode zero-width joiners into selected text. It is an explicit editing tool and only modifies the selected text when invoked.

More features and refinements are planned. If you find a bug or have a feature request, contact **$16 on twocents** or open an issue in the [GitHub repository](https://github.com/fr0meup/2c-client-reworked).

## Security and privacy notes

- Do not share your twocents backup code, bearer token, secret key, release keystore, or signing passwords.
- Account credentials are handled by the app to communicate with twocents services. Push delivery uses Firebase Cloud Messaging.
- Local `.2cbackup` exports may contain private application data and raw draft media. Store them securely.
- The release signing key is not an account credential. It proves that future APK updates came from the same publisher.
- This repository must never contain a private signing key or real signing credentials.

## Technology

- Kotlin and Java 17
- Jetpack Compose and Material 3
- Kotlin coroutines
- OkHttp, JSON-RPC, and WebSockets
- Android Media3 / ExoPlayer
- Coil 3 for images, animated GIFs, and video thumbnails
- Firebase Cloud Messaging
- AndroidX Security Crypto
- Gradle with release optimization and R8 minification

The application currently targets Android SDK 36 and supports Android 8.0 (API 26) and newer.

## Build it yourself

### 1. Install the prerequisites

Install:

- Git
- JDK 17
- Android Studio, or the Android command-line tools
- Android SDK Platform 36
- Android SDK Build-Tools 36.0.0
- Android SDK Platform-Tools

You can install the required SDK packages from Android Studio under **Tools → SDK Manager**, or use `sdkmanager`.

Windows PowerShell:

```powershell
$env:ANDROID_HOME = "C:\Android\Sdk"
$env:JAVA_HOME = "C:\Program Files\Android\Android Studio\jbr"
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" --licenses
& "$env:ANDROID_HOME\cmdline-tools\latest\bin\sdkmanager.bat" "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

macOS:

```bash
export ANDROID_HOME="$HOME/Library/Android/sdk"
export JAVA_HOME="$(/usr/libexec/java_home -v 17)"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

Linux:

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export JAVA_HOME="/path/to/your/jdk-17"
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --licenses
"$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" "platforms;android-36" "build-tools;36.0.0" "platform-tools"
```

Adjust the SDK and JDK paths if yours are installed elsewhere. Add the exports to your shell profile if you want them to persist between terminal sessions.

### 2. Clone the repository

Windows PowerShell, macOS, or Linux:

```bash
git clone https://github.com/fr0meup/2c-client-reworked.git
cd 2c-client-reworked
cd 2c-client-android
```

The repository includes the Gradle wrapper, so a separate Gradle installation is unnecessary.

### 3. Confirm the project compiles

Windows PowerShell:

```powershell
$env:ANDROID_HOME = "C:\Android\Sdk"
.\gradlew.bat :app:compileReleaseKotlin --console=plain
```

macOS or Linux:

```bash
chmod +x ./gradlew
./gradlew :app:compileReleaseKotlin --console=plain
```

### 4. Create a private release-signing key

Every installable Android APK must be signed. Create one dedicated key and keep using that same key for future self-built updates; Android will reject an update signed by a different key. See Android's official [app-signing documentation](https://developer.android.com/studio/publish/app-signing) for the underlying requirements.

Windows PowerShell:

```powershell
New-Item -ItemType Directory -Force "$env:USERPROFILE\.android" | Out-Null
& "$env:JAVA_HOME\bin\keytool.exe" -genkeypair -v -keystore "$env:USERPROFILE\.android\2c-release.jks" -alias twocents -keyalg RSA -keysize 4096 -validity 10000
```

macOS:

```bash
mkdir -p "$HOME/.android"
"$JAVA_HOME/bin/keytool" -genkeypair -v -keystore "$HOME/.android/2c-release.jks" -alias twocents -keyalg RSA -keysize 4096 -validity 10000
```

Linux:

```bash
mkdir -p "$HOME/.android"
"$JAVA_HOME/bin/keytool" -genkeypair -v -keystore "$HOME/.android/2c-release.jks" -alias twocents -keyalg RSA -keysize 4096 -validity 10000
```

`keytool` will ask for strong passwords and certificate details. Back up the resulting `.jks` file and passwords in at least two secure locations. Losing this key means you cannot ship an update that installs over existing copies of the app.

### 5. Configure local signing

Copy the example file:

Windows PowerShell:

```powershell
Copy-Item .\signing.properties.example .\signing.properties
```

macOS or Linux:

```bash
cp signing.properties.example signing.properties
```

Edit `signing.properties` with the real absolute key path and credentials:

```properties
storeFile=C:/Users/your-name/.android/2c-release.jks
storePassword=your-keystore-password
keyAlias=twocents
keyPassword=your-key-password
```

On macOS or Linux, use a path such as `/Users/your-name/.android/2c-release.jks` or `/home/your-name/.android/2c-release.jks`.

Both `signing.properties` and private-key file formats are ignored by Git so local credentials stay outside source control.

Alternatively, CI can provide these four environment variables instead of a properties file:

```text
TWOCENTS_RELEASE_STORE_FILE
TWOCENTS_RELEASE_STORE_PASSWORD
TWOCENTS_RELEASE_KEY_ALIAS
TWOCENTS_RELEASE_KEY_PASSWORD
```

### 6. Build the signed release APK

Windows PowerShell:

```powershell
.\gradlew.bat :app:assembleRelease --console=plain
```

macOS or Linux:

```bash
./gradlew :app:assembleRelease --console=plain
```

The signed APK is written to:

```text
app/build/outputs/apk/release/app-release.apk
```

The release task intentionally fails when signing is missing, incomplete, or points to a nonexistent keystore. Compilation-only tasks continue to work without private signing material.

### 7. Verify the APK signature

Windows PowerShell:

```powershell
& "$env:ANDROID_HOME\build-tools\36.0.0\apksigner.bat" verify --verbose --print-certs ".\app\build\outputs\apk\release\app-release.apk"
```

macOS or Linux:

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs "app/build/outputs/apk/release/app-release.apk"
```

### 8. Install it on a connected Android device

Enable Developer Options and USB debugging on the device, connect it, accept the device authorization prompt, then confirm that ADB can see it.

Windows PowerShell:

```powershell
& "$env:ANDROID_HOME\platform-tools\adb.exe" devices
& "$env:ANDROID_HOME\platform-tools\adb.exe" install --streaming -r ".\app\build\outputs\apk\release\app-release.apk"
```

macOS or Linux:

```bash
"$ANDROID_HOME/platform-tools/adb" devices
"$ANDROID_HOME/platform-tools/adb" install --streaming -r "app/build/outputs/apk/release/app-release.apk"
```

`-r` updates an existing installation while retaining its local data. It only works when the installed APK and replacement APK use the same application ID and signing key. Anyone using an older debug-signed build must uninstall it once before installing the first production-signed release.

### 9. Build and install in one command

These commands build and sign the release through Gradle, then stream-install it. Installation only runs when the build succeeds.

Windows PowerShell:

```powershell
$env:ANDROID_HOME = "C:\Android\Sdk"; .\gradlew.bat :app:assembleRelease --console=plain; if ($LASTEXITCODE -eq 0) { & "$env:ANDROID_HOME\platform-tools\adb.exe" install --streaming -r ".\app\build\outputs\apk\release\app-release.apk" }
```

macOS or Linux:

```bash
./gradlew :app:assembleRelease --console=plain && "$ANDROID_HOME/platform-tools/adb" install --streaming -r "app/build/outputs/apk/release/app-release.apk"
```

## Contributing and reporting problems

When reporting a bug, include the app version, Android version, device model, steps to reproduce it, and relevant crash output with credentials and personal data removed. Never post backup codes, bearer tokens, secret keys, private exports, or keystore passwords in an issue.
