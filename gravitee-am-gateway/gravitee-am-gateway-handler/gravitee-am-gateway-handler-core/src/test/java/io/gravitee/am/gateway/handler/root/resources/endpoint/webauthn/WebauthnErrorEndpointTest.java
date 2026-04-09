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
package io.gravitee.am.gateway.handler.root.resources.endpoint.webauthn;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.DummyHttpRequest;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.DummyHttpResponse;
import io.gravitee.am.gateway.handler.root.resources.handler.dummies.SpyRoutingContext;
import io.gravitee.am.gateway.handler.root.resources.handler.webauthn.WebAuthnTelemetrySameOriginHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.service.AuditService;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static io.gravitee.am.common.utils.ConstantKeys.TRANSACTION_ID_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_IP_LOCATION;
import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_USER_AGENT;
import static io.vertx.core.http.HttpHeaders.CONTENT_TYPE;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebauthnErrorEndpointTest extends RxWebTestBase {

    @Mock
    private Domain domain;

    @Mock
    private AuditService auditService;

    private WebauthnErrorEndpoint endpoint;
    private WebAuthnTelemetrySameOriginHandler sameOriginHandler;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        when(domain.getId()).thenReturn("domain-1");
        endpoint = new WebauthnErrorEndpoint(domain, auditService, WebauthnErrorEndpoint.DEFAULT_TRANSACTION_ID_HTTP_HEADER);
        sameOriginHandler = new WebAuthnTelemetrySameOriginHandler();

        router.route().handler(BodyHandler.create());
    }

    private static int spyStatus(SpyRoutingContext ctx) {
        return ((DummyHttpResponse) ctx.response().getDelegate()).getStatusCode();
    }

    private static JsonObject minimalValidJson() {
        return new JsonObject()
                .put("phase", "login")
                .put("operation", "get")
                .put("errorName", "NotAllowedError");
    }

    private String sameOriginHeader() {
        return "http://localhost:" + server.actualPort();
    }

    private static String jsonBody() {
        return "{\"phase\":\"login\",\"operation\":\"get\",\"errorName\":\"NotAllowedError\",\"errorMessage\":\"cancelled\",\"clientTimestamp\":1,\"rpId\":\"example.com\"}";
    }

    private void mountPostEndpoint() {
        router.route(HttpMethod.POST, "/webauthn/webauthn-error")
                .handler(rc -> {
                    rc.put(ConstantKeys.CLIENT_CONTEXT_KEY, new Client());
                    rc.next();
                })
                .handler(sameOriginHandler)
                .handler(endpoint);
    }

    @Test
    public void shouldReturn204_whenOriginMatches() throws Exception {
        mountPostEndpoint();

        testRequest(
                HttpMethod.POST,
                "/webauthn/webauthn-error",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, "application/json");
                    req.putHeader("Origin", sameOriginHeader());
                    req.putHeader("User-Agent", "TestAgent/1.0");
                    req.write(Buffer.buffer(jsonBody()));
                },
                null,
                204,
                "No Content",
                null);
        verify(auditService).report(any());
    }

    @Test
    public void shouldReturn403_whenOriginMismatch() throws Exception {
        mountPostEndpoint();

        testRequest(
                HttpMethod.POST,
                "/webauthn/webauthn-error",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, "application/json");
                    req.putHeader("Origin", "https://malicious.example");
                    req.write(Buffer.buffer(
                            "{\"phase\":\"login\",\"operation\":\"get\",\"errorName\":\"NotAllowedError\"}"));
                },
                null,
                403,
                "Forbidden",
                null);
        verify(auditService, never()).report(any());
    }

    @Test
    public void shouldReturn403_whenOriginMissing() throws Exception {
        mountPostEndpoint();

        testRequest(
                HttpMethod.POST,
                "/webauthn/webauthn-error",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, "application/json");
                    req.write(Buffer.buffer("{\"phase\":\"login\",\"operation\":\"get\",\"errorName\":\"NotAllowedError\"}"));
                },
                null,
                403,
                "Forbidden",
                null);
        verify(auditService, never()).report(any());
    }

    @Test
    public void shouldReturn400_whenBodyInvalid() throws Exception {
        mountPostEndpoint();

        testRequest(
                HttpMethod.POST,
                "/webauthn/webauthn-error",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, "application/json");
                    req.putHeader("Origin", sameOriginHeader());
                    req.write(Buffer.buffer("{"));
                },
                null,
                400,
                "Bad Request",
                null);
        verify(auditService, never()).report(any());
    }

    @Test
    public void shouldReturn204_whenRegisterPhaseAndCreateOperation() throws Exception {
        mountPostEndpoint();

        testRequest(
                HttpMethod.POST,
                "/webauthn/webauthn-error",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, "application/json");
                    req.putHeader("Origin", sameOriginHeader());
                    req.write(Buffer.buffer(
                            "{\"phase\":\"register\",\"operation\":\"create\",\"errorName\":\"InvalidStateError\"}"));
                },
                null,
                204,
                "No Content",
                null);
        verify(auditService).report(any());
    }

    @Test
    public void shouldReturn204_whenMfaPhase() throws Exception {
        mountPostEndpoint();

        testRequest(
                HttpMethod.POST,
                "/webauthn/webauthn-error",
                req -> {
                    req.setChunked(true);
                    req.putHeader(CONTENT_TYPE, "application/json");
                    req.putHeader("Origin", sameOriginHeader());
                    req.write(Buffer.buffer("{\"phase\":\"mfa\",\"operation\":\"get\",\"errorName\":\"NotAllowedError\"}"));
                },
                null,
                204,
                "No Content",
                null);
        verify(auditService).report(any());
    }

    @Test
    public void shouldFail405_whenMethodIsNotPost() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.GET);
        endpoint.handle(ctx);
        assertTrue(ctx.failed());
        assertEquals(405, ctx.statusCode());
    }

    @Test
    public void shouldReturn400_whenJsonBodyInvalid() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        ctx.setBody(Buffer.buffer("{"));
        endpoint.handle(ctx);
        assertEquals(400, spyStatus(ctx));
    }

    @Test
    public void shouldReturn400_whenBodyJsonIsNull() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        ctx.setBody(null);
        endpoint.handle(ctx);
        assertEquals(400, spyStatus(ctx));
    }

    @Test
    public void shouldReturn400_whenRequiredFieldsMissing() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        ctx.setBody(Buffer.buffer(new JsonObject().encode()));
        endpoint.handle(ctx);
        assertEquals(400, spyStatus(ctx));
    }

    @Test
    public void shouldReturn400_whenErrorNameEmpty() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        JsonObject j = minimalValidJson();
        j.put("errorName", "   ");
        ctx.setBody(Buffer.buffer(j.encode()));
        endpoint.handle(ctx);
        assertEquals(400, spyStatus(ctx));
    }

    @Test
    public void shouldReturn400_whenPhaseNotAllowed() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        JsonObject j = minimalValidJson();
        j.put("phase", "logout");
        ctx.setBody(Buffer.buffer(j.encode()));
        endpoint.handle(ctx);
        assertEquals(400, spyStatus(ctx));
    }

    @Test
    public void shouldReturn400_whenOperationNotAllowed() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        JsonObject j = minimalValidJson();
        j.put("operation", "delete");
        ctx.setBody(Buffer.buffer(j.encode()));
        endpoint.handle(ctx);
        assertEquals(400, spyStatus(ctx));
    }

    @Test
    public void shouldReturn204_whenRegisterAndCreate() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        JsonObject j = minimalValidJson();
        j.put("phase", "register");
        j.put("operation", "create");
        ctx.setBody(Buffer.buffer(j.encode()));
        endpoint.handle(ctx);
        assertEquals(204, spyStatus(ctx));
        verify(auditService).report(any());
    }

    @Test
    public void shouldReturn204_whenMfaAndGet() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        JsonObject j = minimalValidJson();
        j.put("phase", "mfa");
        ctx.setBody(Buffer.buffer(j.encode()));
        endpoint.handle(ctx);
        assertEquals(204, spyStatus(ctx));
        verify(auditService).report(any());
    }

    @Test
    public void shouldNormalisePhaseAndOperationCase() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        JsonObject j = new JsonObject();
        j.put("phase", " LOGIN ");
        j.put("operation", " GET ");
        j.put("errorName", "SecurityError");
        ctx.setBody(Buffer.buffer(j.encode()));
        endpoint.handle(ctx);
        assertEquals(204, spyStatus(ctx));
        verify(auditService).report(any());
    }

    @Test
    public void shouldReturn204_whenDomainIsNull() {
        WebauthnErrorEndpoint ep = new WebauthnErrorEndpoint(null, auditService, WebauthnErrorEndpoint.DEFAULT_TRANSACTION_ID_HTTP_HEADER);
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        ctx.setBody(Buffer.buffer(minimalValidJson().encode()));
        ep.handle(ctx);
        assertEquals(204, spyStatus(ctx));
        verify(auditService).report(any());
    }

    @Test
    public void shouldReturn204_whenClientContextMissing() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        ctx.setBody(Buffer.buffer(minimalValidJson().encode()));
        endpoint.handle(ctx);
        assertEquals(204, spyStatus(ctx));
        verify(auditService).report(any());
    }

    @Test
    public void shouldReturn204_whenAuditReportFails() {
        doThrow(new RuntimeException("audit unavailable")).when(auditService).report(any());
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        ctx.setBody(Buffer.buffer(minimalValidJson().encode()));
        endpoint.handle(ctx);
        assertEquals(204, spyStatus(ctx));
    }

    @Test
    public void shouldIncludeOptionalFieldsInAuditPath() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        JsonObject j = minimalValidJson();
        j.put("errorMessage", "msg");
        j.put("rpId", "rp.example");
        j.put("clientTimestamp", 42L);
        j.put("username", "alice");
        ctx.setBody(Buffer.buffer(j.encode()));
        Client c = new Client();
        c.setClientId("cid-1");
        ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, c);
        endpoint.handle(ctx);
        assertEquals(204, spyStatus(ctx));
        verify(auditService).report(any());
    }

    @Test
    public void shouldResolveCorrelationFromTransactionHttpHeaderFirst() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        ctx.setBody(Buffer.buffer(minimalValidJson().encode()));
        ((DummyHttpRequest) ctx.request().getDelegate())
                .headers()
                .set(WebauthnErrorEndpoint.DEFAULT_TRANSACTION_ID_HTTP_HEADER, "tid-from-header");
        endpoint.handle(ctx);
        assertEquals(204, spyStatus(ctx));
        verify(auditService).report(any());
    }

    @Test
    public void shouldResolveCorrelationFromRoutingContextTransactionIdWhenHeaderAbsent() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        ctx.setBody(Buffer.buffer(minimalValidJson().encode()));
        ctx.put(TRANSACTION_ID_KEY, "tid-from-context");
        endpoint.handle(ctx);
        assertEquals(204, spyStatus(ctx));
        verify(auditService).report(any());
    }

    @Test
    public void shouldPreferTransactionHttpHeaderOverRoutingContextTransactionId() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        ctx.setBody(Buffer.buffer(minimalValidJson().encode()));
        ctx.put(TRANSACTION_ID_KEY, "ctx-tid");
        ((DummyHttpRequest) ctx.request().getDelegate())
                .headers()
                .set(WebauthnErrorEndpoint.DEFAULT_TRANSACTION_ID_HTTP_HEADER, "header-tid");
        endpoint.handle(ctx);
        assertEquals(204, spyStatus(ctx));
        verify(auditService).report(any());
    }

    @Test
    public void shouldUseConfigurableTransactionHeaderName() {
        WebauthnErrorEndpoint ep = new WebauthnErrorEndpoint(domain, auditService, "X-Custom-Transaction");
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        ctx.setBody(Buffer.buffer(minimalValidJson().encode()));
        ((DummyHttpRequest) ctx.request().getDelegate()).headers().set("X-Custom-Transaction", "custom-val");
        ep.handle(ctx);
        assertEquals(204, spyStatus(ctx));
        verify(auditService).report(any());
    }

    @Test
    public void shouldReturn204_whenJsonContainsCorrelationId_isIgnoredForAudit() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        JsonObject j = minimalValidJson();
        j.put("correlationId", "ignored-from-body");
        ctx.setBody(Buffer.buffer(j.encode()));
        endpoint.handle(ctx);
        assertEquals(204, spyStatus(ctx));
        verify(auditService).report(any());
    }

    @Test
    public void shouldCoverConsentLoggingBranches() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        ctx.setBody(Buffer.buffer(minimalValidJson().encode()));
        ctx.session().put(USER_CONSENT_IP_LOCATION, Boolean.TRUE);
        ctx.session().put(USER_CONSENT_USER_AGENT, Boolean.TRUE);
        DummyHttpRequest delegate = (DummyHttpRequest) ctx.request().getDelegate();
        delegate.headers().set("User-Agent", "Mozilla/x-test");
        endpoint.handle(ctx);
        assertEquals(204, spyStatus(ctx));
        verify(auditService).report(any());
    }

    @Test
    public void shouldTruncateVeryLongUsername() {
        SpyRoutingContext ctx = new SpyRoutingContext();
        ctx.setMethod(HttpMethod.POST);
        JsonObject j = minimalValidJson();
        j.put("username", "x".repeat(400));
        ctx.setBody(Buffer.buffer(j.encode()));
        endpoint.handle(ctx);
        assertEquals(204, spyStatus(ctx));
        verify(auditService).report(any());
    }
}
