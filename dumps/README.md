# dumps/

Local drop-folder for notification dumps shared off the device.

## How to use it

1. In a debug build of Rupee, open Debug → Notification dumps → Share dump file.
2. The share intent now attaches **two files** (same `{version}-{device}-{stamp}`
   suffix so they pair by name):
   - `rupee-notif-dumps-…jsonl` — raw notification text + ingest-time outcome.
   - `rupee-notif-outcomes-…jsonl` — joined DB snapshot keyed by `rawEventId`:
     current candidate / inbox / canonical state, plus `mergedIntoExistingTxnId`
     when the user merged into a pre-existing row.
3. Save both somewhere you can reach from this laptop (Drive, AirDrop, email, etc.).
4. Drop both files into this folder.
5. Point Claude at it ("look at the latest dump in `dumps/`" works) and we can
   triage parser misses, see which bodies produced surprising Inbox rows, and
   join against `outcomes.jsonl` to see what the user did with each.

The dump captures **decision at ingest time**; the outcomes file captures
**state at export time**. Different snapshots, complementary purposes — see
`docs/dump-enrichment-followups.md` for the full schema.

## Privacy

Every file in this folder is gitignored by `dumps/.gitignore` except this README
and the gitignore itself. Notification bodies typically contain account numbers,
merchant names, amounts — nothing in here will ever land in the repo by accident.

## Don't commit

If `git status` ever shows a `.jsonl` here as untracked, something's wrong with the
gitignore. Don't `git add -A` blind.
