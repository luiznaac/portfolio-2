import { cn } from "../lib/cn.ts";
import { formatNumber, formatSignedValue } from "../lib/format.ts";
import { type Tone, toneClass } from "../lib/tones.ts";

// The token vocabulary lives in lib/tones.ts; Num is its numeric consumer. `Num` keeps the narrower
// name because a caller says "this number's tone", not "this token's tone".
export type NumTone = Tone;

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
      className={cn(
        "font-mono text-num tabular-nums whitespace-nowrap",
        toneClass(tone),
        className,
      )}
    >
      {SIGNED_TONES.has(tone) ? formatSignedValue(value, format) : format(value)}
    </span>
  );
}
