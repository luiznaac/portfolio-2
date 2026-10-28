import { describe, expect, it } from "vitest";
import { cn } from "./cn.ts";

describe("cn", () => {
  it("joins truthy fragments with a space", () => {
    expect(cn("a", "b")).toBe("a b");
  });

  it("drops false, null and undefined fragments", () => {
    expect(cn("a", false, null, undefined, "b")).toBe("a b");
  });

  it("returns an empty string when nothing is truthy", () => {
    expect(cn(false, undefined)).toBe("");
  });
});
