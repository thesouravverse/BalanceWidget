# Play Console Submission Crib Sheet

Copy-paste everything below into the matching field in Play Console when your developer account is verified.

---

## 1. Create app

| Field | Value |
|---|---|
| App name | `Balance Widget` |
| Default language | `English (United States) – en-US` |
| App or game | `App` |
| Free or paid | `Free` |
| Declarations (Developer Program Policies, US export laws) | Tick both |

---

## 2. App content (left sidebar in Play Console)

### Privacy policy
- **URL:** `https://thesouravverse.github.io/publicprivacypolicy/`

### App access
- **All or some functionality is restricted?** → **All functionality is available without special access.** (No login.)

### Ads
- **Does your app contain ads?** → **No**

### Content rating
Run the questionnaire. Likely answers:
- Category: **Utility, Productivity, Communication, or Other**
- Violence: **No**
- Sexual content: **No**
- Profanity: **No**
- Drugs / alcohol / tobacco: **No**
- Gambling: **No**
- User-generated content: **No**
- Users can interact / share location: **No**
- Digital purchases: **No**
- Result will be **Rated for 3+ / Everyone**.

### Target audience
- **Target age groups:** `18 and over` (it's a banking utility)
- **Appeals to children?** → **No**

### News app
- **Is this a news app?** → **No**

### COVID-19 contact tracing & status
- **Is this a contact-tracing/status app?** → **No**

### Data safety
**Data collected:**
- **SMS messages:** ☑ Collected — Processed ephemerally
  - Why: App functionality (parse bank balance / transaction info from the SMS)
  - Is the data encrypted in transit? **Not applicable — data is not transmitted off device**
  - Can users request data deletion? **Yes, in-app (Reset everything button)**
  - Is collection required or optional? **Optional**
- **Financial info (in-app purchase history, other financial info):** ☑ Collected — Processed ephemerally
  - Specifically: account label, last 4 digits, balance, transaction amount
  - Why: App functionality (show on widget)
  - Shared with third parties? **No**
  - Processed off-device? **No**
- **App activity, App info & performance, Device or other identifiers:** ☐ Not collected
- **Personal info (name, email, address, user IDs, phone):** ☐ Not collected
- **Location:** ☐ Not collected
- **Photos, audio, contacts, calendar:** ☐ Not collected
- **Health & fitness:** ☐ Not collected

**Security practices:**
- Data is encrypted in transit? **N/A — no transmission**
- Users can request data deletion? **Yes** (in-app + uninstall)
- App follows Play Families Policy? **No (not a family app)**
- Independently security reviewed? **No**

### Government app
- **Is this a government app?** → **No**

### Financial features (NEW — required for any app touching financial info)
- **Does the app contain financial features?** → **Yes**
- **Which features?** → tick **"Personal finance management (e.g. budgeting, expense tracking)"** ONLY.
  Do NOT tick "Banking", "Payments", "Loans", "Crypto" — you don't do any of those.
- **Are you a regulated financial entity?** → **No**
- **Does the app charge users?** → **No**

### Health
- N/A — skip.

### Permissions declaration (the IMPORTANT one — SMS)
When you upload the AAB, Play will flag `READ_SMS` and `RECEIVE_SMS` as restricted permissions. You'll be asked to fill a form:

- **Core functionality:** `Read bank balance and transaction details from on-device SMS to display them on a home-screen widget. The app never transmits SMS content or any derived data off the user's device.`

- **Permission requested:**
  - `READ_SMS` — required so user can optionally scan past bank SMS to recover balance history after a re-calibration. Without it, only newly arriving SMS work.
  - `RECEIVE_SMS` — required so the widget can update in real time the moment a new bank SMS arrives.

- **Alternative methods considered:**
  - Notification Listener API (`BIND_NOTIFICATION_LISTENER_SERVICE`) is also implemented as a fallback, but SMS is required as the primary path because (a) many users disable bank-app notifications, (b) notifications are dismissable and unreliable, (c) on dual-SIM devices the bank notification often appears only when the bank's own app is logged in.

- **User benefit:** `User sees their live bank balance on the home screen without unlocking their banking app every time.`

- **Demo video URL:** (paste the unlisted YouTube URL of your 2-3 min demo)

---

## 3. Main store listing

### App name
`Balance Widget`

### Short description (80 chars max)
`Your bank balance, live on your home screen. Reads bank SMS on-device. No login.`

(79 chars ✓)

### Full description (4000 chars max)
```
Balance Widget puts your bank balance right on your home screen.

It reads your bank SMS messages on your phone, extracts the latest balance and transaction info, and shows them in a clean dark widget — without ever sending your data anywhere.

✦ HOW IT WORKS
1. Add your account: pick a label, enter the last 4 digits, and enter your current balance.
2. The widget reads incoming bank SMS in real time and updates your balance after every transaction.
3. Long-press your home screen → Widgets → drag "Balance Widget" anywhere you like.

✦ DESIGNED FOR INDIAN BANKS
Built and tested with HDFC Bank SMS formats first. Works with both savings accounts and credit cards. Multi-account support — keep your salary account, joint account and credit card all on one widget.

✦ PRIVACY-FIRST, NO LOGIN
• 100% on-device — your SMS and balance never leave your phone
• No login, no account, no email required
• No analytics, no ads, no third-party SDKs
• Open about what data is stored (just account label, last 4 digits, balance, transaction history)

✦ CRED-INSPIRED PREMIUM LOOK
Pitch-black canvas with a magenta-purple gradient hero, gold accents, and clean rounded cards. Adjust widget opacity from 10% to 100% to blend with your wallpaper.

✦ ALSO INCLUDED
• Smart parser that handles credit, debit and "current balance" SMS formats
• Recent transactions list grouped by account
• Re-calibrate any time if your tracked balance drifts
• Material You themed icon support on Android 13+
• Auto Backup via Google Drive — your accounts survive a phone change

✦ WHAT IT IS NOT
• Not a banking app — you can't transfer money or pay bills
• Not connected to your bank — it only reads SMS on your phone
• Not a tracker — it doesn't watch where you spend or with whom

Need help or have a feature request? Email thesouravverse@gmail.com.
```

### App icon
- Upload: PNG converted from `store-assets/play_store_icon_512.svg`
- 512×512, no transparency, no rounded corners

### Feature graphic
- Upload: PNG converted from `store-assets/play_feature_graphic_1024x500.svg`
- 1024×500, opaque

### Phone screenshots (need 2-8)
Capture these on your phone:
1. Home screen showing the widget with a balance (this is your hero shot — make it count)
2. Main app screen: hero balance card visible, account chip(s), one or two transactions
3. Multi-account view: 2 accounts in chips row, switching between them
4. Widget look settings: opacity slider visible
5. (Optional) Adding a new account dialog

Each: 1080×1920 portrait (default phone screenshot size).

### Category
- **App category:** `Finance`
- **Tags:** `widgets`, `personal finance`, `bank balance`, `expense tracking`

### Contact details
- **Email:** `thesouravverse@gmail.com`
- **Website:** (optional — can leave blank, or use `https://github.com/thesouravverse/BalanceWidget`)
- **Phone:** (optional, leave blank)

### External marketing
- **Allow Google to promote the app outside of Play:** Your call. Usually tick yes.

---

## 4. Release flow

### Internal testing
1. Releases → Internal testing → Create new release.
2. Upload the signed `.aab` (downloaded from your `release-bundle` GitHub Actions artifact).
3. **Release name:** `0.1.0 (1)` — Play will auto-fill from versionName / versionCode.
4. **Release notes:**
   ```
   <en-US>
   • First internal preview.
   • Live bank balance widget powered by SMS parsing.
   • Multi-account support, opacity control.
   </en-US>
   ```
5. Add yourself (`thesouravverse@gmail.com`) as a tester. Save → Review → Start rollout.
6. After ~10 min, open the opt-in link from Play Console on your phone, install the app from Play, smoke-test for 24h.

### Closed testing (mandatory 14-day phase for new dev accounts)
1. Recruit at least 12 testers. Friends + family + a couple of Reddit / Twitter folks who like utility apps. Their Gmail addresses go into the tester list.
2. Promote the internal release → Closed testing → "Alpha".
3. Wait 14 calendar days while at least 12 testers actually open the app.
4. Address any crash reports, push fixes through CI.

### Production
1. Promote alpha → Production.
2. Initial rollout: start with 20% so you can halt if something breaks.
3. Within 1-7 days Google reviews and either approves, asks questions, or rejects.

---

## 5. Pre-submission checklist

- [ ] App installs and launches on real device without crashing
- [ ] Widget shows up in widget picker
- [ ] Widget actually updates when a bank SMS arrives
- [ ] Reset everything works
- [ ] Privacy policy URL is live
- [ ] SMS permission demo video uploaded to YouTube as Unlisted
- [ ] 512×512 icon PNG ready
- [ ] 1024×500 feature graphic PNG ready
- [ ] ≥2 phone screenshots ready
- [ ] versionCode = 1, versionName = "0.1.0" on first upload
- [ ] release.jks backed up in 2 places
- [ ] All 4 GitHub Secrets saved
- [ ] keystore-gen.yml workflow file deleted from main
