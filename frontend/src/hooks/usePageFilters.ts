import { useState } from "react";
import {
  type FilterBarField,
  type FilterValues,
  clearFilterValues,
  emptyFilterValues,
  setFilterValue,
} from "../lib/filters.ts";

// Page-scoped filter state (#367): the page owns it for the life of its route element, so a keystroke
// re-renders without touching the URL and leaving the page drops the narrowing with the component.
export function usePageFilters(fields: readonly FilterBarField[]) {
  const [values, setValues] = useState<FilterValues>(() => emptyFilterValues(fields));

  function setFilter(key: string, value: string) {
    setValues((current) => setFilterValue(current, key, value));
  }

  function clear() {
    setValues(clearFilterValues);
  }

  return { values, setFilter, clear };
}
