import { describe, expect, it } from "vitest";
import {
  backendDtoIndex,
  mirrorDrift,
  parseKotlinClasses,
  parseMirrorInterfaces,
  toWireName,
} from "./dtoMirror";

// Expected values are Jackson's own `PropertyNamingStrategies.SNAKE_CASE` output for the backend's
// pinned Jackson version — they are not re-derived from this module.
describe("toWireName", () => {
  it.each([
    ["timestamp", "timestamp"],
    ["serviceName", "service_name"],
    ["isHealthy", "is_healthy"],
    ["confirmedByB3", "confirmed_by_b3"],
    ["value2End", "value2_end"],
    ["URLValue", "urlvalue"],
    ["myURLValue", "my_urlvalue"],
    ["XMLHttpRequest", "xmlhttp_request"],
    ["iPhone", "i_phone"],
    ["exDate", "ex_date"],
  ])("maps the Kotlin property %s to the wire field %s", (property, wire) => {
    expect(toWireName(property)).toBe(wire);
  });
});

describe("parseMirrorInterfaces", () => {
  const source = `
// A commented-out declaration must not be picked up:
// export interface Ghost { phantom: string }
export interface HealthCheckResult {
  service_name: string;
  is_healthy: boolean;
  timestamp: string;
}

/** A derived creation shape keeps its parent's fields. */
export interface Applied extends LegacyAttributionAdd {
  strategy_id: number;
  // the display name
  strategy_name?: string;
  amount: number;
}

export type NotAnInterface = "A" | "B";
`;

  it("reads each exported interface with its declared field names", () => {
    const interfaces = parseMirrorInterfaces(source);
    const health = interfaces.find((entry) => entry.name === "HealthCheckResult");

    expect(health?.fields).toEqual(["service_name", "is_healthy", "timestamp"]);
  });

  it("keeps optional markers out of the field name and ignores commented lines", () => {
    const applied = parseMirrorInterfaces(source).find((entry) => entry.name === "Applied");

    expect(applied?.fields).toEqual(["strategy_id", "strategy_name", "amount"]);
  });

  it("records the interfaces a declaration extends", () => {
    const applied = parseMirrorInterfaces(source).find((entry) => entry.name === "Applied");

    expect(applied?.extends).toEqual(["LegacyAttributionAdd"]);
  });

  it("does not treat a type alias as an interface", () => {
    expect(parseMirrorInterfaces(source).map((entry) => entry.name)).not.toContain(
      "NotAnInterface",
    );
  });
});

describe("parseKotlinClasses", () => {
  it("reads constructor properties and direct-body properties of a data class", () => {
    const source = `
data class Income(
    val id: Int,
    val ticker: String,
    val grossAmount: BigDecimal,
    val indexId: IndexId? = null,
    val entries: List<Entry> = emptyList(),
) {
    val isPaid: Boolean
        get() = false

    fun compute(): Int {
        val local = 2
        return local
    }
}
`;
    const income = parseKotlinClasses(source).find((entry) => entry.name === "Income");

    expect(income?.properties).toEqual([
      "id",
      "ticker",
      "grossAmount",
      "indexId",
      "entries",
      "isPaid",
    ]);
  });

  it("accepts visibility modifiers on a top-level declaration", () => {
    const source = `
private data class TickerClassification(
    val assetClass: AssetClass,
) {
    override fun toString() = "classification"
}
`;
    const parsed = parseKotlinClasses(source).find(
      (entry) => entry.name === "TickerClassification",
    );

    expect(parsed?.properties).toEqual(["assetClass"]);
    expect(parsed?.nested).toBe(false);
  });

  it("merges a sealed base class with the properties of its nested variants", () => {
    const source = `
sealed class Gap(
    open val id: Int,
    open val quantity: BigDecimal,
) {
    abstract val status: GapStatus

    data class Buy(
        override val id: Int,
        override val quantity: BigDecimal,
        val unitPrice: BigDecimal,
    ) : Gap(id, quantity)

    data class Sell(
        override val id: Int,
        override val quantity: BigDecimal,
        val unitPrice: BigDecimal?,
    ) : Gap(id, quantity) {
        val realizedPending: Boolean
            get() = unitPrice == null
    }
}
`;
    const parsed = parseKotlinClasses(source);
    const gap = parsed.find((entry) => entry.name === "Gap");

    expect(gap?.sealed).toBe(true);
    expect(gap?.properties).toEqual(["id", "quantity", "status", "unitPrice", "realizedPending"]);
    expect(parsed.find((entry) => entry.name === "Buy")?.nested).toBe(true);
  });

  it("keeps sealed-interface members and their subtype overrides", () => {
    const source = `
sealed interface ClassOrderPlan {
    val assetClass: AssetClass
    val target: BigDecimal?
}

data class FixedIncomeOrderPlan(
    override val target: BigDecimal?,
    val subClasses: List<SubClassOrderPlan>,
) : ClassOrderPlan {
    override val assetClass: AssetClass = AssetClass.RENDA_FIXA
}
`;
    const parsed = parseKotlinClasses(source);

    expect(parsed.find((entry) => entry.name === "ClassOrderPlan")?.properties).toEqual([
      "assetClass",
      "target",
    ]);
    expect(parsed.find((entry) => entry.name === "FixedIncomeOrderPlan")?.properties).toEqual([
      "target",
      "subClasses",
      "assetClass",
    ]);
  });

  it("does not treat an enum as a DTO", () => {
    const source = `
enum class OrderSide {
    BUY,
    SELL,
}
`;
    expect(parseKotlinClasses(source)).toEqual([]);
  });

  it("ignores declarations inside comments and string literals", () => {
    const source = `
// data class Ghost(val phantom: String)
val text = "data class AlsoGhost(val phantom: String)"
data class Real(val kept: Int)
`;
    expect(parseKotlinClasses(source).map((entry) => entry.name)).toEqual(["Real"]);
  });
});

const mirrorOf = (name: string, fields: string[], extendsNames: string[] = []) => ({
  name,
  fields,
  extends: extendsNames,
});

describe("backendDtoIndex", () => {
  it("prefers a top-level declaration over a nested one with the same name", () => {
    const index = backendDtoIndex([
      { name: "Income", properties: ["grossAmount"], nested: false, sealed: false },
      { name: "Income", properties: ["creation"], nested: true, sealed: false },
    ]);

    expect(index.byName.get("Income")).toEqual(["grossAmount"]);
    expect(index.ambiguous.size).toBe(0);
  });

  it("falls back to a single nested declaration", () => {
    const index = backendDtoIndex([
      { name: "FixedRateBondCreation", properties: ["name"], nested: true, sealed: false },
    ]);

    expect(index.byName.get("FixedRateBondCreation")).toEqual(["name"]);
  });

  it("marks a name declared twice at the same level as ambiguous", () => {
    const index = backendDtoIndex([
      { name: "Allocation", properties: ["a"], nested: false, sealed: false },
      { name: "Allocation", properties: ["b"], nested: false, sealed: false },
    ]);

    expect(index.ambiguous.has("Allocation")).toBe(true);
    expect(index.byName.has("Allocation")).toBe(false);
  });
});

describe("mirrorDrift", () => {
  const backend = backendDtoIndex([
    {
      name: "HealthCheckResult",
      properties: ["serviceName", "isHealthy"],
      nested: false,
      sealed: false,
    },
    { name: "EditionDetail", properties: ["referenceMonth"], nested: false, sealed: false },
    { name: "Trade", properties: ["unitPrice", "absorbedQuantity"], nested: false, sealed: false },
  ]);

  it("reports no drift when the wire names line up", () => {
    const mirror = [mirrorOf("HealthCheckResult", ["service_name", "is_healthy"])];

    expect(mirrorDrift(mirror, backend, {})).toEqual([]);
  });

  it("reports a backend field the mirror does not declare", () => {
    const mirror = [mirrorOf("HealthCheckResult", ["service_name"])];
    const drifts = mirrorDrift(mirror, backend, {});

    expect(drifts.map((drift) => [drift.type, drift.mirror])).toContainEqual([
      "missing-field",
      "HealthCheckResult",
    ]);
    expect(drifts.find((drift) => drift.type === "missing-field")?.detail).toContain("is_healthy");
  });

  it("reports a mirrored field the backend never serialises", () => {
    const mirror = [mirrorOf("HealthCheckResult", ["service_name", "is_healthy", "name"])];
    const drifts = mirrorDrift(mirror, backend, {});

    expect(drifts.map((drift) => [drift.type, drift.detail])).toContainEqual([
      "extra-field",
      expect.stringContaining("name"),
    ]);
  });

  it("fails a mirrored interface with no backend DTO and says where to map it", () => {
    const mirror = [mirrorOf("Ghost", ["phantom"])];
    const drifts = mirrorDrift(mirror, backend, {});

    expect(drifts).toEqual([expect.objectContaining({ type: "no-backend", mirror: "Ghost" })]);
    expect(drifts[0].detail).toContain("exception table");
  });

  it("follows the mapped backend name", () => {
    const mirror = [mirrorOf("Edition", ["reference_month"])];

    expect(mirrorDrift(mirror, backend, { Edition: "EditionDetail" })).toEqual([]);
  });

  it("flags a stale exception-map entry", () => {
    const mirror = [mirrorOf("HealthCheckResult", ["service_name", "is_healthy"])];
    const drifts = mirrorDrift(mirror, backend, { Removed: "EditionDetail" });

    expect(drifts).toEqual([
      expect.objectContaining({ type: "stale-exception", mirror: "Removed" }),
    ]);
  });

  it("flags an ambiguous backend name instead of picking one", () => {
    const ambiguous = backendDtoIndex([
      { name: "Allocation", properties: ["a"], nested: false, sealed: false },
      { name: "Allocation", properties: ["b"], nested: false, sealed: false },
    ]);
    const drifts = mirrorDrift([mirrorOf("Allocation", ["a"])], ambiguous, {});

    expect(drifts).toEqual([
      expect.objectContaining({ type: "ambiguous-backend", mirror: "Allocation" }),
    ]);
  });

  it("flattens an interface's extends chain", () => {
    const mirror = [mirrorOf("Applied", ["strategy_name"], ["Add"]), mirrorOf("Add", ["ticker"])];
    const backendWithParent = backendDtoIndex([
      { name: "Applied", properties: ["ticker", "strategyName"], nested: false, sealed: false },
      { name: "Add", properties: ["ticker"], nested: false, sealed: false },
    ]);

    expect(mirrorDrift(mirror, backendWithParent, {})).toEqual([]);
  });
});
