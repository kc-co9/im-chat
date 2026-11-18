import { beforeEach, describe, expect, it, vi } from "vitest";
import {
  cookie,
  http,
  notifyHttpFailure,
  unwrapHttpResult,
} from "../src/api/http";

describe("ops HTTP result boundary", () => {
  beforeEach(() => {
    document.cookie =
      "IM_OPS_IAM_SESSION_XSRF_TOKEN=ops%20csrf; path=/";
  });

  it("submits the application-specific CSRF delivery cookie", async () => {
    let csrfHeader: unknown;
    http.defaults.adapter = async (config) => {
      csrfHeader = config.headers["X-XSRF-TOKEN"];
      return { data: {}, status: 200, statusText: "OK", headers: {}, config };
    };

    await http.post("/iam/logout");

    expect(
      decodeURIComponent(
        cookie("IM_OPS_IAM_SESSION_XSRF_TOKEN") ?? "",
      ),
    ).toBe("ops csrf");
    expect(csrfHeader).toBe("ops csrf");
  });

  it("unwraps successful HttpResult data", () => {
    expect(
      unwrapHttpResult({ code: 0, msg: "success", data: { count: 1 } }),
    ).toEqual({ count: 1 });
  });

  it("preserves Long wire strings and nullable Broker timestamps", () => {
    const payload = { startedAtMs: "1787446800123", uptimeMs: "30001", lastFailureAtMs: null };
    expect(unwrapHttpResult({ code: 0, msg: "success", data: payload })).toEqual(payload);
  });

  it("publishes authentication and authorization failures", () => {
    const unauthorized = vi.fn();
    const forbidden = vi.fn();
    window.addEventListener("iam:unauthorized", unauthorized, { once: true });
    window.addEventListener("ops:forbidden", forbidden, { once: true });

    notifyHttpFailure(10001);
    notifyHttpFailure(10002);

    expect(unauthorized).toHaveBeenCalledOnce();
    expect(forbidden).toHaveBeenCalledOnce();
  });
});
