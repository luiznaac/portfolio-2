// Pure command-palette helpers — no React imports here (see AGENTS.md). The palette is the sidebar's
// nav table flattened into one searchable list, so a route lands there without a second registry.

import type { NavGroup } from "./navigation.ts";

export interface CommandItem {
  to: string;
  label: string;
  group: string;
}

export function commandItems(groups: NavGroup[]): CommandItem[] {
  return groups.flatMap((group) =>
    group.items.map((item) => ({ to: item.to, label: item.label, group: group.label })),
  );
}

// A blank query keeps the whole list (the component trims it for display); otherwise the label and
// the group are both searchable, case-insensitively.
export function filterCommands(items: CommandItem[], query: string): CommandItem[] {
  const needle = query.trim().toLowerCase();
  if (needle.length === 0) return items;
  return items.filter((item) => `${item.label} ${item.group}`.toLowerCase().includes(needle));
}
