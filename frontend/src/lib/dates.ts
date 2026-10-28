// Pure calendar helpers — no React imports here (see AGENTS.md). Pages pass an ISO day in, so the
// agenda and the proventos window are deterministic under test; only `todayIso` reads the clock.

const ISO_DATE = /^(\d{4})-(\d{2})-(\d{2})$/;

// The browser's local calendar day. Building the string from the local fields keeps a 23:30 session
// from jumping to tomorrow's date through UTC.
export function todayIso(now: Date = new Date()): string {
  const year = String(now.getFullYear()).padStart(4, "0");
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

// The `YYYY-MM` period an ISO day belongs to; a non-ISO input comes back untouched.
export function monthKey(iso: string): string {
  return ISO_DATE.test(iso) ? iso.slice(0, 7) : iso;
}

// The `YYYY-MM` month before the one `iso` belongs to; January walks to the previous December.
export function previousMonthKey(iso: string): string {
  const key = monthKey(iso);
  const match = /^(\d{4})-(\d{2})$/.exec(key);
  if (!match) return key;
  const previous = new Date(Date.UTC(Number(match[1]), Number(match[2]) - 2, 1));
  return `${previous.getUTCFullYear()}-${String(previous.getUTCMonth() + 1).padStart(2, "0")}`;
}

export function addDays(iso: string, days: number): string {
  const match = ISO_DATE.exec(iso);
  if (!match) return iso;
  const [, year, month, day] = match;
  const shifted = new Date(Date.UTC(Number(year), Number(month) - 1, Number(day) + days));
  return todayIso(new Date(shifted.getUTCFullYear(), shifted.getUTCMonth(), shifted.getUTCDate()));
}

// Whole days from `from` to `to`; negative when `to` is in the past.
export function daysBetween(from: string, to: string): number {
  const a = ISO_DATE.exec(from);
  const b = ISO_DATE.exec(to);
  if (!a || !b) return 0;
  const start = Date.UTC(Number(a[1]), Number(a[2]) - 1, Number(a[3]));
  const end = Date.UTC(Number(b[1]), Number(b[2]) - 1, Number(b[3]));
  return Math.round((end - start) / 86_400_000);
}
