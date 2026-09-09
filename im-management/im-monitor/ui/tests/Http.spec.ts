import { describe, expect, it, vi } from "vitest";
import { notifyHttpFailure, unwrapHttpResult } from "../src/api/http";

describe("monitor HTTP result boundary", () => {
  it("unwraps successful HttpResult data", () => {
    expect(
      unwrapHttpResult({ code: 0, msg: "success", data: { count: 1 } }),
    ).toEqual({ count: 1 });
  });

  it("publishes authentication and authorization failures", () => {
    const unauthorized = vi.fn();
    const forbidden = vi.fn();
    window.addEventListener("iam:unauthorized", unauthorized, { once: true });
    window.addEventListener("monitor:forbidden", forbidden, { once: true });

    notifyHttpFailure(10001);
    notifyHttpFailure(10002);

    expect(unauthorized).toHaveBeenCalledOnce();
    expect(forbidden).toHaveBeenCalledOnce();
  });
});
