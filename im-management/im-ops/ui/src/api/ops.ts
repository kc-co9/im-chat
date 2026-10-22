import { http } from "./http";

export type NodeStatus = "HEALTHY" | "UNREACHABLE";

export interface BrokerStatistics {
  brokerCount: number;
  gatewayCount: number;
  connectionCount: number;
}

export interface BrokerOverviewData {
  brokerId: string;
  broker: { id: string; address: string; startedAtMs: string | null; uptimeMs: string | null };
  statistics: BrokerStatistics;
}

export interface BrokerNodeOverview {
  brokerId: string;
  status: NodeStatus;
  data: BrokerOverviewData | null;
  errorSummary: string | null;
  queriedAt?: string;
}

export interface ClusterOverview {
  brokerCount: number;
  gatewayCount: number;
  connectionCount: number;
  nodes: BrokerNodeOverview[];
  queriedAt: string;
}

export interface SourcedValue<T> {
  sourceBroker: string;
  value: T;
}

export interface BrokerNodeFailure {
  brokerId: string;
  status: NodeStatus;
  errorSummary: string;
}

export interface ClusterQueryResult<T> {
  values: SourcedValue<T>[];
  failures: BrokerNodeFailure[];
  queriedAt: string;
}

export interface BrokerNode {
  brokerId: string;
  host: string;
  port: number;
  registeredAtMs: string | null;
  lastSeenAtMs: string | null;
}

export interface BrokerClusterNode {
  brokerId: string;
  routeEndpoint: string;
  membershipRole: string;
  routingState: string;
}

export interface BrokerClusterState {
  revision: number;
  epoch: number;
  algorithm: string;
  brokers: BrokerClusterNode[];
}

export interface BrokerOperationResult {
  operationId: string;
  status: string;
  acceptedEpoch: number;
  targetEpoch: number | null;
  leaderEndpoint: string | null;
  message: string | null;
}

export interface GatewayNode {
  gatewayId: string;
  host: string;
  port: number;
  registeredAtMs: string | null;
  lastSeenAtMs: string | null;
}

export interface ConnectionRoute {
  userId: string;
  gatewayId: string;
  registeredAtMs: string | null;
  lastSeenAtMs: string | null;
}

export interface DiagnosticRecord {
  executedAtMs: string | null;
  status: string;
  processedCount: number;
  durationMs: string | null;
  errorSummary: string | null;
  target?: string;
  targetBrokerId?: string;
}

export const getOverview = () =>
  http.get<ClusterOverview>("/api/overview").then(({ data }) => data);
export const getBrokers = () =>
  http
    .get<ClusterQueryResult<BrokerNode>>("/api/brokers")
    .then(({ data }) => data);
export const getBroker = (brokerId: string) =>
  http
    .get<BrokerNodeOverview>(`/api/brokers/${encodeURIComponent(brokerId)}`)
    .then(({ data }) => data);
export const getBrokerClusterState = () =>
  http.get<BrokerClusterState>("/api/cluster-state").then(({ data }) => data);
export const operateBroker = (
  brokerId: string,
  action: "activate" | "drain" | "deactivate",
  expectedEpoch: number,
) =>
  http
    .post<BrokerOperationResult>(
      `/api/brokers/${encodeURIComponent(brokerId)}/cluster-state/${action}`,
      {
        operationId: crypto.randomUUID(),
        expectedEpoch,
      },
    )
    .then(({ data }) => data);
export const getGateways = () =>
  http
    .get<ClusterQueryResult<GatewayNode>>("/api/gateways")
    .then(({ data }) => data);
export const getConnections = (userId: string) =>
  http
    .get<ClusterQueryResult<ConnectionRoute>>("/api/connections", {
      params: { userId },
    })
    .then(({ data }) => data);
export const getGossipRecords = (limit = 20) =>
  http
    .get<ClusterQueryResult<DiagnosticRecord>>("/api/gossip/records", {
      params: { limit },
    })
    .then(({ data }) => data);
