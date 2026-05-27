// Rupee — Settings screen

function SettingRow({ num, label, value, caret = true, danger, last }) {
  return (
    <div style={{
      display: "flex", alignItems: "center",
      padding: "14px 20px",
      borderBottom: last ? 0 : "1px solid var(--divider)",
    }}>
      <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", width: 22, letterSpacing: "0.08em" }}>{num}</span>
      <span style={{ flex: 1, fontSize: 14, color: danger ? "var(--bad)" : "var(--text)", fontWeight: 500 }}>{label}</span>
      {value && <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)", marginRight: caret ? 8 : 0 }}>{value}</span>}
      {caret && <span style={{ color: "var(--dim)" }}>›</span>}
    </div>
  );
}

function SettingsGroup({ title, num, children }) {
  return (
    <div style={{ marginTop: 18 }}>
      <div style={{ padding: "0 20px", display: "flex", alignItems: "center", marginBottom: 6, gap: 10 }}>
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.1em" }}>
          {String(num).padStart(2, "0")} /
        </span>
        <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)", letterSpacing: "0.12em", textTransform: "uppercase" }}>
          {title}
        </span>
        <span style={{ flex: 1, borderTop: "1px solid var(--divider)", marginLeft: 6 }} />
      </div>
      <div style={{ background: "var(--surface)" }}>{children}</div>
    </div>
  );
}

function SettingsScreen() {
  return (
    <RupeeFrame activeTab="set" inboxCount={4}>
      {/* Profile hero */}
      <div style={{ padding: "20px 20px 14px" }}>
        <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE / 05</div>
        <h1 style={{ fontSize: 26, fontWeight: 600, color: "var(--text)", margin: "4px 0 14px", letterSpacing: -0.01 }}>Settings</h1>

        <div style={{ background: "var(--surface)", borderRadius: 10, padding: "16px 18px", position: "relative" }}>
          <span className="r-reg tl" /><span className="r-reg tr" /><span className="r-reg bl" /><span className="r-reg br" />
          <div className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.12em" }}>USER · 001</div>
          <div style={{ fontSize: 22, fontWeight: 600, color: "var(--text)", marginTop: 4 }}>Aman</div>
          <div className="r-mono" style={{ fontSize: 10, color: "var(--muted)", marginTop: 4, letterSpacing: "0.08em" }}>
            BUDGET ₹65,000 · 1ST OF MONTH · LOCAL
          </div>
          {/* mini stat strip */}
          <div style={{ display: "flex", gap: 16, marginTop: 14, paddingTop: 12, borderTop: "1px dashed var(--hairline)" }}>
            <Stat k="LEDGER" v="412 tx" />
            <Stat k="SINCE" v="Jan 2026" />
            <Stat k="STORAGE" v="2.4 MB" />
          </div>
        </div>
      </div>

      <SettingsGroup title="Watching" num={1}>
        <SettingRow num="01" label="Notification access" value="ON" />
        <SettingRow num="02" label="SMS access" value="OFF · alpha" />
        <SettingRow num="03" label="Auto-confirm threshold" value="94%" />
        <SettingRow num="04" label="Trust rules" value="12 merchants" last />
      </SettingsGroup>

      <SettingsGroup title="Money" num={2}>
        <SettingRow num="05" label="Budgets" value="6 active" />
        <SettingRow num="06" label="Cards & EMIs" value="1 + 1" />
        <SettingRow num="07" label="Accounts" value="2 banks · cash" />
        <SettingRow num="08" label="Recurring" value="4 subs" last />
      </SettingsGroup>

      <SettingsGroup title="Reports" num={3}>
        <SettingRow num="09" label="Monthly recap" value="MAY · ready" />
        <SettingRow num="10" label="Export data" />
        <SettingRow num="11" label="Send feedback" last />
      </SettingsGroup>

      <SettingsGroup title="Hazard zone" num={4}>
        <SettingRow num="12" label="Debug · parser logs" />
        <SettingRow num="13" label="Reset learned merchants" danger />
        <SettingRow num="14" label="Erase all data" danger last />
      </SettingsGroup>

      <div style={{ padding: "26px 20px 8px", display: "flex", justifyContent: "space-between" }}>
        <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.1em" }}>RUPEE · v0.4.1 · BUILD 412</span>
        <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)" }}>EOF</span>
      </div>
    </RupeeFrame>
  );
}

function Stat({ k, v }) {
  return (
    <div style={{ flex: 1 }}>
      <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.1em" }}>{k}</div>
      <div style={{ fontSize: 13, color: "var(--text)", fontWeight: 500, marginTop: 2, fontVariantNumeric: "tabular-nums" }}>{v}</div>
    </div>
  );
}

Object.assign(window, { SettingsScreen });
