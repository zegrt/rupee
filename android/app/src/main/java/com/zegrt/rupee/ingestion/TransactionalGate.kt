package com.zegrt.rupee.ingestion

/**
 * Shared pre-parser filter. Answers a single question before any per-provider
 * parser runs: "is this notification body actually about a real transaction?"
 *
 * Why this lives outside the parser registry: every parser was independently
 * re-deriving the same gate (some well, some not — see the v0.13.4 Kotak
 * marketing-push bug). Centralising it means a single fix benefits every
 * parser, and the per-provider parsers can focus on extracting fields instead
 * of guarding against ads.
 *
 * Algorithm:
 *  1. Reject if the body contains any promotional keyword (offers, OTPs, CTAs,
 *     disclosure boilerplate, etc.). Cheap O(n*k) substring scan.
 *  2. Reject if the body contains zero positive money verbs.
 *  3. Otherwise accept.
 *
 * The gate is intentionally lenient on bill-due / EMI-due flows: "due" is on
 * the positive list because CRED/ICICI bill-due notifications drive the
 * upcoming-dues home strip and must reach BILL_DUE parsers. Promo bodies that
 * say "EMI starting" still get killed by the "EMI starting" negative match.
 */
object TransactionalGate {

    sealed class Decision {
        data object Accept : Decision()
        data class Reject(val reason: RejectReason, val matched: String) : Decision()
    }

    enum class RejectReason {
        /** Body matched a known promotional / non-transactional keyword. */
        PROMO_KEYWORD,

        /** Body had no money verb (debited/credited/paid/etc.). */
        NO_TRANSACTIONAL_VERB,

        /** Body was empty or whitespace-only — usually a group summary. */
        EMPTY_BODY,
    }

    fun evaluate(body: String): Decision {
        if (body.isBlank()) return Decision.Reject(RejectReason.EMPTY_BODY, "")
        val lower = body.lowercase()

        // Order matters: short tokens before longer ones for readable triage,
        // but we return the first match either way.
        NEGATIVE_KEYWORDS.firstOrNull { it in lower }?.let { matched ->
            return Decision.Reject(RejectReason.PROMO_KEYWORD, matched)
        }

        val verb = POSITIVE_VERBS.firstOrNull { it in lower }
            ?: return Decision.Reject(RejectReason.NO_TRANSACTIONAL_VERB, "")

        return Decision.Accept
    }

    /**
     * Verbs that indicate a real money movement (or a real bill that needs
     * paying). At least one must be present for the body to clear the gate.
     * "due" is intentionally included so CRED/ICICI bill-due flows survive —
     * BILL_DUE parsers downstream depend on it.
     */
    private val POSITIVE_VERBS = listOf(
        // Debit-side
        "debited", " debit ", "spent ", "spent on", "paid ", "paying ",
        "sent via", "sent to", "sent rs", "sent inr",
        // Title-only Kotak811 native shape: "₹14.00 sent from XX4129".
        // Body is just CTA copy ("Low balance!") so the gate's only
        // signal is the title. Added 2026-05-19 after the v0.14.0
        // production dump showed real UPI debits being dropped as
        // NO_TRANSACTIONAL_VERB.
        "sent from", " debit from", " credit to",
        "transferred", "withdrawn", "withdraw ", "deducted",
        "auto-debit", "auto debit", "autodebit", "charged ", "swiped",
        // ICICI canonical ("Your card has been used for a transaction of INR X")
        // — adding these surfaced from a Gmail-forwarded ICICI alert in the
        // v0.13.3 dump that would otherwise have been false-rejected on
        // "no verb".
        "transaction of", "used for a transaction", "purchase of",
        // Recurring-debit instruments common in Indian banking. SIP / mutual
        // fund / standing-instruction debits often arrive with these phrases
        // and no other money verb — without them the gate false-rejects real
        // outflows. NACH = National Automated Clearing House (auto-debit
        // mandate); ECS = Electronic Clearing Service; "standing instruction"
        // is the user-facing label most banks use for recurring auto-pay.
        "sip installment", "sip debit", "sip of ",
        "nach mandate", "nach debit",
        "ecs debit", "ecs mandate",
        "standing instruction",
        // GPay UPI Autopay native phrasing (Spotify / Netflix / Hotstar /
        // SIP / utility recurrences). Body looks like "Payment to SPOTIFY
        // INDIA PVT LTD was successful. Payment for Autopay of ₹X to
        // MERCHANT was successful." None of the existing auto-debit verbs
        // catch it — there's no `debited` / `auto-debit` token in the
        // GPay shape. The CRED-wrapped variant ("UPI autopay of ₹139 ...
        // debited successfully") already passes via `debited`; these
        // additions specifically rescue the native-GPay phrasing.
        // Added 2026-05-22 (GATE-AUTOPAY-VOCAB) from the Nothing-A015 dump.
        "autopay", "payment to ", "payment for ",
        // Credit-side
        "credited", " credit ", "received ", "deposited", "refunded",
        // Variants of "refund" that the bare "refunded" verb above misses:
        // "Refund of ₹X processed", "Chargeback of ₹X credited", "Reversed
        // ₹X to your account" all represent real money flowing back to the
        // user. Without these phrases the gate would drop them as no-verb.
        "refund of", "reversed", "reversal", "chargeback",
        // Bill / EMI reminders we want to keep
        " due ", "is due", "due on", "due by", "due tomorrow", "payment due",
    )

    /**
     * Promotional / non-transactional keywords. Any one present rejects the
     * body. Listed roughly in order of how often they fire against our real
     * dump corpus so the matched-keyword telemetry stays readable.
     *
     * Curated against `dumps/rupee-notif-dumps-0.13.3-Nothing-A015-*.jsonl`
     * and the patterns documented in docs/notification-ingestion-deep-dive.md.
     */
    private val NEGATIVE_KEYWORDS = listOf(
        // Marketing CTAs — the most common offenders
        "apply now", "apply today", "apply here",
        "pre-approved", "pre approved", "preapproved",
        "eligible for", "selected for", "you are eligible",
        "explore", "know more", "click here", "tap here",
        "register now", "sign up", "download now",
        "avail offer", "claim now", "unlock ", "is unlocked",
        // Loan / credit-product promo framing. Real bank txns never mention
        // "personal loan" / "instant loan" / "loan offer" — those phrases are
        // exclusively marketing copy. EMI debit alerts say "EMI debited" or
        // "EMI for HDFC home loan" but don't combine with the promo verbs.
        "personal loan", "instant loan", "loan offer",
        "disbursed instantly", "approved for ₹", "approved for rs",
        // CRED Cash / credit-line withdrawal promos. The body shape is
        // "₹2,80,000 available for you — withdraw any amount from your CRED
        // cash account before May 31st and start your first EMI in July."
        // It clears the positive-verb gate via `withdraw ` (added for ATM
        // bodies), then the Indian-grouping amount regex truncates ₹2,80,000
        // → ₹2 and the Inbox gets a ₹2.00 SPEND with no merchant.
        // Added 2026-05-22 from the Nothing-A015 dump (CRED-PROMO-BODY-GATE).
        "available for you", "cred cash", "credit line",
        "withdraw any amount", "start your first emi",
        // "Get ₹10k instantly credited" — clickbait that would otherwise pass
        // the credit-side verb check. The "instantly + amount" combo is
        // exclusively promotional.
        "instantly credited", "instant ₹", "instant rs.",
        // Promo / reward framing
        "cashback offer", " offer ", "limited offer",
        "congratulations", "you have won", "lucky draw",
        "rewards await", "earn rewards",
        // Disclosure / fine-print signals
        "t&c", "t & c", "tnc apply", "terms apply", "*terms",
        " p.a.", "% p.a", "per annum",
        // Aspirational / projection framing (the Kotak RD smoking gun)
        " → ", " -> ", "becomes ₹", "grows to ₹", "as low as", "starting at",
        "starting from", "up to ₹", "up to rs", "upto ₹", "upto rs",
        " emi starting ", " emi as low ",
        "/month → ", "/month -> ",
        // Time-pressure
        "limited period", "valid till", "last day", "hurry ",
        "today only", "ends soon",
        // OTP / verification (never a transaction)
        "otp ", " otp.", " otp,", "one time password", "verification code",
        "verification pin", "do not share",
        // Payment requests (someone asking US for money — not a debit yet)
        "has requested", "payment request", "collect request",
        "requesting payment", "requests rs", "ignore if already paid",
        // Truecaller (the SMS mirror) explicitly tags spam; if it's still
        // tagged that way by the time we see it, the real bank notification
        // would have come through the bank's own package anyway.
        "🚨 spam", "spam ·",
    )
}
