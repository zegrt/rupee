// Rupee — more screens
// Settings subscreens (Cards/EMIs, Accounts, Budgets, Recurring) +
// Transactions search active state + Calendar long-press peek

// Shared sub-screen header
function SubHeader({ section, title, hint, right }) {
  return (
    <div style={{ padding: "16px 20px 8px" }}>
      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <button style={{ width: 28, height: 28, padding: 0, border: 0, background: "transparent", color: "var(--muted)", cursor: "pointer" }}>
          <svg width="16" height="16" viewBox="0 0 16 16" fill="none"><path d="M10 3L5 8l5 5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" /></svg>
        </button>
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em", flex: 1 }}>{section}</span>
        {hint && <span className="r-mono" style={{ fontSize: 10, color: "var(--muted)", letterSpacing: "0.1em" }}>{hint}</span>}
      </div>
      <div style={{ display: "flex", alignItems: "baseline", marginTop: 8 }}>
        <h1 style={{ fontSize: 26, fontWeight: 600, color: "var(--text)", margin: 0, letterSpacing: -0.01, flex: 1 }}>{title}</h1>
        {right}
      </div>
    </div>
  );
}

// ════════════════════════════════════════════════════════════════════════
// 1. CARDS & EMIs
// ════════════════════════════════════════════════════════════════════════
function CardsAndEMIs() {
  return (
    <RupeeFrame activeTab="set" inboxCount={4}>
      <div style={{ overflow: "auto", height: "100%" }}>
        <SubHeader section="SETTINGS / 06" title="Cards & EMIs"
          right={<button style={{
            padding: "7px 12px", border: "1px solid var(--accent)",
            background: "color-mix(in oklch, var(--accent) 14%, transparent)",
            color: "var(--accent-bright)", borderRadius: 100,
            fontFamily: "var(--font-mono)", fontSize: 10, letterSpacing: "0.12em", fontWeight: 700,
          }}>+ ADD</button>} />

        {/* CARDS */}
        <Module num={1} title="Credit cards" hint="1 ACTIVE">
          <CardRow bank="HDFC" brand="VISA" last="9421" name="Aman Sharma"
                   limit={400000} used={142850} stmtDay="20" dueIn={3}
                   minDue={2450} fullDue={18420} urgent />
        </Module>

        {/* EMIs */}
        <Module num={2} title="EMIs" hint="1 ACTIVE" style={{ marginTop: 28 }}>
          <EmiRow name="Macbook Pro" lender="Bajaj" totalPaid={84990} total={101988}
                  monthly={8499} of={10} months={12} nextOn="Jun 5" />
          <div style={{ height: 14 }} />
          <EmiRow name="Refrigerator" lender="HDFC" totalPaid={28000} total={28000}
                  monthly={3500} of={8} months={8} nextOn="—" finished />
        </Module>

        {/* PAUSED */}
        <Module num={3} title="Paused" hint="0" style={{ marginTop: 28 }}>
          <div style={{ padding: "14px 16px", border: "1px dashed var(--hairline)", borderRadius: 6, textAlign: "center" }}>
            <span style={{ fontSize: 13, color: "var(--muted)" }}>No paused cards or loans.</span>
          </div>
        </Module>

        <div style={{ padding: "26px 20px 8px", display: "flex", justifyContent: "space-between" }}>
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>NEXT DUE · HDFC JUN 02</span>
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)" }}>EOF</span>
        </div>
      </div>
    </RupeeFrame>
  );
}

function CardRow({ bank, brand, last, name, limit, used, stmtDay, dueIn, minDue, fullDue, urgent }) {
  const pct = used / limit;
  return (
    <div style={{ position: "relative", background: "var(--surface)", borderRadius: 12, overflow: "hidden", marginBottom: 12 }}>
      {/* card face */}
      <div style={{
        background: urgent
          ? "linear-gradient(135deg, color-mix(in oklch, var(--accent) 30%, var(--surface)) 0%, var(--surface) 100%)"
          : "var(--surface-2)",
        padding: "14px 16px 12px",
        position: "relative",
      }}>
        <span className="r-reg tl" /><span className="r-reg tr" />
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <BankBadge id={bank} size={22} />
          <span className="r-mono" style={{ fontSize: 10, color: "var(--text)", letterSpacing: "0.14em", fontWeight: 700 }}>{bank} · {brand}</span>
          <span style={{ flex: 1 }} />
          {urgent && <span className="r-mono" style={{ fontSize: 9, color: "var(--accent-bright)", letterSpacing: "0.12em", fontWeight: 700 }}>● DUE IN {dueIn}D</span>}
        </div>
        <div className="r-mono" style={{ fontSize: 14, color: "var(--text)", letterSpacing: "0.18em", marginTop: 16, fontWeight: 500 }}>
          •••• •••• •••• {last}
        </div>
        <div style={{ display: "flex", marginTop: 12, alignItems: "baseline" }}>
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.1em" }}>{name.toUpperCase()}</span>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.1em" }}>STMT {stmtDay}TH</span>
        </div>
      </div>

      {/* detail rows */}
      <div style={{ padding: "12px 16px" }}>
        <div style={{ display: "flex", alignItems: "baseline", marginBottom: 4 }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.1em" }}>USED · {Math.round(pct * 100)}%</span>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 11, color: "var(--text)" }}>₹{formatINR(used, { withSymbol: false })} / {formatINR(limit, { withSymbol: false })}</span>
        </div>
        <div className="r-bar" style={{ height: 4 }}><div style={{ width: `${pct * 100}%` }} /></div>

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 12, marginTop: 14 }}>
          <DueCell k="MIN DUE" v={minDue} />
          <DueCell k="FULL DUE" v={fullDue} accent />
        </div>

        <div style={{ display: "flex", gap: 6, marginTop: 14 }}>
          <button className="r-btn" style={{ flex: 1, padding: "10px 14px", fontSize: 12 }}>Pay full ₹{formatINR(fullDue, { withSymbol: false })}</button>
          <button className="r-btn ghost" style={{ padding: "10px 14px", fontSize: 12 }}>Pay min</button>
        </div>
      </div>
    </div>
  );
}

function DueCell({ k, v, accent }) {
  return (
    <div style={{ padding: "10px 12px", background: accent ? "color-mix(in oklch, var(--accent) 12%, transparent)" : "var(--surface-2)", borderRadius: 6 }}>
      <div className="r-mono" style={{ fontSize: 9, color: accent ? "var(--accent-bright)" : "var(--faint)", letterSpacing: "0.14em", fontWeight: 600 }}>{k}</div>
      <Amount value={v} size={18} weight={600} color={accent ? "var(--accent-bright)" : "var(--text)"} />
    </div>
  );
}

function EmiRow({ name, lender, totalPaid, total, monthly, of, months, nextOn, finished }) {
  const pct = totalPaid / total;
  return (
    <div style={{
      background: "var(--surface)", borderRadius: 8, padding: "14px 16px",
      opacity: finished ? 0.55 : 1,
    }}>
      <div style={{ display: "flex", alignItems: "baseline", marginBottom: 8 }}>
        <span style={{ fontSize: 14, fontWeight: 600, color: "var(--text)", flex: 1 }}>{name}</span>
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.08em" }}>{lender.toUpperCase()}</span>
        {finished && <span className="r-mono" style={{ fontSize: 9, color: "var(--good)", letterSpacing: "0.12em", marginLeft: 8, fontWeight: 700 }}>● PAID OFF</span>}
      </div>
      <div style={{ display: "flex", marginBottom: 6, alignItems: "baseline", gap: 6 }}>
        <span className="r-mono" style={{ fontSize: 11, color: "var(--text)" }}>
          {of} / {months}
        </span>
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)" }}>installments</span>
        <span style={{ flex: 1 }} />
        <span className="r-mono" style={{ fontSize: 10, color: "var(--muted)" }}>NEXT · {nextOn}</span>
      </div>
      {/* Segmented progress — one tick per month */}
      <div style={{ display: "flex", gap: 2, height: 6 }}>
        {Array.from({ length: months }).map((_, i) => (
          <span key={i} style={{ flex: 1, background: i < of ? "var(--accent)" : "var(--surface-2)", borderRadius: 1 }} />
        ))}
      </div>
      <div style={{ display: "flex", marginTop: 10, alignItems: "baseline" }}>
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)" }}>MONTHLY</span>
        <span style={{ flex: 1 }} />
        <Amount value={monthly} size={15} weight={600} />
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", marginLeft: 10 }}>
          / ₹{formatINR(total, { withSymbol: false })} TOTAL
        </span>
      </div>
    </div>
  );
}

// ════════════════════════════════════════════════════════════════════════
// 2. ACCOUNTS
// ════════════════════════════════════════════════════════════════════════
const SAMPLE_ACCOUNTS = [
  { type: "bank", id: "HDFC",  name: "HDFC Savings",  last: "××4521", live: true, tpl: 0.98, txMo: 142 },
  { type: "bank", id: "ICICI", name: "ICICI Savings", last: "××8830", live: true, tpl: 0.96, txMo: 88 },
  { type: "bank", id: "AXIS",  name: "Axis Credit",   last: "××9421", live: true, tpl: 0.94, txMo: 24, kind: "card" },
  { type: "bank", id: "JUPITER", name: "Jupiter",    last: "××2210", live: false, tpl: 0.72, txMo: 0 },
];

function AccountsScreen() {
  return (
    <RupeeFrame activeTab="set" inboxCount={4}>
      <div style={{ overflow: "auto", height: "100%" }}>
        <SubHeader section="SETTINGS / 07" title="Accounts" hint="4 LINKED"
          right={<button style={{
            padding: "7px 12px", border: "1px solid var(--accent)",
            background: "color-mix(in oklch, var(--accent) 14%, transparent)",
            color: "var(--accent-bright)", borderRadius: 100,
            fontFamily: "var(--font-mono)", fontSize: 10, letterSpacing: "0.12em", fontWeight: 700,
          }}>+ LINK</button>} />

        {/* Banks */}
        <Module num={1} title="Banks" hint="3 LIVE">
          {SAMPLE_ACCOUNTS.filter((a) => a.type === "bank" && a.kind !== "card").map((a) => <AccountRow key={a.id} a={a} />)}
        </Module>

        {/* Credit lines */}
        <Module num={2} title="Credit lines" hint="1 LIVE" style={{ marginTop: 24 }}>
          {SAMPLE_ACCOUNTS.filter((a) => a.kind === "card").map((a) => <AccountRow key={a.id} a={a} />)}
        </Module>

        {/* Cash & wallets */}
        <Module num={3} title="Cash & wallets" hint="MANUAL ONLY" style={{ marginTop: 24 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 0", borderBottom: "1px dotted var(--divider)" }}>
            <CatBadge id="cash" size={36} />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14, color: "var(--text)", fontWeight: 500 }}>Wallet</div>
              <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", marginTop: 2 }}>UPDATED 3D AGO · ₹2,400</div>
            </div>
            <span style={{ color: "var(--muted)" }}>›</span>
          </div>
        </Module>

        {/* Health note */}
        <div style={{ margin: "24px 20px 20px", padding: 14, border: "1px dashed var(--hairline)", borderRadius: 8, position: "relative" }}>
          <div className="r-mono" style={{ fontSize: 9, color: "var(--accent-bright)", letterSpacing: "0.14em", marginBottom: 4 }}>● PARSER HEALTH</div>
          <div style={{ fontSize: 12, color: "var(--text)", lineHeight: 1.45 }}>
            All live accounts at <span style={{ color: "var(--good)", fontWeight: 600 }}>95%+</span> template recognition.
            Jupiter has been quiet for 18 days — paused for now.
          </div>
        </div>
      </div>
    </RupeeFrame>
  );
}

function AccountRow({ a }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 0", borderBottom: "1px dotted var(--divider)" }}>
      <BankBadge id={a.id} size={36} />
      <div style={{ flex: 1 }}>
        <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
          <span style={{ fontSize: 14, color: "var(--text)", fontWeight: 500 }}>{a.name}</span>
          {!a.live && <span style={{ fontFamily: "var(--font-mono)", fontSize: 8, color: "var(--faint)", letterSpacing: "0.12em", background: "var(--surface-2)", padding: "1px 4px", borderRadius: 2 }}>PAUSED</span>}
        </div>
        <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", marginTop: 2, letterSpacing: "0.04em" }}>
          {a.last} · {a.txMo} TX/MO · TEMPLATE {Math.round(a.tpl * 100)}%
        </div>
      </div>
      <Toggle on={a.live} />
    </div>
  );
}

// ════════════════════════════════════════════════════════════════════════
// 3. BUDGETS · editor
// ════════════════════════════════════════════════════════════════════════
function BudgetsScreen() {
  const items = [
    { id: "food", label: "Food",        spent: 6480, cap: 8000, default: 7500 },
    { id: "transport", label: "Transport", spent: 2740, cap: 3500, default: 3000 },
    { id: "groceries", label: "Groceries", spent: 4220, cap: 6000, default: 5500 },
    { id: "shop", label: "Shopping",    spent: 5850, cap: 4500, default: 4500, over: true },
    { id: "bills", label: "Bills",      spent: 7820, cap: 9000, default: 9000 },
    { id: "ent",  label: "Entertainment", spent: 1340, cap: 2500, default: 2500 },
  ];
  const totalCap = items.reduce((a, b) => a + b.cap, 0);
  const totalSpent = items.reduce((a, b) => a + b.spent, 0);
  return (
    <RupeeFrame activeTab="set" inboxCount={4}>
      <div style={{ overflow: "auto", height: "100%" }}>
        <SubHeader section="SETTINGS / 05" title="Budgets" hint="MONTHLY"
          right={<button style={{
            padding: "7px 12px", border: "1px solid var(--divider)",
            background: "transparent", color: "var(--muted)",
            borderRadius: 100, fontFamily: "var(--font-mono)", fontSize: 10, letterSpacing: "0.12em",
          }}>RESET</button>} />

        {/* Total */}
        <div style={{ padding: "8px 20px 0" }}>
          <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.14em", marginBottom: 4 }}>TOTAL MONTHLY CAP</div>
          <div style={{ display: "flex", alignItems: "baseline", gap: 12 }}>
            <HeroAmount value={totalCap} />
            <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)", letterSpacing: "0.06em" }}>USED ₹{formatINR(totalSpent, { withSymbol: false })}</span>
          </div>
          <div className="r-bar" style={{ marginTop: 12, height: 4 }}>
            <div style={{ width: `${(totalSpent / totalCap) * 100}%` }} />
          </div>
        </div>

        {/* Spillover option */}
        <div style={{ margin: "20px 20px 0", padding: "10px 14px", background: "var(--surface)", borderRadius: 6, display: "flex", alignItems: "center", gap: 10 }}>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 13, color: "var(--text)", fontWeight: 500 }}>Spillover unused → next month</div>
            <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", marginTop: 2, letterSpacing: "0.06em" }}>EXTRA SAVINGS LANDED LAST APR</div>
          </div>
          <Toggle on={true} />
        </div>

        {/* Per-category rows with sliders */}
        <Module num={1} title="Per category" hint="6 ACTIVE" style={{ marginTop: 24 }}>
          {items.map((b) => <BudgetSliderRow key={b.id} b={b} />)}
        </Module>

        <div style={{ padding: "26px 20px 8px", display: "flex", justifyContent: "space-between" }}>
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>RESETS · 1ST OF MONTH</span>
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)" }}>EOF</span>
        </div>
      </div>
    </RupeeFrame>
  );
}

function BudgetSliderRow({ b }) {
  const cat = CATEGORIES[b.id];
  const spentPct = Math.min(1, b.spent / b.cap);
  const capPct = b.cap / 12000; // relative to a generous range
  return (
    <div style={{ padding: "10px 0", borderBottom: "1px dotted var(--divider)" }}>
      <div style={{ display: "flex", alignItems: "baseline", marginBottom: 8 }}>
        <span style={{ width: 8, height: 8, borderRadius: 2, background: cat.c, marginRight: 8 }} />
        <span style={{ fontSize: 13, fontWeight: 500, color: "var(--text)", flex: 1 }}>{b.label}</span>
        <span className="r-mono" style={{ fontSize: 11, color: b.over ? "var(--bad)" : "var(--muted)" }}>₹{formatINR(b.spent, { withSymbol: false })}</span>
        <span className="r-mono" style={{ fontSize: 11, color: "var(--faint)", marginLeft: 6 }}>/ {formatINR(b.cap, { withSymbol: false })}</span>
      </div>
      {/* Slider track */}
      <div style={{ position: "relative", height: 26 }}>
        {/* base rail */}
        <div style={{
          position: "absolute", top: 11, left: 0, right: 0, height: 4,
          background: "var(--surface-2)", borderRadius: 2,
        }} />
        {/* filled to cap */}
        <div style={{
          position: "absolute", top: 11, left: 0, height: 4, width: `${capPct * 100}%`,
          background: b.over ? "var(--bad)" : cat.c, borderRadius: 2,
        }} />
        {/* spent indicator (within cap segment) */}
        <div style={{
          position: "absolute", top: 5, left: `calc(${spentPct * capPct * 100}% - 6px)`,
          width: 12, height: 16, borderRadius: 2,
          background: "var(--bg)", border: "1px solid " + (b.over ? "var(--bad)" : cat.c),
          display: "flex", alignItems: "center", justifyContent: "center",
        }}>
          <span style={{ width: 2, height: 8, background: b.over ? "var(--bad)" : cat.c }} />
        </div>
        {/* knob (the cap) */}
        <div style={{
          position: "absolute", top: 4, left: `calc(${capPct * 100}% - 9px)`,
          width: 18, height: 18, borderRadius: 9,
          background: b.over ? "var(--bad)" : cat.c,
          border: "2px solid var(--bg)",
          boxShadow: "0 1px 3px rgba(0,0,0,0.4)",
        }} />
      </div>
    </div>
  );
}

// ════════════════════════════════════════════════════════════════════════
// 4. RECURRING — subscriptions list
// ════════════════════════════════════════════════════════════════════════
const SAMPLE_SUBS = [
  { id: "nf", name: "Netflix",        freq: "monthly", amount: 649,  next: "Jun 8",  detected: true,  mode: "CARD", c: "oklch(0.55 0.18 25)" },
  { id: "ic", name: "iCloud 200GB",   freq: "monthly", amount: 75,   next: "Jun 11", detected: true,  mode: "CARD", c: "oklch(0.62 0.05 60)" },
  { id: "sp", name: "Spotify Family", freq: "monthly", amount: 199,  next: "Jun 14", detected: true,  mode: "UPI",  c: "oklch(0.65 0.13 145)" },
  { id: "cf", name: "Cult.fit Pro",   freq: "monthly", amount: 1999, next: "Jun 20", detected: true,  mode: "CARD", c: "oklch(0.65 0.16 250)" },
  { id: "yt", name: "YouTube Premium",freq: "yearly",  amount: 1290, next: "Sep 02", detected: true,  mode: "UPI",  c: "oklch(0.62 0.18 25)"  },
  { id: "go", name: "Domain · rupee.app", freq: "yearly", amount: 1200, next: "Nov 12", detected: false, mode: "CARD", c: "oklch(0.55 0.10 200)" },
];

function RecurringScreen() {
  const monthly = SAMPLE_SUBS.filter((s) => s.freq === "monthly").reduce((a, b) => a + b.amount, 0);
  const yearlyMonthlyized = SAMPLE_SUBS.filter((s) => s.freq === "yearly").reduce((a, b) => a + b.amount / 12, 0);
  const totalMo = monthly + yearlyMonthlyized;
  return (
    <RupeeFrame activeTab="set" inboxCount={4}>
      <div style={{ overflow: "auto", height: "100%" }}>
        <SubHeader section="SETTINGS / 08" title="Recurring" hint={`${SAMPLE_SUBS.length} ACTIVE`}
          right={<button style={{
            padding: "7px 12px", border: "1px solid var(--accent)",
            background: "color-mix(in oklch, var(--accent) 14%, transparent)",
            color: "var(--accent-bright)", borderRadius: 100,
            fontFamily: "var(--font-mono)", fontSize: 10, letterSpacing: "0.12em", fontWeight: 700,
          }}>+ ADD</button>} />

        {/* Hero — monthly burn */}
        <div style={{ padding: "8px 20px 0" }}>
          <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.14em", marginBottom: 4 }}>MONTHLY BURN</div>
          <div style={{ display: "flex", alignItems: "baseline", gap: 14 }}>
            <HeroAmount value={Math.round(totalMo)} />
          </div>
          <div style={{ display: "flex", gap: 12, marginTop: 10 }}>
            <span className="r-mono" style={{ fontSize: 10, color: "var(--muted)" }}>₹{formatINR(monthly, { withSymbol: false })} MONTHLY</span>
            <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)" }}>+ ₹{formatINR(Math.round(yearlyMonthlyized), { withSymbol: false })} YEARLY ÷ 12</span>
          </div>
        </div>

        {/* Calendar strip — next 4 weeks */}
        <Module num={1} title="Next 4 weeks" style={{ marginTop: 24 }}>
          <div style={{ display: "flex", gap: 6, alignItems: "flex-end", height: 60 }}>
            {[
              { wk: "W22", v: 75 },
              { wk: "W23", v: 649 },
              { wk: "W24", v: 199 + 1999 },
              { wk: "W25", v: 0 },
            ].map((w, i) => {
              const h = Math.min(56, (w.v / 2200) * 56);
              return (
                <div key={i} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 4 }}>
                  <span className="r-mono" style={{ fontSize: 9, color: "var(--text)" }}>
                    {w.v ? `₹${w.v >= 1000 ? `${(w.v / 1000).toFixed(1)}k` : w.v}` : "—"}
                  </span>
                  <div style={{ width: "100%", height: `${h || 2}px`, background: w.v ? "var(--accent)" : "var(--surface-2)", borderRadius: 2 }} />
                  <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)" }}>{w.wk}</span>
                </div>
              );
            })}
          </div>
        </Module>

        {/* List */}
        <Module num={2} title="Subscriptions" hint="ACTIVE" style={{ marginTop: 24 }}>
          {SAMPLE_SUBS.map((s, i) => <SubRow key={s.id} s={s} last={i === SAMPLE_SUBS.length - 1} />)}
        </Module>

        <div style={{ padding: "20px 20px 8px", display: "flex", justifyContent: "space-between" }}>
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE FINDS THESE FROM YOUR LEDGER</span>
        </div>
      </div>
    </RupeeFrame>
  );
}

function SubRow({ s, last }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 0", borderBottom: last ? 0 : "1px dotted var(--divider)" }}>
      <div style={{
        width: 36, height: 36, borderRadius: 4, background: `color-mix(in oklch, ${s.c} 25%, var(--surface-2))`,
        color: s.c, display: "flex", alignItems: "center", justifyContent: "center",
        fontFamily: "var(--font-mono)", fontWeight: 700, fontSize: 14,
      }}>{s.name[0]}</div>
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
          <span style={{ fontSize: 14, color: "var(--text)", fontWeight: 500 }}>{s.name}</span>
          {!s.detected && <span style={{ fontFamily: "var(--font-mono)", fontSize: 8, color: "var(--accent-bright)", letterSpacing: "0.12em", background: "color-mix(in oklch, var(--accent) 14%, transparent)", padding: "1px 4px", borderRadius: 2 }}>MANUAL</span>}
        </div>
        <div className="r-mono" style={{ fontSize: 10, color: "var(--dim)", marginTop: 2, letterSpacing: "0.04em" }}>
          {s.freq.toUpperCase()} · {s.mode} · NEXT {s.next.toUpperCase()}
        </div>
      </div>
      <Amount value={s.amount} size={14} weight={600} />
    </div>
  );
}

// ════════════════════════════════════════════════════════════════════════
// 5. TRANSACTIONS · search active
// ════════════════════════════════════════════════════════════════════════
function TxSearchActive() {
  const results = [
    { id: "r1", merchant: "Swiggy",    cat: "food", mode: "UPI",  amount: 612, conf: "auto", time: "Yesterday · 21:08", bank: "ICICI" },
    { id: "r2", merchant: "Swiggy Instamart", cat: "food", mode: "UPI", amount: 248, conf: "user", time: "May 17 · 14:22", bank: "ICICI" },
    { id: "r3", merchant: "Swiggy",    cat: "food", mode: "UPI",  amount: 488, conf: "auto", time: "May 11 · 19:42", bank: "ICICI" },
    { id: "r4", merchant: "Swiggy",    cat: "food", mode: "UPI",  amount: 1184, conf: "auto", time: "May 7 · 20:30", bank: "ICICI" },
  ];
  const total = results.reduce((a, b) => a + b.amount, 0);
  return (
    <RupeeFrame activeTab="tx" inboxCount={4}>
      <div style={{ overflow: "auto", height: "100%" }} className="r-conf-mode-stripe">
        {/* Search header */}
        <div style={{ padding: "12px 20px 6px" }}>
          <div style={{
            display: "flex", alignItems: "center", gap: 10,
            background: "var(--surface)", borderRadius: 10, padding: "10px 14px",
            border: "1px solid var(--accent)",
          }}>
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <circle cx="6" cy="6" r="4.5" stroke="var(--accent-bright)" strokeWidth="1.4" />
              <path d="M9.5 9.5L13 13" stroke="var(--accent-bright)" strokeWidth="1.4" strokeLinecap="round" />
            </svg>
            <span style={{ fontSize: 14, color: "var(--text)", flex: 1, fontWeight: 500 }}>
              swiggy<span style={{ display: "inline-block", width: 2, height: 16, background: "var(--accent)", marginLeft: 1, verticalAlign: "middle", animation: "rPulse 1.2s ease-in-out infinite" }} />
            </span>
            <button style={{
              fontFamily: "var(--font-mono)", fontSize: 10, color: "var(--muted)",
              border: 0, background: "transparent", letterSpacing: "0.1em",
            }}>CANCEL</button>
          </div>
        </div>

        {/* Active filter chips */}
        <div style={{ display: "flex", gap: 6, padding: "10px 20px 6px", overflowX: "auto" }}>
          <FilterChip label="LAST 30D" on />
          <FilterChip label="FOOD" on />
          <FilterChip label="UPI" on />
          <FilterChip label="+ DATE" />
          <FilterChip label="+ AMOUNT" />
        </div>

        {/* Result summary */}
        <div style={{ padding: "12px 20px 8px", display: "flex", alignItems: "baseline", borderTop: "1px solid var(--divider)", borderBottom: "1px solid var(--divider)" }}>
          <span style={{ fontSize: 13, fontWeight: 600, color: "var(--text)" }}>{results.length} results</span>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.1em" }}>TOTAL</span>
          <Amount value={total} size={14} weight={600} color="var(--text)" />
        </div>

        {/* Results */}
        <div>
          {results.map((r) => <TxListRow key={r.id} tx={r} confMode="stripe" />)}
        </div>

        {/* Aggregate insight callout */}
        <div style={{ margin: "20px 20px 0", padding: "14px 16px", background: "color-mix(in oklch, var(--accent) 8%, var(--surface))", border: "1px solid color-mix(in oklch, var(--accent) 30%, transparent)", borderRadius: 8, position: "relative" }}>
          <div className="r-mono" style={{ fontSize: 9, color: "var(--accent-bright)", letterSpacing: "0.14em", marginBottom: 4 }}>● INSIGHT</div>
          <div style={{ fontSize: 13, color: "var(--text)", lineHeight: 1.45 }}>
            Swiggy is your <span style={{ fontWeight: 600 }}>2nd most-visited merchant</span> this month.
            Avg spend per order: <span style={{ color: "var(--accent-bright)", fontWeight: 600 }}>₹633</span>.
          </div>
        </div>

        <div style={{ padding: "20px 20px 8px", display: "flex", justifyContent: "space-between" }}>
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>SAVED SEARCHES · 0</span>
          <span className="r-mono" style={{ fontSize: 9, color: "var(--accent-bright)", letterSpacing: "0.12em" }}>SAVE SEARCH →</span>
        </div>
      </div>
    </RupeeFrame>
  );
}

function FilterChip({ label, on }) {
  return (
    <span style={{
      display: "inline-flex", alignItems: "center", gap: 5,
      flexShrink: 0, padding: "5px 9px",
      borderRadius: 100,
      background: on ? "color-mix(in oklch, var(--accent) 14%, transparent)" : "transparent",
      color: on ? "var(--accent-bright)" : "var(--muted)",
      border: on ? "1px solid var(--accent)" : "1px dashed var(--hairline)",
      fontFamily: "var(--font-mono)", fontSize: 9, fontWeight: 700,
      letterSpacing: "0.12em",
    }}>
      {label}
      {on && <span style={{ fontSize: 11, lineHeight: 1, marginLeft: 2 }}>×</span>}
    </span>
  );
}

// ════════════════════════════════════════════════════════════════════════
// 6. CALENDAR · long-press peek
// ════════════════════════════════════════════════════════════════════════
function CalendarPeek() {
  const DAYS = 31;
  const spendMap = { 1:280,2:1200,3:0,4:80,5:660,6:0,7:2200,8:480,9:0,10:312,11:0,12:880,13:1490,14:0,15:6240,16:320,17:0,18:7460,19:880,20:1999,21:995,22:4951,23:602,24:0,25:0,26:0,27:0,28:0,29:0,30:0,31:0 };
  const max = Math.max(...Object.values(spendMap));
  const today = 23;
  const PRESSED = 18;
  const lead = 5;
  const cells = [
    ...Array.from({ length: lead }).map(() => ({ blank: true })),
    ...Array.from({ length: DAYS }, (_, i) => {
      const d = i + 1;
      return { d, spend: spendMap[d] || 0, today: d === today, future: d > today, pressed: d === PRESSED };
    }),
  ];
  while (cells.length % 7 !== 0) cells.push({ blank: true });

  return (
    <RupeeFrame activeTab="cal" inboxCount={4}>
      <div style={{ padding: "16px 20px 12px" }}>
        <div style={{ display: "flex", alignItems: "baseline" }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE / 04 · HOLD TO PEEK</span>
        </div>
        <div style={{ display: "flex", alignItems: "center", marginTop: 8, gap: 12 }}>
          <h1 style={{ fontSize: 26, fontWeight: 600, color: "var(--text)", margin: 0, letterSpacing: -0.01, flex: 1 }}>May 2026</h1>
        </div>
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", padding: "0 16px 4px" }}>
        {["M", "T", "W", "T", "F", "S", "S"].map((d, i) => (
          <div key={i} className="r-mono" style={{ textAlign: "center", fontSize: 9, color: "var(--faint)", letterSpacing: "0.1em" }}>{d}</div>
        ))}
      </div>

      <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", padding: "0 16px", gap: 2, position: "relative" }}>
        {cells.map((c, i) => {
          if (c.blank) return <div key={i} style={{ aspectRatio: "1/1.05" }} />;
          const ratio = c.spend / max;
          const isHi = ratio > 0.3;
          return (
            <div key={i} style={{
              aspectRatio: "1/1.05",
              background: c.pressed
                ? "var(--accent)"
                : c.spend > 0 ? `color-mix(in oklch, var(--accent) ${Math.round(ratio * 65 + 8)}%, var(--surface))` : "var(--surface)",
              border: c.today ? "1.5px solid var(--accent-bright)" : c.pressed ? "1.5px solid var(--accent-bright)" : "1px solid var(--divider)",
              borderRadius: 3,
              padding: "5px 5px 4px",
              display: "flex", flexDirection: "column", justifyContent: "space-between",
              opacity: c.future ? 0.4 : 1,
              transform: c.pressed ? "scale(1.06)" : "scale(1)",
              boxShadow: c.pressed ? "0 6px 18px color-mix(in oklch, var(--accent) 40%, transparent)" : "none",
              zIndex: c.pressed ? 2 : 1,
              transition: "transform .15s",
            }}>
              <span className="r-mono" style={{
                fontSize: 10,
                color: c.pressed || isHi ? "var(--accent-on)" : c.today ? "var(--accent-bright)" : "var(--text)",
                fontWeight: c.today || c.pressed ? 700 : 500,
              }}>{c.d}</span>
              {c.spend > 0 && (
                <span style={{
                  fontFamily: "var(--font-mono)", fontSize: 7.5,
                  color: c.pressed || isHi ? "var(--accent-on)" : "var(--muted)",
                  letterSpacing: "-0.02em",
                }}>
                  {c.spend >= 1000 ? `${(c.spend / 1000).toFixed(1)}k` : c.spend}
                </span>
              )}
            </div>
          );
        })}
      </div>

      {/* Floating peek card — anchored above the pressed cell (May 18, row 4, col 0) */}
      <div style={{
        position: "absolute", left: 16, right: 16, top: 200,
        background: "var(--surface-2)", borderRadius: 12,
        padding: "14px 16px 16px",
        boxShadow: "0 20px 40px rgba(0,0,0,0.5), 0 0 0 1px var(--hairline)",
        zIndex: 10,
      }}>
        {/* arrow pointing down at May 18 cell */}
        <div style={{
          position: "absolute", bottom: -7, left: 38,
          width: 14, height: 14, background: "var(--surface-2)",
          transform: "rotate(45deg)",
          boxShadow: "1px 1px 0 var(--hairline)",
        }} />

        <div style={{ display: "flex", alignItems: "baseline" }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.14em", fontWeight: 700 }}>MON · MAY 18</span>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.1em" }}>2 TX</span>
        </div>
        <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", marginTop: 6, marginBottom: 12 }}>
          <HeroAmount value={7460} />
          <span className="r-mono" style={{ fontSize: 10, color: "var(--bad)" }}>2.3× WK AVG</span>
        </div>
        <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
          {[
            { c: "travel", m: "Indigo · BLR-DEL", t: "11:11 · CARD",  v: 7240 },
            { c: "food",   m: "Café Amudham",     t: "08:30 · CASH",  v: 220 },
          ].map((t, i) => (
            <div key={i} style={{ display: "flex", alignItems: "center", gap: 10, padding: "6px 0" }}>
              <CatBadge id={t.c} size={26} />
              <div style={{ flex: 1, minWidth: 0 }}>
                <div style={{ fontSize: 13, color: "var(--text)", fontWeight: 500, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>{t.m}</div>
                <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", marginTop: 1 }}>{t.t}</div>
              </div>
              <Amount value={t.v} size={13} weight={600} />
            </div>
          ))}
        </div>
        <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", textAlign: "center", marginTop: 8, letterSpacing: "0.14em" }}>
          RELEASE TO OPEN · DRAG TO ANOTHER DAY
        </div>
      </div>

      {/* fingerprint / touch hint near the pressed cell */}
      <div style={{
        position: "absolute", left: 24, top: 416, opacity: 0.6,
        pointerEvents: "none",
      }}>
        <svg width="40" height="40" viewBox="0 0 40 40" fill="none">
          <circle cx="20" cy="20" r="18" stroke="var(--accent-bright)" strokeWidth="0.8" strokeDasharray="2 3" />
          <circle cx="20" cy="20" r="11" stroke="var(--accent-bright)" strokeWidth="0.8" />
          <circle cx="20" cy="20" r="5"  fill="var(--accent-bright)" opacity="0.4" />
        </svg>
      </div>
    </RupeeFrame>
  );
}

Object.assign(window, { CardsAndEMIs, AccountsScreen, BudgetsScreen, RecurringScreen, TxSearchActive, CalendarPeek });
