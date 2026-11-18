import { describe, expect, it } from "vitest";
import { formatTime, parseMilliseconds } from "../src/utils/time";

describe("ops time formatting", () => {
  it("formats epoch milliseconds in an explicit IANA time zone", () => {
    expect(formatTime("1787446800000", "Asia/Shanghai")).toBe(
      "2026-08-23 09:00:00",
    );
  });

  it("renders missing timestamps consistently", () => {
    expect(formatTime(null, "Asia/Shanghai")).toBe("-");
  });
});

 it("validates wire milliseconds before conversion", () => {
  expect(parseMilliseconds("1001")).toBe(1001);
  for (const value of ["9007199254740992", "1.5", "", "1e3", "x"]) {
    expect(parseMilliseconds(value)).toBeNull();
    expect(formatTime(value, "Asia/Shanghai")).toBe("-");
  }
  expect(parseMilliseconds(null)).toBeNull();
});
