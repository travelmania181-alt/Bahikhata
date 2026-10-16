# Bahikhata

A native Android personal ledger built with Kotlin and Jetpack Compose. Record
dated income and expenses, review monthly totals, and keep an Excel workbook in
Google Drive in sync. Transactions are stored on-device first; Drive sync runs
when connected to the internet.

## Requirements

- Android Studio with Android SDK Platform 36 installed
- JDK 17 or newer
- An Android device or emulator with Google Play services for Drive sign-in

Open this directory in Android Studio, let Gradle sync, then run the `app`
configuration. From a terminal, build with `./gradlew :app:assembleDebug`.

## Google Drive setup

1. Create or select a project in the Google Cloud Console and enable the Google
	Drive API.
2. Configure the OAuth consent screen for the app.
3. Add an Android OAuth client with package name `com.bahikhata` and the SHA-1
	signing certificate fingerprint. Run `./gradlew :app:signingReport` to find
	the debug fingerprint. For the APK built in this workspace, use
	`C9:F8:7E:83:90:F7:7A:4A:B0:7B:2B:A5:E4:F0:CB:1C:23:83:6B:23`.
4. If the consent screen is in testing mode, add the Google account you'll use
	on the phone as a test user.
5. Install and open the app, then tap **Connect** and approve Drive access.

The app creates `Bahikhata Ledger.xlsx` in the signed-in user's Drive. It keeps
the Drive file ID and replaces that workbook's transaction sheet on subsequent
syncs. No OAuth secret is stored in the app. Amounts currently use Indian rupees
(INR).
