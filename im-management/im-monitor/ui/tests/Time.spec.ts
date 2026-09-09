import { describe, expect, it } from "vitest";
import { formatTime } from "../src/utils/time";

describe("monitor time formatting", () => {
  it("formats epoch milliseconds in an explicit IANA time zone", () => {
    expect(formatTime(1787446800000, "Asia/Shanghai")).toBe(
      "2026-08-23 09:00:00",
    );
  });

  it("renders missing timestamps consistently", () => {
    expect(formatTime(null, "Asia/Shanghai")).toBe("-");
  });
});
