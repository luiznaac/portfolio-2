import type { MovementsImportResult } from "../api/types.ts";
import { formatLocalDateTime } from "./format.ts";

// What one Movimentações upload did. An all-zero result is a legitimate idempotent re-upload, not a
// silent success: when the file's hash was already imported its date is named; a fresh all-zero
// file (every row malformed) still says nothing new landed, without claiming an earlier import.
export function movementsImportSummary(result: MovementsImportResult): string {
  const nothingNew =
    result.trades === 0 && result.incomes === 0 && result.corporate_actions.length === 0;

  if (nothingNew) {
    return result.reuploaded && result.trade_import
      ? `Arquivo já importado em ${formatLocalDateTime(result.trade_import.created_at)} — nenhum lançamento novo.`
      : "Nenhum lançamento novo.";
  }

  return `${result.trades} negócio(s), ${result.incomes} provento(s) e ${result.corporate_actions.length} evento(s) processado(s).`;
}
