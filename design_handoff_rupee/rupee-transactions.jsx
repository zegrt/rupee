// Rupee — Transactions screen

function TxGroupHeader({ date, total }) {
  return (
    <div style={{
      display: "flex", alignItems: "baseline",
      padding: "20px 20px 8px",
      background: "var(--bg)",
    }}>
      <span style={{ fontSize: 12, color: "var(--text)", fontWeight: 600, letterSpacing: 0.02, textTransform: "uppercase" }}>
        {date}
      </span>
      <span style={{ flex: 1, marginLeft: 12, borderTop: "1px solid var(--divider)" }} />
      <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)", marginLeft: 12 }}>
        ₹{formatINR(total, { withSymbol: false })}
      </span>
    </div>
  );
}

function TxListRow({ tx, confMode = "stripe" }) {
  const stripeKind = tx.conf === "user" ? "user" : tx.conf === "suggest" ? "suggest" : "auto";
  const confLabel = tx.conf === "auto" ? "AUTO" : tx.conf === "user" ? "VERIFIED" : "REVIEW";
  const confColor = tx.conf === "auto" ? "var(--faint)" : tx.conf === "user" ? "var(--good)" : "var(--accent-bright)";
  return (
    <div className={`r-conf-row ${stripeKind}`} style={{ display: "flex", alignItems: "stretch" }}>
      <div className={`r-conf-stripe ${stripeKind}`} />
      <div style={{ flex: 1, display: "flex", alignItems: "center", gap: 12, padding: "12px 20px", borderBottom: "1px solid var(--divider)" }}>
        <CatBadge id={tx.cat} size={32} />
        <div style={{ flex: 1, minWidth: 0 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <span style={{ fontSize: 14, fontWeight: 500, color: "var(--text)" }}>{tx.merchant}</span>
            {confMode === "dot" && <span className={`r-conf-dot ${stripeKind}`} />}
            {confMode === "chip" && (
              <span style={{
                fontFamily: "var(--font-mono)", fontSize: 8, letterSpacing: "0.1em",
                color: confColor,
                background: stripeKind === "auto" ? "var(--surface-2)" : `color-mix(in oklch, ${stripeKind === "user" ? "var(--good)" : "var(--accent)"} 16%, transparent)`,
                padding: "1px 5px", borderRadius: 2, marginLeft: 2,
              }}>{confLabel}</span>
            )}
          </div>
          <div style={{ display: "flex", alignItems: "center", gap: 6, marginTop: 2 }}>
            <span style={{ color: "var(--faint)", display: "inline-flex" }}><ModeIcon mode={tx.mode} /></span>
            <span className="r-mono" style={{ fontSize: 10, color: "var(--dim)" }}>
              {tx.mode}{tx.bank ? ` · ${tx.bank}` : ""}
            </span>
            {tx.note && <span style={{ fontSize: 11, color: "var(--faint)", fontStyle: "italic", overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>· {tx.note}</span>}
          </div>
        </div>
        <Amount value={tx.amount} size={15} weight={600} sign={tx.isIncome ? true : false} color={tx.isIncome ? "var(--good)" : undefined} />
      </div>
    </div>
  );
}

function TxScreen({ confMode = "stripe" }) {
  const groups = [
    { date: "Today · May 23", items: SAMPLE_TX.slice(0, 3), total: 480 + 122 - 145000 },
    { date: "Yesterday · May 22", items: SAMPLE_TX.slice(3, 6), total: 612 + 2840 + 1499 },
    { date: "May 21", items: SAMPLE_TX.slice(6, 8), total: 749 + 246 },
    { date: "May 20", items: SAMPLE_TX.slice(8, 9), total: 1999 },
    { date: "May 19", items: SAMPLE_TX.slice(9, 10), total: 880 },
    { date: "May 18", items: SAMPLE_TX.slice(10), total: 7240 + 220 },
  ];

  return (
    <RupeeFrame activeTab="tx" inboxCount={4}>
      <div className={`r-conf-mode-${confMode}`}>
        {/* Header */}
        <div style={{ padding: "16px 20px 8px" }}>
          <div style={{ display: "flex", alignItems: "baseline" }}>
            <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE / 03</span>
            <span style={{ flex: 1 }} />
            <span className="r-mono" style={{ fontSize: 10, color: "var(--muted)" }}>{SAMPLE_TX.length} TX · MAY</span>
          </div>
          <div style={{ display: "flex", alignItems: "baseline", gap: 16, marginTop: 8 }}>
            <h1 style={{ fontSize: 26, fontWeight: 600, color: "var(--text)", margin: 0, letterSpacing: -0.01 }}>
              Transactions
            </h1>
            <span style={{ flex: 1 }} />
            <button style={{ background: "transparent", color: "var(--muted)", border: "1px solid var(--divider)", padding: "6px 10px", borderRadius: 4, fontFamily: "var(--font-mono)", fontSize: 10, letterSpacing: "0.08em" }}>
              FILTER
            </button>
          </div>
        </div>

        {/* Confidence legend */}
        <div style={{ padding: "8px 20px 4px", display: "flex", gap: 12, flexWrap: "wrap" }}>
          <LegendDot label="AUTO" sub="parser high-conf" kind="auto" mode={confMode} />
          <LegendDot label="VERIFIED" sub="you confirmed" kind="user" mode={confMode} />
          <LegendDot label="REVIEW" sub="from inbox" kind="suggest" mode={confMode} />
        </div>

        {/* search */}
        <div style={{ padding: "8px 20px 14px" }}>
          <div style={{
            background: "var(--surface)", borderRadius: 6, padding: "10px 14px",
            display: "flex", alignItems: "center", gap: 8, color: "var(--dim)",
          }}>
            <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
              <circle cx="6" cy="6" r="4.5" stroke="currentColor" strokeWidth="1.2" />
              <path d="M9.5 9.5L13 13" stroke="currentColor" strokeWidth="1.2" strokeLinecap="round" />
            </svg>
            <span style={{ fontSize: 13 }}>Search merchant, note, ₹</span>
          </div>
        </div>

        {/* groups */}
        {groups.map((g, i) => (
          <React.Fragment key={i}>
            <TxGroupHeader date={g.date} total={Math.abs(g.total)} />
            {g.items.map((t) => <TxListRow key={t.id} tx={t} confMode={confMode} />)}
          </React.Fragment>
        ))}

        <div style={{ padding: "20px", textAlign: "center" }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.1em" }}>END OF MAY · TAP TO LOAD APR</span>
        </div>
      </div>
    </RupeeFrame>
  );
}

function LegendDot({ label, sub, kind, mode }) {
  const color = kind === "user" ? "var(--good)" : kind === "suggest" ? "var(--accent)" : "var(--faint)";
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
      {mode === "stripe" && <span style={{ width: 3, height: 12, background: color }} />}
      {mode === "dot" && <span style={{ width: 5, height: 5, borderRadius: 5, background: color }} />}
      {mode === "tint" && <span style={{ width: 12, height: 12, background: `color-mix(in oklch, ${color} 30%, transparent)`, borderRadius: 2 }} />}
      {mode === "chip" && <span style={{ width: 12, height: 12, background: `color-mix(in oklch, ${color} 16%, transparent)`, color, borderRadius: 2 }} />}
      <span className="r-mono" style={{ fontSize: 9, color: "var(--muted)", letterSpacing: "0.1em" }}>{label}</span>
    </div>
  );
}

Object.assign(window, { TxScreen, TxListRow, TxGroupHeader });
