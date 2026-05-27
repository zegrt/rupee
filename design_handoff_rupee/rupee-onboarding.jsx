// Rupee — Onboarding flow
// Welcome · Permissions · Profile · Setup

function OnbWrap({ step, total, children, footer }) {
  return (
    <RupeeFrame showNav={false}>
      <div style={{ display: "flex", flexDirection: "column", height: "100%", padding: "14px 22px 18px", boxSizing: "border-box", overflow: "hidden" }}>
        {/* Header — wordmark + step counter */}
        <div style={{ display: "flex", alignItems: "center", marginBottom: 20, flexShrink: 0 }}>
          <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
            <div style={{ width: 18, height: 18, background: "var(--accent)", borderRadius: 4, display: "flex", alignItems: "center", justifyContent: "center" }}>
              <span style={{ fontFamily: "var(--font-mono)", fontWeight: 700, fontSize: 11, color: "var(--accent-on)" }}>₹</span>
            </div>
            <span className="r-mono" style={{ fontSize: 11, fontWeight: 600, letterSpacing: "0.16em", color: "var(--text)" }}>RUPEE</span>
          </div>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.1em" }}>
            STEP {String(step).padStart(2, "0")} / {String(total).padStart(2, "0")}
          </span>
        </div>

        <div style={{ flex: 1, display: "flex", flexDirection: "column", minHeight: 0 }}>
          {children}
        </div>

        {footer && <div style={{ marginTop: 14, flexShrink: 0 }}>{footer}</div>}
      </div>
    </RupeeFrame>
  );
}

function OnbWelcome() {
  return (
    <OnbWrap step={1} total={4}
      footer={
        <>
          <button className="r-btn block">Begin →</button>
          <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em", textAlign: "center", marginTop: 10 }}>
            LOCAL STORAGE · NO ACCOUNTS
          </div>
        </>
      }>
      <div className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.12em", marginBottom: 12 }}>
        001 · A NEW LEDGER
      </div>
      <h1 style={{
        fontSize: 38, fontWeight: 600, lineHeight: 1.0, letterSpacing: -0.02, margin: 0,
        color: "var(--text)", textWrap: "balance",
      }}>
        Your money,<br />
        <span style={{ color: "var(--accent-bright)" }}>watched.</span>
      </h1>
      <p style={{ fontSize: 14, color: "var(--muted)", lineHeight: 1.45, marginTop: 12, marginBottom: 0, textWrap: "pretty" }}>
        Rupee reads your bank notifications and turns them into a clean ledger — no logins, no accounts, no cloud.
      </p>

      <div style={{ flex: 1 }} />

      <div>
        <FactRow num="01" k="LOCAL ONLY"  v="Stays on this device" />
        <FactRow num="02" k="NO SIGNUP"   v="No accounts, no email" />
        <FactRow num="03" k="ZERO ENTRY"  v="Notifications do the work" />
        <FactRow num="04" k="MADE FOR ₹"  v="UPI, cards, cash, EMI" />
      </div>
    </OnbWrap>
  );
}

function FactRow({ num, k, v }) {
  return (
    <div style={{ display: "flex", alignItems: "baseline", padding: "8px 0", borderTop: "1px solid var(--divider)" }}>
      <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.08em", width: 26 }}>{num}</span>
      <span className="r-mono" style={{ fontSize: 10, color: "var(--text)", letterSpacing: "0.1em", width: 92 }}>{k}</span>
      <span style={{ flex: 1, fontSize: 12, color: "var(--muted)" }}>{v}</span>
    </div>
  );
}

function OnbPermissions() {
  return (
    <OnbWrap step={2} total={4}
      footer={
        <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
          <button className="r-btn block">Grant notification access</button>
          <button className="r-btn ghost block" style={{ background: "transparent", color: "var(--muted)" }}>I'll do this later</button>
        </div>
      }>
      <div className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.12em", marginBottom: 10 }}>
        002 · ACCESS
      </div>
      <h1 style={{ fontSize: 28, fontWeight: 600, lineHeight: 1.1, letterSpacing: -0.02, margin: 0, color: "var(--text)" }}>
        Let Rupee watch your bank notifications.
      </h1>
      <p style={{ fontSize: 13, color: "var(--muted)", lineHeight: 1.45, marginTop: 10, marginBottom: 0 }}>
        It never leaves your phone — the text in each notification becomes a clean transaction.
      </p>

      {/* Demonstration card — fake notification → parsed tx */}
      <div style={{ marginTop: 18, padding: 14, background: "var(--surface)", borderRadius: 8, position: "relative" }}>
        <span className="r-reg tl" /><span className="r-reg tr" /><span className="r-reg bl" /><span className="r-reg br" />
        <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em", marginBottom: 8 }}>
          FLOW · DEMONSTRATION
        </div>
        {/* fake notification */}
        <div style={{ background: "var(--surface-2)", borderRadius: 6, padding: "8px 10px" }}>
          <div style={{ display: "flex", alignItems: "center", gap: 6 }}>
            <BankBadge id="HDFC" size={13} />
            <span className="r-mono" style={{ fontSize: 9, color: "var(--muted)", letterSpacing: "0.1em" }}>HDFC BANK · NOW</span>
          </div>
          <div style={{ fontSize: 11.5, color: "var(--text)", marginTop: 4, lineHeight: 1.35, fontFamily: "var(--font-mono)" }}>
            Rs.480 debited from a/c ××4521 at BLUE TOKAI on 23-MAY-26.
          </div>
        </div>
        {/* arrow */}
        <div style={{ textAlign: "center", margin: "4px 0", color: "var(--faint)", fontSize: 12, lineHeight: 1 }}>↓</div>
        {/* parsed */}
        <div style={{ display: "flex", alignItems: "center", gap: 10, padding: "8px 10px", background: "color-mix(in oklch, var(--accent) 10%, var(--surface-2))", borderRadius: 6 }}>
          <CatBadge id="food" size={26} />
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 13, fontWeight: 500, color: "var(--text)" }}>Blue Tokai</div>
            <div className="r-mono" style={{ fontSize: 10, color: "var(--muted)" }}>UPI · HDFC · Food</div>
          </div>
          <Amount value={480} size={14} weight={600} />
        </div>
      </div>

      <div style={{ flex: 1 }} />
    </OnbWrap>
  );
}

function OnbProfile() {
  return (
    <OnbWrap step={3} total={4}
      footer={<button className="r-btn block">Continue →</button>}>
      <div className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.12em", marginBottom: 10 }}>
        003 · YOU
      </div>
      <h1 style={{ fontSize: 28, fontWeight: 600, lineHeight: 1.05, letterSpacing: -0.02, margin: 0, color: "var(--text)" }}>
        A name and a number.
      </h1>
      <p style={{ fontSize: 13, color: "var(--muted)", lineHeight: 1.45, marginTop: 8, marginBottom: 0 }}>
        The budget you pick anchors everything else.
      </p>

      <div style={{ marginTop: 16 }}>
        <Field label="NAME" value="Aman" />
        <Field label="MONTHLY BUDGET" value="65,000" prefix="₹" big />
        <Field label="MONTH STARTS ON" value="1st of every month" caret />
      </div>

      <div style={{ marginTop: 14, padding: "10px 12px", border: "1px dashed var(--hairline)", borderRadius: 6 }}>
        <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em", marginBottom: 4 }}>SUGGESTED · LAST 90D</div>
        <div style={{ fontSize: 12, color: "var(--muted)", lineHeight: 1.35 }}>
          Income avg <span style={{ color: "var(--text)", fontWeight: 500 }}>₹1,45,000</span> — a budget of <span style={{ color: "var(--accent-bright)", fontWeight: 500 }}>~45%</span> is healthy.
        </div>
      </div>

      <div style={{ flex: 1 }} />
    </OnbWrap>
  );
}

function Field({ label, value, prefix, big, caret }) {
  return (
    <div style={{ borderBottom: "1px solid var(--divider)", padding: "10px 0" }}>
      <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em", marginBottom: 4 }}>{label}</div>
      <div style={{ display: "flex", alignItems: "baseline", gap: 6 }}>
        {prefix && <span style={{ fontSize: big ? 20 : 14, color: "var(--muted)", fontWeight: 500 }}>{prefix}</span>}
        <span style={{
          fontSize: big ? 28 : 16, fontWeight: big ? 600 : 500,
          color: "var(--text)", letterSpacing: big ? -0.02 : 0,
          fontVariantNumeric: "tabular-nums", flex: 1, lineHeight: 1.1,
        }}>{value}</span>
        {caret && <span style={{ color: "var(--muted)" }}>›</span>}
      </div>
    </div>
  );
}

function OnbSetup() {
  return (
    <OnbWrap step={4} total={4}
      footer={
        <div>
          <button className="r-btn block">Continue with 2 accounts →</button>
          <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.1em", textAlign: "center", marginTop: 10 }}>
            YOU CAN ADD MORE ANYTIME
          </div>
        </div>
      }>
      <div className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.12em", marginBottom: 10 }}>
        004 · SOURCES
      </div>
      <h1 style={{ fontSize: 28, fontWeight: 600, lineHeight: 1.05, letterSpacing: -0.02, margin: 0, color: "var(--text)" }}>
        Where does your money sit?
      </h1>
      <p style={{ fontSize: 13, color: "var(--muted)", lineHeight: 1.4, marginTop: 8, marginBottom: 0 }}>
        Pick the banks, cards and wallets you use.
      </p>

      <div className="r-eyebrow" style={{ marginTop: 14, marginBottom: 6 }}>BANKS</div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 6 }}>
        {[
          { id: "HDFC",  on: true,  num: "××4521" },
          { id: "ICICI", on: true,  num: "××8830" },
          { id: "AXIS",  on: false },
          { id: "KOTAK", on: false },
        ].map((b) => <BankPill key={b.id} bank={b} />)}
      </div>
      <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", marginTop: 6, letterSpacing: "0.1em" }}>+ 2 MORE · SBI, JUPITER</div>

      <div className="r-eyebrow" style={{ marginTop: 14, marginBottom: 6 }}>CARDS</div>
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 6 }}>
        <CardPill brand="VISA" bank="HDFC" last="9421" on />
        <div style={{
          border: "1px dashed var(--hairline)", borderRadius: 6, padding: "9px 12px",
          display: "flex", alignItems: "center", justifyContent: "center", gap: 6,
          color: "var(--muted)", fontSize: 12,
        }}>
          <span style={{ fontSize: 14 }}>+</span> Add card
        </div>
      </div>

      <div className="r-eyebrow" style={{ marginTop: 14, marginBottom: 6 }}>CASH</div>
      <div style={{ padding: "8px 12px", background: "var(--surface)", borderRadius: 6, display: "flex", alignItems: "center", gap: 10 }}>
        <CatBadge id="cash" size={24} />
        <div style={{ flex: 1 }}>
          <div style={{ fontSize: 12, color: "var(--text)" }}>Wallet</div>
          <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)" }}>MANUAL ONLY</div>
        </div>
        <div style={{ fontFamily: "var(--font-mono)", fontSize: 12, color: "var(--text)" }}>₹2,400</div>
      </div>

      <div style={{ flex: 1 }} />
    </OnbWrap>
  );
}

function BankPill({ bank }) {
  return (
    <div style={{
      display: "flex", alignItems: "center", gap: 8,
      padding: "8px 10px", borderRadius: 6,
      background: bank.on ? "var(--surface)" : "transparent",
      border: bank.on ? "1px solid var(--accent)" : "1px solid var(--divider)",
      position: "relative",
    }}>
      <BankBadge id={bank.id} size={20} />
      <div style={{ flex: 1, minWidth: 0 }}>
        <div style={{ fontSize: 11, fontWeight: 500, color: "var(--text)" }}>{bank.id}</div>
        {bank.num && <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)" }}>{bank.num}</div>}
      </div>
      {bank.on && <span style={{ width: 6, height: 6, borderRadius: 3, background: "var(--accent)" }} />}
    </div>
  );
}

function CardPill({ brand, bank, last, on }) {
  return (
    <div style={{
      padding: "8px 10px", borderRadius: 6,
      background: on ? "var(--surface)" : "transparent",
      border: on ? "1px solid var(--accent)" : "1px solid var(--divider)",
    }}>
      <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em", marginBottom: 2 }}>{brand} · {bank}</div>
      <div className="r-mono" style={{ fontSize: 12, color: "var(--text)", letterSpacing: "0.06em" }}>•• •• •• {last}</div>
    </div>
  );
}

Object.assign(window, { OnbWelcome, OnbPermissions, OnbProfile, OnbSetup });
