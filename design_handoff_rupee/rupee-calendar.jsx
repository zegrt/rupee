// Rupee — Calendar screen + day sheet

function CalendarScreen() {
  // May 2026 — starts on a Friday (day 1 = Fri). 31 days.
  const DAYS = 31;
  // Sample spend per day (₹). Some days are 0 (no activity).
  const spendMap = {
    1: 280, 2: 1200, 3: 0, 4: 80, 5: 660, 6: 0, 7: 2200,
    8: 480, 9: 0, 10: 312, 11: 0, 12: 880, 13: 1490, 14: 0,
    15: 6240, 16: 320, 17: 0, 18: 7460, 19: 880, 20: 1999, 21: 995,
    22: 4951, 23: 602, 24: 0, 25: 0, 26: 0, 27: 0, 28: 0,
    29: 0, 30: 0, 31: 0,
  };
  const max = Math.max(...Object.values(spendMap));
  const today = 23;

  // build 6-row grid: leading blanks for May 1 = Friday (=5)
  const lead = 5;
  const cells = [
    ...Array.from({ length: lead }).map(() => ({ blank: true })),
    ...Array.from({ length: DAYS }, (_, i) => {
      const d = i + 1;
      return { d, spend: spendMap[d] || 0, today: d === today, future: d > today };
    }),
  ];
  while (cells.length % 7 !== 0) cells.push({ blank: true });

  // top 3 days
  const topDays = Object.entries(spendMap)
    .map(([d, s]) => ({ d: +d, s }))
    .filter((x) => x.s > 0)
    .sort((a, b) => b.s - a.s)
    .slice(0, 3);
  const totalMonth = Object.values(spendMap).reduce((a, b) => a + b, 0);

  return (
    <RupeeFrame activeTab="cal" inboxCount={4}>
      {/* Header */}
      <div style={{ padding: "16px 20px 12px" }}>
        <div style={{ display: "flex", alignItems: "baseline" }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE / 04</span>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 10, color: "var(--muted)" }}>MTD ₹{formatINR(totalMonth, { withSymbol: false })}</span>
        </div>
        <div style={{ display: "flex", alignItems: "center", marginTop: 8, gap: 12 }}>
          <button style={{ background: "transparent", color: "var(--muted)", border: 0, padding: 4, cursor: "pointer" }}>
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none"><path d="M11 4l-5 5 5 5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" /></svg>
          </button>
          <h1 style={{ fontSize: 26, fontWeight: 600, color: "var(--text)", margin: 0, letterSpacing: -0.01, flex: 1 }}>
            May 2026
          </h1>
          <button style={{ background: "transparent", color: "var(--dim)", border: 0, padding: 4 }}>
            <svg width="18" height="18" viewBox="0 0 18 18" fill="none"><path d="M7 4l5 5-5 5" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" /></svg>
          </button>
        </div>
      </div>

      {/* Heatmap legend */}
      <div style={{ display: "flex", padding: "0 20px", marginBottom: 8, alignItems: "center", gap: 6 }}>
        <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.1em" }}>LOW</span>
        <div style={{ flex: 1, height: 4, borderRadius: 2, background: "linear-gradient(to right, var(--surface), var(--accent))" }} />
        <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)" }}>HIGH</span>
      </div>

      {/* weekday row */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", padding: "0 16px 4px" }}>
        {["M", "T", "W", "T", "F", "S", "S"].map((d, i) => (
          <div key={i} className="r-mono" style={{ textAlign: "center", fontSize: 9, color: "var(--faint)", letterSpacing: "0.1em" }}>{d}</div>
        ))}
      </div>

      {/* grid */}
      <div style={{ display: "grid", gridTemplateColumns: "repeat(7, 1fr)", padding: "0 16px", gap: 2 }}>
        {cells.map((c, i) => {
          if (c.blank) return <div key={i} style={{ aspectRatio: "1/1.05" }} />;
          const ratio = c.spend / max;
          const isHi = ratio > 0.3;
          return (
            <div key={i} style={{
              aspectRatio: "1/1.05",
              background: c.spend > 0 ? `color-mix(in oklch, var(--accent) ${Math.round(ratio * 65 + 8)}%, var(--surface))` : "var(--surface)",
              border: c.today ? "1.5px solid var(--accent-bright)" : "1px solid var(--divider)",
              borderRadius: 3,
              padding: "5px 5px 4px",
              display: "flex", flexDirection: "column",
              justifyContent: "space-between",
              opacity: c.future ? 0.4 : 1,
              position: "relative",
            }}>
              <span className="r-mono" style={{
                fontSize: 10, color: isHi ? "var(--accent-on)" : c.today ? "var(--accent-bright)" : "var(--text)",
                fontWeight: c.today ? 700 : 500, lineHeight: 1,
              }}>{c.d}</span>
              {c.spend > 0 && (
                <span style={{
                  fontFamily: "var(--font-mono)",
                  fontSize: 7.5,
                  color: isHi ? "var(--accent-on)" : "var(--muted)",
                  lineHeight: 1,
                  letterSpacing: "-0.02em",
                }}>
                  {c.spend >= 1000 ? `${(c.spend / 1000).toFixed(1)}k` : c.spend}
                </span>
              )}
            </div>
          );
        })}
      </div>

      <Module num={1} title="Heaviest days" hint="MAY" style={{ marginTop: 32 }}>
        {topDays.map((d, i) => (
          <div key={d.d} style={{ display: "flex", alignItems: "center", padding: "10px 0", borderBottom: i < 2 ? "1px dashed var(--divider)" : 0 }}>
            <span className="r-mono" style={{ fontSize: 11, color: "var(--faint)", width: 28 }}>{String(d.d).padStart(2, "0")}</span>
            <span style={{ flex: 1, fontSize: 13, color: "var(--text)" }}>May {d.d}{d.d === 18 ? " · BLR-DEL" : d.d === 22 ? " · payday week" : ""}</span>
            <span style={{ width: 64, height: 4, background: "var(--surface-2)", borderRadius: 2, marginRight: 10, overflow: "hidden" }}>
              <span style={{ display: "block", height: "100%", width: `${(d.s / topDays[0].s) * 100}%`, background: "var(--accent)" }} />
            </span>
            <Amount value={d.s} size={13} weight={500} />
          </div>
        ))}
      </Module>

      <div style={{ padding: "32px 20px 8px", display: "flex", justifyContent: "space-between" }}>
        <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>SWIPE · CHANGE MONTH</span>
        <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>EOF</span>
      </div>
    </RupeeFrame>
  );
}

// DaySheet — what comes up when you tap a day
function DaySheet() {
  const dayTx = [
    { merchant: "Indigo · BLR-DEL", cat: "travel", mode: "CARD", amount: 7240, time: "11:11", bank: "AXIS" },
    { merchant: "Café Amudham", cat: "food", mode: "CASH", amount: 220, time: "08:30" },
  ];
  return (
    <RupeeFrame showNav={false}>
      {/* dim background */}
      <div style={{ position: "absolute", inset: 0, background: "rgba(0,0,0,0.5)" }} />
      {/* sheet */}
      <div style={{
        position: "absolute", left: 0, right: 0, bottom: 0,
        background: "var(--bg)", borderTopLeftRadius: 18, borderTopRightRadius: 18,
        padding: "10px 20px 24px",
      }}>
        <div style={{ width: 36, height: 4, background: "var(--surface-3)", borderRadius: 2, margin: "0 auto 14px" }} />
        <div style={{ display: "flex", alignItems: "baseline", marginBottom: 4 }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>MON · MAY 18</span>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 10, color: "var(--muted)" }}>2 TX</span>
        </div>
        <div style={{ display: "flex", alignItems: "baseline", justifyContent: "space-between", marginBottom: 18 }}>
          <HeroAmount value={7460} />
          <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)" }}>+2.3× WK AVG</span>
        </div>

        {/* per-tx mini bars */}
        <div style={{ display: "flex", gap: 6, marginBottom: 18 }}>
          <span style={{ flex: 7240, height: 8, background: "oklch(0.74 0.15 200)", borderRadius: 2 }} />
          <span style={{ flex: 220, height: 8, background: "oklch(0.72 0.16 30)", borderRadius: 2 }} />
        </div>

        {dayTx.map((t, i) => (
          <div key={i} style={{ display: "flex", alignItems: "center", gap: 12, padding: "12px 0", borderTop: "1px solid var(--divider)" }}>
            <CatBadge id={t.cat} size={32} />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14, fontWeight: 500, color: "var(--text)" }}>{t.merchant}</div>
              <div className="r-mono" style={{ fontSize: 10, color: "var(--dim)", marginTop: 2 }}>{t.time} · {t.mode}{t.bank ? ` · ${t.bank}` : ""}</div>
            </div>
            <Amount value={t.amount} size={14} weight={600} />
          </div>
        ))}
      </div>
    </RupeeFrame>
  );
}

Object.assign(window, { CalendarScreen, DaySheet });
