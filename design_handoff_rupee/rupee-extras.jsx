// Rupee — extras: notifications, minimal recap, trust rules, widgets

// ════════════════════════════════════════════════════════════════════════
// 1. NOTIFICATIONS — Android system-tray + lock-screen heads-up
// Two states: auto-confirmed (silent, minimal chrome) and needs-review
// (heads-up, inline Confirm / Not a transaction actions).
// ════════════════════════════════════════════════════════════════════════

// Shared mini-icon (small saffron square with ₹)
function RupeeAppIcon({ size = 18 }) {
  return (
    <div style={{
      width: size, height: size, borderRadius: size * 0.28,
      background: "var(--accent)", color: "var(--accent-on)",
      display: "flex", alignItems: "center", justifyContent: "center",
      fontFamily: "var(--font-mono)", fontWeight: 700, fontSize: size * 0.6, flexShrink: 0,
    }}>₹</div>
  );
}

// One notification card. variant: "review" | "auto" | "system"
function NotifCard({ variant, title, body, time = "now", priority }) {
  const isReview = variant === "review";
  const isSystem = variant === "system";
  const accent = isReview ? "var(--accent-bright)" : "var(--muted)";
  return (
    <div style={{
      background: "color-mix(in oklch, var(--surface) 80%, var(--bg-deep))",
      backdropFilter: "blur(12px)",
      borderRadius: 22,
      padding: "12px 14px 14px",
      position: "relative",
      boxShadow: isReview ? "0 0 0 1px color-mix(in oklch, var(--accent) 35%, transparent)" : "0 0 0 1px var(--divider)",
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 8 }}>
        {isSystem ? (
          <div style={{ width: 16, height: 16, borderRadius: 8, background: "var(--surface-3)" }} />
        ) : <RupeeAppIcon size={16} />}
        <span className="r-mono" style={{ fontSize: 10, fontWeight: 600, letterSpacing: "0.12em", color: isSystem ? "var(--muted)" : "var(--text)" }}>
          {isSystem ? "MESSAGES" : "RUPEE"}
        </span>
        <span style={{ width: 3, height: 3, borderRadius: 2, background: "var(--faint)" }} />
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)" }}>{time}</span>
        {isReview && (
          <>
            <span style={{ flex: 1 }} />
            <span className="r-mono" style={{ fontSize: 9, color: accent, letterSpacing: "0.12em", fontWeight: 700 }}>
              ● REVIEW
            </span>
          </>
        )}
        {!isReview && !isSystem && (
          <>
            <span style={{ flex: 1 }} />
            <span className="r-mono" style={{ fontSize: 9, color: "var(--muted)", letterSpacing: "0.12em", fontWeight: 600 }}>
              SILENT
            </span>
          </>
        )}
      </div>
      <div style={{ fontSize: 14, fontWeight: 600, color: "var(--text)", lineHeight: 1.25, marginBottom: 4 }}>{title}</div>
      <div style={{ fontSize: 13, color: "var(--muted)", lineHeight: 1.35 }}>{body}</div>
      {isReview && (
        <div style={{ display: "flex", gap: 8, marginTop: 12, paddingTop: 8, borderTop: "1px solid var(--divider)" }}>
          <button style={{
            flex: 1, padding: "8px 10px", border: 0, borderRadius: 100,
            background: "var(--accent)", color: "var(--accent-on)",
            fontFamily: "var(--font-sans)", fontSize: 12, fontWeight: 600,
          }}>Confirm</button>
          <button style={{
            flex: 1, padding: "8px 10px", border: "1px solid var(--hairline)", borderRadius: 100,
            background: "transparent", color: "var(--muted)",
            fontFamily: "var(--font-sans)", fontSize: 12, fontWeight: 500,
          }}>Not a tx</button>
        </div>
      )}
    </div>
  );
}

// Lock screen with one heads-up Rupee notification
function NotifLockScreen() {
  return (
    <RupeeFrame showNav={false}>
      <div style={{
        position: "absolute", inset: 0,
        background: "radial-gradient(ellipse at 30% 20%, oklch(0.22 0.04 60) 0%, oklch(0.10 0.005 60) 65%)",
        padding: "20px 18px 20px",
        display: "flex", flexDirection: "column",
      }}>
        {/* Lock icon top */}
        <div style={{ textAlign: "center", marginBottom: 16 }}>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <rect x="3" y="6" width="8" height="6" rx="1" stroke="var(--text)" strokeWidth="1.4" opacity="0.6" />
            <path d="M5 6V4.5a2 2 0 0 1 4 0V6" stroke="var(--text)" strokeWidth="1.4" opacity="0.6" />
          </svg>
        </div>

        {/* Big lock-screen clock */}
        <div style={{ textAlign: "center", marginBottom: 32 }}>
          <div style={{ fontFamily: "var(--font-sans)", fontWeight: 500, fontSize: 84, lineHeight: 0.95, letterSpacing: -0.04, color: "var(--text)" }}>
            9:30
          </div>
          <div className="r-mono" style={{ fontSize: 12, color: "var(--muted)", letterSpacing: "0.1em", marginTop: 6 }}>
            SAT · MAY 23
          </div>
        </div>

        {/* Heads-up notification — review type */}
        <NotifCard
          variant="review"
          time="now"
          title="₹1,499 at AMZN*PMTS — is this Amazon?"
          body="Auto-categorized as Shopping. Tap Confirm to keep, or correct it."
        />

        {/* Older silent notif beneath */}
        <div style={{ marginTop: 8, opacity: 0.85 }}>
          <NotifCard
            variant="auto"
            time="08:42"
            title="₹480 · Blue Tokai · Food"
            body="Auto-confirmed from HDFC notification."
          />
        </div>

        <div style={{ flex: 1 }} />

        {/* Swipe-up dock hint */}
        <div className="r-mono" style={{ textAlign: "center", fontSize: 10, color: "var(--faint)", letterSpacing: "0.18em" }}>
          SWIPE UP TO UNLOCK
        </div>

        <div style={{
          display: "flex", justifyContent: "space-between", marginTop: 18,
        }}>
          <div style={{ width: 44, height: 44, borderRadius: 22, background: "color-mix(in oklch, var(--surface) 60%, transparent)", display: "flex", alignItems: "center", justifyContent: "center", color: "var(--text)" }}>
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none"><path d="M9 13.5l-4.5-4.5L9 4.5M5 9h9" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" /></svg>
          </div>
          <div style={{ width: 44, height: 44, borderRadius: 22, background: "color-mix(in oklch, var(--surface) 60%, transparent)", display: "flex", alignItems: "center", justifyContent: "center", color: "var(--text)" }}>
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none"><rect x="3" y="4" width="12" height="10" rx="1.5" stroke="currentColor" strokeWidth="1.6" /><circle cx="9" cy="9" r="2" stroke="currentColor" strokeWidth="1.6" /></svg>
          </div>
        </div>
      </div>
    </RupeeFrame>
  );
}

// Pulled-down notification shade
function NotifShade() {
  return (
    <RupeeFrame showNav={false}>
      <div style={{
        position: "absolute", inset: 0,
        background: "var(--bg-deep)",
        display: "flex", flexDirection: "column",
        padding: "0 12px 16px",
        overflow: "hidden",
      }}>
        {/* Top date row */}
        <div style={{ padding: "8px 6px 14px", display: "flex", alignItems: "baseline" }}>
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 12, color: "var(--muted)", letterSpacing: "0.04em" }}>
            Sat, May 23
          </span>
          <span style={{ flex: 1 }} />
          <div style={{ display: "flex", gap: 8 }}>
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><circle cx="7" cy="7" r="2.5" stroke="var(--muted)" strokeWidth="1.3" /><path d="M7 1v2M7 11v2M1 7h2M11 7h2" stroke="var(--muted)" strokeWidth="1.3" strokeLinecap="round" /></svg>
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M7 1l1.7 4.2L13 6l-3.5 3 1 4.5L7 11l-3.5 2.5 1-4.5L1 6l4.3-.8z" stroke="var(--muted)" strokeWidth="1.3" /></svg>
          </div>
        </div>

        {/* Quick settings tiles row */}
        <div style={{ display: "flex", gap: 6, padding: "0 0 14px" }}>
          {["WiFi", "Cellular", "DND", "Flash"].map((t, i) => (
            <div key={t} style={{
              flex: 1, height: 44, borderRadius: 14,
              background: i < 2 ? "color-mix(in oklch, var(--accent) 30%, var(--surface))" : "var(--surface)",
              display: "flex", alignItems: "center", justifyContent: "center",
              fontFamily: "var(--font-mono)", fontSize: 10, letterSpacing: "0.08em",
              color: i < 2 ? "var(--accent-bright)" : "var(--muted)",
            }}>{t}</div>
          ))}
        </div>

        {/* Notifications header */}
        <div style={{ display: "flex", alignItems: "baseline", padding: "0 6px 8px" }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.14em" }}>NOTIFICATIONS · 3</span>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 10, color: "var(--muted)", letterSpacing: "0.14em" }}>CLEAR ALL</span>
        </div>

        {/* Notification list */}
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          <NotifCard
            variant="review"
            time="2m"
            title="₹612 at SWIGGY — already in your ledger?"
            body="Looks like a duplicate of yesterday's ₹612. Merge or keep as new?"
          />
          <NotifCard
            variant="auto"
            time="11m"
            title="₹480 · Blue Tokai · Food"
            body="Auto-confirmed. May ₹6,480 / ₹8,000 in Food."
          />
          <NotifCard
            variant="auto"
            time="1h"
            title="₹122 · Auto Mahesh · Transport"
            body="Auto-confirmed from ICICI."
          />
        </div>

        <div style={{ flex: 1 }} />

        {/* Pull handle */}
        <div style={{ display: "flex", justifyContent: "center", paddingTop: 12 }}>
          <div style={{ width: 32, height: 4, borderRadius: 2, background: "var(--muted)", opacity: 0.5 }} />
        </div>
      </div>
    </RupeeFrame>
  );
}

// ════════════════════════════════════════════════════════════════════════
// 2. MINIMAL RECAP — single screen, glanceable
// Alternative to the 7-card Wrapped story
// ════════════════════════════════════════════════════════════════════════
function MinimalRecap() {
  return (
    <RupeeFrame showNav={false}>
      <div style={{ padding: "16px 20px 20px", display: "flex", flexDirection: "column", height: "100%", overflow: "auto" }}>
        {/* Header */}
        <div style={{ display: "flex", alignItems: "baseline", marginBottom: 18 }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.14em", fontWeight: 700 }}>RECAP · MAY 2026</span>
          <span style={{ flex: 1 }} />
          <span style={{ color: "var(--muted)", fontSize: 14 }}>✕</span>
        </div>

        {/* Hero — total */}
        <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.14em", marginBottom: 4 }}>
          YOU SPENT
        </div>
        <HeroAmount value={36580} />
        <div style={{ display: "flex", alignItems: "baseline", marginTop: 8 }}>
          <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)" }}>56% OF BUDGET</span>
          <span style={{ flex: 1 }} />
          <span style={{ fontSize: 13, fontWeight: 600, color: "var(--good)" }}>−8.2% vs avg</span>
        </div>

        {/* Mini stat row */}
        <div style={{
          display: "grid", gridTemplateColumns: "1fr 1fr 1fr",
          gap: 0, marginTop: 20, padding: "14px 0",
          borderTop: "1px solid var(--divider)", borderBottom: "1px solid var(--divider)",
        }}>
          <MiniStat k="₹28,420" v="SAVED" />
          <MiniStat k="412" v="TX" />
          <MiniStat k="6 days" v="NO-SPEND" />
        </div>

        {/* Top categories */}
        <div style={{ marginTop: 22 }}>
          <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.14em", marginBottom: 10 }}>TOP CATEGORIES</div>
          {[
            { l: "Food", v: 11420, p: 1.00, c: "oklch(0.72 0.16 30)" },
            { l: "Bills", v: 7820, p: 0.68, c: "oklch(0.74 0.14 80)" },
            { l: "Shopping", v: 5850, p: 0.51, c: "oklch(0.74 0.16 330)" },
          ].map((c) => (
            <div key={c.l} style={{ marginBottom: 12 }}>
              <div style={{ display: "flex", alignItems: "baseline", marginBottom: 4 }}>
                <span style={{ width: 8, height: 8, borderRadius: 2, background: c.c, marginRight: 8 }} />
                <span style={{ fontSize: 13, fontWeight: 500, color: "var(--text)", flex: 1 }}>{c.l}</span>
                <span className="r-mono" style={{ fontSize: 12, color: "var(--text)" }}>
                  ₹{formatINR(c.v, { withSymbol: false })}
                </span>
              </div>
              <div style={{ height: 4, background: "var(--surface-2)", borderRadius: 2, overflow: "hidden" }}>
                <div style={{ width: `${c.p * 100}%`, height: "100%", background: c.c }} />
              </div>
            </div>
          ))}
        </div>

        {/* Top merchants */}
        <div style={{ marginTop: 18 }}>
          <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.14em", marginBottom: 6 }}>TOP MERCHANTS</div>
          {[
            { m: "Blue Tokai",   v: 7242, n: 17 },
            { m: "Swiggy",       v: 4820, n: 11 },
            { m: "Big Bazaar",   v: 4220, n: 4 },
          ].map((r, i) => (
            <div key={i} style={{ display: "flex", alignItems: "baseline", padding: "8px 0", borderBottom: i < 2 ? "1px dotted var(--divider)" : 0, gap: 8 }}>
              <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", width: 22 }}>{String(i + 1).padStart(2, "0")}</span>
              <span style={{ fontSize: 14, fontWeight: 500, color: "var(--text)", flex: 1 }}>{r.m}</span>
              <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)" }}>{r.n}×</span>
              <span style={{ fontFamily: "var(--font-sans)", fontWeight: 500, fontSize: 14, color: "var(--text)", fontVariantNumeric: "tabular-nums", minWidth: 64, textAlign: "right" }}>
                ₹{formatINR(r.v, { withSymbol: false })}
              </span>
            </div>
          ))}
        </div>

        {/* Biggest day callout */}
        <div style={{
          marginTop: 18, padding: "14px 16px",
          background: "color-mix(in oklch, var(--accent) 10%, var(--surface))",
          border: "1px solid color-mix(in oklch, var(--accent) 30%, transparent)",
          borderRadius: 10,
        }}>
          <div className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.14em", marginBottom: 4 }}>BIGGEST DAY</div>
          <div style={{ display: "flex", alignItems: "baseline", gap: 10 }}>
            <span style={{ fontSize: 22, fontWeight: 600, color: "var(--text)", letterSpacing: -0.01 }}>May 18</span>
            <span style={{ flex: 1, fontSize: 12, color: "var(--muted)" }}>BLR → DEL · 1 flight, 1 coffee</span>
            <Amount value={7460} size={18} weight={600} />
          </div>
        </div>

        {/* Footer — link to Wrapped */}
        <div style={{ flex: 1 }} />
        <button style={{
          marginTop: 22, padding: "14px 16px",
          background: "var(--surface)", color: "var(--text)",
          border: "1px solid var(--divider)", borderRadius: 10,
          fontFamily: "var(--font-sans)", fontWeight: 500, fontSize: 13,
          display: "flex", alignItems: "center", justifyContent: "center", gap: 8,
          cursor: "pointer",
        }}>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none"><path d="M3 7h8M7 3l4 4-4 4" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round" /></svg>
          See the full story — 7 cards
        </button>
      </div>
    </RupeeFrame>
  );
}

function MiniStat({ k, v }) {
  return (
    <div style={{ padding: "0 4px" }}>
      <div style={{ fontFamily: "var(--font-sans)", fontWeight: 600, fontSize: 18, color: "var(--text)", letterSpacing: -0.01, fontVariantNumeric: "tabular-nums" }}>{k}</div>
      <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.14em", marginTop: 2 }}>{v}</div>
    </div>
  );
}

// ════════════════════════════════════════════════════════════════════════
// 3. TRUST RULES — managing 12 trusted merchants
// Each rule: merchant + default category + optional guards + on/off
// ════════════════════════════════════════════════════════════════════════
const TRUST_RULES = [
  { m: "Blue Tokai",      cat: "food",      mode: "UPI",   limit: 1500, count: 17, on: true,  bank: "HDFC" },
  { m: "Swiggy",          cat: "food",      mode: "UPI",   limit: 2000, count: 11, on: true,  bank: "ICICI" },
  { m: "Uber",            cat: "transport", mode: "UPI",   limit: null, count: 28, on: true },
  { m: "Big Bazaar",      cat: "groceries", mode: "CARD",  limit: null, count: 4,  on: true,  bank: "AXIS" },
  { m: "Airtel Postpaid", cat: "bills",     mode: "UPI",   limit: 1000, count: 6,  on: true },
  { m: "Cult.fit",        cat: "health",    mode: "CARD",  limit: null, count: 5,  on: true,  bank: "AXIS" },
  { m: "Netflix",         cat: "ent",       mode: "CARD",  limit: 800,  count: 1,  on: true },
  { m: "BookMyShow",      cat: "ent",       mode: "CARD",  limit: 2000, count: 3,  on: true },
  { m: "Auto Mahesh",     cat: "transport", mode: "UPI",   limit: 300,  count: 22, on: true,  bank: "ICICI" },
  { m: "Cafe Amudham",    cat: "food",      mode: "CASH",  limit: 500,  count: 8,  on: false },
  { m: "Indigo",          cat: "travel",    mode: "CARD",  limit: 15000, count: 2, on: true },
  { m: "BESCOM",          cat: "bills",     mode: "UPI",   limit: 3000, count: 1,  on: false },
];

function TrustRulesScreen() {
  return (
    <RupeeFrame activeTab="set" inboxCount={4}>
      <div style={{ overflow: "auto", height: "100%" }}>
        {/* Header */}
        <div style={{ padding: "16px 20px 8px" }}>
          <div style={{ display: "flex", alignItems: "baseline", gap: 10 }}>
            <button style={{ width: 28, height: 28, padding: 0, border: 0, background: "transparent", color: "var(--muted)", cursor: "pointer" }}>
              <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M10 3L5 8l5 5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" /></svg>
            </button>
            <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em", flex: 1 }}>SETTINGS / TRUST</span>
          </div>
          <h1 style={{ fontSize: 26, fontWeight: 600, color: "var(--text)", margin: "8px 0 4px", letterSpacing: -0.01 }}>Trust rules</h1>
          <p style={{ fontSize: 13, color: "var(--muted)", lineHeight: 1.4, marginTop: 4, marginBottom: 0 }}>
            Merchants Rupee auto-confirms without sending them to the Inbox. Add guards if you want a cap.
          </p>
        </div>

        {/* Stats bar */}
        <div style={{ padding: "14px 20px", display: "flex", gap: 16, borderTop: "1px solid var(--divider)", borderBottom: "1px solid var(--divider)", marginTop: 16 }}>
          <MiniStat k="10" v="ACTIVE" />
          <MiniStat k="2" v="PAUSED" />
          <MiniStat k="₹0" v="DAILY CAP" />
          <span style={{ flex: 1 }} />
          <button style={{
            padding: "8px 12px", border: "1px solid var(--accent)",
            background: "color-mix(in oklch, var(--accent) 14%, transparent)",
            color: "var(--accent-bright)",
            borderRadius: 100, fontFamily: "var(--font-mono)", fontSize: 10, letterSpacing: "0.1em", fontWeight: 700,
            display: "flex", alignItems: "center", gap: 4,
          }}>+ ADD</button>
        </div>

        {/* List */}
        <div style={{ padding: "8px 0 20px" }}>
          {TRUST_RULES.map((r, i) => <TrustRow key={i} rule={r} idx={i} />)}
        </div>

        <div style={{ padding: "0 20px 16px", display: "flex", justifyContent: "space-between" }}>
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>RULES LEARN FROM YOUR CONFIRMS</span>
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>EOF</span>
        </div>
      </div>
    </RupeeFrame>
  );
}

function TrustRow({ rule, idx }) {
  const cat = CATEGORIES[rule.cat];
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 12,
      padding: "12px 20px",
      borderBottom: "1px solid var(--divider)",
      opacity: rule.on ? 1 : 0.55,
    }}>
      <CatBadge id={rule.cat} size={36} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
          <span style={{ fontSize: 14, fontWeight: 500, color: "var(--text)" }}>{rule.m}</span>
          {!rule.on && (
            <span style={{ fontFamily: "var(--font-mono)", fontSize: 8, color: "var(--faint)", letterSpacing: "0.12em", background: "var(--surface-2)", padding: "1px 4px", borderRadius: 2 }}>PAUSED</span>
          )}
        </div>
        <div className="r-mono" style={{ fontSize: 10, color: "var(--dim)", marginTop: 2, letterSpacing: "0.04em" }}>
          {cat.label.toUpperCase()} · {rule.mode}{rule.bank ? ` · ${rule.bank}` : ""}
          {rule.limit && <> · ≤ ₹{formatINR(rule.limit, { withSymbol: false })}</>}
        </div>
      </div>
      <div style={{ textAlign: "right", paddingRight: 4 }}>
        <div className="r-mono" style={{ fontSize: 11, color: "var(--text)", fontWeight: 600, fontVariantNumeric: "tabular-nums" }}>{rule.count}</div>
        <div className="r-mono" style={{ fontSize: 8, color: "var(--faint)", letterSpacing: "0.12em", marginTop: 1 }}>USES</div>
      </div>
      <Toggle on={rule.on} />
    </div>
  );
}

// ════════════════════════════════════════════════════════════════════════
// 4. HOME-SCREEN WIDGETS — Android Pixel-style mock with 3 widget sizes
// ════════════════════════════════════════════════════════════════════════
function WidgetsMock() {
  return (
    <RupeeFrame showNav={false}>
      <div style={{
        position: "absolute", inset: 0,
        background: "linear-gradient(180deg, oklch(0.18 0.04 60) 0%, oklch(0.12 0.02 60) 100%)",
        overflow: "hidden",
      }}>
        {/* status row */}
        <div style={{ display: "flex", justifyContent: "space-between", padding: "30px 22px 14px" }}>
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 12, color: "var(--text)", fontWeight: 600 }}>9:30</span>
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 12, color: "var(--text)", letterSpacing: "0.04em" }}>SAT, MAY 23</span>
        </div>

        <div style={{ padding: "0 16px", display: "flex", flexDirection: "column", gap: 12 }}>
          {/* 4x2 Hero widget */}
          <WidgetHero />

          {/* row of 2x2 + 2x2 */}
          <div style={{ display: "flex", gap: 12 }}>
            <WidgetReview />
            <WidgetToday />
          </div>

          {/* 4x1 strip */}
          <WidgetStrip />
        </div>

        {/* app icon grid — placeholder */}
        <div style={{ position: "absolute", left: 16, right: 16, bottom: 72, display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 16 }}>
          {[
            { l: "Maps",     c: "oklch(0.65 0.12 145)" },
            { l: "Chrome",   c: "oklch(0.62 0.14 30)" },
            { l: "Photos",   c: "oklch(0.70 0.15 100)" },
            { l: "Files",    c: "oklch(0.60 0.10 250)" },
          ].map((a) => (
            <div key={a.l} style={{ display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}>
              <div style={{ width: 48, height: 48, borderRadius: 14, background: a.c }} />
              <span style={{ fontSize: 11, color: "var(--text)", fontWeight: 500 }}>{a.l}</span>
            </div>
          ))}
        </div>

        {/* dock */}
        <div style={{
          position: "absolute", left: 16, right: 16, bottom: 14,
          background: "color-mix(in oklch, var(--surface) 60%, transparent)",
          backdropFilter: "blur(20px)",
          borderRadius: 24, padding: "10px",
          display: "flex", justifyContent: "space-between",
        }}>
          {[
            { c: "oklch(0.62 0.14 30)" },
            { c: "oklch(0.65 0.18 250)" },
            { c: "var(--accent)", isRupee: true },
            { c: "oklch(0.65 0.12 145)" },
          ].map((a, i) => (
            a.isRupee ? <RupeeAppIcon key={i} size={44} /> :
            <div key={i} style={{ width: 44, height: 44, borderRadius: 12, background: a.c }} />
          ))}
        </div>
      </div>
    </RupeeFrame>
  );
}

function WidgetHero() {
  return (
    <div style={{
      background: "color-mix(in oklch, var(--surface) 75%, transparent)",
      backdropFilter: "blur(20px)",
      borderRadius: 22, padding: "16px 18px 18px",
      position: "relative",
    }}>
      <div style={{ display: "flex", alignItems: "center", marginBottom: 8 }}>
        <RupeeAppIcon size={18} />
        <span className="r-mono" style={{ fontSize: 10, color: "var(--text)", letterSpacing: "0.14em", marginLeft: 8, fontWeight: 600 }}>RUPEE</span>
        <span style={{ flex: 1 }} />
        <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.14em" }}>9D LEFT</span>
      </div>
      <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.14em", marginBottom: 2 }}>REMAINING</div>
      <div style={{
        fontFamily: "var(--font-sans)", fontWeight: 600, fontSize: 40, lineHeight: 1, letterSpacing: -0.025,
        color: "var(--text)", fontVariantNumeric: "tabular-nums", display: "flex", alignItems: "flex-start",
      }}>
        <span style={{ fontSize: 20, fontWeight: 500, marginTop: 4, marginRight: 2, color: "var(--muted)" }}>₹</span>
        28,420
      </div>
      <div className="r-ticks" style={{ marginTop: 10, height: 6 }}>
        {Array.from({ length: 30 }).map((_, i) => (
          <span key={i} className={i < 17 ? "on" : ""} />
        ))}
      </div>
      <div style={{ display: "flex", marginTop: 6, alignItems: "baseline" }}>
        <span className="r-mono" style={{ fontSize: 10, color: "var(--muted)" }}>56% used</span>
        <span style={{ flex: 1 }} />
        <span className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)" }}>−8.2% vs avg</span>
      </div>
    </div>
  );
}

function WidgetReview() {
  return (
    <div style={{
      flex: 1, background: "color-mix(in oklch, var(--accent) 72%, var(--surface))",
      color: "var(--accent-on)",
      borderRadius: 22, padding: "16px 16px",
      aspectRatio: "1/1",
      display: "flex", flexDirection: "column",
    }}>
      <div className="r-mono" style={{ fontSize: 9, letterSpacing: "0.14em", fontWeight: 700, opacity: 0.7 }}>INBOX</div>
      <div style={{
        fontFamily: "var(--font-sans)", fontWeight: 600, fontSize: 60,
        lineHeight: 0.95, letterSpacing: -0.04, fontVariantNumeric: "tabular-nums",
        marginTop: 6,
      }}>04</div>
      <div style={{ flex: 1 }} />
      <div style={{ fontSize: 12, fontWeight: 600, lineHeight: 1.2 }}>To review</div>
      <div style={{ display: "flex", alignItems: "center", gap: 4, marginTop: 4 }}>
        <span style={{ fontFamily: "var(--font-mono)", fontSize: 10, opacity: 0.7, letterSpacing: "0.06em" }}>tap to open</span>
        <span style={{ fontFamily: "var(--font-mono)", fontSize: 11, opacity: 0.7 }}>→</span>
      </div>
    </div>
  );
}

function WidgetToday() {
  return (
    <div style={{
      flex: 1, background: "color-mix(in oklch, var(--surface) 75%, transparent)",
      backdropFilter: "blur(20px)",
      borderRadius: 22, padding: "16px 16px",
      aspectRatio: "1/1",
      display: "flex", flexDirection: "column",
    }}>
      <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.14em", fontWeight: 700 }}>TODAY</div>
      <div style={{
        fontFamily: "var(--font-sans)", fontWeight: 600, fontSize: 30,
        lineHeight: 1, letterSpacing: -0.02, color: "var(--text)",
        fontVariantNumeric: "tabular-nums",
        marginTop: 8,
        display: "flex", alignItems: "flex-start",
      }}>
        <span style={{ fontSize: 16, fontWeight: 500, marginTop: 4, marginRight: 2, color: "var(--muted)" }}>₹</span>602
      </div>
      <div style={{ flex: 1 }} />
      {/* 3 mini-row stack */}
      <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
        {[{ c: "oklch(0.72 0.16 30)", w: 60 }, { c: "oklch(0.74 0.15 220)", w: 28 }].map((b, i) => (
          <div key={i} style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <span style={{ width: 3, height: 10, background: b.c }} />
            <div style={{ flex: 1, height: 3, background: "var(--surface-2)", borderRadius: 1.5 }}>
              <div style={{ width: `${b.w}%`, height: "100%", background: b.c }} />
            </div>
          </div>
        ))}
      </div>
      <div style={{ marginTop: 8, fontSize: 11, color: "var(--muted)", fontWeight: 500 }}>3 tx</div>
    </div>
  );
}

function WidgetStrip() {
  return (
    <div style={{
      background: "color-mix(in oklch, var(--surface) 70%, transparent)",
      backdropFilter: "blur(20px)",
      borderRadius: 18, padding: "10px 16px",
      display: "flex", alignItems: "center", gap: 14,
    }}>
      <div style={{
        width: 32, height: 32, borderRadius: 8,
        background: "color-mix(in oklch, var(--accent) 14%, transparent)",
        color: "var(--accent-bright)",
        display: "flex", alignItems: "center", justifyContent: "center",
        fontFamily: "var(--font-mono)", fontSize: 14, fontWeight: 700,
      }}>!</div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 12, fontWeight: 600, color: "var(--text)" }}>HDFC Credit · ₹18,420 due in 3d</div>
        <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.06em", marginTop: 1 }}>TAP TO REVIEW · UPCOMING</div>
      </div>
      <span style={{ color: "var(--muted)", fontSize: 14 }}>›</span>
    </div>
  );
}

// ════════════════════════════════════════════════════════════════════════
// 5. EMPTY STATES — Inbox cleared, Transactions pre-data, Calendar quiet
// ════════════════════════════════════════════════════════════════════════

// Inbox · all clear
function InboxEmpty() {
  return (
    <RupeeFrame activeTab="inbox" inboxCount={0}>
      <div style={{ padding: "16px 20px 0" }}>
        <div style={{ display: "flex", alignItems: "baseline" }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE / 02</span>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 10, color: "var(--good)", letterSpacing: "0.1em", fontWeight: 600 }}>● ALL CLEAR</span>
        </div>
        <h1 style={{ fontSize: 26, fontWeight: 600, color: "var(--text)", margin: "4px 0 0", letterSpacing: -0.01 }}>Inbox</h1>
        <p style={{ fontSize: 13, color: "var(--muted)", marginTop: 8, marginBottom: 0, lineHeight: 1.4 }}>
          Nothing waiting on you. Rupee handled the last 24 hours on its own.
        </p>
      </div>

      {/* Hero — big "00" */}
      <div style={{ padding: "32px 20px 0", textAlign: "center" }}>
        <div style={{
          fontFamily: "var(--font-sans)", fontWeight: 600, fontSize: 140, lineHeight: 0.85, letterSpacing: -0.05,
          color: "var(--good)", fontVariantNumeric: "tabular-nums",
        }}>00</div>
        <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.18em", marginTop: 4, fontWeight: 600 }}>
          TO REVIEW
        </div>
      </div>

      {/* What it did silently */}
      <div style={{ padding: "32px 20px 0" }}>
        <Module num={1} title="What Rupee did" hint="LAST 24 H">
          <ConfirmStat k="38" v="Auto-confirmed" />
          <ConfirmStat k="12" v="Trusted merchants used" />
          <ConfirmStat k="2" v="Duplicates merged" />
          <ConfirmStat k="0" v="Errors caught" last />
        </Module>
      </div>

      <div style={{ padding: "28px 20px 0", display: "flex", flexDirection: "column", gap: 8 }}>
        <button className="r-btn ghost" style={{ width: "100%" }}>Review last week →</button>
        <div className="r-mono" style={{ textAlign: "center", fontSize: 9, color: "var(--faint)", letterSpacing: "0.14em", marginTop: 10 }}>
          NEXT REVIEW · WHEN PARSER NEEDS YOU
        </div>
      </div>
    </RupeeFrame>
  );
}

function ConfirmStat({ k, v, last }) {
  return (
    <div style={{ display: "flex", alignItems: "baseline", padding: "10px 0", borderBottom: last ? 0 : "1px dotted var(--divider)" }}>
      <span style={{ fontFamily: "var(--font-sans)", fontWeight: 600, fontSize: 20, color: "var(--text)", width: 50, fontVariantNumeric: "tabular-nums", letterSpacing: -0.01 }}>{k}</span>
      <span style={{ fontSize: 13, color: "var(--muted)", flex: 1 }}>{v}</span>
    </div>
  );
}

// Transactions · pre-data
function TransactionsEmpty() {
  return (
    <RupeeFrame activeTab="tx" inboxCount={0}>
      <div style={{ padding: "16px 20px 0" }}>
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE / 03</span>
        <h1 style={{ fontSize: 26, fontWeight: 600, color: "var(--text)", margin: "4px 0 0", letterSpacing: -0.01 }}>Transactions</h1>
      </div>

      {/* Listening panel */}
      <div style={{ margin: "24px 20px 0", padding: 18, border: "1px dashed var(--hairline)", borderRadius: 10, position: "relative" }}>
        <span className="r-reg tl" /><span className="r-reg tr" /><span className="r-reg bl" /><span className="r-reg br" />
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 8 }}>
          <span style={{ width: 8, height: 8, borderRadius: 4, background: "var(--accent)", boxShadow: "0 0 0 3px color-mix(in oklch, var(--accent) 20%, transparent)" }} />
          <span className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.12em" }}>WATCHING · NOTIFICATIONS</span>
        </div>
        <div style={{ fontSize: 15, color: "var(--text)", lineHeight: 1.4, fontWeight: 500 }}>
          No transactions yet. Your first ping will appear here.
        </div>
        <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", marginTop: 10, letterSpacing: "0.08em" }}>
          0 EVENTS · LAST CHECK 11s AGO
        </div>
      </div>

      {/* Skeleton rows — pulsing placeholders */}
      <div style={{ padding: "28px 0 0", opacity: 0.5 }}>
        <div style={{ padding: "0 20px 8px", display: "flex", alignItems: "baseline" }}>
          <span style={{ fontSize: 12, color: "var(--muted)", fontWeight: 600, textTransform: "uppercase" }}>Today</span>
          <span style={{ flex: 1, marginLeft: 12, borderTop: "1px solid var(--divider)" }} />
        </div>
        {Array.from({ length: 3 }).map((_, i) => <SkelRow key={i} />)}
      </div>

      <div style={{ padding: "28px 20px 0" }}>
        <Module num={1} title="Speed things up" hint="OPTIONAL">
          <ActionRow2 num="01" title="Add an expense manually" sub="Skip the wait" />
          <ActionRow2 num="02" title="Import last month's CSV" sub="From your bank" />
          <ActionRow2 num="03" title="Take a receipt photo" sub="OCR · alpha" last />
        </Module>
      </div>
    </RupeeFrame>
  );
}

function SkelRow() {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 20px", borderBottom: "1px solid var(--divider)" }}>
      <div className="r-skel" style={{ width: 32, height: 32, borderRadius: 4, flexShrink: 0 }} />
      <div style={{ flex: 1 }}>
        <div className="r-skel" style={{ width: "60%", height: 12, marginBottom: 6 }} />
        <div className="r-skel" style={{ width: "30%", height: 10 }} />
      </div>
      <div className="r-skel" style={{ width: 60, height: 14 }} />
    </div>
  );
}

function ActionRow2({ num, title, sub, last }) {
  return (
    <div style={{ display: "flex", alignItems: "center", padding: "12px 0", borderBottom: last ? 0 : "1px dotted var(--divider)" }}>
      <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", marginRight: 14, letterSpacing: "0.08em", width: 22 }}>{num}</span>
      <div style={{ flex: 1 }}>
        <div style={{ fontSize: 14, color: "var(--text)", fontWeight: 500 }}>{title}</div>
        <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", marginTop: 2, letterSpacing: "0.06em" }}>{sub}</div>
      </div>
      <span style={{ color: "var(--muted)", fontSize: 14 }}>›</span>
    </div>
  );
}

// Calendar · quiet month
function CalendarQuiet() {
  const DAYS = 31;
  const spendMap = { 3: 80, 9: 120, 17: 200, 22: 60 };
  const today = 23;
  const lead = 5;
  const cells = [
    ...Array.from({ length: lead }).map(() => ({ blank: true })),
    ...Array.from({ length: DAYS }, (_, i) => {
      const d = i + 1;
      return { d, spend: spendMap[d] || 0, today: d === today, future: d > today };
    }),
  ];
  while (cells.length % 7 !== 0) cells.push({ blank: true });
  const total = Object.values(spendMap).reduce((a, b) => a + b, 0);

  return (
    <RupeeFrame activeTab="cal" inboxCount={0}>
      <div style={{ padding: "16px 20px 12px" }}>
        <div style={{ display: "flex", alignItems: "baseline" }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE / 04</span>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 10, color: "var(--good)", letterSpacing: "0.1em", fontWeight: 600 }}>● QUIET MONTH</span>
        </div>
        <div style={{ display: "flex", alignItems: "center", marginTop: 8, gap: 12 }}>
          <button style={{ background: "transparent", color: "var(--muted)", border: 0, padding: 4 }}>
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none"><path d="M11 4l-5 5 5 5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" /></svg>
          </button>
          <h1 style={{ fontSize: 26, fontWeight: 600, color: "var(--text)", margin: 0, letterSpacing: -0.01, flex: 1 }}>May 2026</h1>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--muted)" }}>MTD ₹{total}</span>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", padding: "0 16px 4px" }}>
        {["M", "T", "W", "T", "F", "S", "S"].map((d, i) => (
          <div key={i} className="r-mono" style={{ textAlign: "center", fontSize: 9, color: "var(--faint)", letterSpacing: "0.1em" }}>{d}</div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", padding: "0 16px", gap: 2 }}>
        {cells.map((c, i) => {
          if (c.blank) return <div key={i} style={{ aspectRatio: "1/1.05" }} />;
          return (
            <div key={i} style={{
              aspectRatio: "1/1.05",
              background: "var(--surface)",
              border: c.today ? "1.5px solid var(--accent-bright)" : "1px solid var(--divider)",
              borderRadius: 3,
              padding: "5px 5px 4px",
              display: "flex", flexDirection: "column", justifyContent: "space-between",
              opacity: c.future ? 0.4 : 1,
            }}>
              <span className="r-mono" style={{ fontSize: 10, color: c.today ? "var(--accent-bright)" : "var(--text)", fontWeight: c.today ? 700 : 500 }}>{c.d}</span>
              {c.spend > 0 ? (
                <span style={{ fontFamily: "var(--font-mono)", fontSize: 7.5, color: "var(--muted)" }}>{c.spend}</span>
              ) : !c.future ? (
                <span style={{ fontSize: 7.5, color: "var(--faint)" }}>·</span>
              ) : null}
            </div>
          );
        })}
      </div>

      {/* Big celebration block */}
      <div style={{ padding: "32px 20px 0" }}>
        <div style={{
          padding: "20px 18px 18px",
          background: "color-mix(in oklch, var(--good) 12%, var(--surface))",
          border: "1px solid color-mix(in oklch, var(--good) 35%, transparent)",
          borderRadius: 10,
          position: "relative",
        }}>
          <span className="r-reg tl" /><span className="r-reg tr" /><span className="r-reg bl" /><span className="r-reg br" />
          <div className="r-mono" style={{ fontSize: 10, color: "var(--good)", letterSpacing: "0.14em", marginBottom: 6, fontWeight: 700 }}>
            ● QUIETEST MONTH IN A YEAR
          </div>
          <div style={{ fontSize: 18, fontWeight: 500, color: "var(--text)", lineHeight: 1.35, textWrap: "balance" }}>
            <span style={{ fontWeight: 700 }}>19 no-spend days</span>. Most spent: ₹200 on May 17. You're <span style={{ color: "var(--good)", fontWeight: 600 }}>₹64,540 under budget</span>.
          </div>
        </div>
      </div>

      <div style={{ padding: "20px 20px 8px", display: "flex", justifyContent: "space-between" }}>
        <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>NEXT BIG SPEND · ESTIMATED RENT JUN 1</span>
      </div>
    </RupeeFrame>
  );
}

Object.assign(window, { NotifLockScreen, NotifShade, MinimalRecap, TrustRulesScreen, WidgetsMock, InboxEmpty, TransactionsEmpty, CalendarQuiet });
