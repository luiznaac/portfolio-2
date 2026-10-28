import { useState } from "react";
import type { Month } from "../api/types.ts";
import { defaultClosedMonthId } from "../lib/month.ts";

// The history screens' shared month selector: the newest closed month until the owner picks another.
// The selection is the id, never a copy of the month, so a refreshed list keeps the choice coherent.
export function useClosedMonthSelection(months: Month[]) {
  const [selectedMonthId, setSelectedMonthId] = useState<number | null>(null);

  return {
    monthId: selectedMonthId ?? defaultClosedMonthId(months),
    select: setSelectedMonthId,
  };
}
