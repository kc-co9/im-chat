package com.co.kc.imchat.plugin.bolt;

import com.alipay.remoting.exception.RemotingException;
import com.alipay.remoting.rpc.RpcClient;
import com.co.kc.imchat.plugin.bolt.core.BoltClientInvoker;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException;
import com.co.kc.imchat.plugin.bolt.exception.BoltInvocationException.Failure;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import javax.net.ssl.SSLHandshakeException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BoltAuthenticatedFailureTest {
    @Test
    void onlyTypedTransportCausesProveTargetUnreachable() throws Exception {
        assertFailure(new RemotingException("connection timeout"), Failure.LOCAL);
        assertFailure(new RemotingException("wrapped", new ConnectException()), Failure.TRANSPORT_UNAVAILABLE);
        assertFailure(new RemotingException("wrapped", new java.net.SocketTimeoutException()),
                Failure.TRANSPORT_UNAVAILABLE);
        assertFailure(new RemotingException("wrapped", new SSLHandshakeException("untrusted")),
                Failure.AUTHENTICATION);
        assertFailure(new IllegalArgumentException("local timeout"), Failure.LOCAL);
    }

    @Test
    void interruptRemainsLocalAndRestoresThreadFlag() throws Exception {
        try {
            assertFailure(new InterruptedException("cancelled"), Failure.LOCAL);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void latePoolResultIsLocalAndNeverDispatched() throws Exception {
        assertLateAcquisition(null);
    }

    @Test
    void latePoolConnectFailureIsLocalAndRetainsOriginalCause() throws Exception {
        assertLateAcquisition(new RemotingException("wrapped", new ConnectException()));
    }

    private void assertLateAcquisition(Exception acquisitionFailure) throws Exception {
        RpcClient client = mock(RpcClient.class);
        java.util.concurrent.CountDownLatch entered = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CountDownLatch release = new java.util.concurrent.CountDownLatch(1);
        java.util.concurrent.CompletableFuture<BoltInvocationException> result = new java.util.concurrent.CompletableFuture<>();
        com.alipay.remoting.Connection connection = mock(com.alipay.remoting.Connection.class);
        when(client.getConnection(anyString(), anyInt())).thenAnswer(invocation -> {
            entered.countDown();
            release.await();
            if (acquisitionFailure != null) {
                throw acquisitionFailure;
            }
            return connection;
        });
        Thread caller = new Thread(() -> result.complete(catchThrowableOfType(() -> new BoltClientInvoker(client, System::nanoTime)
                .invokeAuthenticated("host:1", "service", "op", "request", String.class, 30,
                        "spiffe://test/one", "spiffe://test/"), BoltInvocationException.class)));
        caller.start();
        try {
            assertThat(entered.await(3, java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            assertThatThrownByTimeout(result);
        } finally {
            release.countDown();
        }
        BoltInvocationException error = result.get(3, java.util.concurrent.TimeUnit.SECONDS);
        assertThat(error.failure()).isEqualTo(Failure.LOCAL);
        if (acquisitionFailure != null) {
            assertThat(error.getCause()).isSameAs(acquisitionFailure);
        }
        org.mockito.Mockito.verifyNoInteractions(connection);
        org.mockito.Mockito.verify(client, org.mockito.Mockito.never()).invokeSync(
                org.mockito.ArgumentMatchers.any(com.alipay.remoting.Connection.class),
                org.mockito.ArgumentMatchers.any(), anyInt());
        caller.join(3000);
        assertThat(caller.isAlive()).isFalse();
    }

    private void assertThatThrownByTimeout(java.util.concurrent.CompletableFuture<?> result) {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> result.get(60, java.util.concurrent.TimeUnit.MILLISECONDS))
                .isInstanceOf(java.util.concurrent.TimeoutException.class);
    }

    private void assertFailure(Exception cause, Failure expected) throws Exception {
        RpcClient client = mock(RpcClient.class);
        when(client.getConnection(anyString(), anyInt())).thenThrow(cause);
        // 此处只验证异常归因；预算耗尽由专门的迟到获取与TLS测试覆盖。
        BoltInvocationException failure = catchThrowableOfType(() -> new BoltClientInvoker(client, () -> 0L)
                .invokeAuthenticated("host:1", "service", "op", "request", String.class, 500,
                        "spiffe://test/one", "spiffe://test/"), BoltInvocationException.class);
        assertThat(failure.failure()).isEqualTo(expected);
        assertThat(failure.getCause()).isSameAs(cause);
    }
}
