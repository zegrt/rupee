// Rupee — Home screen
// Hero budget · weekly · category budgets · upcoming dues · quick actions · recent activity

function HomeHero({ remaining = 28420, budget = 65000, daysLeft = 9, over = false }) {
  const pct = Math.min(1, Math.max(0, 1 - remaining / budget));
  const spent = budget - remaining;
  // segmented ticks — 30 days
  const TICKS = 30;
  const onCount = Math.round((spent / budget) * TICKS);
  const ticks = Array.from({ length: TICKS }).map((_, i) => {
    if (over && i >= TICKS) return "over";
    return i < onCount ? "on" : "";
  });

  return (
    <div style={{ padding: "20px 20px 24px", background: "var(--bg)" }}>
      {/* eyebrow row */}
      <div style={{ display: "flex", alignItems: "center", marginBottom: 24 }}>
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>
          MAY · 2026
        </span>
        <span style={{ flex: 1 }} />
        <span className="r-mono" style={{ fontSize: 10, color: "var(--muted)", letterSpacing: "0.1em" }}>
          {daysLeft}D · LEFT
        </span>
      </div>

      <div className="r-eyebrow" style={{ marginBottom: 8 }}>
        {over ? "OVER BUDGET BY" : "REMAINING THIS MONTH"}
      </div>
      <HeroAmount value={remaining} color={over ? "var(--bad)" : "var(--text)"} />

      <div style={{ marginTop: 20 }}>
        <div className="r-ticks" style={{ height: 12 }}>
          {ticks.map((c, i) => <span key={i} className={c} />)}
        </div>
        <div style={{ display: "flex", marginTop: 8, alignItems: "baseline", gap: 6 }}>
          <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)" }}>
            ₹{formatINR(spent, { withSymbol: false })}
          </span>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)" }}>
            of ₹{formatINR(budget, { withSymbol: false })} · {Math.round(pct * 100)}%
          </span>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)" }}>
            ₹{formatINR(Math.round(remaining / Math.max(daysLeft, 1)), { withSymbol: false })}/DAY
          </span>
        </div>
      </div>
    </div>
  );
}

function WeeklyStrip() {
  const days = [
    { d: "M", v: 0.55 },
    { d: "T", v: 0.35 },
    { d: "W", v: 0.78 },
    { d: "T", v: 0.45 },
    { d: "F", v: 0.92 },
    { d: "S", v: 0.30 },
    { d: "S", v: 0.18, today: true },
  ];
  const week = 14820;
  return (
    <div style={{ padding: "0 20px" }}>
      <div style={{
        display: "flex",
        borderTop: "1px solid var(--divider)",
        paddingTop: 12, marginBottom: 12,
      }}>
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.1em", marginRight: 10 }}>02 /</span>
        <span style={{ fontFamily: "var(--font-mono)", fontSize: 11, color: "var(--muted)", letterSpacing: "0.12em", textTransform: "uppercase", flex: 1 }}>This week</span>
        <span className="r-mono" style={{ fontSize: 11, color: "var(--text)" }}>₹{formatINR(week, { withSymbol: false })}</span>
      </div>
      <div style={{ display: "flex", gap: 6, alignItems: "flex-end", height: 56 }}>
        {days.map((day, i) => (
          <div key={i} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}>
            <div style={{
              width: "100%", height: `${day.v * 48}px`, minHeight: 4,
              background: day.today ? "var(--accent)" : "var(--surface-3)",
              borderRadius: 2,
            }} />
            <span className="r-mono" style={{ fontSize: 9, color: day.today ? "var(--accent)" : "var(--faint)" }}>
              {day.d}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

function CategoryBudgets() {
  return (
    <Module num={3} title="Categories" hint="6 / 6" action="EDIT" style={{ marginTop: 20 }}>
      <div style={{ display: "flex", flexDirection: "column", gap: 14 }}>
        {SAMPLE_BUDGETS.map((b) => {
          const pct = Math.min(1.2, b.spent / b.limit);
          const isOver = b.over || pct > 1;
          const cat = CATEGORIES[b.id];
          return (
            <div key={b.id}>
              <div style={{ display: "flex", alignItems: "baseline", marginBottom: 6, gap: 8 }}>
                <span style={{ width: 8, height: 8, borderRadius: 2, background: cat.c, marginRight: 2 }} />
                <span style={{ fontSize: 13, fontWeight: 500, color: "var(--text)", flex: 1 }}>{b.label}</span>
                <span className="r-mono" style={{ fontSize: 11, color: isOver ? "var(--bad)" : "var(--muted)" }}>
                  ₹{formatINR(b.spent, { withSymbol: false })}
                </span>
                <span className="r-mono" style={{ fontSize: 11, color: "var(--faint)" }}>
                  / {formatINR(b.limit, { withSymbol: false })}
                </span>
              </div>
              <div className={`r-bar ${isOver ? "over" : ""}`} style={{ height: 4 }}>
                <div style={{ width: `${Math.min(100, pct * 100)}%`, background: isOver ? "var(--bad)" : cat.c }} />
              </div>
            </div>
          );
        })}
      </div>
    </Module>
  );
}

function DuesStrip() {
  const totalDue = SAMPLE_DUES.reduce((a, b) => a + b.amount, 0);
  return (
    <Module num={4} title="Upcoming" hint={`${SAMPLE_DUES.length} · DUES`} action="VIEW" style={{ marginTop: 36 }}>
      <div style={{ display: "flex", gap: 8, overflowX: "auto", paddingBottom: 4, marginRight: -20 }}>
        {SAMPLE_DUES.map((d) => <DueCard key={d.id} due={d} />)}
        <div style={{ width: 20, flexShrink: 0 }} />
      </div>
      <div style={{ marginTop: 10, display: "flex", justifyContent: "space-between" }}>
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)" }}>TOTAL · NEXT 30D</span>
        <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)" }}>₹{formatINR(totalDue, { withSymbol: false })}</span>
      </div>
    </Module>
  );
}

// DueCard — visually differentiated by type (cards/EMIs/recurring)
function DueCard({ due }) {
  const isCard = due.type === "card";
  const isEMI = due.type === "emi";
  const isSub = due.type === "sub";
  return (
    <div style={{
      flexShrink: 0,
      width: 158,
      padding: "12px 12px 12px",
      background: due.urgent ? "color-mix(in oklch, var(--accent) 12%, var(--surface))" : "var(--surface)",
      borderRadius: isCard ? 8 : isEMI ? 2 : 999,
      border: due.urgent ? "1px solid var(--accent)" : "1px solid transparent",
      position: "relative",
      ...(isCard ? { aspectRatio: "1.6/1", width: 178 } : isSub ? { borderRadius: 14 } : {}),
    }}>
      <div style={{ display: "flex", alignItems: "center", gap: 6, marginBottom: 8 }}>
        {due.bank ? <BankBadge id={due.bank} size={20} /> : (
          <div style={{
            width: 20, height: 20, borderRadius: isSub ? 10 : 2,
            background: isEMI ? "var(--surface-3)" : "var(--surface-3)",
            display: "flex", alignItems: "center", justifyContent: "center",
            fontFamily: "var(--font-mono)", fontSize: 10, color: "var(--muted)",
          }}>{isEMI ? "≡" : "↻"}</div>
        )}
        <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.1em" }}>
          {isCard ? "CARD" : isEMI ? "EMI" : "SUB"}
        </span>
        <span style={{ flex: 1 }} />
        {due.urgent && <span style={{ width: 5, height: 5, borderRadius: 5, background: "var(--accent)" }} />}
      </div>
      <div style={{ fontSize: 13, fontWeight: 500, color: "var(--text)" }}>{due.title}</div>
      <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", marginTop: 2 }}>{due.sub}</div>
      <div style={{ marginTop: 10, display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
        <Amount value={due.amount} size={16} weight={600} color={due.urgent ? "var(--accent-bright)" : "var(--text)"} />
        <span className="r-mono" style={{ fontSize: 9, color: due.urgent ? "var(--accent-bright)" : "var(--muted)", letterSpacing: "0.06em" }}>{due.due}</span>
      </div>
    </div>
  );
}

function QuickActions({ inboxCount = 4 }) {
  return (
    <div style={{ padding: "0 20px", marginTop: 36 }}>
      <div style={{ display: "flex", gap: 8 }}>
        <button className="r-btn" style={{ flex: 1, justifyContent: "space-between", padding: "14px 16px" }}>
          <span style={{ display: "flex", flexDirection: "column", alignItems: "flex-start", lineHeight: 1.1 }}>
            <span className="r-mono" style={{ fontSize: 9, opacity: 0.7, letterSpacing: "0.12em" }}>REVIEW</span>
            <span style={{ fontSize: 14, fontWeight: 600 }}>{inboxCount} pending</span>
          </span>
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none">
            <path d="M5 3l5 5-5 5" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </button>
        <button className="r-btn ghost" style={{ flex: 1, justifyContent: "space-between", padding: "14px 16px" }}>
          <span style={{ display: "flex", flexDirection: "column", alignItems: "flex-start", lineHeight: 1.1 }}>
            <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>MANUAL</span>
            <span style={{ fontSize: 14, fontWeight: 600 }}>Add tx</span>
          </span>
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <path d="M7 2v10M2 7h10" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" />
          </svg>
        </button>
      </div>
    </div>
  );
}

function RecentActivity({ confMode = "stripe" }) {
  const items = SAMPLE_TX.slice(0, 5);
  return (
    <Module num={5} title="Recent" hint="LIVE" action="ALL →" style={{ marginTop: 36 }}>
      <div className={`r-conf-mode-${confMode}`} style={{ marginLeft: -20, marginRight: -20 }}>
        {items.map((t, i) => (
          <TxRow key={t.id} tx={t} compact />
        ))}
      </div>
    </Module>
  );
}

function TxRow({ tx, compact = false }) {
  const stripeKind = tx.conf === "user" ? "user" : tx.conf === "suggest" ? "suggest" : "auto";
  return (
    <div className={`r-conf-row ${stripeKind}`} style={{ display: "flex", alignItems: "stretch", gap: 0 }}>
      <div className={`r-conf-stripe ${stripeKind}`} />
      <div style={{ flex: 1, display: "flex", alignItems: "center", gap: 14, padding: "14px 20px", minHeight: 60 }}>
        <CatBadge id={tx.cat} size={32} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <span style={{ fontSize: 14, fontWeight: 500, color: "var(--text)", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
              {tx.merchant}
            </span>
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 6, marginTop: 2 }}>
            <span style={{ color: "var(--faint)", display: "inline-flex", alignItems: "center" }}>
              <ModeIcon mode={tx.mode} />
            </span>
            <span className="r-mono" style={{ fontSize: 10, color: "var(--dim)", letterSpacing: "0.06em" }}>
              {tx.mode}{tx.bank ? ` · ${tx.bank}` : ""} · {tx.time.replace(/^.+· /, "")}
            </span>
          </div>
        </div>
        <div style={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 4 }}>
          <Amount value={tx.amount} size={15} weight={600} sign={tx.isIncome ? true : false} color={tx.isIncome ? "var(--good)" : undefined} />
          {!compact && (
            <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.06em" }}>
              {tx.conf === "auto" ? "AUTO" : tx.conf === "user" ? "VERIFIED" : "SUGGESTED"}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

// Home screen — full
function HomeScreen({ confMode = "stripe", over = false, inboxCount = 4 }) {
  return (
    <RupeeFrame activeTab="home" inboxCount={inboxCount}>
      <div style={{ paddingBottom: 24 }}>
        {/* Greeting row */}
        <div style={{ display: "flex", padding: "16px 20px 4px", alignItems: "center" }}>
          <div style={{ flex: 1 }}>
            <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE / 01</div>
            <div style={{ fontSize: 20, fontWeight: 600, color: "var(--text)", marginTop: 2, letterSpacing: -0.01 }}>
              Hi, Aman.
            </div>
          </div>
          <div style={{
            width: 32, height: 32, border: "1px solid var(--divider)", borderRadius: 4,
            display: "flex", alignItems: "center", justifyContent: "center", color: "var(--muted)",
          }}>
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <path d="M7 2v6.5M7 12v.5M7 8.5l3-2M7 8.5l-3-2M1 7a6 6 0 1 1 12 0 6 6 0 0 1-12 0z" stroke="currentColor" strokeWidth="1.2" />
            </svg>
          </div>
        </div>

        <HomeHero over={over} />
        <WeeklyStrip />
        <CategoryBudgets />
        <DuesStrip />
        <QuickActions inboxCount={inboxCount} />
        <RecentActivity confMode={confMode} />

        {/* footer registration */}
        <div style={{ padding: "32px 20px 8px", display: "flex", justifyContent: "space-between" }}>
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE · v0.4 · LOCAL</span>
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>EOF</span>
        </div>
      </div>
    </RupeeFrame>
  );
}

// HomeEmpty — first-run state, no tx yet
function HomeEmpty() {
  return (
    <RupeeFrame activeTab="home" inboxCount={0}>
      <div style={{ padding: "16px 20px" }}>
        <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE / 01</div>
        <div style={{ fontSize: 20, fontWeight: 600, color: "var(--text)", marginTop: 2 }}>Hi, Aman.</div>
      </div>

      <div style={{ padding: "8px 20px 0" }}>
        <div className="r-eyebrow" style={{ marginBottom: 8 }}>REMAINING THIS MONTH</div>
        <HeroAmount value={65000} />
        <div className="r-ticks" style={{ marginTop: 20, height: 12 }}>
          {Array.from({ length: 30 }).map((_, i) => <span key={i} />)}
        </div>
        <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.06em", marginTop: 8 }}>
          0 of ₹65,000 — month begins.
        </div>
      </div>

      {/* "watching" panel — alive but not broken */}
      <div style={{ margin: "32px 20px 0", padding: 18, border: "1px dashed var(--hairline)", borderRadius: 8, position: "relative" }}>
        <span className="r-reg tl" /><span className="r-reg tr" /><span className="r-reg bl" /><span className="r-reg br" />
        <div style={{ display: "flex", alignItems: "center", gap: 10, marginBottom: 8 }}>
          <span style={{ width: 8, height: 8, borderRadius: "50%", background: "var(--accent)", boxShadow: "0 0 0 3px color-mix(in oklch, var(--accent) 20%, transparent)" }} />
          <span className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.12em" }}>WATCHING · NOTIFICATIONS</span>
        </div>
        <div style={{ fontSize: 15, color: "var(--text)", lineHeight: 1.4, fontWeight: 500, textWrap: "pretty" }}>
          Rupee is listening to your bank notifications. The first transaction will appear here automatically.
        </div>
        <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", marginTop: 10, letterSpacing: "0.08em" }}>
          0 EVENTS · LAST CHECK 11s AGO
        </div>
      </div>

      <Module num={2} title="While you wait" style={{ marginTop: 28 }}>
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          <ActionRow num="01" title="Add a recent expense manually" />
          <ActionRow num="02" title="Set category budgets" />
          <ActionRow num="03" title="Add a credit card or EMI to track" />
        </div>
      </Module>
    </RupeeFrame>
  );
}

function ActionRow({ num, title }) {
  return (
    <div style={{ display: "flex", alignItems: "center", padding: "12px 0", borderBottom: "1px dashed var(--divider)" }}>
      <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", marginRight: 12, letterSpacing: "0.08em" }}>{num}</span>
      <span style={{ flex: 1, fontSize: 14, color: "var(--text)" }}>{title}</span>
      <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
        <path d="M3 7h8M7 3l4 4-4 4" stroke="var(--muted)" strokeWidth="1.4" strokeLinecap="round" strokeLinejoin="round" />
      </svg>
    </div>
  );
}

Object.assign(window, { HomeScreen, HomeEmpty, TxRow, DueCard });
