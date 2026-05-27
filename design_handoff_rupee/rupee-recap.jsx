// Rupee — Monthly Recap (Wrapped-style story cards)
// Each card is a full-bleed phone frame. Bold typography, single statement per screen.

// Story chrome — top bar with progress ticks + close, bottom with index
function StoryShell({ idx, total, children, bg, color = "var(--text)", title }) {
  return (
    <RupeeFrame showNav={false}>
      <div style={{
        position: "absolute", inset: 0,
        background: bg || "var(--bg)",
        color, fontFamily: "var(--font-sans)",
        overflow: "hidden",
      }}>
        {/* progress ticks at top */}
        <div style={{ display: "flex", gap: 3, padding: "8px 16px 0", position: "relative", zIndex: 5 }}>
          {Array.from({ length: total }).map((_, i) => (
            <div key={i} style={{
              flex: 1, height: 2.5, borderRadius: 1,
              background: i < idx ? color : i === idx ? color : "color-mix(in oklch, " + color + " 25%, transparent)",
              opacity: i < idx ? 1 : i === idx ? 0.85 : 0.4,
            }} />
          ))}
        </div>

        {/* top row: brand + close */}
        <div style={{
          display: "flex", alignItems: "center", padding: "12px 18px 0",
          position: "relative", zIndex: 5,
        }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <div style={{ width: 14, height: 14, background: color, borderRadius: 3, opacity: 0.9 }} />
            <span className="r-mono" style={{ fontSize: 10, letterSpacing: "0.16em", fontWeight: 600, opacity: 0.85 }}>
              RUPEE · MAY 26
            </span>
          </div>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 14, opacity: 0.75 }}>✕</span>
        </div>

        <div style={{ position: "absolute", inset: 0, paddingTop: 56, paddingBottom: 36 }}>
          {children}
        </div>

        {/* bottom registration */}
        <div style={{
          position: "absolute", bottom: 12, left: 18, right: 18,
          display: "flex", alignItems: "center",
          fontFamily: "var(--font-mono)", fontSize: 10, opacity: 0.6, letterSpacing: "0.12em",
        }}>
          <span>{String(idx + 1).padStart(2, "0")} / {String(total).padStart(2, "0")}</span>
          <span style={{ flex: 1 }} />
          <span>{title || "RECAP"}</span>
        </div>
      </div>
    </RupeeFrame>
  );
}

// 1. Cover — "May was a month"
function RecapCover() {
  return (
    <StoryShell idx={0} total={7} bg="var(--bg)" title="COVER">
      <div style={{ position: "absolute", inset: 0, padding: "56px 28px 0", display: "flex", flexDirection: "column" }}>
        {/* decorative numbers */}
        <div style={{ flex: 1, position: "relative" }}>
          <div style={{
            position: "absolute", top: 20, left: 0, right: 0,
            fontFamily: "var(--font-sans)", fontWeight: 700,
            fontSize: 200, lineHeight: 0.85, letterSpacing: -0.06,
            color: "color-mix(in oklch, var(--accent) 40%, var(--bg))",
            opacity: 0.16,
            pointerEvents: "none",
          }}>05</div>
          <div className="r-mono" style={{ position: "absolute", top: 24, right: 0, fontSize: 10, color: "var(--muted)", letterSpacing: "0.16em" }}>
            01 / 31 → 31 / 31
          </div>
        </div>

        <div style={{ position: "relative", paddingBottom: 28 }}>
          <span className="r-mono" style={{ fontSize: 12, color: "var(--accent-bright)", letterSpacing: "0.18em", fontWeight: 600 }}>
            YOUR MAY · IN MOTION
          </span>
          <h1 style={{
            fontSize: 52, fontWeight: 600, lineHeight: 1.0, letterSpacing: -0.03,
            color: "var(--text)", margin: "12px 0 0", textWrap: "balance",
          }}>
            412 transactions.<br />
            One <span style={{ color: "var(--accent-bright)" }}>month</span> of you.
          </h1>
          <p style={{ fontSize: 14, color: "var(--muted)", marginTop: 16, lineHeight: 1.5, maxWidth: 280 }}>
            Rupee watched every ping. Here's what it saw.
          </p>

          <div style={{ display: "flex", gap: 8, marginTop: 24 }}>
            <button className="r-btn">Begin →</button>
            <button className="r-btn ghost">Skip</button>
          </div>
        </div>
      </div>
    </StoryShell>
  );
}

// 2. Total spent — huge number reveal
function RecapTotal() {
  return (
    <StoryShell idx={1} total={7} bg="var(--bg-deep)" title="TOTAL">
      <div style={{ position: "absolute", inset: 0, padding: "56px 28px 0", display: "flex", flexDirection: "column" }}>
        <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)", letterSpacing: "0.16em" }}>
          YOU SPENT
        </span>

        <div style={{
          marginTop: 24,
          fontFamily: "var(--font-sans)", fontWeight: 600,
          fontSize: 110, lineHeight: 0.92, letterSpacing: -0.05,
          color: "var(--text)", fontVariantNumeric: "tabular-nums",
        }}>
          <span style={{ fontSize: 50, fontWeight: 500, color: "var(--accent-bright)", marginRight: 6, verticalAlign: "top" }}>₹</span>
          36,580
        </div>
        <div className="r-mono" style={{ fontSize: 12, color: "var(--muted)", marginTop: 8, letterSpacing: "0.06em" }}>
          THAT'S 56% OF YOUR BUDGET — AND ₹3,440 UNDER PLAN.
        </div>

        {/* delta from avg */}
        <div style={{ marginTop: 32, padding: 16, background: "var(--surface)", borderRadius: 8, border: "1px solid var(--divider)" }}>
          <div style={{ display: "flex", alignItems: "baseline", marginBottom: 12 }}>
            <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>VS YOUR 3M AVG</span>
            <span style={{ flex: 1 }} />
            <span style={{ color: "var(--good)", fontSize: 14, fontWeight: 600 }}>−8.2%</span>
          </div>
          <div style={{ display: "flex", gap: 4, alignItems: "flex-end", height: 60 }}>
            {[42, 38, 41, 36].map((v, i) => (
              <div key={i} style={{ flex: 1, display: "flex", flexDirection: "column", alignItems: "center", gap: 6 }}>
                <div style={{ width: "100%", height: `${v * 1.2}px`, background: i === 3 ? "var(--accent)" : "var(--surface-3)", borderRadius: 2 }} />
                <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)" }}>{["FEB", "MAR", "APR", "MAY"][i]}</span>
              </div>
            ))}
          </div>
        </div>

        <div style={{ flex: 1 }} />

        <div className="r-mono" style={{ fontSize: 10, color: "var(--muted)", letterSpacing: "0.06em", marginBottom: 40 }}>
          ₹1,179/DAY · ₹49/HOUR AWAKE
        </div>
      </div>
    </StoryShell>
  );
}

// 3. Top category — big colored block
function RecapCategory() {
  return (
    <StoryShell idx={2} total={7} bg="oklch(0.22 0.08 30)" color="oklch(0.98 0.01 30)" title="CATEGORY">
      <div style={{ position: "absolute", inset: 0, padding: "56px 28px 0", display: "flex", flexDirection: "column" }}>
        <span className="r-mono" style={{ fontSize: 11, letterSpacing: "0.16em", opacity: 0.7 }}>YOUR FAVOURITE LINE-ITEM</span>

        <div style={{ marginTop: 32, display: "flex", alignItems: "baseline", gap: 14 }}>
          <div style={{
            width: 80, height: 80, background: "oklch(0.72 0.16 30)",
            borderRadius: 8, display: "flex", alignItems: "center", justifyContent: "center",
            fontFamily: "var(--font-mono)", fontWeight: 700, fontSize: 48, color: "oklch(0.18 0.05 30)",
          }}>F</div>
          <span className="r-mono" style={{ fontSize: 50, fontWeight: 600, opacity: 0.4 }}>·</span>
        </div>

        <h1 style={{
          fontSize: 64, fontWeight: 600, lineHeight: 0.95, letterSpacing: -0.03,
          margin: "20px 0 0", color: "oklch(0.98 0.01 30)",
        }}>
          Food.<br />
          <span style={{ color: "oklch(0.85 0.14 30)" }}>Always food.</span>
        </h1>

        <div style={{ display: "flex", alignItems: "baseline", gap: 12, marginTop: 24 }}>
          <span style={{
            fontSize: 48, fontWeight: 600, fontVariantNumeric: "tabular-nums", letterSpacing: -0.02,
          }}>
            <span style={{ fontSize: 24, opacity: 0.7, marginRight: 2 }}>₹</span>11,420
          </span>
          <span className="r-mono" style={{ fontSize: 11, opacity: 0.7 }}>31% OF MONTH</span>
        </div>

        {/* mini bar comparison */}
        <div style={{ marginTop: 28, display: "flex", flexDirection: "column", gap: 8 }}>
          {[
            { l: "Food", v: 100, on: true },
            { l: "Bills", v: 68 },
            { l: "Shopping", v: 51 },
            { l: "Groceries", v: 37 },
            { l: "Transport", v: 24 },
          ].map((c) => (
            <div key={c.l} style={{ display: "flex", alignItems: "center", gap: 10, opacity: c.on ? 1 : 0.6 }}>
              <span className="r-mono" style={{ width: 80, fontSize: 10, letterSpacing: "0.06em" }}>{c.l.toUpperCase()}</span>
              <div style={{ flex: 1, height: 14, borderRadius: 2, background: "oklch(0.30 0.05 30)", position: "relative", overflow: "hidden" }}>
                <div style={{ width: `${c.v}%`, height: "100%", background: c.on ? "oklch(0.78 0.16 30)" : "oklch(0.55 0.08 30)" }} />
              </div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1 }} />
        <div className="r-mono" style={{ fontSize: 10, opacity: 0.55, marginBottom: 40, letterSpacing: "0.08em" }}>
          IF FOOD WERE A SUBSCRIPTION, IT'D BE ₹378/DAY.
        </div>
      </div>
    </StoryShell>
  );
}

// 4. Top merchant — Blue Tokai shrine
function RecapMerchant() {
  return (
    <StoryShell idx={3} total={7} bg="var(--bg)" title="MERCHANT">
      <div style={{ position: "absolute", inset: 0, padding: "56px 28px 0", display: "flex", flexDirection: "column" }}>
        <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)", letterSpacing: "0.16em" }}>MOST-VISITED MERCHANT</span>

        <div style={{ marginTop: 26, display: "flex", alignItems: "center", gap: 16 }}>
          <div style={{
            width: 64, height: 64,
            background: "color-mix(in oklch, var(--accent) 22%, var(--surface))",
            color: "var(--accent-bright)",
            borderRadius: 6,
            display: "flex", alignItems: "center", justifyContent: "center",
            fontFamily: "var(--font-mono)", fontWeight: 700, fontSize: 40,
          }}>B</div>
          <div>
            <div style={{ fontSize: 32, fontWeight: 600, color: "var(--text)", letterSpacing: -0.01 }}>Blue Tokai</div>
            <div className="r-mono" style={{ fontSize: 11, color: "var(--muted)", marginTop: 2, letterSpacing: "0.06em" }}>FOOD · COFFEE</div>
          </div>
        </div>

        {/* The 17 dots */}
        <div style={{ marginTop: 36 }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>17 VISITS · ONE EACH MARK</span>
          <div style={{ display: "grid", gridTemplateColumns: "repeat(8, 1fr)", gap: 8, marginTop: 12 }}>
            {Array.from({ length: 17 }).map((_, i) => (
              <div key={i} style={{
                aspectRatio: "1/1",
                background: "color-mix(in oklch, var(--accent) " + (40 + (i % 4) * 12) + "%, var(--surface))",
                borderRadius: 4,
              }} />
            ))}
            {Array.from({ length: 7 }).map((_, i) => (
              <div key={"e" + i} style={{ aspectRatio: "1/1", border: "1px dashed var(--divider)", borderRadius: 4 }} />
            ))}
          </div>
        </div>

        <div style={{ flex: 1 }} />

        {/* total + breakdown */}
        <div style={{
          marginBottom: 40, padding: 16,
          background: "var(--surface)", borderRadius: 8,
        }}>
          <div style={{ display: "flex", alignItems: "baseline", marginBottom: 6 }}>
            <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>TOTAL SPENT</span>
            <span style={{ flex: 1 }} />
            <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)" }}>AVG ₹426/visit</span>
          </div>
          <div style={{ fontSize: 36, fontWeight: 600, color: "var(--text)", fontVariantNumeric: "tabular-nums", letterSpacing: -0.02 }}>
            <span style={{ fontSize: 20, color: "var(--muted)", marginRight: 4 }}>₹</span>7,242
          </div>
          <div className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", marginTop: 8, letterSpacing: "0.06em" }}>
            ↗ ENOUGH FOR 24 BAGS OF BEANS AT HOME
          </div>
        </div>
      </div>
    </StoryShell>
  );
}

// 5. Biggest day — calendar drama
function RecapDay() {
  return (
    <StoryShell idx={4} total={7} bg="oklch(0.22 0.04 240)" color="oklch(0.98 0.01 240)" title="DAY">
      <div style={{ position: "absolute", inset: 0, padding: "56px 28px 0", display: "flex", flexDirection: "column" }}>
        <span className="r-mono" style={{ fontSize: 11, opacity: 0.7, letterSpacing: "0.16em" }}>YOUR BIGGEST DAY</span>

        {/* huge date */}
        <div style={{ marginTop: 28, display: "flex", alignItems: "flex-start", gap: 16 }}>
          <div style={{
            fontFamily: "var(--font-sans)", fontWeight: 700,
            fontSize: 144, lineHeight: 0.85, letterSpacing: -0.05,
            color: "oklch(0.98 0.01 240)",
          }}>
            18
          </div>
          <div style={{ paddingTop: 12 }}>
            <div className="r-mono" style={{ fontSize: 13, opacity: 0.7, letterSpacing: "0.14em" }}>MON</div>
            <div className="r-mono" style={{ fontSize: 13, opacity: 0.7, letterSpacing: "0.14em" }}>MAY</div>
            <div style={{ marginTop: 12, width: 32, height: 1, background: "oklch(0.98 0.01 240)", opacity: 0.5 }} />
            <div className="r-mono" style={{ fontSize: 11, marginTop: 8, opacity: 0.6 }}>WEEK 20</div>
          </div>
        </div>

        <div style={{ display: "flex", alignItems: "baseline", gap: 16, marginTop: 28 }}>
          <span style={{
            fontSize: 56, fontWeight: 600, fontVariantNumeric: "tabular-nums", letterSpacing: -0.02,
          }}>
            <span style={{ fontSize: 28, opacity: 0.7, marginRight: 2 }}>₹</span>7,460
          </span>
          <span className="r-mono" style={{ fontSize: 11, opacity: 0.7, letterSpacing: "0.06em" }}>2.3× WK AVG</span>
        </div>

        <div className="r-mono" style={{ fontSize: 11, opacity: 0.7, marginTop: 6, letterSpacing: "0.06em" }}>
          1 FLIGHT · 1 COFFEE · BLR-DEL
        </div>

        {/* mini transactions */}
        <div style={{ marginTop: 28, display: "flex", flexDirection: "column", gap: 1, background: "oklch(0.35 0.05 240)", borderRadius: 8, overflow: "hidden" }}>
          <div style={{ display: "flex", padding: "14px 16px", background: "oklch(0.28 0.05 240)", alignItems: "center", gap: 12 }}>
            <div style={{ width: 32, height: 32, background: "oklch(0.74 0.15 200)", borderRadius: 4, color: "oklch(0.18 0.05 240)", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "var(--font-mono)", fontWeight: 700, fontSize: 14 }}>V</div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14, fontWeight: 500 }}>Indigo · BLR-DEL</div>
              <div className="r-mono" style={{ fontSize: 10, opacity: 0.6 }}>11:11 · TRAVEL · AXIS</div>
            </div>
            <span style={{ fontSize: 15, fontWeight: 600, fontVariantNumeric: "tabular-nums" }}>₹7,240</span>
          </div>
          <div style={{ display: "flex", padding: "14px 16px", background: "oklch(0.28 0.05 240)", alignItems: "center", gap: 12 }}>
            <div style={{ width: 32, height: 32, background: "oklch(0.72 0.16 30)", borderRadius: 4, color: "oklch(0.18 0.05 30)", display: "flex", alignItems: "center", justifyContent: "center", fontFamily: "var(--font-mono)", fontWeight: 700, fontSize: 14 }}>F</div>
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 14, fontWeight: 500 }}>Café Amudham</div>
              <div className="r-mono" style={{ fontSize: 10, opacity: 0.6 }}>08:30 · FOOD · CASH</div>
            </div>
            <span style={{ fontSize: 15, fontWeight: 600, fontVariantNumeric: "tabular-nums" }}>₹220</span>
          </div>
        </div>

        <div style={{ flex: 1 }} />
        <div className="r-mono" style={{ fontSize: 10, opacity: 0.55, marginBottom: 40, letterSpacing: "0.08em" }}>
          THE CHEAPEST DAY WAS MAY 06 — ₹0.
        </div>
      </div>
    </StoryShell>
  );
}

// 6. Mode — UPI dominates
function RecapMode() {
  return (
    <StoryShell idx={5} total={7} bg="var(--bg)" title="MODE">
      <div style={{ position: "absolute", inset: 0, padding: "56px 28px 0", display: "flex", flexDirection: "column" }}>
        <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)", letterSpacing: "0.16em" }}>HOW YOU PAID</span>

        <h1 style={{
          fontSize: 64, fontWeight: 600, lineHeight: 0.95, letterSpacing: -0.03,
          color: "var(--text)", margin: "20px 0 0",
        }}>
          <span style={{ color: "var(--accent-bright)" }}>UPI</span> won.<br />
          By a lot.
        </h1>

        {/* mode breakdown donut-as-stack */}
        <div style={{ marginTop: 32, display: "flex", flexDirection: "column", gap: 14 }}>
          {[
            { l: "UPI", v: 71, n: 292, c: "var(--accent)" },
            { l: "CARD", v: 18, n: 74, c: "oklch(0.65 0.05 80)" },
            { l: "CASH", v: 8, n: 32, c: "oklch(0.50 0.04 80)" },
            { l: "BANK", v: 3, n: 14, c: "oklch(0.42 0.04 80)" },
          ].map((m) => (
            <div key={m.l}>
              <div style={{ display: "flex", alignItems: "baseline", marginBottom: 4 }}>
                <span style={{ color: m.c, display: "inline-flex", alignItems: "center", marginRight: 6 }}>
                  <ModeIcon mode={m.l} size={14} />
                </span>
                <span className="r-mono" style={{ fontSize: 11, color: "var(--text)", letterSpacing: "0.08em", flex: 1 }}>{m.l}</span>
                <span style={{ fontSize: 14, color: "var(--text)", fontWeight: 600, fontVariantNumeric: "tabular-nums" }}>{m.v}%</span>
                <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", marginLeft: 8 }}>{m.n} TX</span>
              </div>
              <div style={{ height: m.l === "UPI" ? 24 : 10, background: "var(--surface)", borderRadius: 2, overflow: "hidden" }}>
                <div style={{ width: `${m.v}%`, height: "100%", background: m.c }} />
              </div>
            </div>
          ))}
        </div>

        <div style={{ flex: 1 }} />

        <div style={{
          marginBottom: 40, padding: "12px 14px",
          border: "1px dashed var(--hairline)", borderRadius: 6,
          position: "relative",
        }}>
          <span className="r-reg tl" /><span className="r-reg tr" /><span className="r-reg bl" /><span className="r-reg br" />
          <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>FUN FACT</div>
          <div style={{ fontSize: 13, color: "var(--text)", marginTop: 4 }}>
            You tapped a QR <span style={{ color: "var(--accent-bright)", fontWeight: 600 }}>292</span> times.
            That's <span style={{ color: "var(--accent-bright)", fontWeight: 600 }}>9.4</span> per day.
          </div>
        </div>
      </div>
    </StoryShell>
  );
}

// 7. Wrap — see you in June
function RecapWrap() {
  return (
    <StoryShell idx={6} total={7} bg="var(--accent)" color="var(--accent-on)" title="WRAP">
      <div style={{ position: "absolute", inset: 0, padding: "56px 28px 0", display: "flex", flexDirection: "column" }}>
        <span className="r-mono" style={{ fontSize: 11, letterSpacing: "0.16em", opacity: 0.7 }}>
          MAY · CLOSED
        </span>

        <div style={{ flex: 1, display: "flex", alignItems: "center" }}>
          <div>
            <div className="r-mono" style={{ fontSize: 11, letterSpacing: "0.14em", opacity: 0.7 }}>YOU SAVED</div>
            <div style={{
              fontFamily: "var(--font-sans)", fontWeight: 700,
              fontSize: 96, lineHeight: 0.9, letterSpacing: -0.05,
              fontVariantNumeric: "tabular-nums",
              marginTop: 6,
            }}>
              <span style={{ fontSize: 44, fontWeight: 600, opacity: 0.7, marginRight: 4, verticalAlign: "top" }}>₹</span>
              28,420
            </div>
            <div style={{ fontSize: 16, fontWeight: 500, marginTop: 12, opacity: 0.85, lineHeight: 1.4, textWrap: "balance", maxWidth: 280 }}>
              That's a flight, two months of groceries, or a quiet weekend somewhere new.
            </div>
          </div>
        </div>

        {/* stats grid */}
        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 1, background: "color-mix(in oklch, var(--accent-on) 16%, var(--accent))", borderRadius: 6, overflow: "hidden" }}>
          <WrapStat k="412" l="TRANSACTIONS" />
          <WrapStat k="₹49/hr" l="AWAKE RATE" />
          <WrapStat k="6 days" l="NO-SPEND" />
          <WrapStat k="100%" l="PARSED" />
        </div>

        <div style={{ marginTop: 18, display: "flex", gap: 8, marginBottom: 40 }}>
          <button style={{
            flex: 1, padding: "13px 0", borderRadius: 6, border: 0,
            background: "var(--accent-on)", color: "var(--accent)",
            fontFamily: "var(--font-sans)", fontWeight: 700, fontSize: 13,
          }}>Share my May</button>
          <button style={{
            padding: "13px 18px", borderRadius: 6,
            background: "transparent", color: "var(--accent-on)",
            border: "1.5px solid var(--accent-on)",
            fontFamily: "var(--font-sans)", fontWeight: 600, fontSize: 13,
          }}>Done</button>
        </div>
      </div>
    </StoryShell>
  );
}

function WrapStat({ k, l }) {
  return (
    <div style={{ padding: "14px 14px 12px", background: "var(--accent)" }}>
      <div style={{ fontSize: 22, fontWeight: 600, fontVariantNumeric: "tabular-nums", letterSpacing: -0.01 }}>{k}</div>
      <div className="r-mono" style={{ fontSize: 9, marginTop: 2, opacity: 0.7, letterSpacing: "0.12em" }}>{l}</div>
    </div>
  );
}

Object.assign(window, {
  RecapCover, RecapTotal, RecapCategory, RecapMerchant, RecapDay, RecapMode, RecapWrap,
});
