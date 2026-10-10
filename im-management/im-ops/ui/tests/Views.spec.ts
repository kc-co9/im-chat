import { flushPromises, mount } from "@vue/test-utils";
import { beforeEach, describe, expect, it, vi } from "vitest";
import BrokersView from "../src/views/BrokersView.vue";
import ConnectionsView from "../src/views/ConnectionsView.vue";
import DiagnosticsView from "../src/views/DiagnosticsView.vue";
import * as api from "../src/api/ops";
import { ElMessageBox } from "element-plus";
import { can } from "../src/state/auth";

vi.mock("../src/api/ops");
vi.mock("../src/state/auth", () => ({ can: vi.fn() }));

describe("ops detail views", () => {
  beforeEach(() => {
    vi.mocked(can).mockReturnValue(true);
    vi.mocked(api.getBrokers).mockResolvedValue({
      values: [
        {
          sourceBroker: "broker-a",
          value: {
            brokerId: "broker-a",
            host: "10.0.0.1",
            port: 19020,
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
    vi.mocked(api.getBrokerClusterState).mockResolvedValue({
      revision: 1,
      epoch: 1,
      algorithm: "RENDEZVOUS_HASH",
      brokers: [{
        brokerId: "broker-a",
        routeEndpoint: "10.0.0.1:18020",
        membershipRole: "VOTER",
        routingState: "READY",
      }],
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
  });

  it("shows broker source data and partial failures", async () => {
    const wrapper = mount(BrokersView);
    await flushPromises();
    expect(wrapper.text()).toContain("10.0.0.1:19020");
    expect(wrapper.text()).toContain("broker-b：timeout");
  });

  it("does not submit an activation when confirmation is cancelled", async () => {
    const confirm = vi.spyOn(ElMessageBox, "confirm").mockRejectedValueOnce("cancel");
    const wrapper = mount(BrokersView);
    await flushPromises();

    const activate = wrapper.findAll("button").find((button) => button.text().includes("激活"));
    expect(activate).toBeDefined();
    await activate!.trigger("click");
    await flushPromises();

    expect(confirm).toHaveBeenCalledOnce();
    expect(api.operateBroker).not.toHaveBeenCalled();
    confirm.mockRestore();
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
  });
});
