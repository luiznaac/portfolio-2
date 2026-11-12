import { cn } from "../lib/cn.ts";
import { formatNumber, formatSignedValue } from "../lib/format.ts";

export type NumTone = "default" | "muted" | "deficit" | "surplus" | "return-up" | "return-down";

// The one place a numeric read maps its meaning to a token — callers name the role, never a colour.
// Deficit/short is blue, surplus/attention amber, return+ green and return− red; the sign is text,
// so colour never carries meaning alone.
const TONES: Record<NumTone, string> = {
  default: "text-text",
  muted: "text-muted",
  deficit: "text-deficit",
  surplus: "text-surplus",
  "return-up": "text-return-up",
  "return-down": "text-return-down",
};

const SIGNED_TONES: ReadonlySet<NumTone> = new Set([
  "deficit",
  "surplus",
  "return-up",
  "return-down",
]);

// Tabular mono numerals at the console's 11,5 px numeral size. A tonal read always carries its sign;
// an unsigned quantity uses the default/muted tone, so a tonal number can never decode by colour
// alone.
export function Num({
  value,
  format = formatNumber,
  tone = "default",
  testId = "num",
  className,
}: {
  value: number;
  format?: (value: number) => string;
  tone?: NumTone;
  testId?: string;
  className?: string;
}) {
  return (
    <span
      data-testid={testId}
      data-tone={tone}
      className={cn("font-mono text-num tabular-nums whitespace-nowrap", TONES[tone], className)}
    >
      {SIGNED_TONES.has(tone) ? formatSignedValue(value, format) : format(value)}
    </span>
  );
}
