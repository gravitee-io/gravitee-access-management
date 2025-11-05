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
package io.gravitee.am.gateway.handler.root.handler;

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;

import java.util.function.Consumer;

/**
 * @author Eric Leleu (eric.leleu@graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class LoggerJsonMessageTokenHandlerTest extends RxWebTestBase {
    @Mock
    private Environment environment;

    @BeforeEach
    public void setUp() throws Exception {
        if (server == null) {
            super.setUp();
        }
    }

    @AfterEach
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void should_not_fail_on_logger_error() throws Exception {
        Mockito.when(environment.getProperty(LoggerJsonMessageTokenHandler.PROPERTY_HANDLERS_REQUEST_LOGGER_JSON_MESSAGE_HEADERS, String.class)).thenReturn(null);
        SpyLoggerJsonMessageTokenHandler handler = new SpyLoggerJsonMessageTokenHandler(environment);
        handler.setConsumer(ctx -> {
            throw new RuntimeException("exception for test");
        });

        router.route("/token")
                .handler(BodyHandler.create())
                .handler(handler)
                .handler(ctx -> ctx.response().setStatusCode(HttpStatusCode.OK_200).end());

        testRequest(
                HttpMethod.POST,
                "/token",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("grant_type=test");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                HttpStatusCode.OK_200, "OK", null); // even in case of error in the logger, should return OK
        // test that the log method has been called by checking the captured element
        assertNotNull(handler.capture);
    }

    @Test
    public void should_log_only_token_endpoint() throws Exception {
        Mockito.when(environment.getProperty(LoggerJsonMessageTokenHandler.PROPERTY_HANDLERS_REQUEST_LOGGER_JSON_MESSAGE_HEADERS, String.class)).thenReturn(null);
        SpyLoggerJsonMessageTokenHandler handler = new SpyLoggerJsonMessageTokenHandler(environment);
        var consumer = new CounterConsumer();
        handler.setConsumer(consumer);

        router.route("/other")
                .handler(BodyHandler.create())
                .handler(handler)
                .handler(ctx -> ctx.response().setStatusCode(HttpStatusCode.OK_200).end());

        testRequest(
                HttpMethod.POST,
                "/other",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    req.write(buffer);
                },
                HttpStatusCode.OK_200, "OK", null); // even in case of error in the logger, should return OK

        assertTrue(consumer.verifyNumberOfCall(0));
    }

    @Test
    public void should_call_logger_but_headers_are_empty_as_allowedList_is_null() throws Exception {
        Mockito.when(environment.getProperty(LoggerJsonMessageTokenHandler.PROPERTY_HANDLERS_REQUEST_LOGGER_JSON_MESSAGE_HEADERS, String.class)).thenReturn(null);
        SpyLoggerJsonMessageTokenHandler handler = new SpyLoggerJsonMessageTokenHandler(environment);

        var consumer = new CounterConsumer();
        handler.setConsumer(consumer);

        router.route("/token")
                .handler(BodyHandler.create())
                .handler(handler)
                .handler(ctx -> ctx.response().setStatusCode(HttpStatusCode.OK_200).end());

        testRequest(
                HttpMethod.POST,
                "/token",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("grant_type=test");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                HttpStatusCode.OK_200, "OK", null);

        assertTrue(consumer.verifyNumberOfCall(1));
        Assertions.assertThat(handler.capture.getIncomingHeaders()).isEmpty();
        Assertions.assertThat(handler.capture.getOutgoingHeaders()).isEmpty();
    }

    @Test
    public void should_call_logger_but_headers_are_empty_as_allowedList_is_empty() throws Exception {
        Mockito.when(environment.getProperty(LoggerJsonMessageTokenHandler.PROPERTY_HANDLERS_REQUEST_LOGGER_JSON_MESSAGE_HEADERS, String.class)).thenReturn("");
        SpyLoggerJsonMessageTokenHandler handler = new SpyLoggerJsonMessageTokenHandler(environment);

        var consumer = new CounterConsumer();
        handler.setConsumer(consumer);

        router.route("/token")
                .handler(BodyHandler.create())
                .handler(handler)
                .handler(ctx -> ctx.response().setStatusCode(HttpStatusCode.OK_200).end());

        testRequest(
                HttpMethod.POST,
                "/token",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("grant_type=test");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                HttpStatusCode.OK_200, "OK", null);

        assertTrue(consumer.verifyNumberOfCall(1));
        Assertions.assertThat(handler.capture.getIncomingHeaders()).isEmpty();
        Assertions.assertThat(handler.capture.getOutgoingHeaders()).isEmpty();
    }

    @Test
    public void should_call_logger_but_headers_are_empty_as_they_are_missing_from_allowed_list() throws Exception {
        Mockito.when(environment.getProperty(LoggerJsonMessageTokenHandler.PROPERTY_HANDLERS_REQUEST_LOGGER_JSON_MESSAGE_HEADERS, String.class)).thenReturn("Authorization");
        SpyLoggerJsonMessageTokenHandler handler = new SpyLoggerJsonMessageTokenHandler(environment);

        var consumer = new CounterConsumer();
        handler.setConsumer(consumer);

        router.route("/token")
                .handler(BodyHandler.create())
                .handler(handler)
                .handler(ctx -> ctx.response().setStatusCode(HttpStatusCode.OK_200).end());

        testRequest(
                HttpMethod.POST,
                "/token",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("grant_type=test");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                HttpStatusCode.OK_200, "OK", null);

        assertTrue(consumer.verifyNumberOfCall(1));
        Assertions.assertThat(handler.capture.getIncomingHeaders()).isEmpty();
        Assertions.assertThat(handler.capture.getOutgoingHeaders()).isEmpty();
    }

    @Test
    public void should_call_logger_headers_present_as_they_are_defined_in_allowed_list() throws Exception {
        Mockito.when(environment.getProperty(LoggerJsonMessageTokenHandler.PROPERTY_HANDLERS_REQUEST_LOGGER_JSON_MESSAGE_HEADERS, String.class)).thenReturn("Content-Type, content-length");
        SpyLoggerJsonMessageTokenHandler handler = new SpyLoggerJsonMessageTokenHandler(environment);

        var consumer = new CounterConsumer();
        handler.setConsumer(consumer);

        router.route("/token")
                .handler(BodyHandler.create())
                .handler(handler)
                .handler(ctx -> ctx.response().setStatusCode(HttpStatusCode.OK_200).end());

        testRequest(
                HttpMethod.POST,
                "/token",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("grant_type=test");
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                HttpStatusCode.OK_200, "OK", null);

        assertTrue(consumer.verifyNumberOfCall(1));
        Assertions.assertThat(handler.capture.getIncomingHeaders()).containsKey("content-length").containsKey("content-type");
        Assertions.assertThat(handler.capture.getOutgoingHeaders()).containsKey("content-length");
        Assertions.assertThat(handler.capture.authTokenType).isNullOrEmpty();
        Assertions.assertThat(handler.capture.clientId).isNullOrEmpty();
        Assertions.assertThat(handler.capture.grantType).isEqualTo("test");
        Assertions.assertThat(handler.capture.method).isEqualTo("POST");
        Assertions.assertThat(handler.capture.path).isEqualTo("/token");
        Assertions.assertThat(handler.capture.statusCode).isEqualTo(200);
        Assertions.assertThat(handler.capture.timestamp).isLessThanOrEqualTo(System.currentTimeMillis());
        Assertions.assertThat(handler.capture.responseTime).isGreaterThanOrEqualTo(0);
        Assertions.assertThat(handler.capture.requestBodySize).isGreaterThan(0);
        Assertions.assertThat(handler.capture.responseBodySize).isEqualTo(0);
    }

    @Test
    public void should_call_logger_with_authorization_information() throws Exception {
        Mockito.when(environment.getProperty(LoggerJsonMessageTokenHandler.PROPERTY_HANDLERS_REQUEST_LOGGER_JSON_MESSAGE_HEADERS, String.class)).thenReturn("Content-Type, content-length");
        SpyLoggerJsonMessageTokenHandler handler = new SpyLoggerJsonMessageTokenHandler(environment);

        var consumer = new CounterConsumer();
        handler.setConsumer(consumer);

        router.route("/token")
                .handler(BodyHandler.create())
                .handler(handler)
                .handler(ctx -> ctx.response().setStatusCode(HttpStatusCode.OK_200).end("test"));

        testRequest(
                HttpMethod.POST,
                "/token?with=param",
                req -> {
                    Buffer buffer = Buffer.buffer();
                    buffer.appendString("grant_type=test");
                    req.headers().set("Authorization", "Basic YS1jbGllbnQtaWQ6YS1jbGllbnQtc2VjcmV0Cg=="); //a-client-is:a-client-secret
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/x-www-form-urlencoded");
                    req.write(buffer);
                },
                HttpStatusCode.OK_200, "OK", null);

        assertTrue(consumer.verifyNumberOfCall(1));
        Assertions.assertThat(handler.capture.getIncomingHeaders()).containsKey("content-length").containsKey("content-type");
        Assertions.assertThat(handler.capture.getOutgoingHeaders()).containsKey("content-length");
        Assertions.assertThat(handler.capture.authTokenType).isEqualTo("Basic");
        Assertions.assertThat(handler.capture.clientId).isEqualTo("a-client-id");
        Assertions.assertThat(handler.capture.grantType).isEqualTo("test");
        Assertions.assertThat(handler.capture.method).isEqualTo("POST");
        Assertions.assertThat(handler.capture.path).isEqualTo("/token?with=param");
        Assertions.assertThat(handler.capture.statusCode).isEqualTo(200);
        Assertions.assertThat(handler.capture.timestamp).isLessThanOrEqualTo(System.currentTimeMillis());
        Assertions.assertThat(handler.capture.responseTime).isGreaterThanOrEqualTo(0);
        Assertions.assertThat(handler.capture.requestBodySize).isGreaterThan(0);
        Assertions.assertThat(handler.capture.responseBodySize).isEqualTo("test".getBytes().length);
    }

    private static class SpyLoggerJsonMessageTokenHandler extends LoggerJsonMessageTokenHandler {
        private Consumer<LoggerContext> loggerContextConsumer;
        private LoggerContext capture;
        public SpyLoggerJsonMessageTokenHandler(Environment environment) {
            super(environment);
        }

        @Override
        void log(LoggerJsonMessageTokenHandler.LoggerContext loggerContext) {
            this.capture = loggerContext;
            if (loggerContextConsumer != null) {
                loggerContextConsumer.accept(loggerContext);
            }
        }
        public void setConsumer(Consumer<LoggerContext> loggerContextConsumer) {
            this.loggerContextConsumer = loggerContextConsumer;
        }
    }

    private class CounterConsumer implements Consumer<LoggerJsonMessageTokenHandler.LoggerContext> {
        private int count = 0;

        @Override
        public void accept(LoggerJsonMessageTokenHandler.LoggerContext loggerContext) {
            count++;
        }

        public boolean verifyNumberOfCall(int expectedValue) {
            return count == expectedValue;
        }
    }

}
