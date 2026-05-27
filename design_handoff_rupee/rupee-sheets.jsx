// Rupee — Bottom sheets: Manual Entry, Transaction Detail

function SheetWrap({ children, dim = true }) {
  return (
    <RupeeFrame showNav={false}>
      {dim && <div style={{ position: "absolute", inset: 0, background: "rgba(0,0,0,0.5)" }} />}
      <div style={{
        position: "absolute", left: 0, right: 0, bottom: 0,
        background: "var(--bg)",
        borderTopLeftRadius: 18, borderTopRightRadius: 18,
        boxShadow: "0 -12px 32px rgba(0,0,0,0.4)",
        maxHeight: "85%",
        display: "flex", flexDirection: "column",
      }}>
        <div style={{ width: 36, height: 4, background: "var(--surface-3)", borderRadius: 2, margin: "10px auto 8px", flexShrink: 0 }} />
        {children}
      </div>
    </RupeeFrame>
  );
}

function ManualEntrySheet() {
  return (
    <SheetWrap>
      <div style={{ padding: "8px 20px 20px", overflow: "auto" }}>
        {/* Header */}
        <div style={{ display: "flex", alignItems: "baseline", marginBottom: 16 }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>SHEET · 01</span>
          <span style={{ flex: 1 }} />
          <span style={{ color: "var(--muted)", fontFamily: "var(--font-mono)", fontSize: 13 }}>✕</span>
        </div>
        <h2 style={{ fontSize: 22, fontWeight: 600, color: "var(--text)", margin: 0, letterSpacing: -0.01 }}>Add transaction</h2>

        {/* Type segmented */}
        <div style={{ display: "flex", marginTop: 18, background: "var(--surface-2)", borderRadius: 6, padding: 3 }}>
          {[
            { id: "expense", label: "Expense", on: true },
            { id: "income", label: "Income" },
          ].map((t) => (
            <button key={t.id} style={{
              flex: 1, padding: "9px 0", border: 0, borderRadius: 4,
              background: t.on ? "var(--bg)" : "transparent",
              color: t.on ? "var(--text)" : "var(--muted)",
              fontFamily: "var(--font-sans)", fontWeight: 600, fontSize: 13,
            }}>{t.label}</button>
          ))}
        </div>

        {/* Recent merchants — quick-fill */}
        <div className="r-eyebrow" style={{ marginTop: 22, marginBottom: 8 }}>RECENT · TAP TO FILL</div>
        <div style={{ display: "flex", gap: 6, overflowX: "auto", marginRight: -20 }}>
          {["Blue Tokai", "Swiggy", "Big Bazaar", "Uber", "Cult.fit"].map((m, i) => (
            <button key={m} style={{
              flexShrink: 0,
              padding: "8px 10px", borderRadius: 5,
              background: i === 0 ? "color-mix(in oklch, var(--accent) 14%, var(--surface))" : "var(--surface)",
              color: i === 0 ? "var(--accent-bright)" : "var(--text)",
              border: i === 0 ? "1px solid var(--accent)" : "1px solid var(--divider)",
              fontFamily: "var(--font-sans)", fontWeight: 500, fontSize: 12,
            }}>{m}</button>
          ))}
          <div style={{ width: 20, flexShrink: 0 }} />
        </div>

        {/* Amount — big input */}
        <div style={{ marginTop: 20, padding: "18px 16px", background: "var(--surface)", borderRadius: 8, position: "relative" }}>
          <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em", marginBottom: 6 }}>AMOUNT</div>
          <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
            <span style={{ fontSize: 32, color: "var(--dim)", fontWeight: 500 }}>₹</span>
            <span style={{
              fontSize: 44, fontWeight: 600, color: "var(--text)",
              fontVariantNumeric: "tabular-nums", letterSpacing: -0.02, lineHeight: 1,
            }}>480</span>
            <span style={{ width: 2, height: 36, background: "var(--accent)", marginLeft: 2, animation: "rPulse 1.2s ease-in-out infinite" }} />
          </div>
        </div>

        {/* Merchant text */}
        <div style={{ marginTop: 12, padding: "14px 16px", background: "var(--surface)", borderRadius: 8 }}>
          <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em", marginBottom: 6 }}>MERCHANT</div>
          <div style={{ fontSize: 16, color: "var(--text)", fontWeight: 500 }}>Blue Tokai</div>
        </div>

        {/* Mode chips */}
        <div className="r-eyebrow" style={{ marginTop: 18, marginBottom: 8 }}>MODE</div>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {[
            { id: "UPI", on: true },
            { id: "CARD" },
            { id: "CASH" },
            { id: "BANK", label: "TRANSFER" },
            { id: "ATM" },
          ].map((m) => (
            <button key={m.id} style={{
              padding: "8px 12px", borderRadius: 5,
              background: m.on ? "color-mix(in oklch, var(--accent) 14%, var(--surface))" : "var(--surface)",
              color: m.on ? "var(--accent-bright)" : "var(--muted)",
              border: m.on ? "1px solid var(--accent)" : "1px solid var(--divider)",
              fontFamily: "var(--font-mono)", fontSize: 10, letterSpacing: "0.1em",
              display: "flex", alignItems: "center", gap: 5,
            }}>
              <ModeIcon mode={m.id} /> {m.label || m.id}
            </button>
          ))}
        </div>

        {/* Category chips */}
        <div className="r-eyebrow" style={{ marginTop: 18, marginBottom: 8 }}>CATEGORY</div>
        <div style={{ display: "flex", gap: 6, flexWrap: "wrap" }}>
          {Object.entries(CATEGORIES).slice(0, 8).map(([k, v]) => {
            const on = k === "food";
            return (
              <button key={k} style={{
                padding: "6px 10px 6px 6px", borderRadius: 5,
                background: on ? "color-mix(in oklch, var(--accent) 14%, var(--surface))" : "var(--surface)",
                color: on ? "var(--accent-bright)" : "var(--text)",
                border: on ? "1px solid var(--accent)" : "1px solid var(--divider)",
                fontFamily: "var(--font-sans)", fontSize: 12, fontWeight: 500,
                display: "flex", alignItems: "center", gap: 6,
              }}>
                <span style={{ width: 16, height: 16, borderRadius: 2, background: v.c, color: "white",
                  fontFamily: "var(--font-mono)", fontWeight: 600, fontSize: 9,
                  display: "flex", alignItems: "center", justifyContent: "center" }}>{v.mono}</span>
                {v.label}
              </button>
            );
          })}
        </div>

        {/* Note */}
        <div style={{ marginTop: 18, padding: "14px 16px", background: "var(--surface)", borderRadius: 8 }}>
          <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em", marginBottom: 6 }}>NOTE · OPTIONAL</div>
          <div style={{ fontSize: 13, color: "var(--dim)" }}>Cold brew + breakfast</div>
        </div>

        {/* CTA */}
        <button className="r-btn block" style={{ marginTop: 22 }}>Save transaction</button>
      </div>
    </SheetWrap>
  );
}

function TxDetailSheet() {
  return (
    <SheetWrap>
      <div style={{ padding: "8px 20px 24px" }}>
        <div style={{ display: "flex", alignItems: "baseline", marginBottom: 16 }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>TX · 0042</span>
          <span style={{ flex: 1 }} />
          <span style={{ color: "var(--muted)", fontFamily: "var(--font-mono)", fontSize: 13 }}>✕</span>
        </div>

        {/* Hero amount */}
        <div style={{ display: "flex", justifyContent: "space-between", alignItems: "flex-start", marginBottom: 18 }}>
          <div>
            <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>BLUE TOKAI</div>
            <HeroAmount value={480} />
            <div className="r-mono" style={{ fontSize: 11, color: "var(--muted)", marginTop: 4, letterSpacing: "0.06em" }}>
              TODAY · 08:42 · UPI · HDFC
            </div>
          </div>
          <CatBadge id="food" size={48} />
        </div>

        {/* Confidence + trust */}
        <div style={{ background: "var(--surface)", borderRadius: 8, padding: 14, marginBottom: 10 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
            <span style={{ width: 8, height: 8, borderRadius: 4, background: "var(--good)" }} />
            <span className="r-mono" style={{ fontSize: 10, color: "var(--good)", letterSpacing: "0.12em", flex: 1 }}>AUTO-CONFIRMED · 96%</span>
            <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)" }}>11s parse</span>
          </div>
          <div style={{ fontSize: 12, color: "var(--muted)", marginTop: 6, lineHeight: 1.4 }}>
            HDFC standard UPI template. Merchant matched a trusted name (3rd time this month).
          </div>
        </div>

        {/* Editable fields */}
        <Field2 label="MERCHANT" value="Blue Tokai" />
        <Field2 label="CATEGORY" value="Food" cat="food" />
        <Field2 label="MODE" value="UPI · HDFC" />
        <Field2 label="NOTE" value="Cold brew + breakfast" muted />

        {/* Trust toggle */}
        <div style={{
          marginTop: 14, padding: "14px 16px",
          background: "var(--surface)", borderRadius: 8,
          display: "flex", alignItems: "center", gap: 12,
        }}>
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 14, fontWeight: 500, color: "var(--text)" }}>Always trust Blue Tokai</div>
            <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", marginTop: 2, letterSpacing: "0.06em" }}>
              SKIP REVIEW · AUTO-CATEGORIZE
            </div>
          </div>
          <Toggle on />
        </div>

        {/* Raw notif source */}
        <div style={{ marginTop: 10, padding: "10px 12px", background: "var(--bg-deep)", borderRadius: 6, border: "1px dashed var(--hairline)" }}>
          <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em", marginBottom: 4 }}>SOURCE · HDFC NOTIF</div>
          <div style={{ fontFamily: "var(--font-mono)", fontSize: 11, color: "var(--muted)", lineHeight: 1.4 }}>
            Rs.480 debited from a/c ××4521 at BLUE TOKAI on 23-MAY-26.
          </div>
        </div>

        <div style={{ display: "flex", gap: 8, marginTop: 18 }}>
          <button className="r-btn ghost" style={{ flex: 1 }}>Delete</button>
          <button className="r-btn" style={{ flex: 2 }}>Save changes</button>
        </div>
      </div>
    </SheetWrap>
  );
}

function Field2({ label, value, cat, muted }) {
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 12,
      padding: "12px 16px", background: "var(--surface)", borderRadius: 8, marginBottom: 6,
    }}>
      {cat && <CatBadge id={cat} size={26} />}
      <div style={{ flex: 1 }}>
        <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>{label}</div>
        <div style={{ fontSize: 14, fontWeight: 500, color: muted ? "var(--muted)" : "var(--text)", marginTop: 2 }}>{value}</div>
      </div>
      <span style={{ color: "var(--dim)", fontSize: 12 }}>EDIT</span>
    </div>
  );
}

function Toggle({ on }) {
  return (
    <div style={{
      width: 44, height: 26, borderRadius: 13,
      background: on ? "var(--accent)" : "var(--surface-3)",
      position: "relative",
      transition: "background .15s",
      flexShrink: 0, cursor: "pointer",
    }}>
      <div style={{
        position: "absolute", top: 2, left: on ? 20 : 2,
        width: 22, height: 22, borderRadius: 11,
        background: on ? "var(--accent-on)" : "var(--bg-deep)",
        transition: "left .15s, background .15s",
      }} />
    </div>
  );
}

Object.assign(window, { ManualEntrySheet, TxDetailSheet, SheetWrap, Toggle });
