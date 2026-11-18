package com.co.kc.imchat.e2e;

import com.co.kc.imchat.e2e.support.OpsCliGovernanceTopology;
import com.co.kc.imchat.management.audit.sdk.model.AuditOutcome;
import com.co.kc.imchat.management.ops.cli.http.OpsCliClient;
import com.co.kc.imchat.management.ops.cli.operation.OperationRequestFile;
import com.co.kc.imchat.management.ops.domain.model.BrokerManagementEndpoint;
import com.co.kc.imchat.management.ops.infrastructure.client.BrokerManagementClientException;
import com.co.kc.imchat.management.ops.infrastructure.client.HttpBrokerManagementClient;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerClusterStatePayload;
import com.co.kc.imchat.management.ops.infrastructure.client.model.BrokerOperationPayload;
import com.co.kc.imchat.management.ops.model.cqrs.command.BrokerClusterOperationCommand;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

/** 真实浏览器式 PKCE 登录、CLI Bearer 读取与 IAM 客户端停用的首个纵向旅程。 */
@Tag("e2e")
class OpsCliGovernanceE2ETest {
    @Test
    void authenticatedCliFollowsRealFollowerToThreeVoterLeader() {
        OpsCliGovernanceTopology topology = OpsCliGovernanceTopology.forFollowerJourney();
        try (topology) {
            topology.start();
            OpsCliClient client = topology.login();
            JsonNode before = client.queryCluster();
            assertThat(before.path("brokers").size()).isEqualTo(4);
            String operationId = "cli-follower-" + UUID.randomUUID();
            OperationRequestFile.Request request = new OperationRequestFile.Request(
                    topology.opsOrigin().toString(), operationId, before.path("epoch").asLong(),
                    OpsCliGovernanceTopology.LEARNER_ID, "add-learner",
                    topology.learnerMembershipEndpoint(), null);
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_SUBMIT);
            JsonNode submitted = client.submit(topology.operationRecordPath(), request);
            if (!"APPLIED".equals(submitted.path("status").asText())) {
                throw topology.submissionAssertionFailure(submitted, operationId);
            }
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_RECORD_ASSERT);
            assertThat(submitted.path("status").asText()).isEqualTo("APPLIED");
            assertThat(submitted.path("operationId").asText()).isEqualTo(operationId);
            assertThat(topology.networkWrites()).hasSize(2);
            OpsCliGovernanceTopology.NetworkWrite first = topology.networkWrites().getFirst();
            OpsCliGovernanceTopology.NetworkWrite second = topology.networkWrites().get(1);
            assertThat(first.brokerId()).isNotEqualTo(second.brokerId());
            assertThat(first.leaderAtReceipt()).isFalse();
            assertThat(second.leaderAtReceipt()).isTrue();
            if (!first.notLeaderResponse() || !first.hintMatchesOfficialLeader()) {
                throw topology.submissionAssertionFailure(submitted, operationId);
            }
            assertThat(first.notLeaderResponse()).isTrue();
            assertThat(first.hintMatchesOfficialLeader()).isTrue();
            assertThat(first.path()).isEqualTo(second.path())
                    .isEqualTo("/management/cluster-state/brokers/governance-learner/add-learner");
            assertThat(first.payload()).isEqualTo(second.payload());
            JsonNode payload = new com.fasterxml.jackson.databind.ObjectMapper().readTree(first.payload());
            assertThat(first.operationId()).isEqualTo(second.operationId()).isEqualTo(operationId);
            assertThat(first.expectedEpoch()).isEqualTo(second.expectedEpoch())
                    .isEqualTo(Long.toString(request.expectedEpoch()));
            assertThat(payload.path("membershipEndpoint").asText()).isEqualTo(request.membershipEndpoint());
            assertThat(first.scope()).isEqualTo(second.scope()).isEqualTo("ops:broker:raft:write");
        } catch (Exception | AssertionError failure) {
            throw topology.sanitizeCurrentFailure(failure);
        }
    }

    @Test
    void authenticatedCliRejectsFollowerHintWhenLeaderMetadataIsMissing() {
        assertFollowerDiscoveryRefusal(OpsCliGovernanceTopology.forMissingLeaderMetadataJourney());
    }

    @Test
    void authenticatedCliRejectsFollowerHintWhenLeaderPairingConflicts() {
        assertFollowerDiscoveryRefusal(OpsCliGovernanceTopology.forConflictingLeaderMetadataJourney());
    }

    private void assertFollowerDiscoveryRefusal(OpsCliGovernanceTopology topology) {
        try (topology) {
            topology.start();
            OpsCliClient client = topology.login();
            JsonNode before = client.queryCluster();
            String operationId = "cli-unpaired-" + UUID.randomUUID();
            OperationRequestFile.Request request = new OperationRequestFile.Request(
                    topology.opsOrigin().toString(), operationId, before.path("epoch").asLong(),
                    OpsCliGovernanceTopology.LEARNER_ID, "add-learner", topology.learnerMembershipEndpoint(), null);
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_SUBMIT);
            JsonNode submitted = client.submit(topology.operationRecordPath(), request);
            assertThat(submitted.path("operationId").asText()).isEqualTo(operationId);
            assertThat(submitted.path("status").asText()).isEqualTo("UNKNOWN");
            assertThat(topology.networkWrites()).hasSize(1);
            OpsCliGovernanceTopology.NetworkWrite first = topology.networkWrites().getFirst();
            assertThat(first.leaderAtReceipt()).isFalse();
            assertThat(first.notLeaderResponse()).isTrue();
            assertThat(first.operationId()).isEqualTo(operationId);
            assertThat(first.expectedEpoch()).isEqualTo(Long.toString(request.expectedEpoch()));
            assertThat(first.scope()).isEqualTo("ops:broker:raft:write");
        } catch (Exception | AssertionError failure) {
            throw topology.sanitizeCurrentFailure(failure);
        }
    }

    @Test
    void authenticatedCliAddsLearnerQueriesCompletionAndRetriesOriginalRecord() {
        OpsCliGovernanceTopology topology = OpsCliGovernanceTopology.forWriteJourney();
        try (topology) {
            topology.start();
            OpsCliClient client = topology.login();
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_WRITE_BASELINE);
            JsonNode before = client.queryCluster();
            assertThat(before.path("brokers").size()).isEqualTo(2);
            JsonNode discovered = brokerNode(before, OpsCliGovernanceTopology.LEARNER_ID);
            assertThat(discovered.path("membershipRole").asText()).isEqualTo("DISCOVERED");
            assertThat(discovered.path("routingState").asText()).isEqualTo("INACTIVE");
            long epoch = before.path("epoch").asLong();
            String operationId = "cli-add-learner-" + UUID.randomUUID();
            OperationRequestFile.Request request = new OperationRequestFile.Request(
                    topology.opsOrigin().toString(), operationId, epoch,
                    OpsCliGovernanceTopology.LEARNER_ID, "add-learner",
                    topology.learnerMembershipEndpoint(), null);
            Path record = topology.operationRecordPath();

            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_SUBMIT);
            JsonNode submitted = client.submit(record, request);
            try {
                assertThat(submitted.path("operationId").asText()).isEqualTo(operationId);
                assertThat(submitted.path("status").asText()).isEqualTo("APPLIED");
            } catch (AssertionError failure) {
                throw topology.submissionAssertionFailure(submitted, operationId);
            }
            topology.assertBrokerWriteObserverRegistered();
            assertThat(topology.networkWrites()).hasSize(1);
            OpsCliGovernanceTopology.NetworkWrite write = topology.networkWrites().getFirst();
            assertThat(write.operationId()).isEqualTo(operationId);
            assertThat(write.path()).isEqualTo("/management/cluster-state/brokers/"
                    + OpsCliGovernanceTopology.LEARNER_ID + "/add-learner");
            assertThat(write.expectedEpoch()).isEqualTo(Long.toString(epoch));
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_RECORD_ASSERT);
            assertThat(Files.getPosixFilePermissions(record.getParent()))
                    .isEqualTo(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
            assertThat(Files.getPosixFilePermissions(record))
                    .isEqualTo(PosixFilePermissions.fromString("rw-------"));
            assertThat(Files.getOwner(record)).isEqualTo(record.getFileSystem().getUserPrincipalLookupService()
                    .lookupPrincipalByName(System.getProperty("user.name")));
            assertThat(OperationRequestFile.read(record)).isEqualTo(request);
            byte[] originalRecord = Files.readAllBytes(record);

            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_OPERATION_QUERY);
            await().atMost(Duration.ofSeconds(15)).untilAsserted(() ->
                    assertThat(client.queryOperation(operationId).path("phase").asText()).isEqualTo("SUCCEEDED"));
            JsonNode completed = client.queryOperation(operationId);
            assertThat(completed.path("operationId").asText()).isEqualTo(operationId);
            assertThat(completed.path("type").asText()).isEqualTo("START_LEARNER_SYNC");
            assertThat(completed.path("targetBrokerId").asText()).isEqualTo(OpsCliGovernanceTopology.LEARNER_ID);
            assertThat(completed.path("phase").asText()).isEqualTo("SUCCEEDED");
            assertThat(completed.path("acceptedEpoch").asLong()).isEqualTo(epoch);
            assertThat(completed.path("resultEpoch").asLong()).isEqualTo(epoch);
            assertThat(completed.path("createdAtMs").isTextual()).isTrue();
            assertThat(completed.path("completedAtMs").isTextual()).isTrue();
            long created = Long.parseLong(completed.path("createdAtMs").textValue());
            long finished = Long.parseLong(completed.path("completedAtMs").textValue());
            assertThat(created).isPositive().isLessThanOrEqualTo(Instant.now().toEpochMilli());
            assertThat(finished).isGreaterThanOrEqualTo(created).isLessThanOrEqualTo(Instant.now().toEpochMilli());
            assertThat(completed.path("error").isNull()).isTrue();

            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_LEARNER_PROJECTION);
            JsonNode after = client.queryCluster();
            assertThat(after.path("brokers").size()).isEqualTo(2);
            assertThat(brokerNode(after, OpsCliGovernanceTopology.LEARNER_ID).path("membershipRole").asText())
                    .isEqualTo("LEARNER_SYNCING");
            assertThat(brokerNode(after, OpsCliGovernanceTopology.LEARNER_ID).path("routingState").asText())
                    .isEqualTo("INACTIVE");
            assertThat(brokerNode(after, OpsCliGovernanceTopology.BROKER_ID).path("membershipRole").asText())
                    .isEqualTo("VOTER");
            assertThat(brokerNode(after, OpsCliGovernanceTopology.BROKER_ID).path("routingState").asText())
                    .isEqualTo("ACTIVE");

            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_EXPLICIT_RETRY);
            OpsCliClient successor = topology.loginSuccessor();
            JsonNode replay = successor.retry(record);
            assertThat(replay.path("operationId").asText()).isEqualTo(operationId);
            assertThat(replay.path("status").asText()).isEqualTo("DUPLICATE");
            await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
                assertThat(topology.operationAudits(operationId)).hasSize(2);
                assertThat(topology.operationAudits(operationId)).extracting(event -> event.actor().id())
                        .containsExactlyInAnyOrder(Long.toString(topology.administratorId()), "90004");
                assertThat(topology.operationAudits(operationId)).allSatisfy(event -> {
                    assertThat(event.outcome()).isEqualTo(AuditOutcome.SUCCESS);
                    assertThat(event.attributes().values()).containsEntry("clientId", "im-ops-cli")
                            .containsEntry("operationId", operationId)
                            .containsEntry("expectedEpoch", Long.toString(epoch))
                            .containsEntry("action", "add-learner");
                });
            });
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_RETRY_UNCHANGED);
            assertThat(client.queryOperation(operationId)).isEqualTo(completed);
            assertThat(client.queryCluster()).isEqualTo(after);
            assertThat(OperationRequestFile.read(record)).isEqualTo(request);
            assertThat(Files.readAllBytes(record)).isEqualTo(originalRecord);
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLEANUP);
        } catch (Exception | AssertionError failure) {
            throw topology.sanitizeCurrentFailure(failure);
        }
    }

    @Test
    void authenticatedCliConfirmsRealLearnerAndRetriesOriginalEndpoint() {
        OpsCliGovernanceTopology topology = OpsCliGovernanceTopology.forConfirmationJourney();
        try (topology) {
            topology.start();
            OpsCliClient client = topology.login();
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_WRITE_BASELINE);
            long epoch = client.queryCluster().path("epoch").asLong();
            assertThat(brokerNode(client.queryCluster(), OpsCliGovernanceTopology.LEARNER_ID)
                    .path("membershipRole").asText()).isEqualTo("LEARNER_SYNCING");
            String confirmationId = "cli-confirm-" + UUID.randomUUID();
            Path record = topology.operationRecordPath().resolveSibling("confirmation.json");
            OperationRequestFile.Request request = new OperationRequestFile.Request(
                    topology.opsOrigin().toString(), confirmationId, epoch, OpsCliGovernanceTopology.LEARNER_ID,
                    "complete-learner-sync", topology.learnerMembershipEndpoint(), "verified");
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_SUBMIT);
            JsonNode confirmed = client.submit(record, request);
            if (!"APPLIED".equals(confirmed.path("status").asText())) {
                throw topology.submissionAssertionFailure(confirmed, confirmationId);
            }
            assertThat(confirmed.path("status").asText()).isEqualTo("APPLIED");
            byte[] original = Files.readAllBytes(record);
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_OPERATION_QUERY);
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                    assertThat(client.queryOperation(confirmationId).path("phase").asText()).isEqualTo("SUCCEEDED"));
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_EXPLICIT_RETRY);
            JsonNode replay = client.retry(record);
            if (!"DUPLICATE".equals(replay.path("status").asText())) {
                throw topology.submissionAssertionFailure(replay, confirmationId);
            }
            assertThat(replay.path("status").asText()).isEqualTo("DUPLICATE");
            assertThat(replay.path("operationId").asText()).isEqualTo(confirmationId);
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_RETRY_UNCHANGED);
            assertThat(Files.readAllBytes(record)).isEqualTo(original);
            assertThat(OperationRequestFile.read(record)).isEqualTo(request);
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_LEARNER_PROJECTION);
            assertThat(brokerNode(client.queryCluster(), OpsCliGovernanceTopology.LEARNER_ID)
                    .path("membershipRole").asText()).isEqualTo("LEARNER");
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLEANUP);
        } catch (Exception | AssertionError failure) {
            throw topology.sanitizeCurrentFailure(failure);
        }
    }

    @Test
    void authenticatedCliRetriesCommittedLearnerBeforeSchedulerCompletesIt() {
        String operationId = "cli-recover-" + UUID.randomUUID();
        OpsCliGovernanceTopology topology = OpsCliGovernanceTopology.forConfirmationRecoveryJourney(operationId);
        try (topology) {
            topology.start();
            OpsCliClient client = topology.login();
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_WRITE_BASELINE);
            long epoch = client.queryCluster().path("epoch").asLong();
            Path record = topology.operationRecordPath().resolveSibling("confirmation.json");
            OperationRequestFile.Request request = new OperationRequestFile.Request(
                    topology.opsOrigin().toString(), operationId, epoch, OpsCliGovernanceTopology.LEARNER_ID,
                    "complete-learner-sync", topology.learnerMembershipEndpoint(), "verified");
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_SUBMIT);
            assertThat(client.submit(record, request).path("status").asText()).isEqualTo("UNAVAILABLE");
            byte[] original = Files.readAllBytes(record);
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_OPERATION_QUERY);
            JsonNode committed = client.queryOperation(operationId);
            assertThat(committed.path("phase").asText()).isEqualTo("RAFT_COMMITTED");

            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_EXPLICIT_RETRY);
            JsonNode retry = client.retry(record);
            assertThat(retry.path("status").asText()).isEqualTo("DUPLICATE");
            assertThat(retry.path("operationId").asText()).isEqualTo(operationId);
            assertThat(client.queryOperation(operationId)).isEqualTo(committed);
            assertThat(Files.readAllBytes(record)).isEqualTo(original);

            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_OPERATION_QUERY);
            topology.allowScheduledRecovery();
            await().atMost(Duration.ofSeconds(20)).untilAsserted(() -> {
                JsonNode completed = client.queryOperation(operationId);
                assertThat(completed.path("operationId").asText()).isEqualTo(operationId);
                assertThat(completed.path("phase").asText()).isEqualTo("SUCCEEDED");
            });
            topology.assertScheduledRecoveryRanOnce();
            assertThat(brokerNode(client.queryCluster(), OpsCliGovernanceTopology.LEARNER_ID)
                    .path("membershipRole").asText()).isEqualTo("LEARNER");
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLEANUP);
        } catch (Exception | AssertionError failure) {
            throw topology.sanitizeCurrentFailure(failure);
        }
    }

    @Test
    void authenticatedCliRejectsWriteWithoutScopeDespiteAdministratorAuthority() {
        OpsCliGovernanceTopology topology = OpsCliGovernanceTopology.forWriteJourney();
        try (topology) {
            topology.start();
            OpsCliClient client = topology.loginWithReadScope();
            JsonNode before = client.queryCluster();
            String operationId = "cli-scope-refusal-" + UUID.randomUUID();
            OperationRequestFile.Request request = new OperationRequestFile.Request(
                    topology.opsOrigin().toString(), operationId, before.path("epoch").asLong(),
                    OpsCliGovernanceTopology.LEARNER_ID, "add-learner",
                    topology.learnerMembershipEndpoint(), null);
            Path record = topology.operationRecordPath();
            topology.assertBrokerWriteObserverRegistered();
            assertThatThrownBy(() -> client.submit(record, request))
                    .isExactlyInstanceOf(IllegalStateException.class).hasMessage("Ops permission denied");
            assertThat(OperationRequestFile.read(record)).isEqualTo(request);
            byte[] original = Files.readAllBytes(record);
            assertThatThrownBy(() -> client.retry(record))
                    .isExactlyInstanceOf(IllegalStateException.class).hasMessage("Ops permission denied");
            assertThat(Files.readAllBytes(record)).isEqualTo(original);
            assertThat(topology.networkWrites()).isEmpty();
            assertThat(client.queryOperation(operationId).path("phase").asText()).isEqualTo("UNKNOWN");
            assertThat(client.queryCluster()).isEqualTo(before);
            OpsCliClient authorized = topology.login();
            assertThat(authorized.retry(record).path("status").asText()).isEqualTo("APPLIED");
            assertThat(topology.networkWrites()).hasSize(1);
            assertThat(Files.readAllBytes(record)).isEqualTo(original);
        } catch (Exception | AssertionError failure) {
            throw topology.sanitizeCurrentFailure(failure);
        }
    }

    @Test
    void authenticatedCliRejectsWriteWithoutAdministratorAuthority() {
        OpsCliGovernanceTopology topology = OpsCliGovernanceTopology.forWriteWithoutAuthorityJourney();
        try (topology) {
            topology.start();
            OpsCliClient client = topology.login();
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_WRITE_BASELINE);
            JsonNode before = client.queryCluster();
            long epoch = before.path("epoch").asLong();
            String operationId = "cli-rbac-refusal-" + UUID.randomUUID();
            OperationRequestFile.Request request = new OperationRequestFile.Request(
                    topology.opsOrigin().toString(), operationId, epoch,
                    OpsCliGovernanceTopology.LEARNER_ID, "add-learner",
                    topology.learnerMembershipEndpoint(), null);
            Path record = topology.operationRecordPath();
            assertThat(Files.exists(record)).isFalse();
            topology.assertBrokerWriteObserverRegistered();
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_SUBMIT);
            assertThatThrownBy(() -> client.submit(record, request))
                    .isExactlyInstanceOf(IllegalStateException.class).hasMessage("Ops permission denied");
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_RECORD_ASSERT);
            assertThat(OperationRequestFile.read(record)).isEqualTo(request);
            assertThat(Files.getPosixFilePermissions(record.getParent()))
                    .isEqualTo(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE,
                            PosixFilePermission.OWNER_EXECUTE));
            assertThat(Files.getPosixFilePermissions(record))
                    .isEqualTo(Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
            byte[] originalRecord = Files.readAllBytes(record);
            assertThat(topology.networkWrites()).isEmpty();
            assertThat(client.queryCluster()).isEqualTo(before);
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_EXPLICIT_RETRY);
            assertThatThrownBy(() -> client.retry(record))
                    .isExactlyInstanceOf(IllegalStateException.class).hasMessage("Ops permission denied");
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLI_RETRY_UNCHANGED);
            assertThat(Files.readAllBytes(record)).isEqualTo(originalRecord);
            assertThat(OperationRequestFile.read(record)).isEqualTo(request);
            assertThat(topology.networkWrites()).isEmpty();
            assertThat(client.queryCluster()).isEqualTo(before);
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLEANUP);
        } catch (Exception | AssertionError failure) {
            throw topology.sanitizeCurrentFailure(failure);
        }
    }

    private static JsonNode brokerNode(JsonNode state, String brokerId) {
        for (JsonNode broker : state.path("brokers")) {
            if (brokerId.equals(broker.path("brokerId").asText())) {
                return broker;
            }
        }
        throw new AssertionError("Expected fixture Broker projection is absent");
    }

    @Test
    void invalidBrokerServiceCredentialIsRejectedWithoutMutationInIsolatedContext() {
        OpsCliGovernanceTopology topology = new OpsCliGovernanceTopology();
        try (topology) {
            topology.start();
            HttpBrokerManagementClient authorized = topology.authorizedBrokerClient();
            HttpBrokerManagementClient unauthorized = topology.unauthorizedBrokerClient();
            BrokerManagementEndpoint endpoint = topology.brokerManagementEndpoint();
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.BROKER_WRITE_BASELINE);
            BrokerClusterStatePayload before = authorized.clusterState(endpoint);
            String operationId = "credential-check-" + UUID.randomUUID();
            String scope = "ops:broker:raft:write";
            // 已有 VOTER 不能再次加入 learner；正确凭证到达领域校验也不会改变成员配置。
            BrokerClusterOperationCommand command = new BrokerClusterOperationCommand(
                    OpsCliGovernanceTopology.BROKER_ID, "add-learner", operationId, before.epoch(),
                    topology.brokerMembershipEndpoint(), "credential boundary test", scope);

            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.BROKER_INVALID_CREDENTIAL);
            assertThatThrownBy(() -> unauthorized.operate(endpoint, command, scope))
                    .isInstanceOf(BrokerManagementClientException.class)
                    .hasMessage("系统繁忙,请稍后重试");
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.BROKER_INVALID_UNCHANGED);
            assertThat(authorized.clusterState(endpoint)).isEqualTo(before);
            assertThatThrownBy(() -> authorized.queryOperation(endpoint, operationId))
                    .isInstanceOf(BrokerManagementClientException.class)
                    .hasMessage("参数错误,请稍后重试");

            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.BROKER_VALID_CREDENTIAL);
            BrokerOperationPayload authenticated = authorized.operate(endpoint, command, scope);
            assertThat(authenticated.operationId()).isEqualTo(operationId);
            assertThat(authenticated.status()).isEqualTo("REJECTED");
            assertThat(authenticated.message()).contains("DISCOVERED");
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.BROKER_VALID_UNCHANGED);
            assertThat(authorized.clusterState(endpoint)).isEqualTo(before);
            assertThatThrownBy(() -> authorized.queryOperation(endpoint, operationId))
                    .isInstanceOf(BrokerManagementClientException.class)
                    .hasMessage("参数错误,请稍后重试");
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLEANUP);
        } catch (Exception | AssertionError failure) {
            throw topology.sanitizeCurrentFailure(failure);
        }
    }

    @Test
    void committedPublicClientDisableRejectsNextReadFromSameLoggedInCli() {
        OpsCliGovernanceTopology topology = new OpsCliGovernanceTopology();
        try (topology) {
            topology.start();
            OpsCliClient client = topology.login();
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.INITIAL_QUERY);
            JsonNode state;
            try {
                state = client.queryCluster();
            } catch (IOException failure) {
                // CLI 在非 200 时先抛通用异常；仍先报告已观察到的安全状态码，再保留原失败类型。
                topology.assertHttpStatus(topology.lastClusterStatus(), 200);
                throw topology.initialQueryFailure(failure);
            }
            topology.assertHttpStatus(topology.lastClusterStatus(), 200);
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.INITIAL_PROJECTION_ASSERT);
            assertThat(state.path("sourceBroker").asText()).isEqualTo(OpsCliGovernanceTopology.BROKER_ID);
            assertThat(state.path("revision").isIntegralNumber()).isTrue();
            assertThat(state.path("revision").asLong()).isNotNegative();
            assertThat(state.path("epoch").isIntegralNumber()).isTrue();
            assertThat(state.path("epoch").asLong()).isNotNegative();
            assertThat(state.path("algorithm").asText()).isEqualTo("RENDEZVOUS_HASH");
            assertThat(state.path("brokers").isArray()).isTrue();
            assertThat(state.path("brokers").size()).isEqualTo(1);
            JsonNode broker = state.path("brokers").get(0);
            assertThat(broker.path("brokerId").asText()).isEqualTo(OpsCliGovernanceTopology.BROKER_ID);
            assertThat(broker.path("membershipRole").asText()).isEqualTo("VOTER");
            assertThat(broker.path("routingState").asText()).isEqualTo("ACTIVE");

            topology.disablePublicClient();

            // 不重新登录、不等待 TTL、不替换客户端；下一次真实 Ops 请求必须查询 IAM 后拒绝。
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.REJECTED_READ);
            assertThatThrownBy(client::queryCluster)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessage("Login is required");
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.REJECTED_STATUS_ASSERT);
            topology.assertHttpStatus(topology.lastClusterStatus(), 401);
            topology.enterStage(OpsCliGovernanceTopology.JourneyStage.CLEANUP);
        } catch (Exception | AssertionError failure) {
            throw topology.sanitizeCurrentFailure(failure);
        }
    }
}
