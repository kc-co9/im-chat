package com.co.kc.imchat.broker.application.cluster;

import com.co.kc.imchat.broker.domain.cluster.service.MembershipOperationService;
import com.co.kc.imchat.broker.domain.cluster.model.MembershipExecutionConfirmation;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryMembershipOperationAttemptRepository;
import com.co.kc.imchat.broker.infrastructure.domain.repository.InMemoryLearnerConfirmationAttemptRepository;

import com.co.kc.imchat.broker.adapter.cluster.membership.BrokerMembershipExecutor;

import com.co.kc.imchat.broker.domain.cluster.model.*;
import com.co.kc.imchat.common.domain.cluster.model.MembershipEndpoint;
import com.co.kc.imchat.broker.adapter.cluster.membership.MembershipConfigurationResult;
import java.util.List;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BrokerMembershipExecutorTest {
    @Test
    void admittedContinuationCanFinishAfterLastPermanentPermitReservation() throws Exception {
        ThreadPoolExecutor worker = worker();
        CompletableFuture<String> completed = new CompletableFuture<>();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            for (int index = 0; index < 4095; index++) {
                reserve(executor, "budget-" + index);
            }
            assertThat(executor.submitApplicationStage(() -> null, stages -> {
                try {
                    reserve(executor, "last-permit");
                    stages.execute(() -> completed.complete(Thread.currentThread().getName()));
                } catch (RuntimeException failure) {
                    completed.completeExceptionally(failure);
                }
            }, () -> completed.completeExceptionally(new AssertionError("current stage invalidated")))
                    .accepted()).isTrue();

            assertThat(completed.get(5, TimeUnit.SECONDS)).isEqualTo("permit-test-worker");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> reserve(executor, "over-budget"))
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(executor.submitApplicationStage(() -> null, stages -> {
                throw new AssertionError("new execution admitted at capacity");
            }, () -> { }).accepted()).isFalse();
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void sendCompletionNotifiesOutsideExecutorLock(boolean synchronousFailure) throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { occupied.countDown(); await(release); });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        try (ExecutorService observer = Executors.newSingleThreadExecutor();
             BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "lock-free-send");
            CompletionStage<?> observed = executor.retransmitDrained(permit.operationId(), new BrokerId("b"), () -> {
                if (synchronousFailure) {
                    throw new IllegalStateException("controlled send failure");
                }
                return CompletableFuture.completedStage(confirmationResponse());
            }).thenApply(result -> {
                try {
                    return observer.submit(executor::undrainedPermitCount).get(2, TimeUnit.SECONDS);
                } catch (Exception failure) {
                    throw new CompletionException(failure);
                }
            });
            release.countDown();

            assertThat(observed.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(1L);
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void queuedApplicationStageInvalidatesOnWorkerAfterRoleRestart() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { occupied.countDown(); await(release); });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<String> invalidatedOn = new CompletableFuture<>();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(7L, 1L);
            assertThat(executor.submitApplicationStage(() -> null, stages -> calls.incrementAndGet(),
                    () -> invalidatedOn.complete(Thread.currentThread().getName())).accepted()).isTrue();
            executor.onRoleChanged(0L, 2L);
            executor.onRoleChanged(7L, 3L);
            release.countDown();
            assertThat(invalidatedOn.get(5, TimeUnit.SECONDS)).isEqualTo("permit-test-worker");
            assertThat(calls).hasValue(0);
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void learnerExecutionReturnsAsyncContinuationToWorkerAndInvalidatesAfterRoleRestart() throws Exception {
        ThreadPoolExecutor worker = worker();
        CompletableFuture<String> outcome = new CompletableFuture<>();
        CompletableFuture<String> publicationThread = new CompletableFuture<>();
        CompletableFuture<String> invalidationThread = new CompletableFuture<>();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            java.util.concurrent.Executor stages = executor.reserveLearnerExecution(() ->
                    invalidationThread.complete(Thread.currentThread().getName()));
            assertThat(stages).isNotNull();
            CompletableFuture<Void> started = new CompletableFuture<>();
            stages.execute(() -> {
                outcome.thenRunAsync(() -> publicationThread.complete(Thread.currentThread().getName()), stages);
                started.complete(null);
            });
            started.get(5, TimeUnit.SECONDS);
            outcome.complete("success");
            assertThat(publicationThread.get(5, TimeUnit.SECONDS)).isEqualTo("permit-test-worker");
            executor.onRoleChanged(0L, 1L);
            executor.onRoleChanged(1L, 2L);
            stages.execute(() -> { throw new AssertionError("stale stage executed"); });
            assertThat(invalidationThread.get(5, TimeUnit.SECONDS)).isEqualTo("permit-test-worker");
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void learnerExecutionRejectsSecondPendingStageAndAcceptsNextStageAfterConsumption() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { occupied.countDown(); await(release); });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            java.util.concurrent.Executor stages = executor.reserveLearnerExecution(() -> {
                throw new AssertionError("current execution invalidated");
            });
            CompletableFuture<String> first = new CompletableFuture<>();
            CompletableFuture<String> next = new CompletableFuture<>();
            stages.execute(() -> first.complete(Thread.currentThread().getName()));
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> stages.execute(() -> {
                throw new AssertionError("duplicate pending stage executed");
            })).isInstanceOf(java.util.concurrent.RejectedExecutionException.class);
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS)).isEqualTo("permit-test-worker");
            stages.execute(() -> next.complete(Thread.currentThread().getName()));
            assertThat(next.get(5, TimeUnit.SECONDS)).isEqualTo("permit-test-worker");
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void diagnosticTasksCoalesceAndUseWorkerWithoutReservingPermit() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { occupied.countDown(); await(release); });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        AtomicInteger calls = new AtomicInteger();
        CompletableFuture<String> ranOn = new CompletableFuture<>();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            Runnable diagnostic = () -> {
                calls.incrementAndGet();
                ranOn.complete(Thread.currentThread().getName());
            };
            for (int index = 0; index < 5000; index++) {
                executor.diagnoseMembership(diagnostic);
            }
            assertThat(worker.getQueue()).hasSize(1);
            assertThat(calls).hasValue(0);
            assertThat(executor.undrainedPermitCount()).isZero();
            release.countDown();
            assertThat(ranOn.get(5, TimeUnit.SECONDS)).isEqualTo("permit-test-worker");
            worker.shutdown();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(1);
        } finally {
            release.countDown(); worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"RESTART", "FAULT", "CLOSE"})
    void invalidatedDiagnosticTaskNeverExecutes(String boundary) throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { occupied.countDown(); await(release); });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        AtomicInteger calls = new AtomicInteger();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            executor.diagnoseMembership(calls::incrementAndGet);
            switch (boundary) {
                case "RESTART" -> { executor.onRoleChanged(0L, 1L); executor.onRoleChanged(1L, 2L); }
                case "FAULT" -> executor.faultLatch().fail(new IllegalStateException("controlled fault"));
                case "CLOSE" -> executor.close();
                default -> throw new AssertionError(boundary);
            }
            release.countDown();
            worker.shutdown();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(0);
            assertThat(executor.undrainedPermitCount()).isZero();
        } finally {
            release.countDown(); worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void drainedSendTaskSharesReceiverAndRunsOnlyOnceOnWorker() throws Exception {
        ThreadPoolExecutor worker = worker();
        CompletableFuture<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> response =
                new CompletableFuture<>();
        CountDownLatch entered = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "send-task");
            BrokerId receiver = new BrokerId("b");
            CompletionStage<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> first =
                    executor.retransmitDrained(permit.operationId(), receiver, () -> {
                        assertThat(Thread.currentThread().getName()).isEqualTo("permit-test-worker");
                        calls.incrementAndGet(); entered.countDown(); return response;
                    });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(executor.retransmitDrained(permit.operationId(), receiver,
                    () -> { throw new AssertionError("duplicate send"); }) == first).isTrue();
            assertThat(executor.retransmitDrained(permit.operationId(), new BrokerId("c"),
                    () -> { throw new AssertionError("receiver replacement"); })
                    .toCompletableFuture().get(5, TimeUnit.SECONDS).status())
                    .isEqualTo(com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.UNKNOWN);
            assertThat(first.toCompletableFuture()).isNotDone();
            response.complete(confirmationResponse());
            assertThat(first.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(confirmationResponse());
            assertThat(calls).hasValue(1);
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void duplicateConfirmationWaitsDoNotAccumulateRelays() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { occupied.countDown(); await(release); });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "bounded-waits");
            com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand fact = confirmationFact(permit);
            AtomicInteger calls = new AtomicInteger();
            com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult response = confirmationResponse();
            BrokerMembershipExecutor.ConfirmationRound round = executor.confirm(permit, com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipExecutionReportFrom(fact), stages -> {
                calls.incrementAndGet(); return CompletableFuture.completedFuture(response);
            });
            java.lang.reflect.Field field = round.getClass().getDeclaredField("completion");
            field.setAccessible(true);
            CompletableFuture<?> source = (CompletableFuture<?>) field.get(round);
            int dependents = source.getNumberOfDependents();
            assertThat(dependents).isEqualTo(1);
            assertThat(round.waiting() instanceof CompletableFuture<?>).isFalse();
            for (int request = 0; request < 128; request++) {
                BrokerMembershipExecutor.ConfirmationRound duplicate = executor.confirm(permit, com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipExecutionReportFrom(fact),
                        stages -> { throw new AssertionError("duplicate confirmation action"); });
                assertThat(duplicate.waiting()).isSameAs(round.waiting());
                assertThat(duplicate.stage() == round.stage()).isTrue();
                assertThat(duplicate.waiting().cancel(request % 2 == 0)).isFalse();
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> duplicate.waiting().get(1, TimeUnit.NANOSECONDS))
                        .isInstanceOf(TimeoutException.class);
                assertThat(source.getNumberOfDependents()).isEqualTo(dependents);
            }
            assertThat(round.waiting().isDone()).isFalse();
            assertThat(round.waiting().isCancelled()).isFalse();
            release.countDown();
            assertThat(round.waiting().get(5, TimeUnit.SECONDS)).isEqualTo(response);
            assertThat(round.stage().toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(response);
            assertThat(calls).hasValue(1);
        } finally { release.countDown(); worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void confirmationWaitingViewsKeepCompletedRoundsIsolated() throws Exception {
        ThreadPoolExecutor worker = worker();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "round-isolation");
            CompletableFuture<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> firstSource = new CompletableFuture<>();
            CompletableFuture<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> nextSource = new CompletableFuture<>();
            BrokerMembershipExecutor.ConfirmationRound first = executor.confirm(permit, com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipExecutionReportFrom(confirmationFact(permit)), stages -> firstSource);
            firstSource.complete(confirmationResponse());
            assertThat(first.waiting().get(5, TimeUnit.SECONDS)).isEqualTo(confirmationResponse());
            BrokerMembershipExecutor.ConfirmationRound next = executor.confirm(permit, com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipExecutionReportFrom(confirmationFact(permit)), stages -> nextSource);
            assertThat(next.waiting()).isNotSameAs(first.waiting());
            assertThat(next.waiting().isDone()).isFalse();
            assertThat(firstSource.completeExceptionally(new IllegalStateException("late old result"))).isFalse();
            assertThat(next.waiting().isDone()).isFalse();
            IllegalStateException failure = new IllegalStateException("new round failure");
            nextSource.completeExceptionally(failure);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> next.waiting().get(5, TimeUnit.SECONDS)).hasCause(failure);
            assertThat(first.waiting().get()).isEqualTo(confirmationResponse());
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    private static com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand confirmationFact(BrokerMembershipPermit permit) {
        return new com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand("cluster", "a", "a",
                permit.operationId().value(), permit.stepId(), permit.permitId(), "a", permit.executorIncarnation(), "digest",
                com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand.Result.TARGET_APPLIED, "a");
    }

    private static com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult confirmationResponse() {
        return new com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult(
                com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.COMMITTED, null, "confirmed");
    }

    @Test
    void duplicateExecuteRequestsShareReadOnlyViewWithoutRepeatingNative() throws Exception {
        ThreadPoolExecutor worker = worker();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "shared-execute");
            CompletableFuture<MembershipConfigurationResult> nativeResult = new CompletableFuture<>();
            CountDownLatch entered = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            CompletionStage<BrokerMembershipPermit.Result> first = executor.execute(permit, () -> {
                calls.incrementAndGet(); entered.countDown(); return nativeResult;
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<BrokerMembershipPermit.Result> cancelled = first.toCompletableFuture();
            assertThat(cancelled.cancel(false)).isTrue();
            CompletableFuture<BrokerMembershipPermit.Result> forged = first.toCompletableFuture();
            assertThat(forged.complete(BrokerMembershipPermit.Result.TARGET_NOT_APPLIED)).isTrue();
            for (int index = 0; index < 128; index++) {
                assertThat(executor.execute(permit, () -> {
                    throw new AssertionError("duplicate native entry");
                }) == first).as("pending execute must reuse one read-only view").isTrue();
            }
            assertThat(first.toCompletableFuture()).isNotDone();
            nativeResult.complete(applied());
            assertThat(first.toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
            assertThat(executor.execute(permit, () -> { throw new AssertionError("completed native replay"); }) == first)
                    .isTrue();
            assertThat(cancelled).isCancelled();
            assertThat(calls).hasValue(1);
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void duplicateConfirmationRequestsShareReadOnlyViewAndStartExplicitNewRound() throws Exception {
        ThreadPoolExecutor worker = worker();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "shared-confirm");
            com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand fact =
                    new com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand(
                            "cluster", "a", "a", permit.operationId().value(), permit.stepId(), permit.permitId(),
                            "a", permit.executorIncarnation(), "digest",
                            com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand.Result.TARGET_APPLIED, "a");
            CompletableFuture<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> pending =
                    new CompletableFuture<>();
            CountDownLatch started = new CountDownLatch(1);
            AtomicInteger calls = new AtomicInteger();
            CompletionStage<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> first =
                    executor.confirm(permit, com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipExecutionReportFrom(fact), stages -> {
                        calls.incrementAndGet(); started.countDown(); return pending;
                    }).stage();
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(first.toCompletableFuture().cancel(false)).isTrue();
            for (int index = 0; index < 128; index++) {
                assertThat(executor.confirm(permit, com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipExecutionReportFrom(fact), stages -> {
                    throw new AssertionError("duplicate confirmation chain");
                }).stage() == first).as("pending confirmation must reuse one read-only view").isTrue();
            }
            assertThat(first.toCompletableFuture()).isNotDone();
            com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult response =
                    new com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult(
                            com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult.Status.COMMITTED, null, "confirmed");
            pending.complete(response);
            assertThat(first.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(response);
            IllegalStateException failure = new IllegalStateException("explicit confirmation failed");
            CompletionStage<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> next =
                    executor.confirm(permit, com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipExecutionReportFrom(fact), stages -> {
                        calls.incrementAndGet(); return CompletableFuture.failedFuture(failure);
                    }).stage();
            assertThat(next == first).as("completed round must not suppress explicit retry").isFalse();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> next.toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .hasCause(failure);
            assertThat(first.toCompletableFuture().join()).isEqualTo(response);
            assertThat(calls).hasValue(2);
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void completedNativeResultReplayRoutesNodeReportingOnlyThroughWorker() throws Exception {
        ThreadPoolExecutor worker = worker();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "late-report");
            assertThat(executor.execute(permit, () -> CompletableFuture.completedFuture(applied()))
                    .toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
            com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter adapter =
                    org.mockito.Mockito.mock(com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter.class);
            com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter.LocalBroker local =
                    org.mockito.Mockito.mock(com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter.LocalBroker.class);
            org.mockito.Mockito.when(adapter.getLocalBroker()).thenReturn(local);
            AtomicInteger nodeReads = new AtomicInteger();
            org.mockito.Mockito.when(local.isLeader()).thenAnswer(ignored -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("permit-test-worker");
                nodeReads.incrementAndGet(); return false;
            });
            org.mockito.Mockito.when(adapter.getLeaderEndpoint()).thenAnswer(ignored -> {
                assertThat(Thread.currentThread().getName()).isEqualTo("permit-test-worker");
                nodeReads.incrementAndGet(); return null;
            });
            BrokerMembershipAppService service = new BrokerMembershipAppService(adapter,
                    org.mockito.Mockito.mock(com.co.kc.imchat.broker.adapter.cluster.BrokerPeerAdapter.class), new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                    new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
            java.lang.reflect.Method replay = BrokerMembershipAppService.class.getDeclaredMethod(
                    "executeMembershipStep", BrokerMembershipPermit.class);
            replay.setAccessible(true);
            for (String threadName : List.of("controlled-caller", "controlled-fsm")) {
                CompletableFuture<Object> result = new CompletableFuture<>();
                Thread thread = Thread.ofPlatform().name(threadName).start(() -> {
                    try { result.complete(((CompletionStage<?>) replay.invoke(service, permit)).toCompletableFuture().get(5, TimeUnit.SECONDS)); }
                    catch (Exception failure) { result.completeExceptionally(failure); }
                });
                org.assertj.core.api.Assertions.assertThatCode(() -> result.get(6, TimeUnit.SECONDS)).doesNotThrowAnyException();
                thread.join(1000); assertThat(thread.isAlive()).isFalse();
            }
            assertThat(nodeReads).hasValue(4);
            org.mockito.Mockito.verify(adapter, org.mockito.Mockito.never()).changeMembershipConfiguration(org.mockito.ArgumentMatchers.any());
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void startupRequiresLeaderEventAndShutdownOrFaultCanNeverReopenAdmission() throws Exception {
        ThreadPoolExecutor worker = worker();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> reserve(executor, "before-role"))
                    .isInstanceOf(RejectedExecutionException.class);
            BrokerMembershipPermit absent = new BrokerMembershipPermit("absent", new ClusterOperationId("op"), "step",
                    new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120")), Set.of()), new BrokerId("a"),
                    executor.incarnation(), BrokerMembershipPermit.Result.UNKNOWN);
            AtomicInteger calls = new AtomicInteger();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> executor.execute(absent, () -> {
                calls.incrementAndGet(); return CompletableFuture.completedFuture(applied());
            }).toCompletableFuture().join()).hasCauseInstanceOf(SecurityException.class);
            executor.onRoleChanged(3L, 1L);
            BrokerMembershipPermit permit = reserve(executor, "after-start");
            assertThat(executor.execute(permit, () -> {
                calls.incrementAndGet(); return CompletableFuture.completedFuture(applied());
            }).toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
            executor.faultLatch().fail(new IllegalStateException("permanent"));
            executor.onRoleChanged(4L, 2L);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> reserve(executor, "after-fault"))
                    .isInstanceOf(RejectedExecutionException.class);
            executor.close(); executor.onRoleChanged(5L, 3L);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> reserve(executor, "after-shutdown"))
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(calls).hasValue(1);
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void synchronousConfirmationFailureDoesNotLoseOtherPendingDrainOrRepeatNative() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { occupied.countDown(); await(release); });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit fact = reserve(executor, "exception-fact");
            BrokerMembershipPermit nativePermit = reserve(executor, "exception-native");
            com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand request =
                    new com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand(
                            "cluster", "a", "a", fact.operationId().value(), fact.stepId(), fact.permitId(), "a",
                            fact.executorIncarnation(), "digest",
                            com.co.kc.imchat.broker.model.cqrs.command.AcceptMembershipPermitDrainedCommand.Result.TARGET_APPLIED, "a");
            CompletionStage<com.co.kc.imchat.broker.domain.cluster.model.MembershipConfirmationResult> failed =
                    executor.confirm(fact, com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipExecutionReportFrom(request), stages -> { throw new IllegalStateException("synchronous phase failure"); }).stage();
            AtomicInteger calls = new AtomicInteger();
            CompletionStage<BrokerMembershipPermit.Result> rejected = executor.execute(nativePermit, () -> {
                calls.incrementAndGet(); return CompletableFuture.completedFuture(applied());
            });
            release.countDown();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> failed.toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .hasCauseInstanceOf(IllegalStateException.class);
            assertThat(rejected.toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .isEqualTo(BrokerMembershipPermit.Result.TARGET_NOT_APPLIED);
            assertThat(executor.execute(nativePermit, () -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(applied()); })
                    .toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(BrokerMembershipPermit.Result.TARGET_NOT_APPLIED);
            assertThat(calls).hasValue(0);
        } finally { release.countDown(); worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(booleans = {false, true})
    void synchronousNativeFailureKeepsUnknownPendingWithoutReenteringNative(boolean faulted) throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch release = new CountDownLatch(1);
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "native-throw");
            AtomicInteger calls = new AtomicInteger();
            CountDownLatch entered = new CountDownLatch(1);
            CompletionStage<BrokerMembershipPermit.Result> result = executor.execute(permit, () -> {
                calls.incrementAndGet();
                entered.countDown();
                await(release);
                throw new IllegalStateException("native entry failed before returning a stage");
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            AtomicInteger continuations = new AtomicInteger();
            result.thenRun(continuations::incrementAndGet);
            if (faulted) {
                executor.faultLatch().fail(new IllegalStateException("permanent FSM failure"));
            }
            release.countDown();
            worker.shutdown();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(result.toCompletableFuture()).isNotDone();
            assertThat(continuations).hasValue(0);
            assertThat(executor.execute(permit, () -> {
                calls.incrementAndGet();
                return CompletableFuture.completedFuture(applied());
            }) == result).isTrue();
            assertThat(calls).hasValue(1);
            assertThat(executor.undrainedPermitCount()).isEqualTo(1L);
        } finally {
            release.countDown();
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void confirmationCannotCreateFactSlotWithoutNativeReservation() {
        ThreadPoolExecutor worker = worker();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = new BrokerMembershipPermit("missing", new ClusterOperationId("op"), "step",
                    new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120")), Set.of()), new BrokerId("a"),
                    executor.incarnation(), BrokerMembershipPermit.Result.UNKNOWN);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> executor.nativeConfirmationExecutor(permit))
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(executor.undrainedPermitCount()).isZero();
        } finally {
            worker.shutdownNow();
        }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {0, 1})
    void runtimeAndExecutorShareFirstFaultAfterPartialRoleAccountingAndNeverReopen(
            int accountedSlots, @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        com.alipay.sofa.jraft.Node node = org.mockito.Mockito.mock(com.alipay.sofa.jraft.Node.class);
        java.util.concurrent.atomic.AtomicReference<com.alipay.sofa.jraft.StateMachine> callback =
                new java.util.concurrent.atomic.AtomicReference<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (org.mockito.MockedStatic<com.alipay.sofa.jraft.RaftServiceFactory> services =
                org.mockito.Mockito.mockStatic(com.alipay.sofa.jraft.RaftServiceFactory.class);
             org.mockito.MockedStatic<com.alipay.sofa.jraft.rpc.RaftRpcServerFactory> servers =
                org.mockito.Mockito.mockStatic(com.alipay.sofa.jraft.rpc.RaftRpcServerFactory.class)) {
            services.when(() -> com.alipay.sofa.jraft.RaftServiceFactory.createRaftNode(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(node);
            org.mockito.Mockito.when(node.init(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
                callback.set(((com.alipay.sofa.jraft.option.NodeOptions) invocation.getArgument(0)).getFsm());
                return true;
            });
            com.alipay.sofa.jraft.rpc.RpcServer rpc = org.mockito.Mockito.mock(com.alipay.sofa.jraft.rpc.RpcServer.class);
            org.mockito.Mockito.when(rpc.init(null)).thenReturn(true);
            servers.when(() -> com.alipay.sofa.jraft.rpc.RaftRpcServerFactory.createRaftRpcServer(
                    org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                    org.mockito.ArgumentMatchers.any())).thenReturn(rpc);
            try (RealPermitMember member = new RealPermitMember("fault-group", 0, List.of("127.0.0.1:18120"), directory)) {
                member.executor.onRoleChanged(1L, 0L);
                ConsensusMembership target = new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120")), Set.of());
                java.util.Map<String, BrokerMembershipPermit> permits = new java.util.LinkedHashMap<>();
                for (String id : List.of("accounted", "reserved", "queued", "entered", "drained")) {
                    member.executor.reserve(new ClusterOperationId("fault-op"), "step", id, target);
                    permits.put(id, new BrokerMembershipPermit(id, new ClusterOperationId("fault-op"), "step", target,
                            new BrokerId(member.id), member.executor.incarnation(), BrokerMembershipPermit.Result.UNKNOWN));
                }
                AtomicInteger nativeCalls = new AtomicInteger();
                CompletableFuture<MembershipConfigurationResult> nativeResult = new CompletableFuture<>();
                CompletionStage<BrokerMembershipPermit.Result> inFlight = member.executor.execute(permits.get("entered"), () -> {
                    nativeCalls.incrementAndGet(); entered.countDown(); await(release); return nativeResult;
                });
                assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
                AtomicInteger forbiddenCalls = new AtomicInteger();
                CompletionStage<BrokerMembershipPermit.Result> queued = member.executor.execute(permits.get("queued"), () -> {
                    forbiddenCalls.incrementAndGet(); return CompletableFuture.completedFuture(applied());
                });
                member.executor.closePermit("drained");
                AtomicInteger continuations = new AtomicInteger();
                queued.thenRun(continuations::incrementAndGet);
                inFlight.thenRun(continuations::incrementAndGet);
                member.executor.confirmationExecutor(permits.get("drained")).execute(continuations::incrementAndGet);

                // worker 已停在 supplier，等待槽已满；仅此静止点替换测试账本。
                java.lang.reflect.Field field = BrokerMembershipExecutor.class.getDeclaredField("slots");
                field.setAccessible(true);
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> original = (java.util.Map<String, Object>) field.get(member.executor);
                java.util.Map<String, Object> ordered = new java.util.LinkedHashMap<>();
                permits.keySet().forEach(id -> ordered.put(id, original.get(id)));
                Object pendingTask = slotField(ordered.get("drained"), "confirmationTask");
                assertThat(pendingTask).isInstanceOf(Runnable.class);
                RuntimeException first = new IllegalStateException("role traversal interrupted after " + accountedSlots);
                AtomicInteger traversed = new AtomicInteger();
                java.util.concurrent.atomic.AtomicBoolean armed = new java.util.concurrent.atomic.AtomicBoolean(true);
                java.util.Map<String, Object> interrupted = new java.util.LinkedHashMap<>(ordered) {
                    @Override
                    public java.util.Collection<Object> values() {
                        java.util.Collection<Object> values = super.values();
                        if (!armed.get()) { return values; }
                        return new java.util.AbstractCollection<>() {
                            @Override public int size() { return values.size(); }
                            @Override public java.util.Iterator<Object> iterator() {
                                java.util.Iterator<Object> delegate = values.iterator();
                                return new java.util.Iterator<>() {
                                    @Override public boolean hasNext() {
                                        if (traversed.get() == accountedSlots && armed.compareAndSet(true, false)) { throw first; }
                                        return delegate.hasNext();
                                    }
                                    @Override public Object next() { traversed.incrementAndGet(); return delegate.next(); }
                                };
                            }
                        };
                    }
                };
                field.set(member.executor, interrupted);
                CompletableFuture<Void> stopped = new CompletableFuture<>();
                Thread fsm = Thread.ofPlatform().name("controlled-fsm").start(() -> {
                    try { callback.get().onLeaderStop(com.alipay.sofa.jraft.Status.OK()); stopped.complete(null); }
                    catch (Throwable failure) { stopped.completeExceptionally(failure); }
                });
                stopped.get(5, TimeUnit.SECONDS); fsm.join(1000);
                assertThat(fsm.isAlive()).isFalse();
                assertThat(traversed).hasValue(accountedSlots);
                assertThat(slotField(interrupted.get("accounted"), "state").toString())
                        .isEqualTo(accountedSlots == 0 ? "OPEN" : "DRAINED");
                assertThat(slotField(interrupted.get("reserved"), "state").toString()).isEqualTo("OPEN");
                assertThat(slotField(interrupted.get("queued"), "queued")).isEqualTo(true);
                assertThat(slotField(interrupted.get("entered"), "entered")).isEqualTo(true);
                assertThat(slotField(interrupted.get("entered"), "returned")).isEqualTo(false);
                assertThat(slotField(interrupted.get("drained"), "state").toString()).isEqualTo("DRAINED");
                assertThat(slotField(interrupted.get("drained"), "confirmationTask")).isSameAs(pendingTask);
                assertThat(slotField(interrupted.get("drained"), "published")).isEqualTo(false);
                assertThat(member.runtime.roleFault()).isSameAs(member.executor.faultLatch());
                assertThat(member.runtime.roleFault().failure()).containsSame(first);
                assertThat(continuations).hasValue(0);
                assertThat(nativeCalls).hasValue(1);
                callback.get().onLeaderStart(7L);
                assertThat(member.runtime.roleFault().failure()).containsSame(first);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> reserve(member.executor, "new"))
                        .isInstanceOf(RejectedExecutionException.class);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> member.executor.confirmationExecutor(permits.get("reserved")))
                        .isInstanceOf(RejectedExecutionException.class);
                org.assertj.core.api.Assertions.assertThatThrownBy(() -> member.runtime.confirmMembershipPermitDrain(new byte[]{1})
                        .toCompletableFuture().join()).hasCauseInstanceOf(IllegalStateException.class);
                for (String id : List.of("queued", "reserved", "entered")) {
                    member.executor.execute(permits.get(id), () -> {
                        forbiddenCalls.incrementAndGet(); return CompletableFuture.completedFuture(applied());
                    });
                }
                assertThat(nativeResult).isNotCancelled();
                release.countDown(); nativeResult.complete(applied());
                member.worker.shutdown();
                assertThat(member.worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                assertThat(forbiddenCalls).hasValue(0);
                assertThat(nativeCalls).hasValue(1);
                assertThat(continuations).hasValue(0);
                assertThat(inFlight.toCompletableFuture()).isNotDone();
                assertThat(slotField(interrupted.get("entered"), "knownResult"))
                        .isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
                assertThat(slotField(interrupted.get("drained"), "confirmationTask")).isSameAs(pendingTask);
                assertThat(interrupted.keySet()).containsExactlyElementsOf(permits.keySet());
                org.mockito.Mockito.verify(node, org.mockito.Mockito.never()).readIndex(
                        org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
            } finally { release.countDown(); }
        } finally { release.countDown(); }
    }

    private static Object slotField(Object slot, String name) throws ReflectiveOperationException {
        java.lang.reflect.Field field = slot.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(slot);
    }

    @Test
    void revokedQueuedNativeExitWakesDrainAfterRolePumpWasRejected() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { occupied.countDown(); await(release); });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "role-revoked");
            CompletionStage<BrokerMembershipPermit.Result> result = executor.execute(permit, () -> {
                throw new AssertionError("revoked native entered");
            });
            executor.onRoleChanged(0L, 1L);
            assertThat(result.toCompletableFuture()).isNotDone();
            release.countDown();
            org.assertj.core.api.Assertions.assertThatCode(() ->
                    assertThat(result.toCompletableFuture().get(2, TimeUnit.SECONDS))
                            .isEqualTo(BrokerMembershipPermit.Result.TARGET_NOT_APPLIED)).doesNotThrowAnyException();
        } finally { release.countDown(); worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void realAppliedNativeReturnHeldAcrossElectionRequiresExactDrainBeforeNextTarget(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        String group = "permit-real-" + directory.getFileName();
        java.util.List<String> endpoints = new java.util.ArrayList<>();
        for (int index = 0; index < 3; index++) {
            try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
                endpoints.add("127.0.0.1:" + socket.getLocalPort());
            }
        }
        java.util.List<RealPermitMember> members = new java.util.ArrayList<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            for (int index = 0; index < 3; index++) {
                members.add(new RealPermitMember(group, index, endpoints, directory.resolve("n" + index)));
            }
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).until(() ->
                    members.stream().filter(member -> member.runtime.isLeader()).count() == 1);
            RealPermitMember oldLeader = members.stream().filter(member -> member.runtime.isLeader()).findFirst().orElseThrow();
            RealPermitMember nextLeader = members.stream().filter(member -> member != oldLeader).findFirst().orElseThrow();
            RealPermitMember target = members.stream().filter(member -> member != oldLeader && member != nextLeader).findFirst().orElseThrow();
            BrokerCluster snapshot = oldLeader.state.getCluster();
            ClusterOperationId operationId = new ClusterOperationId("real-demote");
            BrokerClusterDecision begin = snapshot.demoteVoter(VoterDemotionIntent.builder().operationId(operationId)
                    .commandDigest(new CommandDigest("real-digest")).expectedRevision(snapshot.revision())
                    .brokerId(new BrokerId(target.id)).minimumVoterCount(new MinimumVoterCount(1))
                    .changedAt(java.time.Instant.EPOCH).build());
            assertThat(begin.accepted()).isTrue();
            assertThat(oldLeader.submit(com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE
                    .beginCommandFrom(begin.operation())).accepted()).isTrue();
            BrokerMembershipStep first = oldLeader.state.findOperation(operationId).orElseThrow().currentMembershipStep();
            oldLeader.executor.reserve(operationId, first.stepId(), "real-old", first.target());
            assertThat(oldLeader.submit(new com.co.kc.imchat.broker.model.cqrs.command.RegisterMembershipPermitCommand(
                    operationId, first.stepId(), "real-old", new BrokerId(oldLeader.id), oldLeader.executor.incarnation(),
                    oldLeader.state.getCluster().revision())).accepted()).isTrue();
            BrokerMembershipPermit permit = oldLeader.state.findOperation(operationId).orElseThrow().currentMembershipStep().currentPermit();
            AtomicInteger oldCalls = new AtomicInteger();
            CompletionStage<BrokerMembershipPermit.Result> oldOutcome = oldLeader.executor.execute(permit, () -> {
                oldCalls.incrementAndGet();
                MembershipConfigurationResult applied = com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE.membershipConfigurationResultFrom(
                        oldLeader.runtime.changeConfiguration(nativeTarget(permit.target())).toCompletableFuture().join());
                assertThat(applied.status()).isEqualTo(MembershipConfigurationResult.Status.APPLIED);
                entered.countDown(); await(release);
                return CompletableFuture.completedFuture(applied);
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            com.alipay.sofa.jraft.entity.PeerId nextPeer = new com.alipay.sofa.jraft.entity.PeerId();
            assertThat(nextPeer.parse(nextLeader.endpoint)).isTrue();
            com.alipay.sofa.jraft.entity.PeerId oldPeer = new com.alipay.sofa.jraft.entity.PeerId();
            assertThat(oldPeer.parse(oldLeader.endpoint)).isTrue();
            assertThat(com.alipay.sofa.jraft.NodeManager.getInstance().get(group, oldPeer)
                    .transferLeadershipTo(nextPeer).isOk()).isTrue();
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).until(nextLeader.runtime::isLeader);
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() ->
                    nextLeader.state.findOperation(operationId).map(value -> value.currentMembershipStep().currentPermit() != null).orElse(false));
            BrokerMembershipStep second = nextLeader.state.findOperation(operationId).orElseThrow().membershipSteps().getLast();
            assertThat(nextLeader.submit(new com.co.kc.imchat.broker.model.cqrs.command.RegisterMembershipPermitCommand(
                    operationId, second.stepId(), "too-soon", new BrokerId(nextLeader.id), nextLeader.executor.incarnation(),
                    nextLeader.state.getCluster().revision())).accepted()).isFalse();
            // 原生 APPLIED 已真实发生，但 supplier 未返回，许可不能提前排空。
            assertThat(oldOutcome.toCompletableFuture()).isNotDone();
            release.countDown();
            assertThat(oldOutcome.toCompletableFuture().get(10, TimeUnit.SECONDS))
                    .isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
            assertThat(nextLeader.submit(com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE
                    .confirmPermitCommandFrom(first, permit, BrokerMembershipPermit.Result.TARGET_APPLIED)).accepted()).isTrue();
            BrokerMembershipStep finalStep = nextLeader.state.findOperation(operationId).orElseThrow().currentMembershipStep();
            assertThat(finalStep.stepId()).isEqualTo(second.stepId());
            nextLeader.executor.reserve(operationId, finalStep.stepId(), "real-final", finalStep.target());
            assertThat(nextLeader.submit(new com.co.kc.imchat.broker.model.cqrs.command.RegisterMembershipPermitCommand(
                    operationId, finalStep.stepId(), "real-final", new BrokerId(nextLeader.id), nextLeader.executor.incarnation(),
                    nextLeader.state.getCluster().revision())).accepted()).isTrue();
            BrokerMembershipPermit finalPermit = nextLeader.state.findOperation(operationId).orElseThrow().currentMembershipStep().currentPermit();
            assertThat(nextLeader.executor.execute(finalPermit,
                    () -> nextLeader.runtime.changeConfiguration(nativeTarget(finalPermit.target()))
                            .thenApply(com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE::membershipConfigurationResultFrom))
                    .toCompletableFuture().get(10, TimeUnit.SECONDS)).isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
            assertThat(nextLeader.submit(com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE
                    .confirmPermitCommandFrom(finalStep, finalPermit, BrokerMembershipPermit.Result.TARGET_APPLIED)).accepted()).isTrue();
            assertThat(oldLeader.executor.execute(permit, () -> { throw new AssertionError("old native replay"); })
                    .toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
            assertThat(oldCalls).hasValue(1);
            assertThat(nextLeader.runtime.leaderConfiguration()).isEqualTo(nativeTarget(finalPermit.target()));
            assertThat(nextLeader.submit(com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE
                    .commitCommandFrom(nextLeader.state.findOperation(operationId).orElseThrow(),
                            nextLeader.state.getCluster().revision(), java.time.Instant.EPOCH)).accepted()).isTrue();
            assertThat(nextLeader.runtime.leaderConfiguration()).isEqualTo(nativeTarget(finalPermit.target()));
        } finally {
            release.countDown();
            for (RealPermitMember member : members) { member.close(); }
        }
    }

    @Test
    void realRuntimeElectionCannotAdvanceUndrainedPermitOrReplayOldConfiguration(
            @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory) throws Exception {
        String group = "permit-real-" + directory.getFileName();
        java.util.List<String> endpoints = new java.util.ArrayList<>();
        for (int index = 0; index < 3; index++) {
            try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
                endpoints.add("127.0.0.1:" + socket.getLocalPort());
            }
        }
        java.util.List<RealPermitMember> members = new java.util.ArrayList<>();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            for (int index = 0; index < 3; index++) {
                members.add(new RealPermitMember(group, index, endpoints, directory.resolve("n" + index)));
            }
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).until(() ->
                    members.stream().filter(member -> member.runtime.isLeader()).count() == 1);
            RealPermitMember oldLeader = members.stream().filter(member -> member.runtime.isLeader()).findFirst().orElseThrow();
            RealPermitMember nextLeader = members.stream().filter(member -> member != oldLeader).findFirst().orElseThrow();
            RealPermitMember target = members.stream().filter(member -> member != oldLeader && member != nextLeader).findFirst().orElseThrow();
            BrokerCluster snapshot = oldLeader.state.getCluster();
            ClusterOperationId operationId = new ClusterOperationId("real-demote");
            BrokerClusterDecision begin = snapshot.demoteVoter(VoterDemotionIntent.builder().operationId(operationId)
                    .commandDigest(new CommandDigest("real-digest")).expectedRevision(snapshot.revision())
                    .brokerId(new BrokerId(target.id)).minimumVoterCount(new MinimumVoterCount(1))
                    .changedAt(java.time.Instant.EPOCH).build());
            assertThat(begin.accepted()).isTrue();
            assertThat(oldLeader.submit(com.co.kc.imchat.broker.transformer.adapter.BrokerClusterRaftTransformer.INSTANCE
                    .beginCommandFrom(begin.operation())).accepted()).isTrue();
            BrokerMembershipStep first = oldLeader.state.findOperation(operationId).orElseThrow().currentMembershipStep();
            oldLeader.executor.reserve(operationId, first.stepId(), "real-old", first.target());
            assertThat(oldLeader.submit(new com.co.kc.imchat.broker.model.cqrs.command.RegisterMembershipPermitCommand(
                    operationId, first.stepId(), "real-old", new BrokerId(oldLeader.id), oldLeader.executor.incarnation(),
                    oldLeader.state.getCluster().revision())).accepted()).isTrue();
            BrokerMembershipPermit permit = oldLeader.state.findOperation(operationId).orElseThrow().currentMembershipStep().currentPermit();
            AtomicInteger oldCalls = new AtomicInteger();
            CompletionStage<BrokerMembershipPermit.Result> oldOutcome = oldLeader.executor.execute(permit, () -> {
                entered.countDown(); await(release); oldCalls.incrementAndGet();
                return oldLeader.runtime.changeConfiguration(nativeTarget(permit.target()))
                        .thenApply(com.co.kc.imchat.broker.transformer.adapter.BrokerMembershipTransformer.INSTANCE::membershipConfigurationResultFrom);
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            com.alipay.sofa.jraft.entity.PeerId nextPeer = new com.alipay.sofa.jraft.entity.PeerId();
            assertThat(nextPeer.parse(nextLeader.endpoint)).isTrue();
            com.alipay.sofa.jraft.entity.PeerId oldPeer = new com.alipay.sofa.jraft.entity.PeerId();
            assertThat(oldPeer.parse(oldLeader.endpoint)).isTrue();
            assertThat(com.alipay.sofa.jraft.NodeManager.getInstance().get(group, oldPeer)
                    .transferLeadershipTo(nextPeer).isOk()).isTrue();
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(15)).until(nextLeader.runtime::isLeader);
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(10)).until(() ->
                    nextLeader.state.findOperation(operationId).map(value -> value.currentMembershipStep().currentPermit() != null).orElse(false));
            BrokerMembershipStep second = nextLeader.state.findOperation(operationId).orElseThrow().membershipSteps().getLast();
            assertThat(nextLeader.submit(new com.co.kc.imchat.broker.model.cqrs.command.RegisterMembershipPermitCommand(
                    operationId, second.stepId(), "too-soon", new BrokerId(nextLeader.id), nextLeader.executor.incarnation(),
                    nextLeader.state.getCluster().revision())).accepted()).isFalse();
            release.countDown();
            org.awaitility.Awaitility.await().atMost(java.time.Duration.ofSeconds(5)).until(() -> oldCalls.get() == 1);
            // NOT_LEADER 仍是 UNKNOWN：不能伪造旧调用排空；本反例首先证明真实换届后的永久阻塞。
            assertThat(oldOutcome.toCompletableFuture()).isNotDone();
            oldLeader.executor.execute(permit, () -> { throw new AssertionError("old native replay"); });
            assertThat(oldCalls).hasValue(1);
            assertThat(nextLeader.runtime.leaderConfiguration().voters()).containsExactlyInAnyOrderElementsOf(endpoints);
            assertThat(nextLeader.submit(new com.co.kc.imchat.broker.model.cqrs.command.RegisterMembershipPermitCommand(
                    operationId, second.stepId(), "still-blocked", new BrokerId(nextLeader.id), nextLeader.executor.incarnation(),
                    nextLeader.state.getCluster().revision())).accepted()).isFalse();
        } finally {
            release.countDown();
            for (RealPermitMember member : members) { member.close(); }
        }
    }

    private static com.co.kc.imchat.plugin.raft.model.RaftConfiguration nativeTarget(ConsensusMembership target) {
        return new com.co.kc.imchat.plugin.raft.model.RaftConfiguration(
                target.voters().stream().map(MembershipEndpoint::value).toList(),
                target.learners().stream().map(MembershipEndpoint::value).toList());
    }

    private static final class RealPermitMember implements AutoCloseable {
        private final String id;
        private final String endpoint;
        private final ThreadPoolExecutor worker = worker();
        private final BrokerMembershipExecutor executor;
        private final com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine state;
        private final com.co.kc.imchat.plugin.raft.core.RaftRuntime<
                com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand,
                com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult> runtime;

        private RealPermitMember(String group, int index, java.util.List<String> endpoints, java.nio.file.Path directory) throws Exception {
            id = "b" + index;
            endpoint = endpoints.get(index);
            executor = new BrokerMembershipExecutor(new BrokerId(id), worker);
            java.util.List<com.co.kc.imchat.broker.sdk.cluster.BrokerNode> nodes = new java.util.ArrayList<>();
            java.util.Map<String, String> bindings = new java.util.HashMap<>();
            for (int n = 0; n < endpoints.size(); n++) {
                nodes.add(new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("b" + n, endpoints.get(n),
                        com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER,
                        com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState.ACTIVE));
                bindings.put("b" + n, endpoints.get(n));
            }
            state = new com.co.kc.imchat.broker.support.raft.statemachine.RaftBrokerClusterStateMachine(group,
                    new com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState(0L, 0L,
                            com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm.RENDEZVOUS_HASH, nodes), 100, bindings);
            runtime = new com.co.kc.imchat.plugin.raft.core.RaftRuntime<>(
                    com.co.kc.imchat.plugin.raft.model.RaftNodeSettings.builder().clusterId(group).groupId(group).nodeId(id)
                            .endpoint(endpoint).dataDirectory(directory).voters(endpoints).learners(List.of())
                            .electionTimeoutMillis(500).snapshotIntervalSeconds(0).commandTimeoutMillis(5000)
                            .snapshotFileName("state.bin").build(),
                    com.co.kc.imchat.broker.support.raft.codec.BrokerClusterCommandCodec.INSTANCE, state, executor);
            assertThat(runtime.roleFault()).isSameAs(executor.faultLatch());
        }

        private com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult submit(
                com.co.kc.imchat.broker.model.cqrs.command.BrokerClusterConsensusCommand command) throws Exception {
            com.co.kc.imchat.plugin.raft.model.RaftResult<com.co.kc.imchat.broker.adapter.cluster.consensus.BrokerClusterConsensusResult> result =
                    runtime.submit(command).toCompletableFuture().get(10, TimeUnit.SECONDS);
            assertThat(result.applied()).isTrue();
            return result.value();
        }

        @Override
        public void close() throws Exception {
            executor.close(); runtime.close(); worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void remoteFactSlotsSharePermanentCapacityAndNeverAuthorizeNative() throws Exception {
        ThreadPoolExecutor worker = worker();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit fact = new BrokerMembershipPermit("remote", new ClusterOperationId("op"), "step",
                    new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120")), Set.of()), new BrokerId("removed"), "old-process", BrokerMembershipPermit.Result.UNKNOWN);
            java.util.concurrent.Executor stages = executor.confirmationExecutor(fact);
            CompletableFuture<String> thread = new CompletableFuture<>();
            stages.execute(() -> thread.complete(Thread.currentThread().getName()));
            assertThat(thread.get(5, TimeUnit.SECONDS)).isEqualTo("permit-test-worker");
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> executor.execute(fact,
                    () -> { throw new AssertionError("remote fact acquired native authority"); }).toCompletableFuture().join())
                    .hasCauseInstanceOf(SecurityException.class);
            for (int index = 1; index < 4096; index++) { reserve(executor, "local-" + index); }
            executor.confirmationExecutor(fact);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> reserve(executor, "overflow"))
                    .isInstanceOf(RejectedExecutionException.class);
            BrokerMembershipPermit conflict = new BrokerMembershipPermit("remote", fact.operationId(), fact.stepId(),
                    fact.target(), fact.executorBrokerId(), "other-process", BrokerMembershipPermit.Result.UNKNOWN);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> executor.confirmationExecutor(conflict))
                    .isInstanceOf(SecurityException.class);
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void revokedQueuedTaskExitPublishesOtherPendingDrainAfterFullQueueRejection() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { occupied.countDown(); await(release); });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit first = reserve(executor, "exit-pending");
            BrokerMembershipPermit second = reserve(executor, "exit-queued");
            CompletionStage<BrokerMembershipPermit.Result> pending = executor.execute(first, () -> {
                throw new AssertionError("revoked native");
            });
            executor.closePermit(first.permitId());
            // 先前 pump 占据等待槽；第二许可被拒绝但其 DRAINED 必须保留。
            CompletionStage<BrokerMembershipPermit.Result> other = executor.execute(second, () -> {
                throw new AssertionError("rejected native");
            });
            assertThat(pending.toCompletableFuture()).isNotDone();
            assertThat(other.toCompletableFuture()).isNotDone();
            release.countDown();
            assertThat(pending.toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .isEqualTo(BrokerMembershipPermit.Result.TARGET_NOT_APPLIED);
            assertThat(other.toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .isEqualTo(BrokerMembershipPermit.Result.TARGET_NOT_APPLIED);
        } finally { release.countDown(); worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void saturatedCallbackRetainsDrainUntilExplicitReplayWithoutRepeatingNative() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch queuedExited = new CountDownLatch(1);
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "saturated-drain");
            AtomicInteger calls = new AtomicInteger();
            CompletableFuture<MembershipConfigurationResult> callback = new CompletableFuture<>();
            CompletionStage<BrokerMembershipPermit.Result> outcome = executor.execute(permit, () -> {
                calls.incrementAndGet(); entered.countDown(); return callback;
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            worker.execute(() -> { occupied.countDown(); await(release); });
            assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
            worker.execute(queuedExited::countDown);
            callback.complete(applied());
            assertThat(outcome.toCompletableFuture()).isNotDone();
            release.countDown();
            assertThat(queuedExited.await(5, TimeUnit.SECONDS)).isTrue();
            CompletionStage<BrokerMembershipPermit.Result> replay = executor.execute(permit, () -> {
                throw new AssertionError("fact replay invoked native");
            });
            org.assertj.core.api.Assertions.assertThatCode(() ->
                    replay.toCompletableFuture().get(5, TimeUnit.SECONDS)).doesNotThrowAnyException();
            assertThat(replay.toCompletableFuture().join()).isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
            assertThat(calls).hasValue(1);
        } finally { release.countDown(); worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void callbackOnlyRecordsDrainAndNeverPublishesApplicationContinuationInline() throws Exception {
        ThreadPoolExecutor worker = worker();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "callback-boundary");
            CompletableFuture<MembershipConfigurationResult> callback = new CompletableFuture<>();
            CountDownLatch entered = new CountDownLatch(1);
            CompletionStage<BrokerMembershipPermit.Result> outcome = executor.execute(permit, () -> {
                entered.countDown(); return callback;
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<String> continuationThread = outcome.thenApply(ignored -> Thread.currentThread().getName())
                    .toCompletableFuture();
            Thread source = Thread.ofPlatform().name("simulated-native-callback").start(() -> callback.complete(applied()));
            source.join(5000L);
            assertThat(source.isAlive()).isFalse();
            assertThat(continuationThread.get(5, TimeUnit.SECONDS)).isEqualTo("permit-test-worker");
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void roleStopRevokesQueuedCapabilityAndSameTermRestartCannotReviveIt() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { occupied.countDown(); await(release); });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            executor.onRoleChanged(7L, 1L);
            BrokerMembershipPermit old = reserve(executor, "old-generation");
            AtomicInteger calls = new AtomicInteger();
            executor.execute(old, () -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(applied()); });
            executor.onRoleChanged(0L, 2L);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> reserve(executor, "stopped"))
                    .isInstanceOf(RejectedExecutionException.class);
            executor.onRoleChanged(7L, 3L);
            reserve(executor, "fresh-generation");
            executor.execute(old, () -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(applied()); });
            release.countDown();
            worker.shutdown();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(0);
        } finally { release.countDown(); worker.shutdownNow(); }
    }

    @Test
    void sharedRoleFaultBlocksReservedAndQueuedNativeWithoutPublishingOnFsmThread() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { occupied.countDown(); await(release); });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit queued = reserve(executor, "fault-queued");
            BrokerMembershipPermit reserved = reserve(executor, "fault-reserved");
            AtomicInteger calls = new AtomicInteger();
            CompletionStage<BrokerMembershipPermit.Result> observation = executor.execute(queued, () -> {
                calls.incrementAndGet(); return CompletableFuture.completedFuture(applied());
            });
            AtomicInteger continuations = new AtomicInteger();
            observation.thenRun(continuations::incrementAndGet);
            executor.faultLatch().fail(new IllegalStateException("listener failed before all-slot accounting"));
            executor.onRoleChanged(9L, 5L);
            executor.execute(reserved, () -> { calls.incrementAndGet(); return CompletableFuture.completedFuture(applied()); });
            assertThat(continuations).hasValue(0);
            release.countDown();
            worker.shutdown();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls).hasValue(0);
            assertThat(observation.toCompletableFuture()).isNotDone();
        } finally { release.countDown(); worker.shutdownNow(); }
    }

    @Test
    void roleFaultPermanentlyRejectsReservationAfterLaterStart() {
        ThreadPoolExecutor worker = worker();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            IllegalStateException first = new IllegalStateException("partial accounting");
            executor.faultLatch().fail(first);
            executor.onRoleChanged(4L, 3L);
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> reserve(executor, "after-fault"))
                    .isInstanceOf(RejectedExecutionException.class);
            assertThat(executor.faultLatch().failure()).containsSame(first);
        } finally { worker.shutdownNow(); }
    }

    @Test
    void committedRegistrationDoesNotInvokeNativeOnFsmCompletionThread() throws Exception {
        com.co.kc.imchat.broker.support.StatefulClusterAdapterFixture fixture =
                new com.co.kc.imchat.broker.support.StatefulClusterAdapterFixture(
                        new com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState(0L, 0L,
                                com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                                List.of(new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("a", "a:18020",
                                                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER,
                                                com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState.ACTIVE),
                                        new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("b", "b:18020",
                                                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.DISCOVERED,
                                                com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState.INACTIVE))));
        fixture.stateMachine.restore(new com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot(
                com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot.CURRENT_FORMAT_VERSION,
                "cluster-a", fixture.stateMachine.committedState(), List.of(), null, java.util.Map.of("a", "a:18120")));
        org.mockito.Mockito.when(fixture.adapter.getLocalBroker().getId()).thenReturn(new BrokerId("a"));
        org.mockito.Mockito.when(fixture.adapter.getLocalBroker().isLeader()).thenReturn(true);
        com.co.kc.imchat.broker.adapter.cluster.BrokerRaftAdapter.LocalBroker local = fixture.adapter.getLocalBroker();
        org.mockito.Mockito.when(local.getLeaderTerm()).thenReturn(java.util.Optional.of(
                new com.co.kc.imchat.broker.domain.cluster.model.LeaderTerm(1L)));
        org.mockito.Mockito.doReturn(new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120")), Set.of()))
                .when(fixture.adapter).readLeaderMembershipConfiguration();
        org.mockito.Mockito.doAnswer(invocation -> CompletableFuture.completedFuture(
                fixture.stateMachine.apply(invocation.getArgument(0))))
                .when(fixture.adapter).registerMembershipPermit(org.mockito.ArgumentMatchers.any());
        CopyOnWriteArrayList<String> nativeThreads = new CopyOnWriteArrayList<>();
        CountDownLatch entered = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            nativeThreads.add(Thread.currentThread().getName()); entered.countDown();
            return new CompletableFuture<MembershipConfigurationResult>();
        }).when(fixture.adapter).changeMembershipConfiguration(org.mockito.ArgumentMatchers.any());
        ThreadPoolExecutor worker = worker();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipAppService service = new BrokerMembershipAppService(fixture.adapter, fixture.peerAdapter,
                    new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                    new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
            service.addLearner(new com.co.kc.imchat.broker.model.cqrs.command.AddLearnerCommand("join", "b", 0L, "b:18120"));
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(nativeThreads).containsExactly("permit-test-worker");
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void queuedCommittedPermitCannotStartNativeAfterLeadershipLoss() throws Exception {
        verifyQueuedCommittedPermit(true);
    }

    @Test
    void queuedCommittedPermitStartsNativeWithoutLeadershipLoss() throws Exception {
        verifyQueuedCommittedPermit(false);
    }

    private void verifyQueuedCommittedPermit(boolean loseLeadership) throws Exception {
        com.co.kc.imchat.broker.support.StatefulClusterAdapterFixture fixture =
                new com.co.kc.imchat.broker.support.StatefulClusterAdapterFixture(
                        new com.co.kc.imchat.broker.sdk.cluster.BrokerClusterState(0L, 0L,
                                com.co.kc.imchat.broker.sdk.cluster.BrokerSelectionAlgorithm.RENDEZVOUS_HASH,
                                List.of(new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("a", "a:18020",
                                                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.VOTER,
                                                com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState.ACTIVE),
                                        new com.co.kc.imchat.broker.sdk.cluster.BrokerNode("b", "b:18020",
                                                com.co.kc.imchat.broker.sdk.cluster.BrokerMembershipRole.DISCOVERED,
                                                com.co.kc.imchat.broker.sdk.cluster.BrokerRoutingState.INACTIVE))));
        fixture.stateMachine.restore(new com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot(
                com.co.kc.imchat.broker.support.raft.model.BrokerClusterSnapshot.CURRENT_FORMAT_VERSION,
                "cluster-a", fixture.stateMachine.committedState(), List.of(), null, java.util.Map.of("a", "a:18120")));
        org.mockito.Mockito.when(fixture.adapter.getLocalBroker().getId()).thenReturn(new BrokerId("a"));
        org.mockito.Mockito.when(fixture.adapter.getLocalBroker().isLeader()).thenReturn(true);
        org.mockito.Mockito.when(fixture.adapter.getLocalBroker().getLeaderTerm()).thenReturn(java.util.Optional.of(new LeaderTerm(1L)));
        org.mockito.Mockito.doReturn(new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120")), Set.of()))
                .when(fixture.adapter).readLeaderMembershipConfiguration();
        org.mockito.Mockito.doAnswer(invocation -> CompletableFuture.completedFuture(
                fixture.stateMachine.apply(invocation.getArgument(0))))
                .when(fixture.adapter).registerMembershipPermit(org.mockito.ArgumentMatchers.any());
        AtomicInteger nativeCalls = new AtomicInteger();
        CountDownLatch nativeEntered = new CountDownLatch(1);
        org.mockito.Mockito.doAnswer(invocation -> {
            nativeCalls.incrementAndGet(); nativeEntered.countDown();
            return CompletableFuture.completedFuture(applied());
        }).when(fixture.adapter).changeMembershipConfiguration(org.mockito.ArgumentMatchers.any());
        ThreadPoolExecutor worker = worker();
        CountDownLatch queued = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        java.util.concurrent.atomic.AtomicReference<BrokerMembershipPermit> captured = new java.util.concurrent.atomic.AtomicReference<>();
        java.util.concurrent.atomic.AtomicReference<CompletionStage<BrokerMembershipPermit.Result>> outcome = new java.util.concurrent.atomic.AtomicReference<>();
        BrokerMembershipExecutor executor = org.mockito.Mockito.spy(new BrokerMembershipExecutor(new BrokerId("a"), worker));
        org.mockito.Mockito.doAnswer(invocation -> {
            BrokerMembershipPermit permit = invocation.getArgument(0);
            CompletionStage<BrokerMembershipPermit.Result> result = (CompletionStage<BrokerMembershipPermit.Result>) invocation.callRealMethod();
            captured.set(permit); outcome.set(result); queued.countDown(); await(release);
            return result;
        }).when(executor).execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        try (executor) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipAppService service = new BrokerMembershipAppService(fixture.adapter, fixture.peerAdapter,
                    new MinimumVoterCount(1), executor, new com.co.kc.imchat.broker.support.monitoring.MembershipDiagnosticMetrics(),
                    new MembershipOperationService(new InMemoryLearnerConfirmationAttemptRepository(), new InMemoryMembershipOperationAttemptRepository()));
            service.addLearner(new com.co.kc.imchat.broker.model.cqrs.command.AddLearnerCommand("join", "b", 0L, "b:18120"));
            assertThat(queued.await(5, TimeUnit.SECONDS)).as("public workflow registered and queued native permit").isTrue();
            BrokerMembershipPermit permit = captured.get();
            BrokerClusterOperation stored = fixture.adapter.getClusterSnapshot().findOperation(new ClusterOperationId("join")).orElseThrow();
            assertThat(stored.currentMembershipStep().currentPermit()).isEqualTo(permit);
            assertThat(permit.executorIncarnation()).isEqualTo(executor.incarnation());
            assertThat(permit.executorBrokerId()).isEqualTo(new BrokerId("a"));
            assertThat(worker.getQueue()).hasSize(1);
            assertThat(nativeCalls).hasValue(0);
            if (loseLeadership) {
                org.mockito.Mockito.when(fixture.adapter.getLocalBroker().isLeader()).thenReturn(false);
                executor.onRoleChanged(0L, 1L);
            }
            release.countDown();
            assertThat(outcome.get().toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(loseLeadership
                    ? BrokerMembershipPermit.Result.TARGET_NOT_APPLIED : BrokerMembershipPermit.Result.TARGET_APPLIED);
            assertThat(nativeCalls).hasValue(loseLeadership ? 0 : 1);
            if (loseLeadership) {
                executor.onRoleChanged(2L, 2L);
                assertThat(executor.execute(permit, () -> { throw new AssertionError("closed permit reopened"); })
                        .toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(BrokerMembershipPermit.Result.TARGET_NOT_APPLIED);
                assertThat(nativeCalls).hasValue(0);
            } else { assertThat(nativeEntered.await(5, TimeUnit.SECONDS)).isTrue(); }
        } finally { release.countDown(); worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void committedReservationExecutesExactlyOnceOffRegistrationThread() throws Exception {
        ThreadPoolExecutor worker = worker();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "p");
            CopyOnWriteArrayList<String> calls = new CopyOnWriteArrayList<>();
            CompletableFuture<MembershipConfigurationResult> callback = new CompletableFuture<>();
            CountDownLatch entered = new CountDownLatch(1);
            CompletionStage<BrokerMembershipPermit.Result> outcome = executor.execute(permit, () -> {
                calls.add(Thread.currentThread().getName());
                entered.countDown();
                return callback;
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).as("committed permit reaches native worker").isTrue();
            executor.execute(permit, () -> { throw new AssertionError("duplicate native entry"); });
            assertThat(calls).containsExactly("permit-test-worker");
            assertThat(outcome.toCompletableFuture()).isNotDone();
            callback.complete(applied());
            assertThat(outcome.toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
            assertThat(executor.execute(permit, () -> { throw new AssertionError("closed slot reopened"); })
                    .toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
        } finally {
            worker.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void closingRevokesQueuedTargetWithoutCallingNative() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        worker.execute(() -> { occupied.countDown(); await(release); });
        assertThat(occupied.await(5, TimeUnit.SECONDS)).isTrue();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "queued");
            AtomicInteger calls = new AtomicInteger();
            CompletionStage<BrokerMembershipPermit.Result> outcome = executor.execute(permit, () -> {
                calls.incrementAndGet(); return CompletableFuture.completedFuture(applied());
            });
            executor.closePermit(permit.permitId());
            assertThat(outcome.toCompletableFuture()).isNotDone();
            assertThat(calls).hasValue(0);
            release.countDown();
            assertThat(outcome.toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .isEqualTo(BrokerMembershipPermit.Result.TARGET_NOT_APPLIED);
            worker.shutdown();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(calls.get()).isZero();
            assertThat(executor.execute(permit, () -> { throw new AssertionError("revoked target"); })
                    .toCompletableFuture().join()).isEqualTo(BrokerMembershipPermit.Result.TARGET_NOT_APPLIED);
        } finally { release.countDown(); worker.shutdownNow(); }
    }

    @Test
    void closureBeforeSynchronousReturnCannotDrainAndCallbackThenThrowRemainsUnknown() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch callbackDelivered = new CountDownLatch(1);
        CountDownLatch returnAllowed = new CountDownLatch(1);
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "callback-first");
            CompletableFuture<MembershipConfigurationResult> nativeCompletion = new CompletableFuture<>();
            CompletionStage<BrokerMembershipPermit.Result> outcome = executor.execute(permit, () -> {
                nativeCompletion.complete(applied());
                callbackDelivered.countDown();
                await(returnAllowed);
                throw new IllegalStateException("synchronous throw after closure");
            });
            assertThat(callbackDelivered.await(5, TimeUnit.SECONDS)).isTrue();
            executor.closePermit(permit.permitId());
            assertThat(outcome.toCompletableFuture()).isNotDone();
            returnAllowed.countDown();
            worker.shutdown();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(outcome.toCompletableFuture()).isNotDone();
        } finally { returnAllowed.countDown(); worker.shutdownNow(); }
    }

    @Test
    void closingDuringNativeEntryWaitsForReturnAndSuccessfulClosure() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch returnAllowed = new CountDownLatch(1);
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "active");
            CompletableFuture<MembershipConfigurationResult> callback = new CompletableFuture<>();
            CompletionStage<BrokerMembershipPermit.Result> outcome = executor.execute(permit, () -> {
                entered.countDown(); await(returnAllowed); return callback;
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            executor.closePermit(permit.permitId());
            callback.complete(applied());
            assertThat(outcome.toCompletableFuture()).isNotDone();
            returnAllowed.countDown();
            assertThat(outcome.toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
        } finally { returnAllowed.countDown(); worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void requestTimeoutDoesNotReleaseUnderlyingNativeOrPermit() throws Exception {
        ThreadPoolExecutor worker = worker();
        CountDownLatch entered = new CountDownLatch(1);
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "timeout");
            CompletableFuture<MembershipConfigurationResult> callback = new CompletableFuture<>();
            CompletionStage<BrokerMembershipPermit.Result> outcome = executor.execute(permit, () -> {
                entered.countDown(); return callback;
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<BrokerMembershipPermit.Result> request = outcome.toCompletableFuture();
            request.completeExceptionally(new TimeoutException("request budget"));
            executor.closePermit(permit.permitId());
            assertThat(callback).isNotDone();
            assertThat(executor.execute(permit, () -> { throw new AssertionError("timeout retried native"); })
                    .toCompletableFuture()).isNotDone();
            callback.complete(applied());
            assertThat(executor.execute(permit, () -> { throw new AssertionError("late retry"); })
                    .toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
        } finally { worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    @Test
    void busyAndNotLeaderNeverInferNativeDrain() throws Exception {
        for (MembershipConfigurationResult.Status status : List.of(MembershipConfigurationResult.Status.BUSY,
                MembershipConfigurationResult.Status.NOT_LEADER, MembershipConfigurationResult.Status.UNAVAILABLE)) {
            ThreadPoolExecutor worker = worker();
            try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
                BrokerMembershipPermit permit = reserve(executor, status.name());
                CompletionStage<BrokerMembershipPermit.Result> outcome = executor.execute(permit,
                        () -> CompletableFuture.completedFuture(new MembershipConfigurationResult(status, 1, null, "unknown work")));
                worker.shutdown();
                assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
                executor.closePermit(permit.permitId());
                assertThat(outcome.toCompletableFuture()).isNotDone();
            } finally { worker.shutdownNow(); }
        }
    }

    @Test
    void rejectionRevokesFutureNativeAbility() throws Exception {
        ThreadPoolExecutor worker = worker();
        worker.shutdown();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "rejected");
            CompletionStage<BrokerMembershipPermit.Result> first = executor.execute(permit,
                    () -> { throw new AssertionError("rejected work ran"); });
            assertThat(first.toCompletableFuture()).isNotDone();
            assertThat(executor.execute(permit, () -> { throw new AssertionError("rejected work retried"); })
                    .toCompletableFuture()).isNotDone();
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> reserve(executor, permit.permitId()))
                    .isInstanceOf(RejectedExecutionException.class);
        }
    }

    @Test
    void reservationsIncludingUnknownCommitsHavePermanent4096Limit() {
        ThreadPoolExecutor worker = worker();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit first = null;
            for (int index = 0; index < 4096; index++) {
                BrokerMembershipPermit permit = reserve(executor, "capacity-" + index);
                if (index == 0) { first = permit; }
            }
            executor.closePermit(first.permitId());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> reserve(executor, "overflow"))
                    .isInstanceOf(RejectedExecutionException.class).hasMessageContaining("full");
            assertThat(executor.execute(first, () -> { throw new AssertionError("closed record evicted"); })
                    .toCompletableFuture().join()).isEqualTo(BrokerMembershipPermit.Result.TARGET_NOT_APPLIED);
        } finally { worker.shutdownNow(); }
    }

    @Test
    void missingReservationAndRestartedIncarnationNeverReconstructExecutionAuthority() throws Exception {
        ThreadPoolExecutor oldWorker = worker();
        BrokerMembershipPermit old;
        try (BrokerMembershipExecutor first = new BrokerMembershipExecutor(new BrokerId("a"), oldWorker)) {
            first.onRoleChanged(1L, 0L);
            old = reserve(first, "old");
        }
        ThreadPoolExecutor newWorker = worker();
        try (BrokerMembershipExecutor restarted = new BrokerMembershipExecutor(new BrokerId("a"), newWorker)) {
            assertThat(restarted.incarnation()).isNotEqualTo(old.executorIncarnation());
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> restarted.execute(old,
                    () -> { throw new AssertionError("old process authority revived"); }).toCompletableFuture().join())
                    .hasCauseInstanceOf(SecurityException.class);
        } finally {
            oldWorker.shutdownNow(); newWorker.shutdownNow();
            assertThat(oldWorker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(newWorker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void fsmRegistrationCompletionDoesNotWaitForNodeLockWhileLeaderStopQueueIsFull() throws Exception {
        ThreadPoolExecutor worker = worker();
        ExecutorService fsm = Executors.newSingleThreadExecutor();
        ExecutorService nodeOwner = Executors.newSingleThreadExecutor();
        java.util.concurrent.locks.ReentrantLock nodeLock = new java.util.concurrent.locks.ReentrantLock();
        ArrayBlockingQueue<String> fsmQueue = new ArrayBlockingQueue<>(1);
        fsmQueue.add("pending-event");
        CountDownLatch nodeHeld = new CountDownLatch(1);
        CountDownLatch nativeAttempt = new CountDownLatch(1);
        CountDownLatch registrationApplied = new CountDownLatch(1);
        CountDownLatch beginLeaderStop = new CountDownLatch(1);
        CopyOnWriteArrayList<String> nativeHistory = new CopyOnWriteArrayList<>();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            BrokerMembershipPermit permit = reserve(executor, "fsm-pressure");
            Future<?> owner = nodeOwner.submit(() -> {
                nodeLock.lock();
                try {
                    nodeHeld.countDown(); await(beginLeaderStop);
                    try { fsmQueue.put("LEADER_STOP"); }
                    catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
                } finally { nodeLock.unlock(); }
            });
            assertThat(nodeHeld.await(5, TimeUnit.SECONDS)).isTrue();
            CompletableFuture<BrokerMembershipPermit> registration = new CompletableFuture<>();
            CompletionStage<BrokerMembershipPermit.Result> outcome = registration.thenCompose(committed ->
                    executor.execute(committed, () -> {
                        nativeAttempt.countDown();
                        nodeLock.lock();
                        try { nativeHistory.add("old-target"); return CompletableFuture.completedFuture(applied()); }
                        finally { nodeLock.unlock(); }
                    }));
            Future<?> applied = fsm.submit(() -> {
                registration.complete(permit);
                registrationApplied.countDown();
                try {
                    assertThat(fsmQueue.take()).isEqualTo("pending-event");
                    assertThat(fsmQueue.take()).isEqualTo("LEADER_STOP");
                    executor.closePermit(permit.permitId());
                } catch (InterruptedException interrupted) { throw new AssertionError(interrupted); }
            });
            assertThat(nativeAttempt.await(5, TimeUnit.SECONDS)).isTrue();
            // FSM 必须能完成登记 continuation，才能消费队列并让 Node 锁持有者退出。
            assertThat(registrationApplied.await(5, TimeUnit.SECONDS)).isTrue();
            beginLeaderStop.countDown();
            owner.get(5, TimeUnit.SECONDS);
            applied.get(5, TimeUnit.SECONDS);
            assertThat(outcome.toCompletableFuture().get(5, TimeUnit.SECONDS))
                    .isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
            assertThat(nativeHistory).containsExactly("old-target");
            executor.execute(permit, () -> { throw new AssertionError("old target repeated after leadership change"); });
            assertThat(nativeHistory).containsExactly("old-target");
        } finally {
            beginLeaderStop.countDown(); fsmQueue.clear();
            worker.shutdownNow(); fsm.shutdownNow(); nodeOwner.shutdownNow();
            assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(fsm.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
            assertThat(nodeOwner.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void admittedOldTargetBlocksNextPersistentTargetAcrossLeadershipChange() throws Exception {
        BrokerCluster cluster = BrokerCluster.builder().revision(new ClusterRevision(0L)).epoch(new RoutingEpoch(0L))
                .algorithm(BrokerSelectionAlgorithm.RENDEZVOUS_HASH)
                .brokers(List.of(new BrokerNode(new BrokerId("a"), new BrokerRouteEndpoint("a:18020"),
                                BrokerMembershipRole.VOTER, BrokerTrafficState.ACTIVE),
                        new BrokerNode(new BrokerId("b"), new BrokerRouteEndpoint("b:18020"),
                                BrokerMembershipRole.VOTER, BrokerTrafficState.READY)))
                .operations(List.of()).operationHistoryCapacity(new OperationHistoryCapacity(100))
                .membershipEndpoints(java.util.Map.of(new BrokerId("a"), new MembershipEndpoint("a:18120"),
                        new BrokerId("b"), new MembershipEndpoint("b:18120"))).build();
        ClusterOperationId operationId = new ClusterOperationId("demote");
        assertThat(cluster.demoteVoter(VoterDemotionIntent.builder().operationId(operationId)
                .commandDigest(new CommandDigest("digest")).expectedRevision(cluster.revision())
                .brokerId(new BrokerId("b")).minimumVoterCount(new MinimumVoterCount(1))
                .changedAt(java.time.Instant.EPOCH).build()).accepted()).isTrue();
        BrokerMembershipStep first = cluster.findOperation(operationId).orElseThrow().currentMembershipStep();
        ThreadPoolExecutor worker = worker();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        CopyOnWriteArrayList<ConsensusMembership> history = new CopyOnWriteArrayList<>();
        try (BrokerMembershipExecutor executor = new BrokerMembershipExecutor(new BrokerId("a"), worker)) {
            executor.onRoleChanged(1L, 0L);
            executor.reserve(operationId, first.stepId(), "old", first.target());
            assertThat(cluster.registerMembershipPermit(operationId, first.stepId(), "old", new BrokerId("a"),
                    executor.incarnation(), cluster.revision()).accepted()).isTrue();
            BrokerMembershipPermit old = cluster.findOperation(operationId).orElseThrow().currentMembershipStep().currentPermit();
            CompletionStage<BrokerMembershipPermit.Result> outcome = executor.execute(old, () -> {
                entered.countDown(); await(resume); history.add(old.target());
                return CompletableFuture.completedFuture(applied());
            });
            assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
            // 新 Leader 只有持久化快照，没有旧 incarnation 的本机执行能力。
            BrokerCluster newLeader = cluster.snapshot();
            BrokerMembershipStep next = newLeader.findOperation(operationId).orElseThrow().membershipSteps().getLast();
            executor.closePermit(old.permitId());
            assertThat(outcome.toCompletableFuture()).isNotDone();
            assertThat(newLeader.registerMembershipPermit(operationId, next.stepId(), "new", new BrokerId("b"),
                    "new-leader-process", newLeader.revision()).accepted()).isFalse();
            assertThat(history).isEmpty();
            resume.countDown();
            assertThat(outcome.toCompletableFuture().get(5, TimeUnit.SECONDS)).isEqualTo(BrokerMembershipPermit.Result.TARGET_APPLIED);
            // 即便本机排空，确认提交前崩溃仍不能从持久化 UNKNOWN 越过旧许可。
            assertThat(newLeader.registerMembershipPermit(operationId, next.stepId(), "after-crash", new BrokerId("b"),
                    "new-leader-process", newLeader.revision()).accepted()).isFalse();
            assertThat(newLeader.confirmMembershipExecution(new MembershipExecutionConfirmation(operationId, first.stepId(), old.permitId(), old.executorBrokerId(),
                    old.executorIncarnation(), first.targetDigest(), outcome.toCompletableFuture().join())).accepted()).isTrue();
            assertThat(newLeader.registerMembershipPermit(operationId, next.stepId(), "new", new BrokerId("b"),
                    "new-leader-process", newLeader.revision()).accepted()).isTrue();
            history.add(next.target());
            executor.execute(old, () -> { history.add(old.target()); return CompletableFuture.completedFuture(applied()); });
            assertThat(history).containsExactly(first.target(), next.target());
            assertThat(history.getLast().learners()).containsExactly(new MembershipEndpoint("b:18120"));
        } finally { resume.countDown(); worker.shutdownNow(); assertThat(worker.awaitTermination(5, TimeUnit.SECONDS)).isTrue(); }
    }

    private static void await(CountDownLatch latch) {
        try { if (!latch.await(5, TimeUnit.SECONDS)) { throw new AssertionError("barrier was not released"); } }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }

    private static ThreadPoolExecutor worker() {
        return new ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1),
                task -> new Thread(task, "permit-test-worker"), new ThreadPoolExecutor.AbortPolicy());
    }

    private static BrokerMembershipPermit reserve(BrokerMembershipExecutor executor, String id) {
        ClusterOperationId operation = new ClusterOperationId("op-" + id);
        ConsensusMembership target = new ConsensusMembership(Set.of(new MembershipEndpoint("a:18120")), Set.of());
        executor.reserve(operation, "step", id, target);
        return new BrokerMembershipPermit(id, operation, "step", target, new BrokerId("a"),
                executor.incarnation(), BrokerMembershipPermit.Result.UNKNOWN);
    }

    private static MembershipConfigurationResult applied() {
        return new MembershipConfigurationResult(MembershipConfigurationResult.Status.APPLIED, 0, null, null);
    }
}
