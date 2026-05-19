# SMS Permission Demo Video — Script

**Duration target:** 2 to 3 minutes.
**Where to upload:** YouTube as **Unlisted** (not Private — Google reviewers must be able to open the link).
**What to record on:** your phone's built-in screen recorder, in portrait, with mic on.

This is the video you'll link from the Permissions Declaration form in Play Console. It must clearly show the SMS permission is used only to read bank balance info and is essential to the app.

---

## Script (read aloud while recording)

### 0:00 — 0:15 — Intro on home screen
**Show:** your phone's home screen with the Balance Widget already placed.

**Say:**
> "Hi, this is a demo of Balance Widget. It's a simple home-screen widget that shows my current bank balance. It works by reading the SMS that my bank sends me whenever a transaction happens. Let me show you how that works."

---

### 0:15 — 0:35 — Open the app, show no account first
**Show:** tap the widget → app opens → empty state ("No account yet") with the "ADD ACCOUNT" button visible.

**Say:**
> "This is a fresh install. No balance yet — the app has no idea what's in my account. So the first thing I do is set up the account."

---

### 0:35 — 1:05 — Add an account
**Show:** tap ADD ACCOUNT → fill in:
- Label: `HDFC Savings`
- Account last 4 digits: `9504`
- Current balance: `84210`

Tap SAVE.

**Say:**
> "I give it a label, the last four digits of my account number, and my current balance — which I'm reading directly from my bank's own app or passbook. The app needs this starting point because bank SMS in India usually only show the transaction amount, not the running balance."

---

### 1:05 — 1:35 — Grant SMS permission, explain WHY
**Show:** scroll down to the Advanced section → tap "GRANT SMS ACCESS" → the system permission dialog appears → tap Allow.

**Say:**
> "Now the most important part. I'm granting the SMS permission. The app needs this for one and only one reason: when my bank sends me a transaction SMS, the app reads it on my phone, extracts the amount and direction — credit or debit — and updates the balance on the widget. The SMS message, the amount, the balance — none of this leaves my device. There is no server, there is no cloud, there is no login. Everything stays here on the phone."

---

### 1:35 — 2:10 — Show a real transaction arriving
**Show:** trigger a real bank SMS (do a ₹1 UPI transfer to yourself or use the test parser earlier — but for the video, use a REAL SMS to be credible).
The widget updates on the home screen.

**Say:**
> "Watch this — I just made a small transaction. My bank's SMS arrives in the messages app. The Balance Widget reads it and updates the home-screen widget instantly. You can see the balance and the transaction in the app too."

---

### 2:10 — 2:35 — Show data does not leave the device
**Show:** open Android Settings → Apps → Balance Widget → Mobile data & Wi-Fi → toggle **"Mobile data"** OFF and **"Wi-Fi"** OFF. Back to the app. Trigger another transaction. Widget still updates correctly.

**Say:**
> "To prove the app doesn't send anything anywhere, I'm turning off mobile data and Wi-Fi for this app entirely. Even with no internet, when a new bank SMS arrives, the widget still updates — because everything is parsed and stored locally on the phone."

---

### 2:35 — 2:50 — Closing
**Show:** the home screen with the widget.

**Say:**
> "That's the use case. SMS permission is essential for this app to do its single job — reading bank SMS to keep an on-device widget up to date. Thanks for reviewing."

---

## After recording

1. Trim with your phone's gallery / Photos editor (no fancy tools needed).
2. Upload to YouTube:
   - Title: `Balance Widget — SMS Permission Demo`
   - Visibility: **Unlisted**
   - Description: `Demo for Google Play Permissions Declaration — Balance Widget (com.sourav.balancewidget). The app uses RECEIVE_SMS and READ_SMS only to extract bank balance and transaction details for a home-screen widget. No data leaves the device.`
3. Copy the URL — that's what goes in the Play Console form.

## Common reviewer reasons for rejection — addressed in this script

- ✅ Showed the app's core feature requires the permission
- ✅ Showed there is no alternative that achieves the same UX
- ✅ Demonstrated data is local-only (offline test)
- ✅ Used a real bank SMS (not just a test injection) for credibility
- ✅ Explained the user benefit in plain English
