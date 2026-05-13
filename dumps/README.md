# dumps/

Local drop-folder for notification dumps shared off the device.

## How to use it

1. In a debug build of Rupee, open Debug → Notification dumps → Share dump file.
2. Save the shared `rupee-notif-dumps-{version}-{device}-{stamp}.jsonl` somewhere
   you can reach from this laptop (Drive, AirDrop, email, etc.).
3. Drop the file into this folder.
4. Point Claude at it ("look at the latest dump in `dumps/`" works) and we can
   triage parser misses, see which bodies produced surprising Inbox rows, etc.

## Privacy

Every file in this folder is gitignored by `dumps/.gitignore` except this README
and the gitignore itself. Notification bodies typically contain account numbers,
merchant names, amounts — nothing in here will ever land in the repo by accident.

## Don't commit

If `git status` ever shows a `.jsonl` here as untracked, something's wrong with the
gitignore. Don't `git add -A` blind.
