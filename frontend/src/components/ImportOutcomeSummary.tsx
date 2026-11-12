// The apply's outcome, written once: what entered the ledger, what was a duplicate, and what
// stayed behind as the retry batch. Shared by the session's applied panel and the landing banner.
export function ImportOutcomeSummary({
  applied,
  rejected,
  skippedDuplicate,
}: {
  applied: number;
  rejected: number;
  skippedDuplicate: number;
}) {
  return (
    <div className="space-y-1">
      <p>
        <b>{applied}</b> aplicada(s) · <b>{rejected}</b> rejeitada(s) · <b>{skippedDuplicate}</b>{" "}
        duplicada(s) ignorada(s).
      </p>
      {rejected > 0 ? (
        <p className="text-tax">{rejected} linha(s) rejeitada(s) ficaram no lote de reenvio.</p>
      ) : null}
    </div>
  );
}
