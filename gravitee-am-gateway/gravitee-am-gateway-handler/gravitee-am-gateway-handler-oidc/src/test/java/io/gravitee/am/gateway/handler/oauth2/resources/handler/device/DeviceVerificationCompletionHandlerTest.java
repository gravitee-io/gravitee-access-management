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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.device;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.common.audit.EventType;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.exception.ExpiredTokenException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.resources.endpoint.device.DeviceFlowPageRenderer;
import io.gravitee.am.gateway.handler.oauth2.service.device.DeviceAuthorizationRequestService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AuthorizationRequest;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.reporter.api.audit.model.Audit;
import io.gravitee.am.repository.oauth2.model.DeviceAuthorizationRequest;
import io.gravitee.am.service.AuditService;
import io.gravitee.am.service.reporter.builder.AuditBuilder;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Session;
import io.vertx.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;
import java.util.Set;

import static io.gravitee.am.common.utils.ConstantKeys.AUTHORIZATION_REQUEST_CONTEXT_KEY;
import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static io.gravitee.am.gateway.handler.common.vertx.web.RoutingContextHelper.setUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DeviceVerificationCompletionHandlerTest extends RxWebTestBase {

    @Mock
    private DeviceAuthorizationRequestService requestService;

    @Mock
    private ThymeleafTemplateEngine engine;

    @Mock
    private Session session;

    @Mock
    private AuditService auditService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Client client = new Client();
        client.setId("client-internal-id");
        client.setClientId("client-id");
        client.setDomain("domain-id");

        AuthorizationRequest authorizationRequest = new AuthorizationRequest();
        authorizationRequest.setClientId("client-id");
        authorizationRequest.setScopes(Set.of("openid"));
        authorizationRequest.setApproved(true);

        router.route(HttpMethod.GET, "/oauth/authorize")
                .handler(context -> {
                    ((io.vertx.ext.web.impl.RoutingContextInternal) context.getDelegate()).setSession(session);
                    context.put(CLIENT_CONTEXT_KEY, client);
                    context.put(AUTHORIZATION_REQUEST_CONTEXT_KEY, authorizationRequest);
                    context.next();
                })
                .handler(new DeviceVerificationCompletionHandler(requestService, new DeviceFlowPageRenderer(engine, new Domain()), auditService))
                .handler(rc -> rc.response().setStatusCode(204).end())
                .failureHandler(rc -> rc.response().setStatusCode(403).end());
    }

    @Test
    public void shouldPassThroughWhenTheRequestIsNotADeviceFlow() throws Exception {
        when(session.get(ConstantKeys.DEVICE_FLOW_CLIENT_ID_KEY)).thenReturn(null);

        testRequest(HttpMethod.GET, "/oauth/authorize?client_id=client-id", 204, "No Content");

        verify(requestService, never()).approve(anyString(), anyString(), anyString(), any());
    }

    @Test
    public void shouldRejectWhenTheUserIsNotAuthenticated() throws Exception {
        when(session.get(ConstantKeys.DEVICE_FLOW_CLIENT_ID_KEY)).thenReturn("client-id");
        when(session.get(ConstantKeys.DEVICE_FLOW_DEVICE_CODE_KEY)).thenReturn("device-code");

        testRequest(HttpMethod.GET, "/oauth/authorize?client_id=client-id", 403, "Forbidden");

        verify(requestService, never()).approve(anyString(), anyString(), anyString(), any());
    }

    @Test
    public void shouldApproveTheDeviceCodeAndRenderTheCompletionPage() throws Exception {
        when(session.get(ConstantKeys.DEVICE_FLOW_CLIENT_ID_KEY)).thenReturn("client-id");
        when(session.get(ConstantKeys.DEVICE_FLOW_DEVICE_CODE_KEY)).thenReturn("device-code");
        when(requestService.approve(eq("device-code"), eq("client-id"), eq("user-id"), eq(Set.of("openid"))))
                .thenReturn(Single.just(new DeviceAuthorizationRequest()));
        when(engine.render(any(Map.class), eq("device_completion|client-internal-id")))
                .thenReturn(Single.just(Buffer.buffer("completion page")));

        User user = new User();
        user.setId("user-id");
        router.route().order(-1).handler(context -> {
            setUser(context, new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user));
            context.next();
        });

        testRequest(HttpMethod.GET, "/oauth/authorize?client_id=client-id", null, 200, "OK", "completion page");

        verify(engine).render(ArgumentMatchers.<Map<String, Object>>argThat(data -> "approved".equals(data.get("outcome"))),
                eq("device_completion|client-internal-id"));
        verify(session).remove(ConstantKeys.DEVICE_FLOW_DEVICE_CODE_KEY);
        verify(session).remove(ConstantKeys.DEVICE_FLOW_CLIENT_ID_KEY);
    }

    @Test
    public void shouldRenderTheExpiredOutcomeWhenTheCodeLapsedDuringTheJourney() throws Exception {
        assertOutcomeOnApprovalFailure(new ExpiredTokenException(), "expired");
    }

    @Test
    public void shouldRenderTheInvalidOutcomeWhenTheCodeIsNoLongerActionable() throws Exception {
        assertOutcomeOnApprovalFailure(new InvalidGrantException("device-code"), "invalid");
    }

    @Test
    public void shouldReportAnApprovedEventWithTheUserAsActor() throws Exception {
        when(session.get(ConstantKeys.DEVICE_FLOW_CLIENT_ID_KEY)).thenReturn("client-id");
        when(session.get(ConstantKeys.DEVICE_FLOW_DEVICE_CODE_KEY)).thenReturn("device-code");
        when(requestService.approve(eq("device-code"), eq("client-id"), eq("user-id"), eq(Set.of("openid"))))
                .thenReturn(Single.just(new DeviceAuthorizationRequest()));
        when(engine.render(any(Map.class), eq("device_completion|client-internal-id")))
                .thenReturn(Single.just(Buffer.buffer("completion page")));

        User user = new User();
        user.setId("user-id");
        user.setUsername("bob");
        router.route().order(-1).handler(context -> {
            setUser(context, new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user));
            context.next();
        });

        testRequest(HttpMethod.GET, "/oauth/authorize?client_id=client-id", null, 200, "OK", "completion page");

        ArgumentCaptor<AuditBuilder> captor = ArgumentCaptor.forClass(AuditBuilder.class);
        verify(auditService).report(captor.capture());
        Audit audit = captor.getValue().build(new ObjectMapper());
        assertEquals(EventType.DEVICE_FLOW_APPROVED, audit.getType());
        assertEquals("user-id", audit.getActor().getId());
        assertEquals("bob", audit.getActor().getAlternativeId());
        assertEquals("client-internal-id", audit.getTarget().getId());
        assertEquals("domain-id", audit.getReferenceId());
    }

    @Test
    public void shouldNotReportAnApprovedEventWhenTheApprovalFailed() throws Exception {
        assertOutcomeOnApprovalFailure(new ExpiredTokenException(), "expired");

        verify(auditService, never()).report(any());
    }

    private void assertOutcomeOnApprovalFailure(Throwable failure, String expectedOutcome) throws Exception {
        when(session.get(ConstantKeys.DEVICE_FLOW_CLIENT_ID_KEY)).thenReturn("client-id");
        when(session.get(ConstantKeys.DEVICE_FLOW_DEVICE_CODE_KEY)).thenReturn("device-code");
        when(requestService.approve(eq("device-code"), eq("client-id"), eq("user-id"), eq(Set.of("openid"))))
                .thenReturn(Single.error(failure));
        when(engine.render(any(Map.class), eq("device_completion|client-internal-id")))
                .thenReturn(Single.just(Buffer.buffer("completion page")));

        User user = new User();
        user.setId("user-id");
        router.route().order(-1).handler(context -> {
            setUser(context, new io.gravitee.am.gateway.handler.common.vertx.web.auth.user.User(user));
            context.next();
        });

        testRequest(HttpMethod.GET, "/oauth/authorize?client_id=client-id", null, 200, "OK", "completion page");

        verify(engine).render(ArgumentMatchers.<Map<String, Object>>argThat(data -> expectedOutcome.equals(data.get("outcome"))),
                eq("device_completion|client-internal-id"));
    }
}
