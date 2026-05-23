# Alpha stage-roll log — v0.15.0-alpha.2

**Build:** `rupee-0.15.0-alpha.2-release.apk` (signed) /
`rupee-0.15.0-alpha.2-debug.apk` (with notification-dump tool).
**Soak started:** *(fill in when you side-load alpha.2)*
**Soak ended:** *(fill in 24h+ later)*

## alpha.1 → alpha.2 changelog (read first)

Alpha.1 was side-loaded on 2026-05-23 morning. Within the first hour
the very first real notification capture surfaced an **alpha-blocker**:
every body routing to INBOX_PENDING was being silently dropped from
the DB due to a SQLite REPLACE + ON DELETE CASCADE interaction. The
user reported "nothing has come in the inbox" — see CHANGELOG.md
entry for v0.15.0-alpha.2 for the full deep-research writeup. Hotfix
shipped same day as alpha.2.

**Pre-existing data note:** if you installed alpha.1, any inbox rows
that *should* have appeared between v0.14.1 and the alpha.2 upgrade
are unrecoverable. The candidates still exist in the DB as orphaned
INBOX_PENDING rows. If you want a clean slate, factory-reset the app's
storage (Settings → Apps → Rupee → Storage → Clear data) before
running the alpha.2 soak.

This is the structured log for Sprint 3's **ALPHA-STAGE-ROLL** item.
The goal isn't zero bugs — it's *known* bugs. Anything you spot during
the soak gets a line in the table below, with a verdict (alpha-blocker
/ backlog item / acceptable risk).

---

## Install instructions

1. Connect your phone via USB with USB debugging enabled.
2. From repo root:
   ```bash
   adb install -r android/app/build/outputs/apk/release/rupee-0.15.0-alpha.2-release.apk
   ```
   (Use the `-debug` APK if you want the notification-dump tool for
   capturing bodies that go wrong; otherwise the signed release is the
   real alpha build.)
3. Grant notification access when prompted (Settings → Notifications →
   Special access → Notification access → Rupee).
4. Grant `POST_NOTIFICATIONS` when the onboarding prompt fires (Android 13+).
5. Walk through onboarding (Profile → Permissions → Setup → Home).
   - This is the first end-to-end fresh-install run since SEED-SERVICE
     shipped — pay attention to the budget seeding.

---

## What to actively poke at during the 24h window

Things we already know are wobbly or untested, that the soak should
exercise on purpose:

- **Auto-confirm.** We've never auto-confirmed a single body. If a
  notification clears HIGH tier (≥ 0.85), it should auto-create a
  `SUGGESTED` row visible on Home's recent activity. Watch for one.
  If after 24h none have auto-confirmed, the HIGH threshold is too
  strict — log it as a Sprint 5 input.
- **Battery drain.** Compare battery use over 24h against a typical
  day without Rupee. Listener service shouldn't visibly dent battery.
- **Recurring detection.** Open Settings → Recurring after 24h to see
  if anything got auto-detected.
- **Manual entry's new chips.** Open the manual entry sheet — the
  "Recent" chip row should populate after ~5 confirmed transactions.
- **The new date pickers.** Add an EMI or set a card due — make sure
  the picker fires and the date sticks.
- **Indian-grouping on amounts.** Set a budget of `100000` — should
  display as `1,00,000` while typing.
- **Inbox reason chips.** Watch for the new copy: `AMOUNT ONLY`,
  `NO MERCHANT`, `DUPLICATE SUSPECTED`, etc.
- **The crash log.** Check Settings → About → Debug → Crash log after
  24h. Should be empty if nothing crashed.

---

## Issue log

Fill this in as you go. Severity guidance:

- **🟥 alpha-blocker** — must fix before closed beta (data loss, crash,
  trust-killing UX bug on a happy path).
- **🟧 known-bug** — log it, file a backlog item, keep going.
- **🟨 quality nit** — capture for a future polish pass.
- **🟩 working as designed** — used to be a worry, confirmed fine.

| # | Severity | Surface | What happened | Reproducible? | Verdict / follow-up |
|---|---|---|---|---|---|
| 1 | | | | | |
| 2 | | | | | |
| 3 | | | | | |

---

## Soak verdict

*(Fill in at the end of the 24h window.)*

- [ ] **Pass** — no alpha-blockers, soak complete. Hand to 1–5 testers.
- [ ] **Pass with caveats** — known bugs above are logged into the
  backlog; not blockers. Hand to testers with a heads-up.
- [ ] **Fail** — alpha-blockers found. Patch, re-cut as `0.15.0-alpha.2`,
  re-soak.

**Auto-confirm seen during soak?** Yes / No. *(If No, log a Sprint 5
input — the HIGH threshold needs re-tuning before NOTIF-CHANNELS ships.)*

**Number of transactions ingested:** ___

**Number that auto-confirmed (HIGH tier):** ___

**Number that landed in Inbox (MEDIUM tier):** ___

**Number that were silently dropped (LOW tier or gate-rejected):**
___ *(check Debug → Notification dumps for the raw count)*

---

## After the soak

If the verdict is **Pass** or **Pass with caveats**:

1. Side-load the same APK on 1–5 testers' phones.
2. Send them this log as a template — they fill in their own copy
   over their first week.
3. Move on to Sprint 4 (closed-beta stability) per
   [`docs/rupee-backlog.md`](rupee-backlog.md).

If the verdict is **Fail**:

1. Open a follow-up PR with the fix.
2. Bump `versionName` to `0.15.0-alpha.2`, `versionCode` to 43.
3. Rebuild, re-tag, re-soak. Do NOT hand to external testers until
   the soak passes.
