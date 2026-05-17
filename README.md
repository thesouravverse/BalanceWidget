# Balance Widget

An Android home-screen widget that shows your live bank balance, auto-updated from incoming bank notifications and SMS.

**Stack:** Kotlin · Jetpack Compose · Glance (widget) · Hilt · DataStore · NotificationListenerService · SMS BroadcastReceiver.
**Min SDK:** 26 · **Target SDK:** 34.

## How it works

1. App registers a `NotificationListenerService` and an SMS `BroadcastReceiver`.
2. When a bank message arrives (notification or SMS), the parser scans for "Avl Bal: Rs.XXX" using a regex tuned for HDFC (extensible to other banks).
3. Latest balance + history (last 100) are stored in DataStore.
4. The Glance widget reads from DataStore and shows the current balance on the home screen. It refreshes automatically whenever a new balance is parsed.

## Build (cloud-only — no local Android SDK needed)

This repo is set up to build on **GitHub Actions** because the dev machine cannot install the Android SDK. Every push to `main` triggers a debug APK build.

### First-time setup (one-shot)

1. Create a new GitHub repo (e.g. `balance-widget`).
2. From this folder:
   ```
   git init
   git add .
   git commit -m "feat: initial scaffold"
   git branch -M main
   git remote add origin https://github.com/<your-user>/balance-widget.git
   git push -u origin main
   ```
3. Go to the **Actions** tab on GitHub → wait 3–6 min → download the `BalanceWidget-debug-apk` artifact.
4. Transfer the `.apk` to your Android phone (Google Drive, email, USB).
5. On phone: **Settings → Apps → Special access → Install unknown apps** → enable for your file manager.
6. Tap the APK → install.

### Granting permissions on the phone

1. Open **Balance Widget**.
2. Tap **Open Notification Access** → toggle **Balance Widget** ON.
3. (Optional) Tap **Grant SMS access** → allow.
4. Long-press home screen → **Widgets** → find **Bank Balance** → drag to home.
5. Hit **Inject debit (test)** in the app — widget should immediately show ₹12,345.67.

## Iterating

- Edit code in VS Code.
- `git add . && git commit -m "..." && git push` — cloud builds new APK in 3–6 min.
- Re-download artifact, reinstall (Android keeps your data because package name is the same).

## Adding more banks

Open `app/src/main/java/com/sourav/balancewidget/parser/BalanceParser.kt`. The `balanceRegex` is already generic — most Indian banks follow the "Avl Bal" pattern. To support quirky formats, add more regex alternates.

## Releasing to Play Store

⚠️ **Heads up:** Reading SMS (`READ_SMS`/`RECEIVE_SMS`) is on Google Play's restricted permissions list. Apps with these permissions get rejected unless the app is a default SMS handler. For Play Store submission, **remove the SMS permissions and receiver from the manifest** — the NotificationListener path alone is Play-Store-compliant.

1. Bump `versionCode` and `versionName` in `app/build.gradle.kts`.
2. Add GitHub Secrets: `KEYSTORE_B64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. Tag a release: `git tag v0.1.0 && git push --tags`.
4. CI builds a signed `.aab`; download from Actions artifacts.
5. Upload to Play Console → Internal testing first.

## Privacy

100% on-device. No network calls, no telemetry. Notification + SMS content never leaves your phone.
