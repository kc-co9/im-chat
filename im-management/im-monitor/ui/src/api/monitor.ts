import { http } from "./http";

export type NodeStatus = "HEALTHY" | "UNREACHABLE";

export interface BrokerStatistics {
  brokerCount: number;
  gatewayCount: number;
  connectionCount: number;
}

export interface BrokerOverviewData {
  brokerId: string;
  startedAt: number;
  statistics: BrokerStatistics;
}

export interface BrokerNodeOverview {
  brokerId: string;
  status: NodeStatus;
  data: BrokerOverviewData | null;
  errorSummary: string | null;
  queriedAt?: number;
}

export interface ClusterOverview {
  brokerCount: number;
  gatewayCount: number;
  connectionCount: number;
  nodes: BrokerNodeOverview[];
  queriedAt: number;
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
  queriedAt: number;
}

export interface BrokerNode {
  brokerId: string;
  host: string;
  port: number;
  registeredAt: number;
  lastSeenAt: number;
}

export interface GatewayNode {
  gatewayId: string;
  host: string;
  port: number;
  registeredAt: number;
  lastSeenAt: number;
}

export interface ConnectionRoute {
  userId: string;
  gatewayId: string;
  registeredAt: number;
  lastSeenAt: number;
}

export interface DiagnosticRecord {
  executedAt: number;
  status: string;
  processedCount: number;
  durationMillis: number;
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
export const getMigrations = (limit = 20) =>
  http
    .get<ClusterQueryResult<DiagnosticRecord>>("/api/migrations", {
      params: { limit },
    })
    .then(({ data }) => data);
