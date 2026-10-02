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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.csp;

import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.handler.HttpException;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.common.utils.ConstantKeys.CSP_SCRIPT_INLINE_NONCE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
class CspHandlerImplTest {

    private static final String CSP_HEADER = "Content-Security-Policy";
    private static final String CSP_REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only";

    @Test
    void shouldSendDefaultPolicy() throws IOException {
        var handler = new CspHandlerImpl(null, defaultDirectives(), true);

        var exchange = Exchange.handledBy(handler);

        assertThat(exchange.header(CSP_HEADER)).isEqualTo("default-src 'self' 'self'; "
                + "style-src 'self' 'unsafe-inline'; "
                + "script-src 'self' https://cdn.jsdelivr.net/npm/@fingerprintjs/fingerprintjs@3/dist/fp.min.js "
                + "https://cdn.jsdelivr.net/npm/@fingerprintjs/fingerprintjs-pro@3/dist/fp.min.js *.gstatic.com *.google.com "
                + "'nonce-" + exchange.nonce() + "'; "
                + "frame-src 'self' https://www.google.com; "
                + "frame-ancestors 'none'; "
                + "img-src 'self' data:");
    }

    @Test
    void shouldAppendRequestNonceToConfiguredScriptSrc() {
        var handler = new CspHandlerImpl(false, List.of("script-src 'self' https://cdn.example.com;"), true);

        var exchange = Exchange.handledBy(handler);

        assertThat(exchange.nonce()).hasSize(CspHandlerImpl.NONCE_LENGTH);
        assertThat(exchange.header(CSP_HEADER))
                .isEqualTo("default-src 'self'; script-src 'self' https://cdn.example.com 'nonce-" + exchange.nonce() + "'");
        verify(exchange.context).next();
    }

    @Test
    void shouldAddScriptSrcWithRequestNonce_whenNoneConfigured() {
        var handler = new CspHandlerImpl(false, List.of("img-src 'self' data:;"), true);

        var exchange = Exchange.handledBy(handler);

        assertThat(exchange.header(CSP_HEADER))
                .isEqualTo("default-src 'self'; img-src 'self' data:; script-src 'nonce-" + exchange.nonce() + "'");
    }

    @Test
    void shouldAddScriptSrcWithRequestNonce_whenNoDirectives() {
        var handler = new CspHandlerImpl(null, null, true);

        var exchange = Exchange.handledBy(handler);

        assertThat(exchange.header(CSP_HEADER))
                .isEqualTo("default-src 'self'; script-src 'nonce-" + exchange.nonce() + "'");
    }

    @Test
    void shouldQuoteBareKeyword() {
        var handler = new CspHandlerImpl(false, List.of("script-src self"), true);

        var exchange = Exchange.handledBy(handler);

        assertThat(exchange.header(CSP_HEADER))
                .isEqualTo("default-src 'self'; script-src 'self' 'nonce-" + exchange.nonce() + "'");
    }

    @Test
    void shouldNotAccumulateNoncesAcrossRequests() {
        var handler = new CspHandlerImpl(false, List.of("script-src 'self';"), true);

        var first = Exchange.handledBy(handler);
        var second = Exchange.handledBy(handler);

        assertThat(second.nonce()).isNotEqualTo(first.nonce());
        assertThat(second.header(CSP_HEADER))
                .isEqualTo("default-src 'self'; script-src 'self' 'nonce-" + second.nonce() + "'");
    }

    @Test
    void shouldSendConfiguredPolicyWithoutNonce_whenInlineNonceDisabled() {
        var handler = new CspHandlerImpl(false, List.of("script-src 'self';"), false);

        var exchange = Exchange.handledBy(handler);

        assertThat(exchange.header(CSP_HEADER)).isEqualTo("default-src 'self'; script-src 'self'");
        verify(exchange.context, never()).put(eq(CSP_SCRIPT_INLINE_NONCE), any());
        verify(exchange.context).next();
    }

    @Test
    void shouldUseReportOnlyHeader_whenReportOnlyWithReportUri() {
        var handler = new CspHandlerImpl(true, List.of("report-uri https://csp.example.com/report;"), true);

        var exchange = Exchange.handledBy(handler);

        assertThat(exchange.header(CSP_REPORT_ONLY_HEADER))
                .isEqualTo("default-src 'self'; report-uri https://csp.example.com/report; script-src 'nonce-" + exchange.nonce() + "'");
        verify(exchange.response, never()).putHeader(eq(CSP_HEADER), anyString());
        verify(exchange.context).next();
    }

    @Test
    void shouldFailWith500_whenReportOnlyWithoutReportTarget() {
        var handler = new CspHandlerImpl(true, List.of("script-src 'self';"), true);

        var exchange = Exchange.handledBy(handler);

        var failure = ArgumentCaptor.forClass(Throwable.class);
        verify(exchange.context).fail(failure.capture());
        assertThat(failure.getValue()).isInstanceOf(HttpException.class);
        assertThat(((HttpException) failure.getValue()).getStatusCode()).isEqualTo(500);
        verify(exchange.response, never()).putHeader(anyString(), anyString());
        verify(exchange.context, never()).next();
    }

    @Test
    void shouldSendEachRequestItsOwnNonce_whenHandledConcurrently() throws Exception {
        final int threads = 8;
        final int requestsPerThread = 2_000;
        var handler = new CspHandlerImpl(false, List.of("script-src 'self';"), true);
        var start = new CyclicBarrier(threads);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        try {
            List<Future<List<String>>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                Callable<List<String>> task = () -> {
                    List<String> mismatches = new ArrayList<>();
                    start.await(10, TimeUnit.SECONDS);
                    for (int i = 0; i < requestsPerThread; i++) {
                        var exchange = Exchange.handledBy(handler);
                        var expected = "default-src 'self'; script-src 'self' 'nonce-" + exchange.nonce() + "'";
                        var actual = exchange.header(CSP_HEADER);
                        if (!expected.equals(actual)) {
                            mismatches.add("expected <" + expected + "> but was <" + actual + ">");
                        }
                    }
                    return mismatches;
                };
                results.add(executor.submit(task));
            }

            List<String> mismatches = new ArrayList<>();
            for (Future<List<String>> result : results) {
                mismatches.addAll(result.get(60, TimeUnit.SECONDS));
            }
            assertThat(mismatches).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<String> defaultDirectives() throws IOException {
        try (var reader = new BufferedReader(new InputStreamReader(
                CspHandlerImpl.class.getClassLoader().getResourceAsStream("default-csp-directives.properties"), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        }
    }

    private static final class Exchange {
        private final io.vertx.ext.web.RoutingContext context = mock(io.vertx.ext.web.RoutingContext.class);
        private final HttpServerResponse response = mock(HttpServerResponse.class);

        static Exchange handledBy(CspHandlerImpl handler) {
            var exchange = new Exchange();
            when(exchange.context.response()).thenReturn(exchange.response);
            handler.handle(RoutingContext.newInstance(exchange.context));
            return exchange;
        }

        String nonce() {
            var nonce = ArgumentCaptor.forClass(Object.class);
            verify(context).put(eq(CSP_SCRIPT_INLINE_NONCE), nonce.capture());
            return (String) nonce.getValue();
        }

        String header(String name) {
            var value = ArgumentCaptor.forClass(String.class);
            verify(response).putHeader(eq(name), value.capture());
            return value.getValue();
        }
    }
}
