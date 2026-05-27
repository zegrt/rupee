// Rupee — Inbox screen (review queue)

function InboxRow({ item, reasonMode = "icon", onChipTap }) {
  const reason = REASON_LABELS[item.reason];
  const [popover, setPopover] = React.useState(false);
  return (
    <div style={{ background: "var(--surface)", marginBottom: 10, position: "relative", borderRadius: 10 }}>
      <div style={{ display: "flex", alignItems: "stretch" }}>
        {reasonMode === "stripe" && (
          <div style={{
            width: 3, flexShrink: 0,
            background: item.reason === "DUPLICATE" ? "var(--warn)" : item.reason === "LOW_CONFIDENCE" || item.reason === "AMOUNT_ONLY" ? "var(--bad)" : "var(--accent)",
          }} />
        )}
        <div style={{ flex: 1, padding: "16px 18px 18px" }}>
          {/* top: chip + time */}
          <div style={{ display: "flex", alignItems: "center", marginBottom: 14 }}>
            <button
              className={`r-reason r-rcm-${reasonMode}`}
              onClick={(e) => { e.stopPropagation(); setPopover((p) => !p); if (onChipTap) onChipTap(item.reason); }}
              style={{
                background: reasonMode === "stripe" ? "transparent" : item.reason === "DUPLICATE" ? "color-mix(in oklch, var(--warn) 14%, transparent)" : item.reason === "LOW_CONFIDENCE" || item.reason === "AMOUNT_ONLY" ? "color-mix(in oklch, var(--bad) 14%, transparent)" : "color-mix(in oklch, var(--accent) 14%, transparent)",
                color: item.reason === "DUPLICATE" ? "var(--warn)" : item.reason === "LOW_CONFIDENCE" || item.reason === "AMOUNT_ONLY" ? "var(--bad)" : "var(--accent-bright)",
                padding: reasonMode === "stripe" ? "4px 0" : "4px 8px",
              }}>
              <ReasonIcon reason={item.reason} />
              <span>{reason.label}</span>
              <svg width="8" height="8" viewBox="0 0 8 8" fill="none" style={{ opacity: 0.6 }}>
                <circle cx="4" cy="4" r="3" stroke="currentColor" strokeWidth="1" />
                <path d="M4 2.5v2M4 5.5v.2" stroke="currentColor" strokeWidth="1" strokeLinecap="round" />
              </svg>
            </button>
            <span style={{ flex: 1 }} />
            <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.06em" }}>{item.time}</span>
          </div>

          {/* main row */}
          <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
            <CatBadge id={item.cat || "cash"} size={36} />
            <div style={{ flex: 1, minWidth: 0 }}>
              <div style={{ fontSize: 15, fontWeight: 500, color: "var(--text)" }}>
                {item.merchant || <span style={{ color: "var(--dim)", fontStyle: "italic" }}>unknown merchant</span>}
              </div>
              <div className="r-mono" style={{ fontSize: 10, color: "var(--dim)", marginTop: 2, letterSpacing: "0.04em" }}>
                {item.mode || "?"} · {item.bank} · "{item.raw}"
              </div>
            </div>
            <Amount value={item.amount} size={16} weight={600} />
          </div>

          {/* actions */}
          <div style={{ display: "flex", gap: 8, marginTop: 16 }}>
            <button style={{
              flex: 1, padding: "11px 0", borderRadius: 6, border: 0,
              background: "var(--accent)", color: "var(--accent-on)",
              fontFamily: "var(--font-sans)", fontWeight: 600, fontSize: 13,
              letterSpacing: 0.02,
            }}>Confirm</button>
            <button style={{
              padding: "11px 14px", borderRadius: 6,
              background: "var(--surface-2)", color: "var(--muted)", border: 0,
              fontFamily: "var(--font-sans)", fontWeight: 500, fontSize: 13,
            }}>Edit</button>
            <button style={{
              padding: "11px 12px", borderRadius: 6,
              background: "transparent", color: "var(--dim)", border: "1px solid var(--divider)",
              fontFamily: "var(--font-mono)", fontSize: 13,
            }}>✕</button>
          </div>
        </div>
      </div>

      {/* popover */}
      {popover && (
        <div style={{
          position: "absolute", top: 38, left: 16, right: 16, zIndex: 5,
          background: "var(--surface-3)", color: "var(--text)",
          padding: "12px 14px", borderRadius: 6,
          boxShadow: "0 12px 28px rgba(0,0,0,.4)",
          fontSize: 12, lineHeight: 1.5, textWrap: "pretty",
        }}>
          <div className="r-mono" style={{ fontSize: 9, color: "var(--accent-bright)", letterSpacing: "0.12em", marginBottom: 4 }}>
            WHY THIS WAS FLAGGED
          </div>
          {reason.explain}
          <div style={{ position: "absolute", top: -5, left: 18, width: 10, height: 10, background: "var(--surface-3)", transform: "rotate(45deg)" }} />
        </div>
      )}
    </div>
  );
}

function InboxScreen({ chipMode = "icon" }) {
  return (
    <RupeeFrame activeTab="inbox" inboxCount={SAMPLE_INBOX.length}>
      <div style={{ padding: "16px 20px 0" }}>
        <div style={{ display: "flex", alignItems: "baseline", marginBottom: 4 }}>
          <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE / 02</span>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 10, color: "var(--muted)", letterSpacing: "0.1em" }}>{SAMPLE_INBOX.length} · TO REVIEW</span>
        </div>
        <h1 style={{ fontSize: 26, fontWeight: 600, color: "var(--text)", margin: "4px 0 0", letterSpacing: -0.01 }}>Inbox</h1>
        <p style={{ fontSize: 13, color: "var(--muted)", marginTop: 8, marginBottom: 20, lineHeight: 1.4 }}>
          Transactions Rupee saw but isn't sure about. Confirm to stop seeing this merchant here.
        </p>
      </div>

      {/* filter chips */}
      <div style={{ display: "flex", gap: 6, padding: "0 20px 16px", overflowX: "auto" }}>
        <span className="r-chip solid">All · {SAMPLE_INBOX.length}</span>
        <span className="r-chip">New · 3</span>
        <span className="r-chip">Duplicates · 1</span>
        <span className="r-chip">Low conf · 1</span>
      </div>

      <div style={{ padding: "4px 20px 0", display: "flex", flexDirection: "column", gap: 0 }}>
        {SAMPLE_INBOX.map((item) => <InboxRow key={item.id} item={item} reasonMode={chipMode} />)}
      </div>

      <div style={{ padding: "16px 20px 8px", textAlign: "center" }}>
        <button className="r-btn outline" style={{ padding: "10px 16px", fontSize: 12 }}>
          Mark all as not transactions
        </button>
      </div>

      <div style={{ padding: "8px 20px 16px", display: "flex", justifyContent: "space-between" }}>
        <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)", letterSpacing: "0.1em" }}>SORTED · NEWEST FIRST</span>
        <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)" }}>EOF</span>
      </div>
    </RupeeFrame>
  );
}

// A small focus variant — InboxRow with popover open, for showing the explainer
function InboxFocus({ chipMode = "icon" }) {
  // Just render one row with popover open
  const item = SAMPLE_INBOX[2]; // duplicate
  const item2 = SAMPLE_INBOX[1]; // no merchant
  return (
    <RupeeFrame activeTab="inbox" inboxCount={SAMPLE_INBOX.length}>
      <div style={{ padding: "16px 20px 0" }}>
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.12em" }}>RUPEE / 02 · CHIP TAPPED</span>
        <h1 style={{ fontSize: 26, fontWeight: 600, color: "var(--text)", margin: "4px 0 12px", letterSpacing: -0.01 }}>Inbox</h1>
      </div>
      <div style={{ padding: "4px 20px 12px" }}>
        <InboxRowOpen item={item} chipMode={chipMode} />
        <InboxRow item={item2} reasonMode={chipMode} />
        <InboxRow item={SAMPLE_INBOX[3]} reasonMode={chipMode} />
      </div>
    </RupeeFrame>
  );
}

// InboxRow with the popover forced open (for the focus variant)
function InboxRowOpen({ item, chipMode = "icon" }) {
  const reason = REASON_LABELS[item.reason];
  return (
    <div style={{ background: "var(--surface)", marginBottom: 10, position: "relative", borderRadius: 10 }}>
      <div style={{ padding: "16px 18px 18px" }}>
        <div style={{ display: "flex", alignItems: "center", marginBottom: 14 }}>
          <span
            className={`r-reason r-rcm-${chipMode}`}
            style={{
              background: "color-mix(in oklch, var(--warn) 18%, transparent)",
              color: "var(--warn)",
              outline: "1px solid var(--warn)",
            }}>
            <ReasonIcon reason={item.reason} />
            <span>{reason.label}</span>
          </span>
          <span style={{ flex: 1 }} />
          <span className="r-mono" style={{ fontSize: 9, color: "var(--faint)" }}>{item.time}</span>
        </div>

        {/* expanded popover */}
        <div style={{
          margin: "8px 0 8px",
          background: "var(--surface-3)",
          padding: "14px 14px",
          borderRadius: 6,
          position: "relative",
        }}>
          <span style={{ position: "absolute", top: -5, left: 32, width: 10, height: 10, background: "var(--surface-3)", transform: "rotate(45deg)" }} />
          <div className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.12em", marginBottom: 6 }}>
            WHY THIS WAS FLAGGED
          </div>
          <div style={{ fontSize: 12, color: "var(--text)", lineHeight: 1.5, textWrap: "pretty" }}>
            {reason.explain}
          </div>

          {/* mini "match" card */}
          <div style={{ marginTop: 12, padding: "10px 12px", background: "color-mix(in oklch, var(--bad) 8%, var(--surface))", borderRadius: 5, display: "flex", alignItems: "center", gap: 10 }}>
            <CatBadge id="food" size={26} />
            <div style={{ flex: 1 }}>
              <div style={{ fontSize: 12, fontWeight: 500, color: "var(--text)" }}>Swiggy</div>
              <div className="r-mono" style={{ fontSize: 9, color: "var(--faint)" }}>EXISTING · YESTERDAY 21:08 · ₹612</div>
            </div>
            <button style={{ background: "var(--warn)", color: "var(--accent-on)", border: 0, padding: "6px 10px", borderRadius: 4, fontFamily: "var(--font-sans)", fontWeight: 600, fontSize: 11 }}>Merge</button>
          </div>
        </div>

        <div style={{ display: "flex", alignItems: "center", gap: 12 }}>
          <CatBadge id={item.cat} size={36} />
          <div style={{ flex: 1 }}>
            <div style={{ fontSize: 15, fontWeight: 500, color: "var(--text)" }}>{item.merchant}</div>
            <div className="r-mono" style={{ fontSize: 10, color: "var(--dim)" }}>{item.mode} · {item.bank}</div>
          </div>
          <Amount value={item.amount} size={16} weight={600} />
        </div>

        <div style={{ display: "flex", gap: 6, marginTop: 12 }}>
          <button style={{ flex: 1, padding: "9px 0", borderRadius: 5, border: 0, background: "var(--surface-2)", color: "var(--text)", fontFamily: "var(--font-sans)", fontWeight: 500, fontSize: 12 }}>Keep as new</button>
          <button style={{ padding: "9px 12px", borderRadius: 5, background: "transparent", color: "var(--dim)", border: "1px solid var(--divider)", fontFamily: "var(--font-sans)", fontSize: 12 }}>Dismiss</button>
        </div>
      </div>
    </div>
  );
}

Object.assign(window, { InboxScreen, InboxFocus, InboxRow });
