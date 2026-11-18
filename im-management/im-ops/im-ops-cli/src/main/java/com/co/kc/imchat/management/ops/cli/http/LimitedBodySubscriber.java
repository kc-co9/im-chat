package com.co.kc.imchat.management.ops.cli.http;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** 限制 HTTP 响应体大小的订阅器，超限时立即取消上游请求。 */
final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {

    private final HttpResponse.BodySubscriber<byte[]> delegate = HttpResponse.BodySubscribers.ofByteArray();
    /** 当前上游响应订阅。 */
    private Flow.Subscription subscription;
    /** 已接收的响应体字节数。 */
    private int received;
    /** 是否已经结束或取消订阅。 */
    private boolean finished;

    @Override
    public CompletionStage<byte[]> getBody() {
        return delegate.getBody();
    }

    @Override
    public void onSubscribe(Flow.Subscription upstream) {
        subscription = upstream;
        delegate.onSubscribe(upstream);
    }

    @Override
    public void onNext(List<ByteBuffer> buffers) {
        if (finished) {
            return;
        }
        for (ByteBuffer buffer : buffers) {
            if (buffer.remaining() > 1048576 - received) {
                finished = true;
                subscription.cancel();
                delegate.onError(new IOException("HTTP response exceeds 1 MiB"));
                return;
            }
            received += buffer.remaining();
        }
        delegate.onNext(buffers);
    }

    @Override
    public void onError(Throwable failure) {
        if (!finished) {
            finished = true;
            delegate.onError(failure);
        }
    }

    @Override
    public void onComplete() {
        if (!finished) {
            finished = true;
            delegate.onComplete();
        }
    }
}
