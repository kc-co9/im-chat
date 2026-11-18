package com.co.kc.imchat.management.ops.cli.auth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.ByteArrayInputStream;
import java.io.FilterInputStream;
import java.io.InputStream;
import java.util.concurrent.CountDownLatch;
import org.mockito.MockedConstruction;
import org.mockito.AdditionalAnswers;

import java.io.IOException;
import java.net.BindException;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import java.util.concurrent.ExecutionException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertThrows;

class LoopbackAuthorizationCallbackTest {

    @Test
    void validCallbackReturnsDecodedCodeAndReleasesListener() throws Exception {
        int port = availablePort();
        CliLoginSession session = CliLoginSession.start();
        try (ExecutorService executor = Executors.newSingleThreadExecutor();
             LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(port)) {
            Future<String> result = executor.submit(() -> callback.awaitAuthorizationCode(session));
            String encodedState = "%" + Integer.toHexString(session.state().charAt(0)) + session.state().substring(1);
            String response = request(port, "GET /oauth/callback?code=authorization%2Bcode&state=" + encodedState
                    + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n");
            assertTrue(response.startsWith("HTTP/1.1 200"), response);
            assertEquals("authorization+code", result.get(3, TimeUnit.SECONDS));
            IllegalStateException repeated = assertThrows(IllegalStateException.class,
                    () -> callback.awaitAuthorizationCode(session));
            assertEquals("Authorization callback already awaited", repeated.getMessage());
            try (ServerSocket rebound = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
                assertEquals(port, rebound.getLocalPort());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST /oauth/callback", "GET /other", "GET /oauth/callback/", "GET http://127.0.0.1/oauth/callback"})
    void unexpectedMethodOrPathCannotCompleteLogin(String requestLine) throws Exception {
        int port = availablePort();
        CliLoginSession session = CliLoginSession.start();
        try (ExecutorService executor = Executors.newSingleThreadExecutor();
             LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(port)) {
            Future<String> result = executor.submit(() -> callback.awaitAuthorizationCode(session));
            String response = request(port, requestLine + "?code=wrong&state=" + session.state()
                    + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n");
            assertTrue(response.startsWith("HTTP/1.1 400"), response);
            assertTrue(!result.isDone(), "Rejected request must not complete callback");
            request(port, "GET /oauth/callback?code=correct&state=" + session.state()
                    + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n");
            assertEquals("correct", result.get(3, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"localhost", "127.0.0.2", "127.0.0.1", "127.0.0.1:1", "127.0.0.1:{port}\r\nHost: evil", ""})
    void unexpectedHostCannotCompleteLogin(String host) throws Exception {
        int port = availablePort();
        CliLoginSession session = CliLoginSession.start();
        try (ExecutorService executor = Executors.newSingleThreadExecutor();
             LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(port)) {
            Future<String> result = executor.submit(() -> callback.awaitAuthorizationCode(session));
            String response = request(port, "GET /oauth/callback?code=wrong&state=" + session.state()
                    + " HTTP/1.1\r\nHost: " + host.replace("{port}", Integer.toString(port)) + "\r\n\r\n");
            assertTrue(response.startsWith("HTTP/1.1 400"), response);
            assertTrue(!result.isDone(), "Unexpected Host must not complete callback");
            request(port, "GET /oauth/callback?code=correct&state=" + session.state()
                    + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n");
            assertEquals("correct", result.get(3, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"code=wrong&state=bad", "code=first&code=second&state={state}",
            "code=wrong&state={state}&state={state}", "code=&state={state}", "code=%GG&state={state}",
            "code=wrong&state={state}&%63ode=second"})
    void invalidParametersNeverReturnCode(String query) throws Exception {
        int port = availablePort();
        CliLoginSession session = CliLoginSession.start();
        try (ExecutorService executor = Executors.newSingleThreadExecutor();
             LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(port)) {
            Future<String> result = executor.submit(() -> callback.awaitAuthorizationCode(session));
            try (Socket socket = new Socket("127.0.0.1", port)) {
                socket.setSoTimeout(3000);
                socket.getOutputStream().write(("GET /oauth/callback?" + query.replace("{state}", session.state())
                        + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                /* 响应或关闭均不是成功；公共结果边界必须保持未完成。 */
                try {
                    socket.getInputStream().readAllBytes();
                } catch (SocketException ignored) {
                    /* 错误回调允许直接关闭连接。 */
                }
            }
            assertTrue(!result.isDone(), "Invalid callback must leave login pending");
            request(port, "GET /oauth/callback?code=correct&state=" + session.state()
                    + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n");
            assertEquals("correct", result.get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void oversizedHeadersAreRejectedWithoutCompletingLogin() throws Exception {
        int port = availablePort();
        CliLoginSession session = CliLoginSession.start();
        try (ExecutorService executor = Executors.newSingleThreadExecutor();
             LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(port)) {
            Future<String> result = executor.submit(() -> callback.awaitAuthorizationCode(session));
            String response = request(port, "GET /oauth/callback?code=wrong&state=" + session.state()
                    + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nX-Padding: " + "a".repeat(8192) + "\r\n\r\n");
            assertTrue(response.startsWith("HTTP/1.1 400"), response);
            assertTrue(!result.isDone(), "Oversized request must not complete login");
            request(port, "GET /oauth/callback?code=correct&state=" + session.state()
                    + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n");
            assertEquals("correct", result.get(3, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"HTTP/1.0", "HTTP/2", "HTTP/1.1 extra"})
    void unexpectedHttpVersionCannotCompleteLogin(String version) throws Exception {
        int port = availablePort();
        CliLoginSession session = CliLoginSession.start();
        try (ExecutorService executor = Executors.newSingleThreadExecutor();
             LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(port)) {
            Future<String> result = executor.submit(() -> callback.awaitAuthorizationCode(session));
            String response = request(port, "GET /oauth/callback?code=wrong&state=" + session.state()
                    + " " + version + "\r\nHost: 127.0.0.1:" + port + "\r\n\r\n");
            assertTrue(response.startsWith("HTTP/1.1 400"), response);
            assertTrue(!result.isDone());
            request(port, "GET /oauth/callback?code=correct&state=" + session.state()
                    + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n");
            assertEquals("correct", result.get(3, TimeUnit.SECONDS));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-host", "folded-header", "body", "fragment", "oversized-target"})
    void malformedOrOversizedRequestCannotCompleteLogin(String kind) throws Exception {
        int port = availablePort();
        CliLoginSession session = CliLoginSession.start();
        String target = "/oauth/callback?code=wrong&state=" + session.state();
        String headers = "Host: 127.0.0.1:" + port + "\r\n";
        if (kind.equals("missing-host")) {
            headers = "";
        } else if (kind.equals("folded-header")) {
            headers += " Host: evil\r\n";
        } else if (kind.equals("body")) {
            headers += "Content-Length: 1\r\n";
        } else if (kind.equals("fragment")) {
            target += "#fragment";
        } else {
            target += "&padding=" + "a".repeat(8192);
        }
        try (ExecutorService executor = Executors.newSingleThreadExecutor();
             LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(port)) {
            Future<String> result = executor.submit(() -> callback.awaitAuthorizationCode(session));
            String response = request(port, "GET " + target + " HTTP/1.1\r\n" + headers + "\r\n");
            assertTrue(response.startsWith("HTTP/1.1 400"), response);
            assertTrue(!result.isDone());
            request(port, "GET /oauth/callback?code=correct&state=" + session.state()
                    + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\n\r\n");
            assertEquals("correct", result.get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void exactEightKibRequestIsAccepted() throws Exception {
        int port = availablePort();
        CliLoginSession session = CliLoginSession.start();
        String prefix = "GET /oauth/callback?code=correct&state=" + session.state()
                + " HTTP/1.1\r\nHost: 127.0.0.1:" + port + "\r\nX-Padding: ";
        String wire = prefix + "a".repeat(8192 - prefix.length() - 4) + "\r\n\r\n";
        assertEquals(8192, wire.getBytes(StandardCharsets.US_ASCII).length);
        try (ExecutorService executor = Executors.newSingleThreadExecutor();
             LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(port)) {
            Future<String> result = executor.submit(() -> callback.awaitAuthorizationCode(session));
            assertTrue(request(port, wire).startsWith("HTTP/1.1 200"));
            assertEquals("correct", result.get(3, TimeUnit.SECONDS));
        }
    }

    @Test
    void expiredOriginalSessionClosesListenerImmediately() throws Exception {
        CliLoginSession session;
        Instant old = Instant.parse("2000-01-01T00:00:00Z");
        try (MockedStatic<Instant> time = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(old);
            session = CliLoginSession.start();
        }
        int port = availablePort();
        try (ExecutorService executor = Executors.newSingleThreadExecutor();
             LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(port)) {
            Future<String> result = executor.submit(() -> callback.awaitAuthorizationCode(session));
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> result.get(1, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof SocketTimeoutException, failure.toString());
            try (ServerSocket rebound = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
                assertEquals(port, rebound.getLocalPort());
            }
        }
    }

    @Test
    void browserResponseWriteFailureDoesNotDiscardValidatedCode() throws Exception {
        CliLoginSession session = CliLoginSession.start();
        Socket connection = Mockito.mock(Socket.class);
        Mockito.when(connection.getInetAddress()).thenReturn(InetAddress.getByName("127.0.0.1"));
        Mockito.when(connection.getInputStream()).thenReturn(new ByteArrayInputStream(
                ("GET /oauth/callback?code=validated&state=" + session.state()
                        + " HTTP/1.1\r\nHost: 127.0.0.1:32123\r\n\r\n").getBytes(StandardCharsets.US_ASCII)));
        Mockito.when(connection.getOutputStream()).thenThrow(new SocketException("Browser reset"));
        try (MockedConstruction<ServerSocket> sockets = Mockito.mockConstruction(ServerSocket.class,
                (listener, context) -> {
                    Mockito.when(listener.accept()).thenReturn(connection);
                    Mockito.when(listener.getLocalPort()).thenReturn(32123);
                });
             LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(32123)) {
            assertEquals("validated", assertDoesNotThrow(
                    () -> callback.awaitAuthorizationCode(session), "Validated code survives browser response failure"));
            Mockito.verify(connection).close();
            Mockito.verify(sockets.constructed().getFirst()).close();
        }
    }

    @Test
    void incompleteRequestCannotExtendOriginalLoginBudget() throws Exception {
        CliLoginSession session;
        Instant started = Instant.now().minusSeconds(119);
        try (MockedStatic<Instant> time = Mockito.mockStatic(Instant.class, Mockito.CALLS_REAL_METHODS)) {
            time.when(Instant::now).thenReturn(started);
            session = CliLoginSession.start();
        }
        int port = availablePort();
        CountDownLatch reading = new CountDownLatch(1);
        try (ServerSocket realListener = new ServerSocket();
             MockedConstruction<ServerSocket> listeners = observeAcceptedRead(realListener, reading);
             ExecutorService executor = Executors.newSingleThreadExecutor();
             LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(port);
             Socket socket = new Socket("127.0.0.1", port)) {
            Future<String> result = executor.submit(() -> callback.awaitAuthorizationCode(session));
            socket.getOutputStream().write("GET /oauth/callback?".getBytes(StandardCharsets.US_ASCII));
            assertTrue(reading.await(1, TimeUnit.SECONDS), "Accepted connection must enter request read");
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> result.get(3, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof SocketTimeoutException, failure.toString());
            socket.setSoTimeout(1000);
            assertEquals(-1, socket.getInputStream().read());
        }
    }

    @Test
    void closeUnblocksPendingReadAndReleasesListener() throws Exception {
        int port = availablePort();
        CliLoginSession session = CliLoginSession.start();
        CountDownLatch reading = new CountDownLatch(1);
        try (ServerSocket realListener = new ServerSocket();
             MockedConstruction<ServerSocket> listeners = observeAcceptedRead(realListener, reading);
             ExecutorService executor = Executors.newSingleThreadExecutor();
             LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(port);
             Socket socket = new Socket("127.0.0.1", port)) {
            Future<String> result = executor.submit(() -> callback.awaitAuthorizationCode(session));
            socket.setSoTimeout(3000);
            socket.getOutputStream().write("GET /oauth/callback?".getBytes(StandardCharsets.US_ASCII));
            assertTrue(reading.await(1, TimeUnit.SECONDS), "Accepted connection must enter request read");
            callback.close();
            ExecutionException failure = assertThrows(ExecutionException.class,
                    () -> result.get(1, TimeUnit.SECONDS));
            assertTrue(failure.getCause() instanceof IOException, failure.toString());
            assertEquals(-1, socket.getInputStream().read());
        }
        try (ServerSocket rebound = new ServerSocket(port, 1, InetAddress.getByName("127.0.0.1"))) {
            assertEquals(port, rebound.getLocalPort());
        }
    }

    /* 只替代 JDK listener 边界并委托真实 socket；闸门位于真实空输入读取之前。 */
    private static MockedConstruction<ServerSocket> observeAcceptedRead(
            ServerSocket realListener, CountDownLatch reading) {
        return Mockito.mockConstruction(ServerSocket.class, (listener, context) -> {
            Mockito.doAnswer(invocation -> {
                realListener.bind(invocation.getArgument(0), invocation.getArgument(1));
                return null;
            }).when(listener).bind(Mockito.any(), Mockito.anyInt());
            Mockito.doAnswer(invocation -> {
                realListener.setSoTimeout(invocation.getArgument(0));
                return null;
            }).when(listener).setSoTimeout(Mockito.anyInt());
            Mockito.when(listener.getLocalPort()).thenAnswer(invocation -> realListener.getLocalPort());
            Mockito.when(listener.isClosed()).thenAnswer(invocation -> realListener.isClosed());
            Mockito.doAnswer(invocation -> {
                realListener.close();
                return null;
            }).when(listener).close();
            Mockito.when(listener.accept()).thenAnswer(invocation -> {
                Socket accepted = realListener.accept();
                InputStream input = accepted.getInputStream();
                Socket observed = Mockito.mock(Socket.class, AdditionalAnswers.delegatesTo(accepted));
                Mockito.doReturn(new FilterInputStream(input) {
                    @Override
                    public int read() throws IOException {
                        if (input.available() == 0) {
                            reading.countDown();
                        }
                        return input.read();
                    }
                }).when(observed).getInputStream();
                return observed;
            });
        });
    }

    private static int availablePort() throws Exception {
        try (ServerSocket reservation = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            return reservation.getLocalPort();
        }
    }

    private static String request(int port, String request) throws Exception {
        try (Socket socket = new Socket("127.0.0.1", port)) {
            socket.setSoTimeout(3000);
            socket.getOutputStream().write(request.getBytes(StandardCharsets.US_ASCII));
            socket.getOutputStream().flush();
            ByteArrayOutputStream response = new ByteArrayOutputStream();
            while (!response.toString(StandardCharsets.US_ASCII).endsWith("\r\n\r\n")) {
                int value = socket.getInputStream().read();
                if (value < 0) {
                    break;
                }
                response.write(value);
            }
            return response.toString(StandardCharsets.US_ASCII);
        }
    }

    @Test
    void occupiedFixedPortFailsWithoutChoosingAnotherPort() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0, 1, InetAddress.getByName("127.0.0.1"))) {
            assertThrows(BindException.class, () -> {
                try (LoopbackAuthorizationCallback callback = new LoopbackAuthorizationCallback(occupied.getLocalPort())) {
                    /* 构造器必须在此之前因精确端口占用失败。 */
                }
            });
        }
    }
}
