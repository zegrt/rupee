# axio (Walnut) — Competitor Analysis

**Subject:** `com.daamitt.walnut.app` v8.6.0 build 860 (rules version r126), targetSdk 33, minSdk 21, ~22 MB.
**Publisher:** Axio Digital Pvt. Ltd. (formerly Capital Float). Play Store title: *axio: Expense Tracker & Budget*. 4.4★ / 257K reviews at time of analysis.
**Source of facts in this doc:** static teardown of the APK at [com.daamitt.walnut.app_8.6.0-860…apk](com.daamitt.walnut.app_8.6.0-860_minAPI21(arm64-v8a,armeabi-v7a,x86,x86_64)(nodpi)_apkmirror.com.apk) using `apkanalyzer` (manifest, permissions, dex package map) and `jadx 1.5.5` (resources + full source decompile). All file/line refs below point into `/tmp/walnut-analysis/jadx-src/sources/` unless noted.

> One-line take: Walnut is *not* a competitor for what you're shipping. It's a 9-year-old PFM app whose finance-tracking layer is now the **acquisition funnel** for axio's credit products (BNPL, personal loan, line of credit, fixed deposits). The PFM is mature and very wide, but the company has already moved on from "free SMS expense tracker" as a destination product. That is both a moat (immense rule coverage) and a vulnerability (UX is heavily encrusted with monetization).

---

## 1. Company & product context

- **Company history**: Walnut was an independent Bangalore startup ("Daamitt Technologies") founded around 2013–2014. Capital Float (Zen Lefin / Axio Financial Services) acquired it in 2017 to get the SMS-based credit-data moat for their lending business. Capital Float rebranded the consumer brand to **axio** in 2022. The Android package name `com.daamitt.walnut.app` is a tombstone of the original company.
- **What's in the app today** (Play Store positioning vs. what the binary contains): the listing markets it as an "Expense Tracker & Budget" app, but ~60% of the screens and ~70% of the backend surface are about lending products, not tracking.
- **Five product lines visible in the binary** (every one of them maps to a package under `com.daamitt.walnut.app/`):
  1. **PFM** — the core SMS-based expense tracker (`pfm/` — 368 source files, the biggest single area).
  2. **axio Pay Later** — BNPL at partnered merchants (`w369/`, `paylater`).
  3. **axio Personal Loan** — instant unsecured loans with passbook, EMI schedule, prepayment, past-loans history (`personalloan/`).
  4. **Walnut Prime / Line of Credit (LOC)** — revolving credit drawdowns, top-up EMI conversion (`loc/`).
  5. **Fixed Deposits via Upswing** — opens partner FD products via webview (`upswingfdui/`).
- **Bonus consumer feature**: Splitwise-style group splits (`groups/` — 99 files, full settlement engine, image upload, "smart settlements"). This is a substantial feature, treated as a peer to the PFM module.

## 2. Tech stack & infrastructure

### Backend
- **API base URL**: `https://app.axio.live/_ah/api/` (Google Cloud Endpoints v1 on App Engine — the `_ah/api` path is a dead giveaway for legacy Endpoints Frameworks).
- **API surface**: 68 endpoints on the Axio backend under namespace `walnut/v1/...` (extracted from the Retrofit interface at `com/daamitt/walnut/app/api/AxioApiInterface.java`):
  - Auth/profile: `otp_get`, `otp_validate_v3`, `validate_truecaller_identity`, `auth_refresh_token`, `sync_profile`, `pong_v2`, `get_user_profile_v2`, `get_latest_version`
  - PFM data: `transaction_add`, `transaction_update`, `add_accounts`, `comment_add`, `comment_update`, `remitter_search`, `remitter_change_category`, `hybrid_search_merchant`, `report_sms`, `report_missed_refund_link`, `report_refund_link_changed`, `checkin_merchant`, `get_forex_rate`, `refund_pos_mapping`, `generate_report_v2`, `rules_hit` (telemetry on which patterns fire), `get_sync_settings`
  - **Rule engine OTA**: `getLatestRules(app_version, rules_version, testing)` — server returns updated `rules.json` body when client version is stale. Local version tracked in `SharedPreferences` key `Pref-RulesVersion`. The shipped baseline at `assets/rules.json` (2.4 MB) is version 126 in this build.
  - Backup/restore: `backup_check`, `backup_get_v2`, `backup_set_v2`, `backup_delete`, `file_upload`, `file_download`
  - Splits: `group_create`, `group_get`, `groups_get`, `group_update`, `group_image_upload`, `group_image_download`, `split_settle`, `split_settle_multi`, `split_settle_reminder`
  - Pay Later (BNPL): `get_w369_config`, `get_w369_merchant_config`, `get_w369_promotions`, `w369_amazon_card_config`, `w369_get_user_id`, `w369_line_registration`, `search_w369_merchant`, `search_w369_merchant_by_tags`, `pay_later_day_zero_sms_backup`, `get_balances_and_dues`, `get_pay_later_tranche_details`, `get_pay_later_tranche_list`
  - Personal Loan: `get_pl_config`, `get_pl_app_details`, `get_pl_app_registration_url`, `get_pl_doc_download_link`, `get_pl_passbook`, `get_pl_tranche_details`, `get_prepayment`, `update_repayment`
  - LOC (Walnut Prime): `get_loc_config`, `get_drawdowns`, `loc_get_set`
  - FD: `get_fd_config`, `get_fd_landing_page_config`, `initiate_fd_customer`
  - Content/CMS: `get_homescreen_cards`, `get_content_url` (returns a `redirection_url` + `app_landing_url` for dynamic content), `get_dynamic_content_urls`
- **Other backends called directly from the app**:
  - `content.axio.live` — auth-protected CMS (webviews use a `Bearer` token from SharedPrefs key `Pref-AccessToken`).
  - `mobile.yespayhub.in/services/` — Yes Bank payment hub for card-add / payment rails.
  - `webapp.upswing.one` / `upswing.access.partner/AXCF` — Upswing FD partner.
  - `vkyc.utkarsh.bank` — Utkarsh Small Finance Bank's V-KYC flow.
  - `outline.truecaller.com/v1/` + `sdk-otp-verification-noneu.truecaller.com` — Truecaller SDK for one-tap phone verification (partner key embedded in `strings.xml`).
  - `maps.googleapis.com` — Google Places API for merchant geo lookup.
  - `dq0bg0ej8semd.cloudfront.net` — CloudFront-fronted asset CDN (likely merchant logos, dynamic-content webviews).
  - Freshdesk via `/api/v2/tickets` for in-app support.
- **GCP project**: `walnut-backend-2014`; Firebase DB URL `https://walnut-backend-2014.firebaseio.com`; storage bucket `walnut-backend-2014.appspot.com`. The `2014` suffix dates the GCP project to the original Walnut team.

### Mobile architecture
- **Language**: Kotlin + Java mix. Compose introduced incrementally (`HomeAccountsComposeView`, `HomeGroupsComposeView`) alongside legacy XML/View layouts.
- **DI**: Hilt (Dagger) — `q9/p.java` is the generated `DaggerWalnutApp_HiltComponents_SingletonC` and instantiates `OkHttpClient → Retrofit → AxioApiInterface` and 28+ repositories.
- **Networking**: Retrofit + OkHttp + Gson. Auth is `Bearer <jwt>` from `Pref-AccessToken`; refresh via `auth_refresh_token`.
- **Persistence**: hybrid.
  - **Legacy SQLite** (managed manually in `database/f.java`) for the PFM core. ~16 tables: `walnutSms`, `walnutAccounts`, `walnutTransactions`, `walnutStatements`, `walnutEvents`, `walnutGroup`, `walnutSplitTransaction`, `walnutTags`, `walnutMissedTxns`, `walnutLoanApplication`, `walnutLoanDrawDown`, `walnutLoanEMI`, `walnutLoanRepayments`, `walnutRecentContacts`, `walnutSenderMap`, `walnutConversionRate`.
  - **Room** (`com.daamitt.walnut.app.room.WalnutRoomDatabase`) added later for `w369_*`, `pl_*`, `pay_later_*` caches. The two systems coexist — they migrated incrementally instead of rewriting.
- **Multidex**: 7 dex files, ~24 MB uncompressed code. Top defined packages by file count: `pfm` (368), `apimodels` (138), `groups` (99), `components` (82), `repository` (66), `personalloan` (41), `w369` (36).
- **UI libraries**: Compose, AndroidX, Material, RecyclerView, Lottie, Glide (with OkHttp integration), MPAndroidChart-style trends.
- **App lifecycle**: foreground services, JobIntentService chain (`WalnutService` → `WalnutJobIntentService` → `CategorizationService` → `SummaryAlarmJobIntentService` → `MissedTxnNotificationAlarmJobIntentService`). Heavy use of WorkManager (Hilt + work-manager init providers present).

### Third-party SDKs (initialised in `WalnutApp.onCreate`)
- **Firebase**: Analytics, Crashlytics, Performance, Cloud Messaging (FCM), Remote Config, Realtime Database, Dynamic Links, Storage, Auth.
- **CleverTap** (`658-KK4-546Z`, token `433-b56`) — push + in-app messaging + inbox. Drives most user re-engagement notifications.
- **AppsFlyer** — attribution.
- **Sentry** (`io.sentry.android.core`) — separate error reporting alongside Crashlytics. Two crash-reporters running side by side is unusual; one is probably owned by the lending team for stricter SLAs.
- **Amplitude** — `api.eu.amplitude.com` referenced in s7 SDK; init not visible from main code path so it may be vestigial or piggy-backed via another SDK.
- **Truecaller SDK** for OTP-less identity (partner key in `strings.xml`).
- **Google Play Core** for in-app updates and in-app review.
- **Lottie** (animations on onboarding/empty states).
- **TrueTime / ntp**: a NetworkChangeReceiver + time-zone receiver are wired; rule engine relies on accurate device time.

### Permissions (manifest)
SMS-first identity, plus a heavy services payload:
- `READ_SMS`, `RECEIVE_SMS`, `SEND_SMS` (yes — used to query bank balances via SMS short-codes; see §3.5)
- `BIND_NOTIFICATION_LISTENER_SERVICE` (NOT for ingestion — see §3.2)
- `READ_CONTACTS`, `GET_ACCOUNTS` (for splits + account linking)
- `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, **`ACCESS_BACKGROUND_LOCATION`** (merchant geo enrichment + the W369 "find a store" feature)
- `CAMERA` (KYC selfies for credit products)
- `USE_FINGERPRINT`, `USE_BIOMETRIC` (in-app lock)
- `RECEIVE_BOOT_COMPLETED`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `FOREGROUND_SERVICE`, `WAKE_LOCK` (fighting OEM background-kill)
- `READ_PHONE_STATE`, `BLUETOOTH`, `BLUETOOTH_ADMIN`, `LOCAL_MAC_ADDRESS` (device fingerprinting for risk/anti-fraud)
- `POST_NOTIFICATIONS`, `VIBRATE`
- `INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`
- Custom: `com.truecaller.permission.sdk.internal.read_account_state`, `com.daamitt.walnut.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`

The breadth here is well beyond what a tracker needs — it's a lending app's permission surface.

---

## 3. The SMS engine — the actual moat

### 3.1 Architecture
```
SMS_RECEIVED broadcast (priority 2147483647)
   │
   ▼
SmsBroadcastReceiver  (pfm/services/SmsBroadcastReceiver.java)
   │  bundles all PDUs into one ShortSms{address, body, date, simSlot, subId}
   │  drops anything older than 2 months (replaces date with now())
   ▼
WalnutService  (pfm/services/WalnutService.java, 1582 lines — the brain)
   │  - identifies sender via walnutSenderMap + senders[] + sender_regexes
   │  - applies blacklist_regex (skips OTP/password/passcode/verification/activation/osp/netsecure)
   │  - matches body against entity.patterns[], in sort_UID order
   │  - emits ParsedSms → Transaction / Statement / Event
   │  - applies pos_type_rules + transaction_type_rule for category/type refinement
   │  - applies chaining_rule against existing walnutSms to dedupe / fill incomplete records
   │  - writes to walnutSms + walnutTransactions / walnutStatements / walnutEvents
   ▼
CategorizationService  (pfm/services/CategorizationService.java)
   - finds Transaction.pos (merchant) → merchantId via remitter_search + local cache
   - assigns categories using rules + previous-transaction propagation
   - links refunds to original debits (5 strategies, see §3.4)
   - propagates user-set category to similar merchants
```

### 3.2 SMS-first, not notification-first
This is the **most important architectural fact** about Walnut:

- The `NotificationListenerService` (`WalnutNotificationService.java`) does **only one thing**: when the user opts into "Hide duplicate SMS notifications", it listens for system SMS-app notifications whose `android.text` matches a Walnut-posted notification and cancels them. **It does not ingest any data.** It's a 48-line UX-polish service.
- The manifest defaults this service to `android:enabled="false"`; it's only flipped on at runtime if the user accepts the prompt "axio shows better notification for your spends than your SMS App. Do you wish to suppress SMS Notifications?"
- The SMS receiver is registered with `android:priority="2147483647"` (max int) so Walnut sees the SMS before any other SMS app or competing tracker.

**Implication for your strategy**: you are competing on the *notification* surface, Walnut is competing on the *SMS* surface. The SMS surface is dramatically wider (notifications are per-payment-app and many banks send no notifications), but it's also gated by Play Store policy. Walnut's relationship to Play is grandfathered (acquired with this access historically). New SMS-reading apps have to apply for the permissions-use exemption and survive an annual review.

### 3.3 The rule engine
The full rulebook ships as plain JSON at `assets/rules.json` (2,479,880 bytes, ungzipped). Top-level schema (deserialized in `com/daamitt/walnut/app/smsengine/data/Rules.java`):
```
Rules {
  blacklist_regex: "(?i).*\\b(password|otp|verification|activation|passcode|osp|netsecure)\\b.*"
  min_app_version: "591"      # rules >= 591 require app version >= 591
  version: <int>              # local Pref-RulesVersion compared against this
  rules: Sender[]
}

Sender {
  name: "HDFC"
  full_name: "HDFC Bank"
  sender_UID: 9000001
  senders: ["5676712", "HDFCBK", "HDFCMP", "TXNALE", "HDFCBN", "HDFCCC"]
  sender_regexes?: ["^5\\d{5,6}$", ...]
  misc_information?: { get_balance: [{ account_type, contact_info: [{type:"sms", numbers, format:"bal"}, ...] }] }
  patterns: Rule[]
}

Rule {
  pattern_UID: 10002          # globally stable
  sort_UID: <int>             # trial order within sender
  regex: "..."                # PCRE-style, always (?i)
  account_type: bank|credit_card|prepaid|debit_card|generic|electricity|pay_later|loan|insurance|phone|prepaid_dth|gas|bill_pay|sip   (14)
  sms_type:   transaction|statement|event|generic|walnut|axio                                                                       (6)
  obsolete?: true             # 229/2284 patterns are obsolete-but-kept (rule-versioning)
  account_name_override?: "HDFC Bank"
  set_account_as_expense?: true
  reparse?: true
  data_fields: DataFields
}

DataFields {
  amount, pan, date, pos, note, location, currency, account_balance, outstanding_balance,
  network_reference_id, transaction_type, transaction_category, statement_type, min_due_amount,
  contact, time, pnr, event_type, event_info, event_location, event_reminder_span, name, otp,
  pos_info, pos_type_rules, transaction_type_rule, chaining_rule, create_txn,
  enable_chaining, incomplete, deleted, show_notification, event_type
}
```

Each field that captures from regex is `{ group_id: <int> }` or `{ value: <const> }`; date fields have format arrays with `use_sms_time` fallback.

### 3.4 Numbers
- **199 entities (issuers/billers/merchants)** with 2,284 total patterns. 1,935 are transactional, 303 statement, 43 event, 1 OTP (Walnut), 1 OTP (axio).
- **Coverage**:
  - Every major Indian bank (public + private), neobanks (Slice, One Card, Uni, Jupiter)
  - All four major BNPLs (LazyPay, Simpl, ZestMoney, Sezzle), and the related products PostPe, axio itself
  - Wallets (PayTM, PhonePe, Amazon Pay, MobiKwik, FreeCharge, Sodexo, Zaggle, Happay, PayZapp, JioMoney, etc.)
  - 30+ state electricity boards, 8+ city gas distributors, DTH/cable/broadband, insurers (LIC, HDFC ERGO, Aegon, Max, Tata AIA, etc.)
  - Travel (IRCTC, MakeMyTrip, Goibibo, Cleartrip, Yatra), movies (BookMyShow), taxis (Ola)
- **Transaction type taxonomy** (`Transaction.java` constants):
  `TXN_TYPE_CREDIT_CARD=1, DEBIT_CARD=2, DEBIT_ATM=3, BILLPAY=5, ECS=6, CASH=7, CASH_EXPENSE=8, DEFAULT=9, CHEQUE=10, ACCOUNT_UPDATE=11, BALANCE=12, CHAINED=13, CREDIT_ATM=15, DEBIT_PREPAID=16, CREDIT=17`
- **Account type taxonomy** (`Account.java` constants):
  `BANK=2, CREDIT_CARD=3, DEBIT_CARD=1, BILLPAY=4, PHONE=5, CASH=7, GENERIC=9, KIRANA_PERSONAL=11, KIRANA_BUSINESS=12, USER_CREATED_BILL=13, STATS_MAPS=15, PSEUDO_GROUP=16, PREPAID=17, PREPAID_DTH=18, ELECTRICITY=19, INSURANCE=20, LOAN=21, GAS=22, SIP=23, E_APP=24, PAY_LATER=26, PERSONAL=98, UNKNOWN=99` (plus virtual: `IGNORE=9999, SUMMARY=999`).
  `KIRANA_*` is for India's neighborhood-store credit (informal small-business ledger).
- **Category taxonomy** (27 categories, from strings.xml):
  `account_transfer, bank_deposit, bill_payment, bills, business, credit, emi, entertainment, fooddrink, fuel, groceries, health, insights, interest, investment, investment_returns, loan_disbursal, online, other, recharge, refund, reimbursement, rewards, salary, shopping, smartsettle_solo, transfer, travel`. The categorize-at-parse-time approach means the rule engine emits the category, not a post-step classifier.
- **Transaction flags** (`Transaction.java`):
  `INCOMPLETE, NOPOS, NOPAN, CHAINED, IS_AN_INCOME, FOREIGN_CURRENCY_TXN, POS_MERCHANT_PAYMENT, ADDED_BY_MISSED_TXN_LOGIC, CONVERTED_TO_PRIME_EMI, HAS_DUPLICATE_TXN, TXN_DUPLICATE, TXN_MARKED_AS_ATM_WITHDRAWAL, WALNUT_MERCHANT_PAYMENT, NOT_AN_EXPENSE, RATE_HAPPY/LOVED/SAD_MERCHANT_PLACE, DISABLED, DELETED`
- **Network reference types** (UPI-/IMPS-/NEFT-/RTGS-tagged transactions are first-class) — a single `networkReferenceId` column on transactions with a `networkReferenceType` int.

### 3.5 Bank-balance refresh via SMS short-codes
Each entity can declare `misc_information.get_balance` listing per-account-type contact methods. For HDFC bank: SMS `bal` to `5676712`; for HDFC credit-card: SMS `CCBAL XXXX` to `5676712`. The app can offer a one-tap "refresh balance" that uses `SEND_SMS` to message the bank's short-code, then parses the reply through the same rule engine. Voice fallback is also encoded (`type: "voice"`, `numbers: ["18002703333"]`).

### 3.6 Chaining (the dedup model)
A transaction often arrives as 2–3 SMS messages: a quick "Rs X debited" then a follow-up "for purchase at MERCHANT". A `chaining_rule` declaratively links them:
```
chaining_rule {
  parent_match: { parent_override: [{deleted:false, incomplete:false}],
                  child_override:  [{deleted:true,  incomplete:false}] }
  parent_selection: [
    {parent_field:"amount", child_field:{field:"amount"}, match_type:"exact"},
    {parent_field:"pan",    child_field:{field:"pan"},    match_type:"exact"},
    {parent_field:"date",   child_field:{field:"date"},   match_type:"delta", match_value:3600000},
    {parent_field:"deleted",      match_type:"none"},
    {parent_field:"pattern_UID",  match_type:"none"}  # don't chain to siblings of same pattern
  ]
}
```
`match_type` is `exact | contains | delta | none`. On chain: parent gets enriched (un-incomplete, un-deleted), child gets marked deleted (suppressed from UI). The data class is at `components/ChainingRule.java`. This is the cleanest declarative dedupe model I've seen for SMS-driven PFM.

### 3.7 Rule update path (OTA)
- On launch / periodically, the client calls `GET walnut/v1/getLatestRules?app_version=860&rules_version=126&testing=false`.
- Server returns a `{ rules: <stringified JSON> }` body when stale, else short-circuit response.
- Client persists in SharedPrefs and bumps `Pref-RulesVersion`. The settings screen shows `Version 8.6.0v860r126d126` (build 860, rules 126).
- Telemetry: `POST walnut/v1/rules_hit { pattern_uid: <Long> }` so they know which patterns actually fire in the wild. They retire patterns by setting `obsolete:true` rather than deleting them (existing user inboxes still contain old SMS).

### 3.8 Refund linking — 5 detection strategies
Coded as constants on `Transaction.java`:
- `REFUND_LINK_BY_HAS_POS_EXACT_AMOUNT=1`
- `REFUND_LINK_BY_HAS_POS_POS_MATCH=2`
- `REFUND_LINK_BY_NO_POS_EXACT_AMOUNT=3`
- `REFUND_LINK_BY_USER=4`
- `REFUND_LINK_NOT_FOUND=5`, `REFUND_LINK_NOT_FOUND_SET_BY_USER=6`

There is an explicit refund-pos-mapping API (`refund_pos_mapping`, `report_missed_refund_link`, `report_refund_link_changed`) so the server learns refund↔debit links from users when the heuristics fail.

### 3.9 Missed-transaction detection
Daily alarm (`MissedTxnNotificationAlarmReceiver` → `MissedTxnNotificationAlarmJobIntentService`) inspects the `walnutMissedTxns` table populated when there's a balance jump that no matching transaction explains. The user gets a notification "you might have a missed transaction" with a confirmation flow — confirmed entries become "user-created" transactions. This converts the inherent fragility of SMS parsing into an asset (users self-correct, which is then a label for the parser).

### 3.10 Recurring spends + bill reminders
- `RecurringSpendsActivity` shows transactions the engine has detected as repeating monthly (`txnRecursionAccountID`, `recursionAccountUUID` on Transaction).
- Users can flip a recurring spend into a `Reminder` (`AddNewReminderActivity`) and `BillReviewActivity` reviews dues coming up.
- The `walnutEvents` table holds non-finance reminders too (flight/movie/taxi/train), with `reminderTimeSpan` (default 30 min before).

---

## 4. Feature inventory (by screen)

Manifest contains 81 activities, 27 services, 23 receivers. Mapping the user-facing ones:

### Core PFM (~25 screens)
- **MainActivity** — bottom-nav host with fragments: `HomeFragment, PfmFragment, CreditFragment, ShopFragment` (note: navigation by "fragment" name, not Compose nav).
- **Home**: balances, accounts list, splits balances, homescreen "cards" fetched from `/get_homescreen_cards` (server-driven home; lets them A/B test surfaces without app updates).
- **TxnListActivity, CategoryTxnListActivity, MerchantTxnListActivity, TagTxnListActivity** — transaction lists pivoted by various keys.
- **AllAccountsActivity, AllCreditCardAccountsActivity** — accounts list, separate screen for cards.
- **PFMTransactionDetailsActivity** — per-transaction detail; large ViewModel (54 KB compiled), supports edit, recategorize, split, mark refund, attach photo (server-backed at `file_upload`).
- **PFMManualTxnActivity** — manual add (cash etc.).
- **TrendsActivity** — month/category trends (1100-line activity).
- **ChartListActivity** — chart picker.
- **BudgetCategoryActivity** — category-level budgets (plus a single total monthly budget).
- **RecurringSpendsActivity** — see §3.10.
- **MissedTxnInfoActivity** — see §3.9.
- **BillReviewActivity, AddNewReminderActivity, ReminderViewActivity** — bill/EMI reminder flow (1575-line activity).
- **TagsListActivity, TagsPrefActivity, CategoryPrefActivity** — manage tags/categories.
- **KiranaAccountActivity** — informal-credit ledger (kirana = neighborhood store).
- **SearchViewActivity** — cross-entity search.
- **SummaryActivity, MapsActivity** — daily/weekly recap with optional map of spends (uses Maps SDK).

### Inbox / SMS view (3 screens)
- **InboxActivity** — unified "priority SMS" inbox surfaced as a feature.
- **SmsViewActivity, SmsSenderActivity** — raw SMS view + per-sender filter.

### Splits / Groups (10+ screens, ~99 source files)
- `CreateGroupActivity, GroupViewActivity, GroupEditActivity, GroupDetailsActivity, RecentGroupsActivity, SelectGroupMembersActivity, GroupsAllTransactionsActivity`
- `GroupSettlementActivity` — settlement, with "Smart Settlements" simplification of the debt graph (server-driven via `split_settle_multi`).
- `TxnSplitDetailsActivity, TxnSplitEditActivity` — splitting a single transaction from your account across group members.
- Implicit: contact picker, image upload for group cover, payment-reminder DM via SMS, currency support for travel groups.

### Credit products (~25 screens)
- **Pay Later (BNPL)**:
  - `BrowseAllMerchantsActivity, SearchMerchantsActivity, MerchantDetailsActivity` (W369 = their merchant network)
  - `AxPlTrancheDetailsActivity` (per-purchase tranche detail)
- **Personal Loan**:
  - `PlLoanDetailsActivity, PlPassBookActivity, PlPastLoansActivity, PlRepaymentScheduleActivity, PlTransactionDetailsActivity, DayZeroSmsBackupActivity` (sends recent SMS to the underwriting backend on apply — `pay_later_day_zero_sms_backup`).
- **LOC / Walnut Prime**:
  - `WalnutPrimeDashboardActivity, LOCInformationActivity, LOCDrawDownInfoActivity, LOCDrawDownPrePaymentActivity, LOCTopupsActivity, LOCTopupEMIActivity, LOCNeedHelpActivity, LOCOfferRevokedActivity` (uses `Pref-PaymentTnCAcceptedTimeStamp` to gate flows).
- **FD (Upswing partner)**:
  - `FdFeatureLandingActivity, UpswingFdIntermediateLandingActivity` (handoff to webview via deeplink `upswing.access.partner/AXCF?action=webview&redirect=…`).

### Onboarding / Auth (5 screens)
- **MainOnBoardingActivity** — multi-step onboarding (Lottie animations, permission asks).
- **PFMOnboardingActivity** — separate onboarding for the PFM tab if the user lands on it cold.
- **PfmFeatureLandingActivity** — "Activate Money Manager" gate.
- **OtpActivity** — phone OTP login (auto-fill via `RECEIVE_SMS` + Google's SmsRetriever; Truecaller fallback).
- **FetchAndLoadWebViewActivity** — generic auth'd webview host (loads `content.axio.live` with the JWT bearer header). This is how they ship CMS-driven content and partner KYC flows without app updates.

### Profile / Settings (8 screens)
- `ProfileActivity` — user details, share-the-app, FAQs, contact `ask@axio.co.in`.
- `AppSettingsActivity, AppSubSettingsActivity` — categorized preference screens.
- `EnableAppLock, SilentLockActivity` — biometric/PIN app lock with re-auth on background re-entry.
- `AboutAxioActivity` — about, privacy policy, ToS.

---

## 5. UX patterns worth studying

Several decisions are non-obvious and probably hard-won:

1. **Server-driven homescreen.** `get_homescreen_cards` returns a list of `HomeScreenCardItem`s with `ButtonProperties`. Lets product surface promotions, KYC nudges, and new-product offers without app releases. The `HomescreenBanner` model has CTA actions and tracking IDs.
2. **Server-driven dynamic content URLs.** `get_content_url` returns `{ redirection_url, app_landing_url }` per slot. Webviews load CMS pages from `content.axio.live`, all with `Bearer` auth. Most "marketing" surfaces in the app are actually webviews.
3. **Day-zero SMS backup.** When a user starts a Personal Loan application, the app POSTs the recent SMS history (`pay_later_day_zero_sms_backup`) so underwriting can see the user's transaction footprint *immediately*, not just from the day onwards. Aggressive but legal under their ToS — and explains why the SMS access matters to the business.
4. **"Hide duplicate SMS notification"** is opt-in and on-by-default-after-prompt. Suppresses the native SMS app's notification once Walnut has shown its own enriched one. Massive perceived-quality win.
5. **Account flags `NAME_RESOLVED_VIA_SMS=128` and `BAL_NOT_UPTODATE=4`** — every account knows its name provenance and balance freshness. UI shows "stale" hint when balance hasn't been updated. Combined with the SMS-balance-refresh feature, balances stay live.
6. **Two crash reporters** (Crashlytics + Sentry). Likely so the lending team has its own SLA-bound crash stream separate from the PFM team's.
7. **Refund linking is exposed to user** — they can confirm/deny the auto-detected refund link, and the API reports back (`report_missed_refund_link`, `report_refund_link_changed`). This labels training data for free.
8. **Kirana account** — an entire account type for tracking informal credit at neighborhood stores. Recognizes Indian-specific behavior that imported models miss.
9. **Account "not an expense" / "not an income" flags** — wallets where you mostly transfer in/out get excluded from spend totals so totals stay meaningful. The user can toggle this per-account.
10. **Generates PDF/HTML monthly reports server-side** (`generate_report_v2`) and emails them via a separate `email_expenses` flow. The client doesn't compute the report.

---

## 6. Where they're weak

These are the cracks visible from the binary, useful for positioning a competitor:

- **The PFM is encrusted with monetization.** Bottom nav has `ShopFragment` (BNPL merchant browser) — irrelevant to a user who just wants to track spending. Profile menus mix "FAQ → axio Pay Later" with "FAQ → PFM". The acquisition funnel is the primary UX driver.
- **Two ages of UI.** Compose appears only in `HomeAccountsComposeView` and `HomeGroupsComposeView` — most of the app is legacy XML/View layouts. Visible inconsistency in screen styling.
- **Manual SQLite + Room hybrid** means schema migrations live in two places. Bug surface area visible in past Play Store reviews if you go looking.
- **Backup is opaque.** It's a binary blob to the server with no user-side export. Users complain on Play that they can't get their data out.
- **Permission surface is huge** (background location, READ_PHONE_STATE, BLUETOOTH, SEND_SMS, ACCESS_BACKGROUND_LOCATION). Privacy-conscious users have an obvious axis to choose a lighter competitor.
- **No web/desktop.** Android-only (an iOS app exists but is much more limited per the App Store SKU). All data trapped in the device + server.
- **Notification listener is unused for ingestion.** Means newer payment apps that send SMS-less push (some neobanks, some UPI flows) are partially blind to Walnut.
- **All assets shipped in-APK.** rules.json is 2.4 MB unzipped and parsed at every cold start; merchant logos, category icons, and a giant strings.xml inflate the install.
- **The OTP/blacklist regex is global, not per-sender.** A future bank that legitimately sends "your activation is complete" type confirmations would be silently dropped. Aggressive but coarse.

---

## 7. Lift-for-Rupee — concrete next moves

Ordered by what would move your product furthest, fastest. Each is testable in your codebase given the current state in `project_current_state.md`.

### Tier 1 — copy the design wholesale
1. **Move the parser ruleset to data (JSON) instead of code.** Match Walnut's contract: `entity → patterns[] → { regex, sms_type, account_type, data_fields { amount{group_id}, pan{group_id}, date{group_id, formats[]}, pos, note, ... }, sort_UID, pattern_UID, obsolete? }`. This unlocks OTA updates and lets you ship hundreds of patterns without writing Kotlin. Keep `NotificationParserRegistry` as the dispatcher, just read its rules from JSON.
2. **Adopt the chaining model for dedupe.** `parent_selection: [{parent_field, child_field, match_type: exact|delta|none, match_value}]` + `parent_override` / `child_override`. Strictly more expressive than fingerprint hashes and removes ad-hoc rules from your decision stage.
3. **Two-pass classification via `pos_type_rules` / `transaction_type_rule`.** A captured group in the regex is run through a small sub-rule table for category + type refinement. Bakes salary/refund/interest/reimbursement detection into the parser layer instead of as a separate stage.
4. **`pattern_UID` + `obsolete` versioning.** Patterns are forever once shipped; deprecate, don't delete. Stable UIDs make Crashlytics/Sentry traces point at specific rules.
5. **Global blacklist regex** as the first filter: `(?i).*\b(password|otp|verification|activation|passcode|osp|netsecure)\b.*`. One-line guardrail against a class of false positives.

### Tier 2 — adopt the data model
6. **Refund linking with 5 strategies** (`HAS_POS+EXACT_AMOUNT`, `HAS_POS+POS_MATCH`, `NO_POS+EXACT_AMOUNT`, `USER`, `NOT_FOUND`). Currently you have none — and refunds are a top user complaint area in PFMs.
7. **`networkReferenceId` + `networkReferenceType` on Transaction.** First-class UPI/IMPS/NEFT/RTGS reference IDs let you correlate across SMS streams from different banks (sender + receiver).
8. **Account flags for "not an expense" / "not an income".** A wallet whose deposits *are* the same money already counted at the source bank should not double-count. Lets users own this categorization.
9. **`incomplete` flag + missed-transaction detector.** Track derived account balance against transaction sum; when they diverge, surface the missing transaction as a confirm prompt. Converts parser fragility into a UX win.
10. **`event` sms_type for non-finance** (flights, movies, taxis, trains). Same pipeline, separate sink. Adds a "What's coming up" surface for free.

### Tier 3 — operational maturity
11. **Server-driven home cards** (`get_homescreen_cards`-equivalent). Even if your only "card" is "review these 3 uncategorized transactions", being able to push new prompts without releases is a force multiplier. Local-only for v1 is fine, but design the schema now.
12. **Generate reports server-side, not client-side.** Eventually. For now keep client-side, but isolate the PDF/HTML generation in a way you can move later.
13. **Pattern hit telemetry.** When you have a backend, count which patterns fire and which fail. Tells you where to invest pattern-writing time. (Privacy-safe — only the `pattern_UID`, not the SMS body.)

### What NOT to copy
- The SMS permission. The Play Store policy makes new entrants spend cycles on permission justification and limits SMS to a narrow set of use cases. Notifications are the legitimate path for a 2026-launched tracker.
- The 5-product surface. Walnut's UX is bent by its lending business. Your differentiator is being lender-free.
- Two crash reporters, two databases, two UI frameworks. Pick one of each.
- `READ_PHONE_STATE`, `BLUETOOTH`, `LOCAL_MAC_ADDRESS` device fingerprinting. Not needed without a lending product.

---

## 8. Strategic positioning vs. Walnut

A few angles worth considering as you brief copy / store listing / onboarding:

- **"Tracker, not a loan funnel."** The single highest-trust pitch a new Indian PFM can make. Mention specifically: no BNPL, no loans, no FD partners. Mean it in the manifest (don't slip permissions in later).
- **"Your data, your device."** Walnut sync-uploads everything to GCP for restore and for the lending business. Pitching local-first + explicit export (CSV, JSON) is a clear competitive edge with low cost to deliver.
- **"Lighter permissions."** Notifications only, no SMS, no location, no contacts unless you opt into a splits feature later. Will read better on the Play Store data-safety card.
- **"Honest 2026 UI."** Compose-first, single visual language. Walnut's mixed-era UI is one of the most visible failure modes when you put the apps side-by-side.

The one place Walnut is genuinely far ahead and not easy to catch up: **pattern coverage**. They have 9 years of edge cases. Your shortest path is (a) implement the rule-engine schema first so adding patterns is cheap, then (b) ingest a corpus of real SMS / notifications from 10–20 willing users and write patterns against it in priority order (top 15 senders cover 90% of population). Trying to compete on breadth pattern-by-pattern from scratch is not viable.

---

## Appendix A — full URL inventory

| Host | Purpose |
|------|---------|
| `app.axio.live` | Main backend (GCP App Engine, Cloud Endpoints v1) |
| `content.axio.live` | Auth'd CMS for in-app webview content |
| `dq0bg0ej8semd.cloudfront.net` | Asset CDN (likely merchant logos) |
| `axio.co`, `axio.co.in` | Marketing/legal: `/privacypolicy`, `/tnc`, `/faq`, `/pfm-faq`, `/axpl-faq`, `/pl-faq`, `/wprimecftnc`, `/blog`, `/share`, `/GRpolicy`, `/VL7p` |
| `mobile.yespayhub.in` | Yes Bank payment gateway/services |
| `webapp.upswing.one`, `upswing.access.partner` | Upswing FD partner platform |
| `vkyc.utkarsh.bank` | Utkarsh Small Finance Bank V-KYC |
| `outline.truecaller.com` v1, `sdk-otp-verification-noneu.truecaller.com` | Truecaller identity SDK |
| `walnut-backend-2014.firebaseio.com` | Firebase Realtime DB |
| `walnut-backend-2014.appspot.com` | Firebase Storage |
| `maps.googleapis.com` | Google Places API (merchant geo) |
| `wnut.in/w369merchant` | Shortlink for Pay Later merchants |
| `api.eu.amplitude.com` / `api2.amplitude.com` | Amplitude SDK (bundled; init unclear) |
| `static.wizrocket.com` | CleverTap CDN |

## Appendix B — key SharedPreferences flags
- `Pref-SetupComplete` — first-launch gate
- `Pref-RulesVersion` — local rule version (was 126 in this APK)
- `Pref-AccessToken` — bearer JWT for backend + content webviews
- `Pref-Device-Uuid` — per-install device identifier
- `Pref-Owner-Name`, `Pref-AccountName` — user profile
- `Pref-Backup-Restore` — backup enabled toggle
- `Pref-Credit-Limit` — manually set credit-card limit
- `Pref-Category-Budget-List` — per-category budgets (serialized)
- `Pref-PaymentTnCAcceptedTimeStamp` — gates lending flows
- `Pref-launch-in-money-manager-enabled` — opens MainActivity in PFM tab vs Home
- `Pref-w369-help-url` — overridable Pay Later FAQ URL
- `Pref-SettingsModified` — dirty-flag for cloud sync
- `Pref-Hide-Duplicate-Sms-Notifications-Key` — SMS-suppression toggle
- `Pref-Bill-Days-Key`, `Pref-Bill-Time` — reminder timing
- `Summary-Pref-DailyNotifications`, `Summary-Pref-WeeklyNotifications`, `Summary-Pref-Weekly-Summary-Time`, `Pref-Summary-Time` — recap scheduling

## Appendix C — extracted endpoints (full list)
68 endpoints on `AxioApiInterface`:
```
POST  walnut/v1/add_accounts
POST  walnut/v1/comment_add
POST  walnut/v1/transaction_add
GET   walnut/v1/backup_check
POST  walnut/v1/remitter_change_category
POST  walnut/v1/checkin_merchant
POST  walnut/v1/group_create
POST  walnut/v1/backup_delete
GET   walnut/v1/file_download
GET   walnut/v1/group_image_download
GET   walnut/v1/generate_report_v2
POST  walnut/v1/backup_get_v2
POST  walnut/v1/get_loc_config
POST  walnut/v1/get_drawdowns
GET   walnut/v1/get_content_url
GET   walnut/v1/get_fd_landing_page_config
GET   walnut/v1/get_forex_rate
GET   walnut/v1/group_get
GET   walnut/v1/groups_get
GET   walnut/v1/get_homescreen_cards
GET   walnut/v1/get_latest_version
GET   walnut/v1/otp_get
POST  walnut/v1/get_pl_passbook
GET   walnut/v1/get_balances_and_dues
GET   walnut/v1/get_pay_later_tranche_details
GET   walnut/v1/get_pay_later_tranche_list
GET   walnut/v1/get_pl_app_details
GET   walnut/v1/get_pl_config
GET   walnut/v1/get_pl_doc_download_link
POST  walnut/v1/get_pl_app_registration_url
POST  walnut/v1/get_pl_tranche_details
POST  walnut/v1/get_prepayment
POST  walnut/v1/loc_get_set
GET   walnut/v1/get_sync_settings
GET   walnut/v1/get_fd_config
GET   walnut/v1/get_user_profile_v2
GET   walnut/v1/get_w369_config
GET   walnut/v1/get_w369_merchant_config
GET   walnut/v1/get_w369_promotions
POST  walnut/v1/rules_hit
POST  walnut/v1/hybrid_search_merchant
GET   walnut/v1/initiate_fd_customer
POST  walnut/v1/report_missed_refund_link
POST  walnut/v1/pay_later_day_zero_sms_backup
PUT   walnut/v1/pong_v2
GET   walnut/v1/refund_pos_mapping
POST  walnut/v1/auth_refresh_token
POST  walnut/v1/report_refund_link_changed
POST  walnut/v1/report_sms
POST  walnut/v1/remitter_search
POST  walnut/v1/backup_set_v2
POST  walnut/v1/split_settle_multi
POST  walnut/v1/split_settle_reminder
POST  walnut/v1/split_settle
POST  walnut/v1/sync_profile
POST  walnut/v1/comment_update
POST  walnut/v1/group_update
POST  walnut/v1/update_repayment
POST  walnut/v1/transaction_update
POST  walnut/v1/file_upload
POST  walnut/v1/group_image_upload
POST  walnut/v1/otp_validate_v3
POST  walnut/v1/validate_truecaller_identity
GET   walnut/v1/w369_amazon_card_config
POST  walnut/v1/w369_get_user_id
POST  walnut/v1/w369_line_registration
POST  walnut/v1/search_w369_merchant
POST  walnut/v1/search_w369_merchant_by_tags
GET   walnut/v1/getLatestRules?app_version=&rules_version=&testing=
```

## Appendix D — coverage list (199 entities)
Banks: ACT Broadband, AP Mahesh, APCPDCL, APDCL, APEPDCL, APGV, APSPDCL, AU Bank, Abhyudaya, Allahbad Bk, AmEx, Andhra Bnk, Axis, BKESLM, BOB, BOI, BOM, Bandhan, Bharat Bank, CNTRLBK, CORP BANK, CSB, CUB, Canara, Capital Float, CitiBank, Cosmos, DBS, DCB, DENA, DeutscheBk, Dhanlaxmi Bank, Equitas, Federal Bk, Fino, HDFC, HSBC, ICICI, IDBI, IDFC, IOB, India Post, Indian Bnk, Indus, J&K Bank, KVB, Karnataka Bank, Kotak, Lakshmi Vilas, NKGSB, OBC, PMC, PNB, PSB, RBLBANK, RBS, SBI, SBM Bnk, SVC Bank, Saraswat Bank, South Indian, StanC, Syndicate, TJSB, TMB, UCO, Ujjivan, Union Bank, United Bank, VIJAYA, YesBank.

Cards / neobanks / BNPL: Bajaj Finserv, Bullet, DHFL, HDFC Home, Indiabulls, Jupiter, LazyPay, MobiKwik, NIYO, One Card, PostPe, Sezzle, Simpl, Slice, Sodexo, Tata Capital, Uni Card, Walnut (self), Zaggle, ZestMoney, axio, Capital Float (loans), LazyPay, MoboMoney.

Wallets / UPI / payments: Airtel Money, Amazon Pay, FreeCharge, GOIBIBO, Happay, IMONEY, IRCTC, JioMoney, OlaCab, OlaMoney, PAYTM, PayGo, PayZapp, PhonePe, Swiggy Money.

Insurance: Aditya Birla Sun Life, Aegon, Aviva, Bharti AXA Life, Canara HSBC OBC Life, DHFL Pramerica Life, Edelweiss Tokio Life, HDFC ERGO, HDFC Life, ICICI Prudential, IDBI Federal Life, India First Life, Kotak Life, LIC, Max Life, PNB Metlife, Reliance Nippon Life, SBI Life, Tata AIA Life.

Telecom/DTH/Broadband: Aircel, Airtel, Airtel DTH, BSNL, Connect Broadband, Dish TV, Docomo, Hathway Broadband, Idea, Jio, MTNL, Nextra Broadband, RCom, Spectra Broadband, Sun DTH, TATA, Tata Sky, TTN BroadBand, Tikona, Videocon D2H, Vodafone.

Electricity: APCPDCL/APDCL/APEPDCL/APSPDCL (AP), BESCOM/BESL/BEST/BSES (Bengaluru/Mumbai), CESC, CSEB, DDED, DHBVN, GUVNL, HESCOM, HPSEBL, JUSCO, KEDL, KESCO, MESCOM, MPMKVVCL, MPPKVVCL-EAST/WEST, MSEB, NBPDCL, NDMC, NPCL, PSPCL, SBPDCL, TANGEDCO, Tata Power, TSECL, TSSPDCL, Torrent Power, UCPGPL, UHBVN, UPCL, UPPCL, WBSEDCL, Adani Electricity.

Gas: Adani Gas, Avantika Gas, Gujarat Gas, Haryana City Gas, Indraprastha Gas, MNGL, Mahanagar Gas, Sabarmati Gas, Tripura Natural Gas, Vadodara Gas, UCPGPL.

Travel/movies/other: BookMyShow, Cleartrip, FlipKart, IRCTC, Make My Trip, Ticket, Yatra, Adani Gas, axio.

---

*Generated from static APK analysis. No live testing was performed. Strings.xml has 1,704 entries — additional copy/positioning material is in `/tmp/walnut-analysis/jadx-res/resources/res/values/strings.xml`. Full Retrofit interface is at `/tmp/walnut-analysis/jadx-src/sources/com/daamitt/walnut/app/api/AxioApiInterface.java`. Full rule schema is in `/tmp/walnut-analysis/jadx-src/sources/com/daamitt/walnut/app/smsengine/data/`.*
