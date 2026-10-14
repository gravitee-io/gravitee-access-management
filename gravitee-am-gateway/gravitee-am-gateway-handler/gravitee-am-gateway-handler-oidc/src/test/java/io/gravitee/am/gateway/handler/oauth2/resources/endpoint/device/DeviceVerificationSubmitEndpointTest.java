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
package io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.audit.Status;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.service.ratelimit.DeviceFlowRateLimiterService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.exception.ExpiredTokenException;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestStatus;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Session;
import io.vertx.rxjava3.core.http.HttpClientResponse;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Date;
import java.util.Map;
import java.util.Set;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static io.gravitee.am.gateway.handler.common.vertx.web.RoutingContextHelper.setUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DeviceVerificationSubmitEndpointTest extends RxWebTestBase {

    @Mock
    private DeviceAuthorizationRequestService requestService;

    @Mock
    private Session session;

    @Mock
    private ThymeleafTemplateEngine engine;

    @Mock
    private DeviceFlowRateLimiterService rateLimiterService;

    @Mock
    private AuditService auditService;

    private boolean anonymous;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Client client = new Client();
        client.setId("client-internal-id");
        client.setClientId("client-id");
        client.setDomain("domain-id");

        User endUser = new User();
        endUser.setId("user-id");

        router.route(HttpMethod.POST, "/oauth/device")
                .handler(io.vertx.rxjava3.ext.web.handler.BodyHandler.create())
                .handler(context -> {
                    ((io.vertx.ext.web.impl.RoutingContextInternal) context.getDelegate()).setSession(session);
                    context.put(CLIENT_CONTEXT_KEY, client);
                    context.put(CONTEXT_PATH, "");
                    if (!anonymous) {
                        setUser(context, new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(endUser));
                    }
                    context.next();
                })
                .handler(new DeviceVerificationSubmitEndpoint(requestService, new DeviceFlowPageRenderer(engine, new Domain()), rateLimiterService, auditService))
                .failureHandler(rc -> rc.response().setStatusCode(500).end());
    }

    @Test
    public void shouldRefuseTheAttemptOnceTheUserExhaustedTheirCodeEntryBudget() throws Exception {
        when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        when(rateLimiterService.hasAttemptsLeft("user-id", "domain-id")).thenReturn(Single.just(false));

        assertBackToCodeEntryPage(postForm("user_code=BCDF-GHJK"), DeviceVerificationSubmitEndpoint.ERROR_TOO_MANY_ATTEMPTS);

        verify(requestService, never()).findByUserCode(anyString());
    }

    @Test
    public void shouldRefuseARejectionOnceTheBudgetIsExhausted() throws Exception {
        when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        when(rateLimiterService.hasAttemptsLeft("user-id", "domain-id")).thenReturn(Single.just(false));

        assertBackToCodeEntryPage(postForm("user_code=BCDF-GHJK&action=reject"), DeviceVerificationSubmitEndpoint.ERROR_TOO_MANY_ATTEMPTS);

        verify(requestService, never()).deny(anyString(), anyString());
    }

    @Test
    public void shouldChargeTheBudgetForACodeNoApplicationIsWaitingOn() throws Exception {
        when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        when(rateLimiterService.hasAttemptsLeft("user-id", "domain-id")).thenReturn(Single.just(true));
        when(rateLimiterService.recordWrongAttempt("user-id", "domain-id")).thenReturn(Completable.complete());
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.empty());

        assertBackToCodeEntryPage(postForm("user_code=BCDF-GHJK"), DeviceVerificationSubmitEndpoint.ERROR_INVALID);

        verify(rateLimiterService).recordWrongAttempt("user-id", "domain-id");
    }

    @Test
    public void shouldChargeTheBudgetForACodeOfAnotherApplication() throws Exception {
        DeviceAuthorizationRequest request = pendingRequest();
        request.setClientId("another-client");
        when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        when(rateLimiterService.hasAttemptsLeft("user-id", "domain-id")).thenReturn(Single.just(true));
        when(rateLimiterService.recordWrongAttempt("user-id", "domain-id")).thenReturn(Completable.complete());
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.just(request));

        assertBackToCodeEntryPage(postForm("user_code=BCDF-GHJK"), DeviceVerificationSubmitEndpoint.ERROR_INVALID);

        verify(rateLimiterService).recordWrongAttempt("user-id", "domain-id");
    }

    @Test
    public void shouldNotChargeTheBudgetForACodeThatWasRight() throws Exception {
        DeviceAuthorizationRequest request = pendingRequest();
        when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        when(rateLimiterService.hasAttemptsLeft("user-id", "domain-id")).thenReturn(Single.just(true));
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.just(request));
        when(requestService.isPending(request)).thenReturn(true);

        HttpClientResponse response = postForm("user_code=BCDF-GHJK");

        assertEquals(302, response.statusCode());
        assertTrue(response.headers().get("location").contains("/oauth/authorize"));
        verify(rateLimiterService, never()).recordWrongAttempt(anyString(), anyString());
    }

    @Test
    public void shouldNotChargeTheBudgetForARejection() throws Exception {
        DeviceAuthorizationRequest request = pendingRequest();
        when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        when(rateLimiterService.hasAttemptsLeft("user-id", "domain-id")).thenReturn(Single.just(true));
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.just(request));
        when(requestService.deny("device-code", "client-id")).thenReturn(Single.just(request));
        expectCompletionPage();

        assertEquals(200, postForm("user_code=BCDF-GHJK&action=reject").statusCode());

        verify(rateLimiterService, never()).recordWrongAttempt(anyString(), anyString());
    }

    @Test
    public void shouldFailAnUnattributableSubmissionRatherThanLetItThrough() throws Exception {
        when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        anonymous = true;

        HttpClientResponse response = postForm("user_code=BCDF-GHJK");

        assertEquals(500, response.statusCode());
        verify(requestService, never()).findByUserCode(anyString());
    }

    @Test
    public void shouldNotSpendAnAttemptOnASubmissionCarryingNoCode() throws Exception {
        lenient().when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);

        assertBackToCodeEntryPage(postForm(""), DeviceVerificationSubmitEndpoint.ERROR_MISSING);

        verify(rateLimiterService, never()).hasAttemptsLeft(anyString(), anyString());
    }

    @Test
    public void shouldSendBackToTheCodeEntryPageWhenNoCodeIsSubmitted() throws Exception {
        assertBackToCodeEntryPage(postForm(""), DeviceVerificationSubmitEndpoint.ERROR_MISSING);

        verify(session, never()).put(eq(ConstantKeys.DEVICE_FLOW_DEVICE_CODE_KEY), any());
    }

    @Test
    public void shouldSendBackToTheCodeEntryPageWhenTheCodeIsUnknown() throws Exception {
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.empty());

        assertBackToCodeEntryPage(postForm("user_code=BCDF-GHJK"), DeviceVerificationSubmitEndpoint.ERROR_INVALID);

        verify(session, never()).put(eq(ConstantKeys.DEVICE_FLOW_DEVICE_CODE_KEY), any());
    }

    @Test
    public void shouldSendBackToTheCodeEntryPageWhenTheCodeBelongsToAnotherClient() throws Exception {
        DeviceAuthorizationRequest request = pendingRequest();
        request.setClientId("another-client");
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.just(request));

        assertBackToCodeEntryPage(postForm("user_code=BCDF-GHJK"), DeviceVerificationSubmitEndpoint.ERROR_INVALID);
    }

    @Test
    public void shouldSendBackToTheCodeEntryPageWhenTheCodeIsNoLongerPending() throws Exception {
        DeviceAuthorizationRequest request = pendingRequest();
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.just(request));
        when(requestService.isPending(request)).thenReturn(false);

        assertBackToCodeEntryPage(postForm("user_code=BCDF-GHJK"), DeviceVerificationSubmitEndpoint.ERROR_INVALID);
    }

    @Test
    public void shouldRenderTheExpiredOutcomeWhenTheCodeHasLapsed() throws Exception {
        DeviceAuthorizationRequest request = pendingRequest();
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.just(request));
        when(requestService.isExpired(request)).thenReturn(true);
        expectCompletionPage();

        HttpClientResponse response = postForm("user_code=BCDF-GHJK");

        assertEquals(200, response.statusCode());
        assertRenderedOutcome("expired");
        verify(requestService, never()).deny(anyString(), anyString());
    }

    @Test
    public void shouldDenyTheRequestAndRenderTheDeniedOutcome() throws Exception {
        DeviceAuthorizationRequest request = pendingRequest();
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.just(request));
        when(requestService.deny("device-code", "client-id")).thenReturn(Single.just(request));
        expectCompletionPage();

        HttpClientResponse response = postForm("user_code=BCDF-GHJK&action=reject");

        assertEquals(200, response.statusCode());
        assertRenderedOutcome("denied");
        verify(session, never()).put(eq(ConstantKeys.DEVICE_FLOW_DEVICE_CODE_KEY), any());
    }

    @Test
    public void shouldRenderTheInvalidOutcomeWhenRejectingWithoutACode() throws Exception {
        lenient().when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        expectCompletionPage();

        HttpClientResponse response = postForm("action=reject");

        assertEquals(200, response.statusCode());
        assertRenderedOutcome("invalid");
        verify(requestService, never()).findByUserCode(anyString());
        verify(rateLimiterService, never()).hasAttemptsLeft(anyString(), anyString());
        verify(rateLimiterService, never()).recordWrongAttempt(anyString(), anyString());
    }

    @Test
    public void shouldRenderTheInvalidOutcomeWhenRejectingAnUnknownCode() throws Exception {
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.empty());
        expectCompletionPage();

        HttpClientResponse response = postForm("user_code=BCDF-GHJK&action=reject");

        assertEquals(200, response.statusCode());
        assertRenderedOutcome("invalid");
        verify(requestService, never()).deny(anyString(), anyString());
    }

    @Test
    public void shouldRenderTheSameInvalidOutcomeWhenRejectingACodeOfAnotherClient() throws Exception {
        DeviceAuthorizationRequest request = pendingRequest();
        request.setClientId("another-client");
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.just(request));
        expectCompletionPage();

        HttpClientResponse response = postForm("user_code=BCDF-GHJK&action=reject");

        assertEquals(200, response.statusCode());
        assertRenderedOutcome("invalid");
    }

    @Test
    public void shouldRenderTheExpiredOutcomeWhenRejectingALapsedCode() throws Exception {
        DeviceAuthorizationRequest request = pendingRequest();
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.just(request));
        when(requestService.deny("device-code", "client-id")).thenReturn(Single.error(new ExpiredTokenException()));
        expectCompletionPage();

        HttpClientResponse response = postForm("user_code=BCDF-GHJK&action=reject");

        assertEquals(200, response.statusCode());
        assertRenderedOutcome("expired");
    }

    @Test
    public void shouldHandOverToTheAuthorizationChain() throws Exception {
        DeviceAuthorizationRequest request = pendingRequest();
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.just(request));
        when(requestService.isPending(request)).thenReturn(true);

        HttpClientResponse response = postForm("user_code=BCDF-GHJK");

        assertEquals(302, response.statusCode());
        String location = response.headers().get("location");
        assertNotNull(location);
        assertTrue(location.contains("/oauth/authorize"));
        assertTrue(location.contains("client_id=client-id"));
        assertTrue(location.contains("scope=openid"));
        verify(session).put(ConstantKeys.DEVICE_FLOW_DEVICE_CODE_KEY, "device-code");
        verify(session).put(ConstantKeys.DEVICE_FLOW_CLIENT_ID_KEY, "client-id");
        verify(session).remove(ConstantKeys.RETURN_URL_KEY);
    }

    @Test
    public void shouldReportAVerificationFailedEventWhenTheBudgetIsExhausted() throws Exception {
        when(rateLimiterService.isRateLimitEnabled()).thenReturn(true);
        when(rateLimiterService.hasAttemptsLeft("user-id", "domain-id")).thenReturn(Single.just(false));

        postForm("user_code=BCDF-GHJK");

        Audit audit = reportedAudit();
        assertEquals(EventType.DEVICE_FLOW_VERIFICATION_FAILED, audit.getType());
        assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
        assertTrue(audit.getOutcome().getMessage().contains("too many attempts"));
        assertEquals("user-id", audit.getActor().getId());
    }

    @Test
    public void shouldReportAVerificationFailedEventWhenNoApplicationIsWaitingOnTheCode() throws Exception {
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.empty());

        postForm("user_code=BCDF-GHJK");

        Audit audit = reportedAudit();
        assertEquals(EventType.DEVICE_FLOW_VERIFICATION_FAILED, audit.getType());
        assertEquals(Status.FAILURE, audit.getOutcome().getStatus());
        assertEquals("user-id", audit.getActor().getId());
        assertEquals("client-internal-id", audit.getTarget().getId());
        assertEquals("domain-id", audit.getReferenceId());
    }

    @Test
    public void shouldReportAVerificationFailedEventWhenTheCodeBelongsToAnotherApplication() throws Exception {
        DeviceAuthorizationRequest request = pendingRequest();
        request.setClientId("another-client");
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.just(request));

        postForm("user_code=BCDF-GHJK");

        assertEquals(EventType.DEVICE_FLOW_VERIFICATION_FAILED, reportedAudit().getType());
    }

    @Test
    public void shouldReportADeniedEventWithTheUserAsActorWhenTheCodeIsRejected() throws Exception {
        DeviceAuthorizationRequest request = pendingRequest();
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.just(request));
        when(requestService.deny("device-code", "client-id")).thenReturn(Single.just(request));
        expectCompletionPage();

        postForm("user_code=BCDF-GHJK&action=reject");

        Audit audit = reportedAudit();
        assertEquals(EventType.DEVICE_FLOW_DENIED, audit.getType());
        assertEquals(Status.SUCCESS, audit.getOutcome().getStatus());
        assertEquals("user-id", audit.getActor().getId());
        assertEquals("client-internal-id", audit.getTarget().getId());
    }

    @Test
    public void shouldNotReportAnyEventWhenTheCodeIsAccepted() throws Exception {
        DeviceAuthorizationRequest request = pendingRequest();
        when(requestService.findByUserCode("BCDF-GHJK")).thenReturn(Maybe.just(request));
        when(requestService.isPending(request)).thenReturn(true);

        postForm("user_code=BCDF-GHJK");

        verify(auditService, never()).report(any());
    }

    @Test
    public void shouldNotReportAVerificationFailedEventWhenNoCodeIsSubmitted() throws Exception {
        postForm("");

        verify(auditService, never()).report(any());
    }

    private Audit reportedAudit() {
        ArgumentCaptor<AuditBuilder> captor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(captor.capture());
        return captor.getValue().build(new ObjectMapper());
    }

    private void assertBackToCodeEntryPage(HttpClientResponse response, String expectedError) {
        assertEquals(302, response.statusCode());
        String location = response.headers().get("location");
        assertNotNull(location);
        assertTrue(location.contains("/oauth/device"));
        assertTrue(location.contains("client_id=client-id"));
        verify(session).put(ConstantKeys.DEVICE_FLOW_ERROR_KEY, expectedError);
    }

    private void assertRenderedOutcome(String expectedOutcome) {
        verify(engine).render(ArgumentMatchers.<Map<String, Object>>argThat(data -> expectedOutcome.equals(data.get("outcome"))),
                eq("device_completion|client-internal-id"));
    }

    private void expectCompletionPage() {
        when(engine.render(any(Map.class), eq("device_completion|client-internal-id")))
                .thenReturn(Single.just(Buffer.buffer("completion page")));
    }

    private HttpClientResponse postForm(String body) {
        return client.rxRequest(HttpMethod.POST, "localhost", "/oauth/device?client_id=client-id")
                .flatMap(request -> {
                    request.putHeader("content-type", "application/x-www-form-urlencoded");
                    return request.rxSend(body);
                })
                .blockingGet();
    }

    private DeviceAuthorizationRequest pendingRequest() {
        DeviceAuthorizationRequest request = new DeviceAuthorizationRequest();
        request.setId("device-code");
        request.setUserCode("BCDFGHJK");
        request.setClientId("client-id");
        request.setStatus(DeviceAuthorizationRequestStatus.PENDING.name());
        request.setScopes(Set.of("openid"));
        request.setCreatedAt(new Date());
        request.setLastAccessAt(new Date());
        request.setExpireAt(new Date());
        return request;
    }
}
