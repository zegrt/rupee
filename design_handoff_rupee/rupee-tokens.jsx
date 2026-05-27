// Rupee — shared helpers, icons, badges, sample data

// Indian comma grouping: 1,00,000 not 100,000
function formatINR(n, { withSymbol = true, decimals = 0 } = {}) {
  const abs = Math.abs(n);
  const fixed = abs.toFixed(decimals);
  const [intPart, decPart] = fixed.split(".");
  let formatted;
  if (intPart.length <= 3) {
    formatted = intPart;
  } else {
    const last3 = intPart.slice(-3);
    const rest = intPart.slice(0, -3);
    formatted = rest.replace(/\B(?=(\d{2})+(?!\d))/g, ",") + "," + last3;
  }
  if (decPart) formatted += "." + decPart;
  const sign = n < 0 ? "−" : "";
  return (sign + (withSymbol ? "₹" : "") + formatted);
}

// Display amount with the ₹ as a smaller prefix and tabular numerals
function Amount({ value, size = 16, weight = 600, color, sign, dim }) {
  const positive = value >= 0;
  const signChar = sign === true ? (positive ? "+" : "−") : (sign === "-" || value < 0 ? "−" : "");
  return (
    <span style={{
      fontFamily: "var(--font-sans)", fontWeight: weight, color: color || (dim ? "var(--muted)" : "var(--text)"),
      fontSize: size, fontVariantNumeric: "tabular-nums", letterSpacing: -0.01,
      whiteSpace: "nowrap", lineHeight: 1,
    }}>
      {signChar}
      <span style={{ fontSize: size * 0.62, marginRight: size * 0.06, opacity: 0.75, fontWeight: 500 }}>₹</span>
      {formatINR(Math.abs(value), { withSymbol: false })}
    </span>
  );
}

// HeroAmount — display weight for budget hero
function HeroAmount({ value, color }) {
  return (
    <div style={{
      fontFamily: "var(--font-sans)", fontWeight: 600,
      fontSize: 64, lineHeight: 0.95, letterSpacing: -0.04,
      color: color || "var(--text)", fontVariantNumeric: "tabular-nums",
      display: "flex", alignItems: "flex-start", gap: 4,
    }}>
      <span style={{ fontSize: 28, marginTop: 8, fontWeight: 500, opacity: 0.8 }}>₹</span>
      <span>{formatINR(Math.abs(value), { withSymbol: false })}</span>
    </div>
  );
}

// Category — letter monogram in a tinted square
const CATEGORIES = {
  food:    { label: "Food",        mono: "F", c: "oklch(0.72 0.16 30)"  },
  transport: { label: "Transport", mono: "T", c: "oklch(0.74 0.15 220)" },
  shop:    { label: "Shopping",    mono: "S", c: "oklch(0.74 0.16 330)" },
  bills:   { label: "Bills",       mono: "B", c: "oklch(0.74 0.14 80)"  },
  ent:     { label: "Entertainment", mono: "E", c: "oklch(0.72 0.16 285)" },
  health:  { label: "Health",      mono: "H", c: "oklch(0.74 0.14 165)" },
  travel:  { label: "Travel",      mono: "V", c: "oklch(0.74 0.15 200)" },
  groceries: { label: "Groceries", mono: "G", c: "oklch(0.74 0.14 130)" },
  cash:    { label: "Cash",        mono: "₹", c: "oklch(0.70 0.05 80)"  },
  income:  { label: "Income",      mono: "↓", c: "oklch(0.78 0.13 165)" },
  emi:     { label: "EMI",         mono: "/", c: "oklch(0.62 0.04 80)"  },
  rent:    { label: "Rent",        mono: "R", c: "oklch(0.72 0.06 80)"  },
};

function CatBadge({ id, size = 32 }) {
  const cat = CATEGORIES[id] || CATEGORIES.cash;
  return (
    <div className="r-cat" style={{
      width: size, height: size,
      background: `color-mix(in oklch, ${cat.c} 22%, var(--surface-2))`,
      color: cat.c, fontSize: size * 0.38,
    }}>{cat.mono}</div>
  );
}

// Mode icon — UPI, card, cash, transfer
function ModeIcon({ mode, size = 12 }) {
  const s = size;
  switch (mode) {
    case "UPI":
      return <svg width={s} height={s} viewBox="0 0 12 12" fill="none"><path d="M2 6l3-4v3h5v2H5v3z" stroke="currentColor" strokeWidth="1.2" strokeLinejoin="round" /></svg>;
    case "CARD":
      return <svg width={s+2} height={s} viewBox="0 0 14 10" fill="none"><rect x="0.6" y="0.6" width="12.8" height="8.8" rx="1" stroke="currentColor" strokeWidth="1.1"/><path d="M1 4h12" stroke="currentColor" strokeWidth="1.1"/></svg>;
    case "CASH":
      return <svg width={s+2} height={s} viewBox="0 0 14 10" fill="none"><rect x="0.6" y="0.6" width="12.8" height="8.8" rx="0.6" stroke="currentColor" strokeWidth="1.1"/><circle cx="7" cy="5" r="1.8" stroke="currentColor" strokeWidth="1.1"/></svg>;
    case "BANK":
      return <svg width={s+2} height={s} viewBox="0 0 14 10" fill="none"><path d="M7 1l6 2.5H1zM2 4v5M7 4v5M12 4v5M1 9.4h12" stroke="currentColor" strokeWidth="1.1"/></svg>;
    case "ATM":
      return <svg width={s} height={s} viewBox="0 0 12 12" fill="none"><rect x="1.6" y="1.6" width="8.8" height="8.8" rx="1" stroke="currentColor" strokeWidth="1.1"/><path d="M4 5h4M4 7h2" stroke="currentColor" strokeWidth="1.1"/></svg>;
    default: return null;
  }
}

// Bank monogram — colored square, two-letter mark
const BANKS = {
  ICICI: { mono: "I",  c: "oklch(0.62 0.18 40)"  },
  HDFC:  { mono: "H",  c: "oklch(0.55 0.15 250)" },
  AXIS:  { mono: "A",  c: "oklch(0.55 0.18 0)"   },
  SBI:   { mono: "S",  c: "oklch(0.55 0.15 250)" },
  KOTAK: { mono: "K",  c: "oklch(0.55 0.16 20)"  },
  YES:   { mono: "Y",  c: "oklch(0.55 0.16 250)" },
  FED:   { mono: "F",  c: "oklch(0.55 0.16 130)" },
  JUPITER:{ mono: "J", c: "oklch(0.60 0.16 285)" },
  FI:    { mono: "F",  c: "oklch(0.60 0.16 200)" },
  NIYO:  { mono: "N",  c: "oklch(0.60 0.14 100)" },
};

function BankBadge({ id, size = 28 }) {
  const b = BANKS[id] || { mono: "?", c: "var(--surface-3)" };
  return (
    <div style={{
      width: size, height: size, borderRadius: 4,
      background: b.c, color: "white",
      display: "flex", alignItems: "center", justifyContent: "center",
      fontFamily: "var(--font-mono)", fontWeight: 700, fontSize: size * 0.42,
    }}>{b.mono}</div>
  );
}

// Reason icons for inbox chips
function ReasonIcon({ reason }) {
  const c = "currentColor";
  switch (reason) {
    case "AMOUNT_ONLY":
      return <svg width="10" height="10" viewBox="0 0 10 10" fill="none"><path d="M3 2h4M4 2v6M3 8h4" stroke={c} strokeWidth="1.2"/></svg>;
    case "NO_MERCHANT":
      return <svg width="10" height="10" viewBox="0 0 10 10" fill="none"><circle cx="5" cy="5" r="3.5" stroke={c} strokeWidth="1.2"/><path d="M3 5h4" stroke={c} strokeWidth="1.2"/></svg>;
    case "DUPLICATE":
      return <svg width="10" height="10" viewBox="0 0 10 10" fill="none"><rect x="1" y="3" width="6" height="6" stroke={c} strokeWidth="1.1"/><rect x="3" y="1" width="6" height="6" stroke={c} strokeWidth="1.1"/></svg>;
    case "MERCHANT_UNCLEAR":
      return <svg width="10" height="10" viewBox="0 0 10 10" fill="none"><circle cx="5" cy="5" r="3.5" stroke={c} strokeWidth="1.2"/><path d="M5 3.5v2M5 6.5v.5" stroke={c} strokeWidth="1.2" strokeLinecap="round"/></svg>;
    case "LOW_CONFIDENCE":
      return <svg width="10" height="10" viewBox="0 0 10 10" fill="none"><path d="M1 8l4-6 4 6z" stroke={c} strokeWidth="1.1"/><path d="M5 5v1.5" stroke={c} strokeWidth="1.1" strokeLinecap="round"/></svg>;
    case "NEW_MERCHANT":
      return <svg width="10" height="10" viewBox="0 0 10 10" fill="none"><path d="M5 1v8M1 5h8" stroke={c} strokeWidth="1.2" strokeLinecap="round"/></svg>;
    case "FOREIGN":
      return <svg width="10" height="10" viewBox="0 0 10 10" fill="none"><circle cx="5" cy="5" r="3.5" stroke={c} strokeWidth="1.1"/><path d="M1.5 5h7M5 1.5c1.5 1.5 1.5 5.5 0 7M5 1.5c-1.5 1.5-1.5 5.5 0 7" stroke={c} strokeWidth="1.1"/></svg>;
    default: return null;
  }
}

const REASON_LABELS = {
  AMOUNT_ONLY:       { label: "AMOUNT ONLY",      explain: "Notification had a clear amount but no merchant or context. We guessed the category from your history." },
  NO_MERCHANT:       { label: "NO MERCHANT",      explain: "The parser couldn't read a merchant name from this notification. Tap edit to add one." },
  DUPLICATE:         { label: "DUPLICATE?",       explain: "This looks like it might already exist as a transaction 12 minutes earlier. Merge if you agree." },
  MERCHANT_UNCLEAR:  { label: "MERCHANT UNCLEAR", explain: "Merchant string was \"AMZN*PMTS*7HG2\" — we showed our best guess but you should confirm." },
  LOW_CONFIDENCE:    { label: "LOW CONFIDENCE",   explain: "Notification format didn't match any of our known bank templates. Treat with caution." },
  NEW_MERCHANT:      { label: "NEW MERCHANT",     explain: "First time we've seen this merchant. Confirm once and we'll always trust it." },
  FOREIGN:           { label: "FOREIGN CURRENCY", explain: "This charge was in USD — converted at today's rate. The conversion can be off by ~1–2%." },
};

// Eyebrow — module / section label
function Eyebrow({ children, num, action }) {
  return (
    <div style={{ display: "flex", alignItems: "center", gap: 8, marginBottom: 10 }}>
      {num !== undefined && (
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.1em" }}>
          {String(num).padStart(2, "0")}
        </span>
      )}
      <span className="r-eyebrow" style={{ flex: 1 }}>{children}</span>
      {action && <span className="r-mono" style={{ fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.1em", textTransform: "uppercase" }}>{action}</span>}
    </div>
  );
}

// Module corner — TE-style label with module number + name, with a thin top rule
function Module({ num, title, hint, action, children, style }) {
  return (
    <section style={{ padding: "0 20px", ...style }}>
      <div style={{
        display: "flex", alignItems: "flex-end",
        borderTop: "1px solid var(--divider)",
        paddingTop: 12, marginBottom: 14,
      }}>
        <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)", letterSpacing: "0.1em", marginRight: 10 }}>
          {String(num).padStart(2, "0")} /
        </span>
        <span style={{ fontFamily: "var(--font-mono)", fontSize: 11, color: "var(--muted)", letterSpacing: "0.12em", textTransform: "uppercase", flex: 1 }}>{title}</span>
        {hint && <span className="r-mono" style={{ fontSize: 10, color: "var(--faint)" }}>{hint}</span>}
        {action && (
          <span style={{ fontFamily: "var(--font-mono)", fontSize: 10, color: "var(--accent-bright)", letterSpacing: "0.1em", textTransform: "uppercase" }}>
            {action}
          </span>
        )}
      </div>
      {children}
    </section>
  );
}

// Sample transactions
const SAMPLE_TX = [
  { id: "t01", merchant: "Blue Tokai",        cat: "food",      mode: "UPI",  amount: 480,   conf: "auto",    time: "Today · 8:42",     bank: "HDFC", note: "Cold brew + breakfast" },
  { id: "t02", merchant: "Salary · Acme",     cat: "income",    mode: "BANK", amount: 145000, conf: "auto",   time: "Today · 8:00",     bank: "HDFC", isIncome: true },
  { id: "t03", merchant: "Auto · Mahesh",     cat: "transport", mode: "UPI",  amount: 122,   conf: "user",    time: "Today · 9:14",     bank: "ICICI" },
  { id: "t04", merchant: "Swiggy",            cat: "food",      mode: "UPI",  amount: 612,   conf: "auto",    time: "Yesterday · 21:08", bank: "ICICI" },
  { id: "t05", merchant: "Big Bazaar",        cat: "groceries", mode: "CARD", amount: 2840,  conf: "auto",    time: "Yesterday · 19:22", bank: "AXIS" },
  { id: "t06", merchant: "Amazon",            cat: "shop",      mode: "CARD", amount: 1499,  conf: "suggest", time: "Yesterday · 17:10", bank: "AXIS" },
  { id: "t07", merchant: "Airtel Postpaid",   cat: "bills",     mode: "UPI",  amount: 749,   conf: "user",    time: "May 21 · 11:00",   bank: "ICICI" },
  { id: "t08", merchant: "Uber",              cat: "transport", mode: "UPI",  amount: 246,   conf: "auto",    time: "May 21 · 09:40",   bank: "HDFC" },
  { id: "t09", merchant: "Cult.fit",          cat: "health",    mode: "CARD", amount: 1999,  conf: "user",    time: "May 20 · 06:00",   bank: "AXIS" },
  { id: "t10", merchant: "BookMyShow",        cat: "ent",       mode: "CARD", amount: 880,   conf: "auto",    time: "May 19 · 19:30",   bank: "AXIS" },
  { id: "t11", merchant: "Indigo · BLR-DEL",  cat: "travel",    mode: "CARD", amount: 7240,  conf: "user",    time: "May 18 · 11:11",   bank: "AXIS" },
  { id: "t12", merchant: "Café Amudham",      cat: "food",      mode: "CASH", amount: 220,   conf: "user",    time: "May 18 · 08:30" },
];

// Sample inbox items
const SAMPLE_INBOX = [
  { id: "i01", merchant: "Amazon Pay",  cat: "shop",      mode: "UPI",  amount: 1499, reason: "MERCHANT_UNCLEAR", raw: "AMZN*PMTS*7HG2",   bank: "AXIS", time: "11 min ago" },
  { id: "i02", merchant: null,          cat: null,        mode: "BANK", amount: 4200, reason: "NO_MERCHANT",      raw: "NEFT-N237-IMPS",   bank: "ICICI", time: "27 min ago" },
  { id: "i03", merchant: "Swiggy",      cat: "food",      mode: "UPI",  amount: 612,  reason: "DUPLICATE",        raw: "POSSIBLE DUP of t04", bank: "ICICI", time: "1 hr ago" },
  { id: "i04", merchant: "Starbucks LHR", cat: "food",    mode: "CARD", amount: 540,  reason: "FOREIGN",          raw: "£5.20 GBP",         bank: "AXIS", time: "2 hr ago" },
  { id: "i05", merchant: "BESCOM",      cat: "bills",     mode: "UPI",  amount: 1840, reason: "NEW_MERCHANT",     raw: "first sighting",     bank: "HDFC", time: "Yesterday" },
  { id: "i06", merchant: "—",           cat: null,        mode: null,   amount: 350,  reason: "AMOUNT_ONLY",      raw: "Rs.350 debited",     bank: "SBI", time: "Yesterday" },
  { id: "i07", merchant: "GPay",        cat: null,        mode: "UPI",  amount: 100,  reason: "LOW_CONFIDENCE",   raw: "irregular template", bank: "JUPITER", time: "2 days ago" },
];

// Upcoming dues — mixed types
const SAMPLE_DUES = [
  { id: "d01", type: "card", title: "HDFC Credit", sub: "Min ₹2,450", amount: 18420, due: "in 3 days", urgent: true,  bank: "HDFC" },
  { id: "d02", type: "emi",  title: "Macbook EMI",    sub: "10 of 12", amount: 8499,  due: "Jun 5",    urgent: false },
  { id: "d03", type: "sub",  title: "Netflix",        sub: "Premium",  amount: 649,   due: "Jun 8",    urgent: false },
  { id: "d04", type: "sub",  title: "iCloud 200GB",   sub: "monthly",  amount: 75,    due: "Jun 11",   urgent: false },
];

// Category budgets — for the home progress bars
const SAMPLE_BUDGETS = [
  { id: "food",      label: "Food",        spent: 6480,  limit: 8000  },
  { id: "transport", label: "Transport",   spent: 2740,  limit: 3500  },
  { id: "groceries", label: "Groceries",   spent: 4220,  limit: 6000  },
  { id: "shop",      label: "Shopping",    spent: 5850,  limit: 4500, over: true },
  { id: "bills",     label: "Bills",       spent: 7820,  limit: 9000  },
  { id: "ent",       label: "Entertainment", spent: 1340, limit: 2500 },
];

Object.assign(window, {
  formatINR, Amount, HeroAmount,
  CATEGORIES, CatBadge, BANKS, BankBadge,
  ModeIcon, ReasonIcon, REASON_LABELS,
  Eyebrow, Module,
  SAMPLE_TX, SAMPLE_INBOX, SAMPLE_DUES, SAMPLE_BUDGETS,
});
