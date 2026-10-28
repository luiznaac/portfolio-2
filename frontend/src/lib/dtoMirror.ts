const isUpperCase = (character: string) => character >= "A" && character <= "Z";

/**
 * The wire name of a Kotlin property under the backend's Jackson `SNAKE_CASE` strategy: an
 * underscore goes before an uppercase letter whose predecessor is neither uppercase nor already an
 * underscore (`targetPrice` → `target_price`, `confirmedByB3` → `confirmed_by_b3`, `URLValue` →
 * `urlvalue`).
 */
export function toWireName(property: string): string {
  let wire = "";

  for (let index = 0; index < property.length; index++) {
    const character = property[index];
    const previous = property[index - 1];

    if (index > 0 && isUpperCase(character) && previous !== "_" && !isUpperCase(previous)) {
      wire += "_";
    }
    wire += character.toLowerCase();
  }

  return wire;
}

/** One `export interface` of the frontend DTO mirror, with its direct field names. */
export interface MirroredInterface {
  readonly name: string;
  readonly fields: readonly string[];
  readonly extends: readonly string[];
}

/**
 * Removes line (`//`) and block comments while leaving string literals untouched, so a
 * commented-out declaration can never reach either parser.
 */
function stripComments(source: string): string {
  let clean = "";
  let index = 0;
  let quote: string | null = null;

  while (index < source.length) {
    const character = source[index];
    const next = source[index + 1];

    if (quote) {
      clean += character;
      if (character === "\\") {
        clean += next ?? "";
        index += 2;
        continue;
      }
      if (character === quote) quote = null;
      index += 1;
      continue;
    }

    if (character === '"' || character === "'" || character === "`") {
      quote = character;
      clean += character;
      index += 1;
      continue;
    }
    if (character === "/" && next === "/") {
      while (index < source.length && source[index] !== "\n") index += 1;
      continue;
    }
    if (character === "/" && next === "*") {
      index += 2;
      while (index < source.length && !(source[index] === "*" && source[index + 1] === "/"))
        index += 1;
      index += 2;
      continue;
    }

    clean += character;
    index += 1;
  }

  return clean;
}

/** The index of the delimiter closing the one at `openIndex`, or -1 when it never closes. */
function matchingDelimiter(source: string, openIndex: number, open: string, close: string): number {
  let depth = 0;

  for (let index = openIndex; index < source.length; index++) {
    if (source[index] === open) depth += 1;
    else if (source[index] === close) {
      depth -= 1;
      if (depth === 0) return index;
    }
  }

  return -1;
}

/** The body's members that sit at nesting depth zero, one per top-level line. */
function topLevelLines(body: string): string[] {
  const lines: string[] = [];
  let depth = 0;
  let current = "";

  for (const character of body) {
    if (character === "{") depth += 1;
    else if (character === "}") depth -= 1;

    if (character === "\n" && depth === 0) {
      lines.push(current);
      current = "";
      continue;
    }
    current += character;
  }
  lines.push(current);

  return lines;
}

/** One class declaration of the backend sources, with the property names it serialises. */
export interface KotlinClass {
  readonly name: string;
  readonly properties: readonly string[];
  readonly nested: boolean;
  readonly sealed: boolean;
}

interface KotlinDeclaration {
  readonly name: string;
  readonly start: number;
  readonly depth: number;
  readonly sealed: boolean;
  readonly properties: readonly string[];
  readonly bodyOpen: number;
  readonly bodyClose: number;
}

/**
 * Replaces the contents of string and character literals with spaces, keeping every index intact, so
 * brace depth and declarations are never read out of a literal.
 */
function maskLiterals(source: string): string {
  const masked = source.split("");
  let index = 0;

  while (index < masked.length) {
    if (masked[index] === '"' && masked[index + 1] === '"' && masked[index + 2] === '"') {
      masked[index] = masked[index + 1] = masked[index + 2] = " ";
      index += 3;
      while (
        index < masked.length &&
        !(masked[index] === '"' && masked[index + 1] === '"' && masked[index + 2] === '"')
      ) {
        if (masked[index] !== "\n") masked[index] = " ";
        index += 1;
      }
      for (let quote = 0; quote < 3 && index < masked.length; quote++, index++) masked[index] = " ";
      continue;
    }

    if (masked[index] === '"' || masked[index] === "'") {
      const quote = masked[index];
      masked[index] = " ";
      index += 1;
      while (index < masked.length && masked[index] !== quote) {
        if (masked[index] === "\\") {
          masked[index] = " ";
          if (index + 1 < masked.length) masked[index + 1] = " ";
          index += 2;
          continue;
        }
        if (masked[index] !== "\n") masked[index] = " ";
        index += 1;
      }
      if (index < masked.length) masked[index] = " ";
      index += 1;
      continue;
    }

    index += 1;
  }

  return masked.join("");
}

/** The brace depth just before each character of the source. */
function braceDepths(source: string): number[] {
  const depths = new Array<number>(source.length + 1);
  depths[0] = 0;

  for (let index = 0; index < source.length; index++) {
    const opens = source[index] === "{" ? 1 : 0;
    const closes = source[index] === "}" ? 1 : 0;
    depths[index + 1] = depths[index] + opens - closes;
  }

  return depths;
}

/** Splits on commas (or any separator) that sit outside brackets, angles and parentheses. */
function splitTopLevel(text: string, separator = ","): string[] {
  const parts: string[] = [];
  let depth = 0;
  let quote: string | null = null;
  let current = "";

  for (let index = 0; index < text.length; index++) {
    const character = text[index];

    if (quote) {
      current += character;
      if (character === "\\") current += text[++index] ?? "";
      else if (character === quote) quote = null;
      continue;
    }
    if (character === '"' || character === "'") {
      quote = character;
      current += character;
      continue;
    }

    if ("([{<".includes(character)) depth += 1;
    if (")]}>".includes(character)) depth -= 1;

    if (character === separator && depth === 0) {
      parts.push(current);
      current = "";
      continue;
    }
    current += character;
  }

  if (current.trim()) parts.push(current);

  return parts;
}

/** The `val`/`var` names declared by a primary constructor's parameter list. */
function constructorProperties(parameters: string): string[] {
  return splitTopLevel(parameters)
    .map((parameter) => /\b(?:val|var)\s+([A-Za-z_]\w*)\s*:/.exec(parameter))
    .filter((property): property is RegExpExecArray => property !== null)
    .map((property) => property[1]);
}

/** The skip of a declaration header's type parameters, when it has any. */
function skipTypeParameters(source: string, index: number): number {
  let cursor = index;
  while (cursor < source.length && /\s/.test(source[cursor])) cursor += 1;
  if (source[cursor] !== "<") return cursor;

  let depth = 0;
  while (cursor < source.length) {
    if (source[cursor] === "<") depth += 1;
    else if (source[cursor] === ">") {
      depth -= 1;
      if (depth === 0) return cursor + 1;
    }
    cursor += 1;
  }

  return cursor;
}

const KEYWORD_LOOKBACK = 30;

/** Whether the declaration keyword at `index` is preceded by `modifier` (`sealed`, `enum`, `fun`). */
function hasModifierBefore(source: string, index: number, modifier: string): boolean {
  const before = source.slice(Math.max(0, index - KEYWORD_LOOKBACK), index);
  return new RegExp(`\\b${modifier}\\s+$`).test(before);
}

/**
 * Reads every Kotlin class/interface declaration in the source. A sealed base carries the union of
 * its own properties and those of its direct nested variants, because that is the shape Jackson
 * serialises whichever variant is returned.
 */
export function parseKotlinClasses(source: string): KotlinClass[] {
  const masked = maskLiterals(stripComments(source));
  const depths = braceDepths(masked);
  const declarationPattern = /\b(?:class|interface)\s+([A-Za-z_]\w*)/g;
  const headers: {
    name: string;
    start: number;
    matchEnd: number;
    depth: number;
    sealed: boolean;
  }[] = [];
  const declarations: KotlinDeclaration[] = [];

  for (const match of masked.matchAll(declarationPattern)) {
    const keywordStart = match.index;
    if (
      hasModifierBefore(masked, keywordStart, "enum") ||
      hasModifierBefore(masked, keywordStart, "fun")
    ) {
      continue;
    }

    headers.push({
      name: match[1],
      start: keywordStart,
      matchEnd: match.index + match[0].length,
      depth: depths[keywordStart],
      sealed: hasModifierBefore(masked, keywordStart, "sealed"),
    });
  }

  for (const header of headers) {
    const headerStart = skipTypeParameters(masked, header.matchEnd);

    let headerEnd = headerStart;
    let parameters = "";
    if (masked[headerStart] === "(") {
      const close = matchingDelimiter(masked, headerStart, "(", ")");
      parameters = masked.slice(headerStart + 1, close);
      headerEnd = close + 1;
    }

    const nextPeer = headers.find(
      (peer) => peer.start > header.start && peer.depth <= header.depth,
    );
    const limit = nextPeer?.start ?? masked.length;
    let bodyOpen = -1;
    for (let index = headerEnd; index < limit; index++) {
      if (masked[index] === "{" && depths[index] === header.depth) {
        bodyOpen = index;
        break;
      }
    }

    let bodyProperties: string[] = [];
    let bodyClose = -1;
    if (bodyOpen >= 0) {
      bodyClose = matchingDelimiter(masked, bodyOpen, "{", "}");
      const body = masked.slice(bodyOpen + 1, bodyClose);
      bodyProperties = [...body.matchAll(/\bval\s+([A-Za-z_]\w*)\s*:/g)]
        .filter((property) => depths[bodyOpen + 1 + property.index] === header.depth + 1)
        .map((property) => property[1]);
    }

    declarations.push({
      name: header.name,
      start: header.start,
      depth: header.depth,
      sealed: header.sealed,
      properties: [...constructorProperties(parameters), ...bodyProperties],
      bodyOpen,
      bodyClose,
    });
  }

  return declarations.map((declaration) => {
    const variants = declaration.sealed
      ? declarations.filter(
          (candidate) =>
            candidate.start > declaration.bodyOpen &&
            candidate.start < declaration.bodyClose &&
            candidate.depth === declaration.depth + 1,
        )
      : [];

    return {
      name: declaration.name,
      properties: [
        ...new Set([
          ...declaration.properties,
          ...variants.flatMap((variant) => variant.properties),
        ]),
      ],
      nested: declaration.depth > 0,
      sealed: declaration.sealed,
    };
  });
}

/** Reads every `export interface` of the frontend DTO mirror, in declaration order. */
export function parseMirrorInterfaces(source: string): MirroredInterface[] {
  const clean = stripComments(source);
  const declaration = /export\s+interface\s+([A-Za-z_$][\w$]*)(?:\s+extends\s+([^{]+))?\s*\{/g;
  const interfaces: MirroredInterface[] = [];

  for (const match of clean.matchAll(declaration)) {
    const bodyOpen = clean.indexOf("{", match.index);
    const bodyClose = matchingDelimiter(clean, bodyOpen, "{", "}");
    if (bodyClose < 0) continue;

    const fields = topLevelLines(clean.slice(bodyOpen + 1, bodyClose))
      .map((line) => /^\s*(?:readonly\s+)?([A-Za-z_$][\w$]*)\s*\??\s*:/.exec(line))
      .filter((field): field is RegExpExecArray => field !== null)
      .map((field) => field[1]);

    interfaces.push({
      name: match[1],
      fields,
      extends: (match[2] ?? "")
        .split(",")
        .map((name) => name.trim())
        .filter(Boolean),
    });
  }

  return interfaces;
}

/** Mirror interface name -> the backend type that serialises it, when the names differ. */
export type BackendTypeMap = Readonly<Record<string, string>>;

/** The backend DTO names, resolved deterministically, plus the names that could not be. */
export interface BackendIndex {
  readonly byName: ReadonlyMap<string, readonly string[]>;
  readonly ambiguous: ReadonlySet<string>;
}

/**
 * Indexes the backend declarations by simple name. A top-level declaration wins over a nested one
 * with the same name; two declarations at the same level are ambiguous and never picked silently.
 */
export function backendDtoIndex(classes: readonly KotlinClass[]): BackendIndex {
  const topLevel = new Map<string, string[][]>();
  const nested = new Map<string, string[][]>();

  for (const entry of classes) {
    const target = entry.nested ? nested : topLevel;
    const group = target.get(entry.name) ?? [];
    group.push([...entry.properties]);
    target.set(entry.name, group);
  }

  const byName = new Map<string, readonly string[]>();
  const ambiguous = new Set<string>();

  for (const name of new Set([...topLevel.keys(), ...nested.keys()])) {
    const candidates = topLevel.get(name) ?? nested.get(name) ?? [];
    if (candidates.length === 1) byName.set(name, candidates[0]);
    else ambiguous.add(name);
  }

  return { byName, ambiguous };
}

export type MirrorDriftType =
  | "no-backend"
  | "ambiguous-backend"
  | "missing-field"
  | "extra-field"
  | "stale-exception";

export interface MirrorDrift {
  readonly type: MirrorDriftType;
  readonly mirror: string;
  readonly detail: string;
}

/** The interface's own field names plus every field inherited through `extends`. */
function flattenedFields(
  name: string,
  interfaces: ReadonlyMap<string, MirroredInterface>,
  seen = new Set<string>(),
): string[] {
  const entry = interfaces.get(name);
  if (!entry || seen.has(name)) return [];
  seen.add(name);

  return [
    ...entry.fields,
    ...entry.extends.flatMap((parent) => flattenedFields(parent, interfaces, seen)),
  ];
}

/**
 * Compares every interface of the frontend mirror with the backend DTO that serialises it. An empty
 * result means the mirror is in sync; any entry is a failure the guard reports verbatim.
 */
export function mirrorDrift(
  mirror: readonly MirroredInterface[],
  backend: BackendIndex,
  backendTypes: BackendTypeMap = {},
): MirrorDrift[] {
  const interfaces = new Map(mirror.map((entry) => [entry.name, entry]));
  const drifts: MirrorDrift[] = [];

  for (const name of Object.keys(backendTypes)) {
    if (!interfaces.has(name)) {
      drifts.push({
        type: "stale-exception",
        mirror: name,
        detail: `the exception map names "${name}", which the mirror no longer declares`,
      });
    }
  }

  for (const entry of mirror) {
    const backendName = backendTypes[entry.name] ?? entry.name;

    if (backend.ambiguous.has(backendName)) {
      drifts.push({
        type: "ambiguous-backend",
        mirror: entry.name,
        detail: `"${backendName}" is declared more than once in the backend; make the backend name unique or map this mirror to a distinct backend type`,
      });
      continue;
    }

    const properties = backend.byName.get(backendName);
    if (!properties) {
      drifts.push({
        type: "no-backend",
        mirror: entry.name,
        detail: [
          `no backend DTO named "${backendName}" is serialised; if the backend type is named differently,`,
          `map "${entry.name}" to it in the guard's exception table, otherwise remove the stale mirror`,
        ].join(" "),
      });
      continue;
    }

    const expected = properties.map(toWireName);
    const actual = new Set(flattenedFields(entry.name, interfaces));

    for (const field of expected) {
      if (!actual.has(field)) {
        drifts.push({
          type: "missing-field",
          mirror: entry.name,
          detail: `"${backendName}" serialises "${field}", which the mirror does not declare`,
        });
      }
    }
    for (const field of actual) {
      if (!expected.includes(field)) {
        drifts.push({
          type: "extra-field",
          mirror: entry.name,
          detail: `the mirror declares "${field}", which "${backendName}" does not serialise`,
        });
      }
    }
  }

  return drifts;
}
