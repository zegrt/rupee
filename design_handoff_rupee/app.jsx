// Rupee — main app, composes design canvas

const ACCENTS = [
  { id: "saffron",  name: "Saffron",  c: "oklch(0.74 0.175 55)",  bright: "oklch(0.82 0.19 60)",  on: "oklch(0.15 0.02 55)" },
  { id: "indigo",   name: "Indigo",   c: "oklch(0.65 0.18 270)",  bright: "oklch(0.75 0.18 270)", on: "oklch(0.99 0.01 270)" },
  { id: "teal",     name: "Teal",     c: "oklch(0.72 0.14 175)",  bright: "oklch(0.82 0.13 175)", on: "oklch(0.15 0.04 175)" },
  { id: "magenta",  name: "Magenta",  c: "oklch(0.68 0.20 340)",  bright: "oklch(0.78 0.21 345)", on: "oklch(0.99 0.01 340)" },
];
const ACCENT_BY_C = Object.fromEntries(ACCENTS.map((a) => [a.c, a]));

const TWEAK_DEFAULTS = /*EDITMODE-BEGIN*/{
  "accent": "oklch(0.74 0.175 55)",
  "theme": "dark",
  "chipMode": "icon",
  "confMode": "stripe"
}/*EDITMODE-END*/;

function applyAccent(c) {
  const a = ACCENT_BY_C[c] || ACCENTS[0];
  const style = document.getElementById("rupee-accent-override") || (() => {
    const el = document.createElement("style");
    el.id = "rupee-accent-override";
    document.head.appendChild(el);
    return el;
  })();
  style.textContent = `:root, .rupee-light { --accent: ${a.c}; --accent-bright: ${a.bright}; --accent-on: ${a.on}; }`;
}

function applyTheme(theme) {
  // Global theme — wraps body so all artboards re-render light tokens.
  // The "Light samples" section's inner .rupee-light wrappers still apply harmlessly.
  if (theme === "light") document.body.classList.add("rupee-light");
  else document.body.classList.remove("rupee-light");
}

function App() {
  const [t, setTweak] = useTweaks(TWEAK_DEFAULTS);

  React.useEffect(() => { applyAccent(t.accent); }, [t.accent]);
  React.useEffect(() => { applyTheme(t.theme); }, [t.theme]);

  const chipMode = t.chipMode;     // icon | plain | stripe
  const confMode = t.confMode;     // stripe | dot | tint | chip

  return (
    <>
      <DesignCanvas minScale={0.15} maxScale={2}>
        <DCSection id="found" title="00 · Foundations" subtitle="Tone, type, system anatomy">
          <DCArtboard id="found-system" label="System reference" width={520} height={780}>
            <FoundationsCard />
          </DCArtboard>
          <DCArtboard id="found-palette" label="Palette + type" width={420} height={780}>
            <PaletteCard accent={t.accent} />
          </DCArtboard>
        </DCSection>

        <DCSection id="onb" title="01 · Onboarding" subtitle="Welcome → Permissions → Profile → Setup">
          <DCArtboard id="onb-1" label="01 · Welcome"      width={RFRAME_W} height={RFRAME_H}><OnbWelcome /></DCArtboard>
          <DCArtboard id="onb-2" label="02 · Permissions"  width={RFRAME_W} height={RFRAME_H}><OnbPermissions /></DCArtboard>
          <DCArtboard id="onb-3" label="03 · Profile"      width={RFRAME_W} height={RFRAME_H}><OnbProfile /></DCArtboard>
          <DCArtboard id="onb-4" label="04 · Setup"        width={RFRAME_W} height={RFRAME_H}><OnbSetup /></DCArtboard>
        </DCSection>

        <DCSection id="home" title="02 · Home" subtitle="Hero, weekly, categories, dues, recents — populated · over · empty">
          <DCArtboard id="home-populated" label="Populated"            width={RFRAME_W} height={RFRAME_H}><HomeScreen confMode={confMode} /></DCArtboard>
          <DCArtboard id="home-over"      label="Over budget"          width={RFRAME_W} height={RFRAME_H}><HomeScreen confMode={confMode} over /></DCArtboard>
          <DCArtboard id="home-empty"     label="First-run · empty"    width={RFRAME_W} height={RFRAME_H}><HomeEmpty /></DCArtboard>
        </DCSection>

        <DCSection id="inbox" title="03 · Inbox" subtitle="Review queue · reason chips · empty">
          <DCArtboard id="inbox-main"   label="Queue"                       width={RFRAME_W} height={RFRAME_H}><InboxScreen chipMode={chipMode} /></DCArtboard>
          <DCArtboard id="inbox-tapped" label="Chip tapped · merge prompt"  width={RFRAME_W} height={RFRAME_H}><InboxFocus chipMode={chipMode} /></DCArtboard>
          <DCArtboard id="inbox-empty"  label="All clear · empty"           width={RFRAME_W} height={RFRAME_H}><InboxEmpty /></DCArtboard>
        </DCSection>

        <DCSection id="tx" title="04 · Transactions" subtitle="List · search · empty">
          <DCArtboard id="tx-main"   label={`Chronological · ${confMode}`} width={RFRAME_W} height={RFRAME_H}><TxScreen confMode={confMode} /></DCArtboard>
          <DCArtboard id="tx-search" label='Searching "swiggy"'            width={RFRAME_W} height={RFRAME_H}><TxSearchActive /></DCArtboard>
          <DCArtboard id="tx-empty"  label="Pre-data · empty"              width={RFRAME_W} height={RFRAME_H}><TransactionsEmpty /></DCArtboard>
        </DCSection>

        <DCSection id="cal" title="05 · Calendar" subtitle="Month grid · long-press peek · day sheet · quiet month">
          <DCArtboard id="cal-main"  label="Month grid"          width={RFRAME_W} height={RFRAME_H}><CalendarScreen /></DCArtboard>
          <DCArtboard id="cal-peek"  label="Long-press peek"     width={RFRAME_W} height={RFRAME_H}><CalendarPeek /></DCArtboard>
          <DCArtboard id="cal-day"   label="Day · May 18 sheet"  width={RFRAME_W} height={RFRAME_H}><DaySheet /></DCArtboard>
          <DCArtboard id="cal-quiet" label="Quiet month · empty" width={RFRAME_W} height={RFRAME_H}><CalendarQuiet /></DCArtboard>
        </DCSection>

        <DCSection id="recap" title="06 · Monthly Recap" subtitle="7-card Wrapped story · minimal single-screen alt">
          <DCArtboard id="rc-1"  label="01 · Cover"        width={RFRAME_W} height={RFRAME_H}><RecapCover /></DCArtboard>
          <DCArtboard id="rc-2"  label="02 · Total"        width={RFRAME_W} height={RFRAME_H}><RecapTotal /></DCArtboard>
          <DCArtboard id="rc-3"  label="03 · Category"     width={RFRAME_W} height={RFRAME_H}><RecapCategory /></DCArtboard>
          <DCArtboard id="rc-4"  label="04 · Merchant"     width={RFRAME_W} height={RFRAME_H}><RecapMerchant /></DCArtboard>
          <DCArtboard id="rc-5"  label="05 · Day"          width={RFRAME_W} height={RFRAME_H}><RecapDay /></DCArtboard>
          <DCArtboard id="rc-6"  label="06 · Mode"         width={RFRAME_W} height={RFRAME_H}><RecapMode /></DCArtboard>
          <DCArtboard id="rc-7"  label="07 · Wrap"         width={RFRAME_W} height={RFRAME_H}><RecapWrap /></DCArtboard>
          <DCArtboard id="rc-min" label="Minimal · single screen alt" width={RFRAME_W} height={RFRAME_H}><MinimalRecap /></DCArtboard>
        </DCSection>

        <DCSection id="set" title="07 · Settings" subtitle="Index + the deep sub-screens it links to">
          <DCArtboard id="set-main" label="Index"            width={RFRAME_W} height={RFRAME_H}><SettingsScreen /></DCArtboard>
          <DCArtboard id="set-trust" label="Trust rules"     width={RFRAME_W} height={RFRAME_H}><TrustRulesScreen /></DCArtboard>
          <DCArtboard id="set-cards" label="Cards & EMIs"    width={RFRAME_W} height={RFRAME_H}><CardsAndEMIs /></DCArtboard>
          <DCArtboard id="set-acct"  label="Accounts"        width={RFRAME_W} height={RFRAME_H}><AccountsScreen /></DCArtboard>
          <DCArtboard id="set-bud"   label="Budgets · editor" width={RFRAME_W} height={RFRAME_H}><BudgetsScreen /></DCArtboard>
          <DCArtboard id="set-rec"   label="Recurring · subs" width={RFRAME_W} height={RFRAME_H}><RecurringScreen /></DCArtboard>
        </DCSection>

        <DCSection id="sheets" title="08 · Sheets" subtitle="Manual entry · transaction detail">
          <DCArtboard id="sh-manual" label="Manual entry"       width={RFRAME_W} height={RFRAME_H}><ManualEntrySheet /></DCArtboard>
          <DCArtboard id="sh-detail" label="Transaction detail" width={RFRAME_W} height={RFRAME_H}><TxDetailSheet /></DCArtboard>
        </DCSection>

        <DCSection id="notif" title="09 · Notifications" subtitle="Lock-screen heads-up · pulled-down shade">
          <DCArtboard id="nf-lock"  label="Lock screen · heads-up"     width={RFRAME_W} height={RFRAME_H}><NotifLockScreen /></DCArtboard>
          <DCArtboard id="nf-shade" label="Notification shade · mixed" width={RFRAME_W} height={RFRAME_H}><NotifShade /></DCArtboard>
        </DCSection>

        <DCSection id="widgets" title="10 · Widgets" subtitle="Pixel home-screen mock · 4×2 hero · 2×2s · 4×1 strip">
          <DCArtboard id="wg-1" label="Home screen · 3 widgets" width={RFRAME_W} height={RFRAME_H}><WidgetsMock /></DCArtboard>
        </DCSection>

        <DCSection id="light" title="11 · Light mode samples" subtitle="System-driven; same system, inverted surfaces">
          <DCArtboard id="lt-home" label="Home · light" width={RFRAME_W} height={RFRAME_H}>
            <div className="rupee-light" style={{ height: "100%" }}><HomeScreen confMode={confMode} /></div>
          </DCArtboard>
          <DCArtboard id="lt-recap" label="Recap · category · light" width={RFRAME_W} height={RFRAME_H}>
            <div className="rupee-light" style={{ height: "100%" }}><LightRecap /></div>
          </DCArtboard>
        </DCSection>
      </DesignCanvas>

      <TweaksPanel title="Rupee · Tweaks">
        <TweakSection label="Theme">
          <TweakRadio label="Mode" value={t.theme} onChange={(v) => setTweak("theme", v)} options={[
            { value: "dark",  label: "Dark" },
            { value: "light", label: "Light" },
          ]} />
        </TweakSection>
        <TweakSection label="Accent">
          <TweakColor label="Color" value={t.accent} onChange={(v) => setTweak("accent", v)}
            options={ACCENTS.map((a) => a.c)} />
        </TweakSection>
        <TweakSection label="Inbox reason chip">
          <TweakRadio label="Style" value={t.chipMode} onChange={(v) => setTweak("chipMode", v)} options={[
            { value: "icon",   label: "Icon" },
            { value: "plain",  label: "Tint" },
            { value: "stripe", label: "Plain" },
          ]} />
        </TweakSection>
        <TweakSection label="Confidence signal">
          <TweakSelect label="Style" value={t.confMode} onChange={(v) => setTweak("confMode", v)} options={[
            { value: "stripe", label: "Edge stripe" },
            { value: "dot",    label: "Inline dot" },
            { value: "tint",   label: "Row tint" },
            { value: "chip",   label: "Status chip" },
          ]} />
        </TweakSection>
      </TweaksPanel>
    </>
  );
}

// ─── FoundationsCard — small system reference card on the canvas ───────────
function FoundationsCard() {
  return (
    <div className="r-shell" style={{ padding: 32, fontFamily: "var(--font-sans)", overflow: "auto" }}>
      <div className="r-mono" style={{ fontSize: 11, color: "var(--accent-bright)", letterSpacing: "0.16em" }}>RUPEE · DESIGN SYSTEM</div>
      <h1 style={{ fontSize: 36, fontWeight: 600, color: "var(--text)", margin: "8px 0 4px", letterSpacing: -0.02 }}>
        Calm, bold, useful.
      </h1>
      <div style={{ color: "var(--muted)", fontSize: 14, lineHeight: 1.55, marginBottom: 24, maxWidth: 420, textWrap: "pretty" }}>
        Teenage-Engineering-industrial labels meet GeexArts-statement amounts.
        Single saffron accent. Mono for metadata + tabular figures. Bold geometric sans for everything else.
      </div>

      <div className="r-rule" style={{ marginBottom: 20 }} />

      {/* Tile examples */}
      <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: 16 }}>
        <Tile label="HERO AMOUNT" sub="Statement weight · 64px+">
          <HeroAmount value={28420} />
        </Tile>
        <Tile label="TABULAR" sub="JetBrains Mono · 11–13px">
          <div className="r-mono" style={{ fontSize: 13, color: "var(--text)", lineHeight: 1.5 }}>
            ₹1,00,000 · ↓<br />₹2,840 · ↑<br />2025-05-22 · 18:42
          </div>
        </Tile>
        <Tile label="EYEBROW" sub="Mono caps · 10px · 12% tracking">
          <div className="r-mono" style={{ fontSize: 10, color: "var(--muted)", letterSpacing: "0.12em" }}>03 / CATEGORIES · 6/6</div>
        </Tile>
        <Tile label="CONFIDENCE" sub="Stripe · 3px · color-coded">
          <div style={{ display: "flex", flexDirection: "column", gap: 4 }}>
            {[
              { c: "var(--faint)", l: "auto-confirmed" },
              { c: "var(--good)",  l: "verified by you" },
              { c: "var(--accent)", l: "suggested" },
            ].map((r) => (
              <div key={r.l} style={{ display: "flex", alignItems: "center", gap: 8 }}>
                <span style={{ width: 3, height: 16, background: r.c }} />
                <span style={{ fontSize: 12, color: "var(--text)" }}>{r.l}</span>
              </div>
            ))}
          </div>
        </Tile>
        <Tile label="REASON CHIPS" sub="Inbox · tappable · 7 types">
          <div style={{ display: "flex", flexWrap: "wrap", gap: 4 }}>
            {Object.keys(REASON_LABELS).slice(0, 4).map((k) => (
              <span key={k} className="r-reason r-rcm-icon" style={{ background: "color-mix(in oklch, var(--accent) 14%, transparent)", color: "var(--accent-bright)" }}>
                <ReasonIcon reason={k} /> {REASON_LABELS[k].label}
              </span>
            ))}
          </div>
        </Tile>
        <Tile label="MODULE" sub="Numbered section header">
          <div style={{ borderTop: "1px solid var(--divider)", paddingTop: 8, display: "flex", alignItems: "baseline" }}>
            <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", marginRight: 10 }}>03 /</span>
            <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)", letterSpacing: "0.12em", textTransform: "uppercase", flex: 1 }}>Categories</span>
            <span className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.1em" }}>EDIT</span>
          </div>
        </Tile>
      </div>

      <div className="r-rule" style={{ margin: "24px 0 16px" }} />

      <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center", flexWrap: "wrap", gap: 16 }}>
        <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE / FOUNDATIONS · v0.4</div>
        <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>USE TWEAKS PANEL TO TRY VARIANTS →</div>
      </div>
    </div>
  );
}

function Tile({ label, sub, children }) {
  return (
    <div style={{ padding: 14, background: "var(--surface)", borderRadius: 6, position: "relative", minHeight: 120, display: "flex", flexDirection: "column" }}>
      <div className="r-mono" style={{ fontSize: 9, color: "var(--accent-bright)", letterSpacing: "0.12em", marginBottom: 2 }}>{label}</div>
      <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.04em", marginBottom: 10 }}>{sub}</div>
      <div style={{ flex: 1, display: "flex", alignItems: "center" }}>{children}</div>
    </div>
  );
}

function PaletteCard({ accent }) {
  const a = ACCENT_BY_C[accent] || ACCENTS[0];
  return (
    <div className="r-shell" style={{ padding: 28, overflow: "auto" }}>
      <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.16em" }}>00 · PALETTE</div>
      <h2 style={{ fontSize: 28, fontWeight: 600, color: "var(--text)", margin: "6px 0 18px", letterSpacing: -0.02 }}>One accent. Many surfaces.</h2>

      <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em", marginBottom: 8 }}>ACCENT · {a.name.toUpperCase()}</div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(4,1fr)", gap: 4, marginBottom: 18 }}>
        {[a.c, a.bright, "oklch(0.55 0.16 50)", "oklch(0.42 0.12 50)"].map((c, i) => (
          <div key={i} style={{ aspectRatio: "1/1.1", background: c, borderRadius: 4 }} />
        ))}
      </div>

      <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em", marginBottom: 8 }}>NEUTRALS · WARM</div>
      <div style={{ display: "grid", gridTemplateColumns: "repeat(7,1fr)", gap: 3, marginBottom: 24 }}>
        {[
          "var(--bg-deep)", "var(--bg)", "var(--surface)", "var(--surface-2)", "var(--surface-3)",
          "var(--divider)", "var(--hairline)",
        ].map((c) => <div key={c} style={{ aspectRatio: "1/1.4", background: c, borderRadius: 3 }} />)}
        {[
          "var(--faint)", "var(--dim)", "var(--muted)",
          "var(--text)", "var(--good)", "var(--warn)", "var(--bad)",
        ].map((c) => <div key={c} style={{ aspectRatio: "1/1.4", background: c, borderRadius: 3 }} />)}
      </div>

      <div className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em", marginBottom: 10 }}>TYPE · SCALE</div>
      <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
        {[
          { n: "Display", size: 48, w: 600, sample: "₹28,420" },
          { n: "Headline", size: 26, w: 600, sample: "Transactions" },
          { n: "Title",   size: 18, w: 600, sample: "Blue Tokai" },
          { n: "Body",    size: 14, w: 400, sample: "Calm, bold, useful every day." },
          { n: "Eyebrow", size: 10, w: 500, sample: "RUPEE / 01 · MAY 2026", mono: true },
        ].map((r) => (
          <div key={r.n} style={{ display: "flex", alignItems: "baseline", padding: "10px 0", borderTop: "1px solid var(--divider)" }}>
            <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.1em", width: 70 }}>{r.n.toUpperCase()}</span>
            <span style={{
              fontFamily: r.mono ? "var(--font-mono)" : "var(--font-sans)",
              fontSize: r.size, fontWeight: r.w, color: "var(--text)",
              letterSpacing: r.size > 30 ? -0.02 : 0,
              flex: 1, lineHeight: 1.1,
            }}>{r.sample}</span>
            <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)" }}>{r.size}px</span>
          </div>
        ))}
      </div>

      <div style={{ marginTop: 20, padding: "10px 12px", border: "1px dashed var(--hairline)", borderRadius: 4 }}>
        <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.12em" }}>FONTS</div>
        <div className="r-mono" style={{ fontSize: 11, color: "var(--text)", marginTop: 4 }}>Space Grotesk · JetBrains Mono</div>
      </div>
    </div>
  );
}

// Smaller light-mode recap — just the category screen, recoloured
function LightRecap() {
  return (
    <StoryShell idx={2} total={7} bg="var(--bg)" color="var(--text)" title="CATEGORY · LIGHT">
      <div style={{ position: "absolute", inset: 0, padding: "56px 28px 0", display: "flex", flexDirection: "column" }}>
        <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)", letterSpacing: "0.16em" }}>YOUR FAVOURITE LINE-ITEM</span>
        <div style={{ marginTop: 32, display: "flex", alignItems: "baseline", gap: 14 }}>
          <div style={{
            width: 80, height: 80, background: "oklch(0.65 0.16 30)",
            borderRadius: 8, display: "flex", alignItems: "center", justifyContent: "center",
            fontFamily: "var(--font-mono)", fontWeight: 700, fontSize: 48, color: "white",
          }}>F</div>
        </div>
        <h1 style={{
          fontSize: 64, fontWeight: 600, lineHeight: 0.95, letterSpacing: -0.03,
          margin: "20px 0 0", color: "var(--text)",
        }}>
          Food.<br /><span style={{ color: "var(--accent)" }}>Always food.</span>
        </h1>
        <div style={{ display: "flex", alignItems: "baseline", gap: 12, marginTop: 24 }}>
          <span style={{ fontSize: 48, fontWeight: 600, fontVariantNumeric: "tabular-nums", letterSpacing: -0.02 }}>
            <span style={{ fontSize: 24, color: "var(--muted)", marginRight: 2 }}>₹</span>11,420
          </span>
          <span className="r-mono" style={{ fontSize: 11, color: "var(--muted)" }}>31% OF MONTH</span>
        </div>
        <div style={{ marginTop: 28, display: "flex", flexDirection: "column", gap: 8 }}>
          {[
            { l: "Food", v: 100, on: true },
            { l: "Bills", v: 68 },
            { l: "Shopping", v: 51 },
            { l: "Groceries", v: 37 },
            { l: "Transport", v: 24 },
          ].map((c) => (
            <div key={c.l} style={{ display: "flex", alignItems: "center", gap: 10, opacity: c.on ? 1 : 0.7 }}>
              <span className="r-mono" style={{ width: 80, fontSize: 10, letterSpacing: "0.06em", color: "var(--muted)" }}>{c.l.toUpperCase()}</span>
              <div style={{ flex: 1, height: 14, borderRadius: 2, background: "var(--surface-2)", overflow: "hidden" }}>
                <div style={{ width: `${c.v}%`, height: "100%", background: c.on ? "var(--accent)" : "var(--surface-3)" }} />
              </div>
            </div>
          ))}
        </div>
        <div style={{ flex: 1 }} />
        <div className="r-mono" style={{ fontSize: 10, color: "var(--muted)", marginBottom: 40, letterSpacing: "0.08em" }}>
          IF FOOD WERE A SUBSCRIPTION, IT'D BE ₹378/DAY.
        </div>
      </div>
    </StoryShell>
  );
}

ReactDOM.createRoot(document.getElementById("root")).render(<App />);
