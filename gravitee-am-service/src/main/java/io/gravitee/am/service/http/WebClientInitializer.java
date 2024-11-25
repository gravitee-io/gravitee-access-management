/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.service.http;

import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.functions.Function;
import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.RequestOptions;
import io.vertx.core.net.SSLOptions;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.WebClient;
import io.vertx.uritemplate.UriTemplate;
import lombok.experimental.Delegate;
import lombok.experimental.UtilityClass;
import org.reactivestreams.Publisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@UtilityClass
public class WebClientInitializer {

    public static WebClient asyncInitialize(Maybe<WebClient> webClientInitializer) {
        WebClientDelegate webClientWrapper = new WebClientDelegate(WebClientStub.INSTANCE);
        webClientInitializer
                .retryWhen(new RetryWithDelay())
                .map(WebClient::getDelegate)
                .subscribe(webClientWrapper::setDelegate);
        return new WebClient(webClientWrapper);
    }

    private static class WebClientDelegate implements io.vertx.ext.web.client.WebClient {

        @Delegate
        private io.vertx.ext.web.client.WebClient delegate;

        WebClientDelegate(io.vertx.ext.web.client.WebClient delegate) {
            setDelegate(delegate);
        }

        void setDelegate(io.vertx.ext.web.client.WebClient delegate) {
            this.delegate = delegate;
        }
    }

    private static class WebClientStub implements io.vertx.ext.web.client.WebClient {
        private static final String MSG = "WebClient is being initialized";
        private static final WebClientStub INSTANCE = new WebClientStub();


        @Override
        public HttpRequest<Buffer> request(HttpMethod httpMethod, SocketAddress socketAddress, int i, String s, String s1) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public HttpRequest<Buffer> request(HttpMethod httpMethod, SocketAddress socketAddress, int i, String s, UriTemplate uriTemplate) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public HttpRequest<Buffer> request(HttpMethod httpMethod, SocketAddress socketAddress, String s, String s1) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public HttpRequest<Buffer> request(HttpMethod httpMethod, SocketAddress socketAddress, String s, UriTemplate uriTemplate) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public HttpRequest<Buffer> request(HttpMethod httpMethod, SocketAddress socketAddress, String s) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public HttpRequest<Buffer> request(HttpMethod httpMethod, SocketAddress socketAddress, UriTemplate uriTemplate) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public HttpRequest<Buffer> request(HttpMethod httpMethod, SocketAddress socketAddress, RequestOptions requestOptions) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public HttpRequest<Buffer> requestAbs(HttpMethod httpMethod, SocketAddress socketAddress, String s) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public HttpRequest<Buffer> requestAbs(HttpMethod httpMethod, SocketAddress socketAddress, UriTemplate uriTemplate) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public Future<Boolean> updateSSLOptions(SSLOptions sslOptions, boolean b) {
            throw new UnsupportedOperationException(MSG);
        }

        @Override
        public void close() {
            throw new UnsupportedOperationException(MSG);
        }
    }

    private static final class RetryWithDelay implements Function<Flowable<Throwable>, Publisher<?>> {
        private static final Logger LOGGER = LoggerFactory.getLogger(RetryWithDelay.class);

        private final AtomicInteger delayInSec = new AtomicInteger(1);
        private final AtomicInteger counter = new AtomicInteger(0);

        @Override
        public Publisher<?> apply(Flowable<Throwable> throwableFlowable) {
            return throwableFlowable.flatMap(err -> {
                if (counter.getAndIncrement() < 50) {
                    int delay = delayInSec.get();
                    LOGGER.warn("WebClient init failed, retry={}/50, delay={}", counter.get(), delay);
                    if (delay < 60) {
                        delayInSec.set(delay * 2);
                    }
                    return Flowable.timer(delay, TimeUnit.SECONDS);
                } else {
                    LOGGER.error("Retry limit exceeded");
                    return Flowable.error(err);
                }
            });
        }
    }
}
