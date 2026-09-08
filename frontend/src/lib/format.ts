const rtf = new Intl.RelativeTimeFormat("pt-BR", { numeric: "auto" });

export function fromNow(iso: string): string {
  const diffMs = new Date(iso).getTime() - Date.now();
  const abs = Math.abs(diffMs);
  const min = 60_000;
  const hour = 60 * min;
  const day = 24 * hour;

  if (abs < hour) return rtf.format(Math.round(diffMs / min), "minute");
  if (abs < day) return rtf.format(Math.round(diffMs / hour), "hour");
  if (abs < 30 * day) return rtf.format(Math.round(diffMs / day), "day");
  return rtf.format(Math.round(diffMs / (30 * day)), "month");
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("pt-BR", { dateStyle: "medium" });
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("pt-BR", {
    dateStyle: "medium",
    timeStyle: "short",
  });
}

/** ISO-8601 period ("P2Y", "P1Y6M") -> "2 anos", "1 ano e 6 meses". */
export function formatPeriod(iso: string): string {
  const m = /^P(?:(\d+)Y)?(?:(\d+)M)?(?:(\d+)D)?$/.exec(iso);
  if (!m) return iso;
  const [, y, mo, d] = m;
  const parts: string[] = [];
  if (y) parts.push(`${y} ${+y === 1 ? "ano" : "anos"}`);
  if (mo) parts.push(`${mo} ${+mo === 1 ? "mês" : "meses"}`);
  if (d) parts.push(`${d} ${+d === 1 ? "dia" : "dias"}`);
  return parts.length ? parts.join(" e ") : iso;
}
