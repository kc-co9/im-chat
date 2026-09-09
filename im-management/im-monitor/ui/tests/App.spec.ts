import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import App from "../src/App.vue";
import * as api from "../src/api/monitor";
import { router } from "../src/router";
import { auth } from "../src/state/auth";

vi.mock("../src/api/monitor");
vi.mock("echarts/core", () => ({
  init: () => ({ setOption: vi.fn(), dispose: vi.fn() }),
  use: vi.fn(),
}));
vi.mock("echarts/charts", () => ({ BarChart: {} }));
vi.mock("echarts/components", () => ({ GridComponent: {} }));
vi.mock("echarts/renderers", () => ({ CanvasRenderer: {} }));

describe("monitor dashboard", () => {
  beforeEach(() => {
    auth.checked = true;
    auth.principal = {
      administratorId: 1,
      username: "monitor",
      appKey: "imMonitor",
      authorities: [
        "monitor:overview:read",
        "monitor:broker:read",
        "monitor:gateway:read",
        "monitor:connection:read",
        "monitor:diagnostic:read",
      ],
    };
    vi.mocked(api.getOverview).mockResolvedValue({
      brokerCount: 2,
      gatewayCount: 3,
      connectionCount: 4,
      queriedAt: 1787446800000,
      nodes: [
        {
          brokerId: "broker-1",
          status: "HEALTHY",
          data: null,
          errorSummary: null,
        },
        {
          brokerId: "broker-2",
          status: "UNREACHABLE",
          data: null,
          errorSummary: "timeout",
        },
      ],
    });
    vi.mocked(api.getBrokers).mockResolvedValue({
      values: [],
      failures: [],
      queriedAt: 0,
    });
    vi.mocked(api.getGateways).mockResolvedValue({
      values: [],
      failures: [],
      queriedAt: 0,
    });
    vi.mocked(api.getGossipRecords).mockResolvedValue({
      values: [],
      failures: [],
      queriedAt: 0,
    });
    vi.mocked(api.getMigrations).mockResolvedValue({
      values: [],
      failures: [],
      queriedAt: 0,
    });
  });

  it("shows overview and unreachable nodes", async () => {
    await router.push("/");
    await router.isReady();
    const wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();
    expect(wrapper.text()).toContain("连接路由 4");
    expect(wrapper.text()).toContain("broker-2");
    expect(wrapper.text()).toContain("不可达");
  });

  it("shows empty collection states", async () => {
    await router.push("/brokers");
    const wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();
    expect(wrapper.text()).toContain("暂无 Broker 数据");
    await router.push("/gateways");
    await flushPromises();
    expect(wrapper.text()).toContain("暂无 Gateway 数据");
  });

  it("shows a load failure without masking navigation", async () => {
    vi.mocked(api.getOverview).mockRejectedValue(new Error("offline"));
    await router.push("/");
    const wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();
    expect(wrapper.text()).toContain("总览加载失败");
    expect(wrapper.text()).toContain("诊断记录");
  });

  it("shows locally configured remote consoles", async () => {
    await router.push("/");
    const wrapper = mount(App, { global: { plugins: [router] } });
    await flushPromises();

    expect(wrapper.get('a[href="http://localhost:18090"]').text()).toContain(
      "身份与权限",
    );
  });
});
