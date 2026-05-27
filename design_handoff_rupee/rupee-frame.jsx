// Rupee phone frame — minimal Android shell tuned to the design system.
// No top app bar (screens own their headers); status bar + content + nav.

const RFRAME_W = 380;
const RFRAME_H = 800;

function StatusBar({ time = "9:30" }) {
  return (
    <div className="r-status">
      <span>{time}</span>
      <div className="r-status-icons">
        <svg width="13" height="11" viewBox="0 0 13 11" fill="none">
          <path d="M.5 8.5 6.5 2 12.5 8.5" stroke="currentColor" strokeWidth="1.2" />
        </svg>
        <svg width="14" height="11" viewBox="0 0 14 11" fill="none">
          <rect x="0.6" y="0.6" width="9" height="6.5" rx="0.5" stroke="currentColor" strokeWidth="1.2" />
          <rect x="2" y="2" width="6" height="3.5" fill="currentColor" />
          <rect x="11" y="2.5" width="2.5" height="2.5" rx="0.4" fill="currentColor" />
        </svg>
      </div>
    </div>
  );
}

function GesturePill() {
  return <div className="r-gesture"><div></div></div>;
}

// Bottom nav — 5 tabs, mono labels, top-dot indicator
const NAV_TABS = [
  { id: "home", label: "HOME" },
  { id: "inbox", label: "INBOX" },
  { id: "tx", label: "TX" },
  { id: "cal", label: "CAL" },
  { id: "set", label: "SET" },
];

function NavBar({ active = "home", inboxCount = 0 }) {
  const icon = (id) => {
    const stroke = "currentColor";
    const sw = 1.5;
    switch (id) {
      case "home":
        return (
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <path d="M3 8 L9 3 L15 8 V15 H3 Z" stroke={stroke} strokeWidth={sw} strokeLinejoin="round" />
          </svg>
        );
      case "inbox":
        return (
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <path d="M3 4h12v8a1 1 0 0 1-1 1H4a1 1 0 0 1-1-1z" stroke={stroke} strokeWidth={sw} />
            <path d="M3 9h3l1 2h4l1-2h3" stroke={stroke} strokeWidth={sw} strokeLinejoin="round" />
          </svg>
        );
      case "tx":
        return (
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <path d="M3 5h12M3 9h12M3 13h8" stroke={stroke} strokeWidth={sw} strokeLinecap="round" />
          </svg>
        );
      case "cal":
        return (
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <rect x="3" y="4" width="12" height="11" stroke={stroke} strokeWidth={sw} />
            <path d="M6 2v4M12 2v4M3 8h12" stroke={stroke} strokeWidth={sw} />
          </svg>
        );
      case "set":
        return (
          <svg width="18" height="18" viewBox="0 0 18 18" fill="none">
            <circle cx="9" cy="9" r="2.4" stroke={stroke} strokeWidth={sw} />
            <path d="M9 2v2M9 14v2M2 9h2M14 9h2M4 4l1.4 1.4M12.6 12.6L14 14M4 14l1.4-1.4M12.6 5.4L14 4" stroke={stroke} strokeWidth={sw} strokeLinecap="round"/>
          </svg>
        );
      default: return null;
    }
  };
  return (
    <div className="r-nav">
      {NAV_TABS.map((t) => {
        const isActive = t.id === active;
        return (
          <div key={t.id} className={`r-nav-item ${isActive ? "active" : ""}`}>
            <div className="r-nav-dot" />
            <div style={{ position: "relative" }}>
              {icon(t.id)}
              {t.id === "inbox" && inboxCount > 0 && (
                <div style={{
                  position: "absolute", top: -4, right: -8,
                  background: "var(--accent)", color: "var(--accent-on)",
                  borderRadius: 6, padding: "1px 4px",
                  fontFamily: "var(--font-mono)",
                  fontSize: 8, fontWeight: 600,
                  letterSpacing: 0,
                }}>{inboxCount}</div>
              )}
            </div>
            <span>{t.label}</span>
          </div>
        );
      })}
    </div>
  );
}

// RupeeFrame — wraps any screen content in the standard shell
function RupeeFrame({
  children, theme = "dark", activeTab = "home", inboxCount = 0,
  showNav = true, time = "9:30",
}) {
  const wrap = theme === "light" ? "rupee-light" : "";
  return (
    <div className={`r-shell ${wrap}`} style={{
      display: "flex", flexDirection: "column",
      height: "100%", width: "100%",
    }}>
      <StatusBar time={time} />
      <div className="r-scroll" style={{ flex: 1, position: "relative" }}>
        {children}
      </div>
      {showNav && <NavBar active={activeTab} inboxCount={inboxCount} />}
      <GesturePill />
    </div>
  );
}

Object.assign(window, { RupeeFrame, StatusBar, GesturePill, NavBar, RFRAME_W, RFRAME_H });
