import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import BrokersView from "../src/views/BrokersView.vue";
import ConnectionsView from "../src/views/ConnectionsView.vue";
import DiagnosticsView from "../src/views/DiagnosticsView.vue";
import * as api from "../src/api/monitor";

vi.mock("../src/api/monitor");

describe("monitor detail views", () => {
  beforeEach(() => {
    vi.mocked(api.getBrokers).mockResolvedValue({
      values: [
        {
          sourceBroker: "broker-a",
          value: {
            brokerId: "broker-a",
            host: "10.0.0.1",
            port: 12201,
            registeredAt: 0,
            lastSeenAt: 0,
          },
        },
      ],
      failures: [
        {
          brokerId: "broker-b",
          status: "UNREACHABLE",
          errorSummary: "timeout",
        },
      ],
      queriedAt: 0,
    });
    vi.mocked(api.getConnections).mockResolvedValue({
      values: [
        {
          sourceBroker: "broker-a",
          value: {
            userId: "7496072518638374912",
            gatewayId: "gateway-a",
            registeredAt: 0,
            lastSeenAt: 1787446800000,
          },
        },
      ],
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

  it("shows broker source data and partial failures", async () => {
    const wrapper = mount(BrokersView);
    await flushPromises();
    expect(wrapper.text()).toContain("10.0.0.1:12201");
    expect(wrapper.text()).toContain("broker-b：timeout");
  });

  it("queries routes for only the requested user", async () => {
    const wrapper = mount(ConnectionsView);
    await wrapper.get("input").setValue("7496072518638374912");
    await wrapper.get("form").trigger("submit");
    await flushPromises();
    expect(api.getConnections).toHaveBeenCalledWith("7496072518638374912");
    expect(wrapper.text()).toContain("7496072518638374912");
    expect(wrapper.text()).toContain("gateway-a");
  });

  it("shows empty diagnostic histories", async () => {
    const wrapper = mount(DiagnosticsView);
    await flushPromises();
    expect(wrapper.text()).toContain("暂无 Gossip 记录");
    expect(wrapper.text()).toContain("暂无迁移记录");
  });
});
