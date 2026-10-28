// Joins truthy class-name fragments into one className string — the idiom every primitive in the
// token-based layer shares, so it lives in one place. Pure (no React).

export type ClassNamePart = string | false | null | undefined;

export function cn(...parts: ClassNamePart[]): string {
  return parts.filter(Boolean).join(" ");
}
