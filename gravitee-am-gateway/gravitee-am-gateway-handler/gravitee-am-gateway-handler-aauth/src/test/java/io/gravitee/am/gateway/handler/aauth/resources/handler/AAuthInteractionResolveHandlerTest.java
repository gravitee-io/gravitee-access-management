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
package io.gravitee.am.gateway.handler.aauth.resources.handler;

import io.gravitee.am.gateway.handler.aauth.model.PendingRequestStatus;
import io.gravitee.am.gateway.handler.aauth.service.pending.AAuthPendingRequestService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.repository.oidc.model.AAuthPendingRequest;
import io.gravitee.am.service.ApplicationService;
import io.reactivex.rxjava3.core.Maybe;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;

import java.util.Date;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AAuthInteractionResolveHandler}.
 */
public class AAuthInteractionResolveHandlerTest extends RxWebTestBase {

    private AAuthPendingRequestService pendingService;
    private ApplicationService applicationService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        pendingService = mock(AAuthPendingRequestService.class);
        applicationService = mock(ApplicationService.class);

        var handler = new AAuthInteractionResolveHandler(pendingService, applicationService);

        router.route("/aauth/interact")
                .handler(handler)
                .handler(rc -> rc.response().setStatusCode(200).end("consent page"))
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldReturn400_whenCodeMissing() throws Exception {
        testRequest(HttpMethod.GET, "/aauth/interact", 400, "Bad Request");
    }

    @Test
    public void shouldReturn410_whenCodeNotFound() throws Exception {
        when(pendingService.findByInteractionCode(eq("XXXX-1234"))).thenReturn(Maybe.empty());

        testRequest(HttpMethod.GET, "/aauth/interact?code=XXXX-1234", 410, "Gone");
    }

    @Test
    public void shouldReturn410_whenRequestExpired() throws Exception {
        var pending = createPending(PendingRequestStatus.EXPIRED);
        when(pendingService.findByInteractionCode(eq("XXXX-1234"))).thenReturn(Maybe.just(pending));

        testRequest(HttpMethod.GET, "/aauth/interact?code=XXXX-1234", 410, "Gone");
    }

    @Test
    public void shouldReturn410_whenRequestAlreadyCompleted() throws Exception {
        var pending = createPending(PendingRequestStatus.COMPLETED);
        when(pendingService.findByInteractionCode(eq("XXXX-1234"))).thenReturn(Maybe.just(pending));

        testRequest(HttpMethod.GET, "/aauth/interact?code=XXXX-1234", 410, "Gone");
    }

    @Test
    public void shouldReturn400_whenNoApplicationId() throws Exception {
        var pending = createPending(PendingRequestStatus.PENDING);
        pending.setApplicationId(null);
        when(pendingService.findByInteractionCode(eq("XXXX-1234"))).thenReturn(Maybe.just(pending));

        testRequest(HttpMethod.GET, "/aauth/interact?code=XXXX-1234", 400, "Bad Request");
    }

    @Test
    public void shouldContinueToNextHandler_whenCodeValidAndAppFound() throws Exception {
        var pending = createPending(PendingRequestStatus.PENDING);
        pending.setApplicationId("app-1");
        when(pendingService.findByInteractionCode(eq("XXXX-1234"))).thenReturn(Maybe.just(pending));

        var app = new io.gravitee.am.model.Application();
        app.setId("app-1");
        app.setName("Test Agent");
        when(applicationService.findById(eq("app-1"))).thenReturn(io.reactivex.rxjava3.core.Maybe.just(app));

        testRequest(HttpMethod.GET, "/aauth/interact?code=XXXX-1234", 200, "OK");
    }

    @Test
    public void shouldReturn500_whenApplicationLoadFails() throws Exception {
        var pending = createPending(PendingRequestStatus.PENDING);
        pending.setApplicationId("app-1");
        when(pendingService.findByInteractionCode(eq("XXXX-1234"))).thenReturn(Maybe.just(pending));
        when(applicationService.findById(eq("app-1")))
                .thenReturn(io.reactivex.rxjava3.core.Maybe.error(new RuntimeException("DB error")));

        testRequest(HttpMethod.GET, "/aauth/interact?code=XXXX-1234", 500, "Internal Server Error");
    }

    @Test
    public void shouldReturn400_whenApplicationNotFound() throws Exception {
        var pending = createPending(PendingRequestStatus.PENDING);
        pending.setApplicationId("app-1");
        when(pendingService.findByInteractionCode(eq("XXXX-1234"))).thenReturn(Maybe.just(pending));
        when(applicationService.findById(eq("app-1")))
                .thenReturn(io.reactivex.rxjava3.core.Maybe.empty());

        testRequest(HttpMethod.GET, "/aauth/interact?code=XXXX-1234", 400, "Bad Request");
    }

    private AAuthPendingRequest createPending(PendingRequestStatus status) {
        var req = new AAuthPendingRequest();
        req.setId("pending-1");
        req.setStatus(status.name());
        req.setDomain("domain-1");
        req.setAgentId("https://agent.example");
        req.setInteractionCode("XXXX-1234");
        req.setCreatedAt(new Date());
        req.setExpireAt(new Date(System.currentTimeMillis() + 600_000));
        return req;
    }
}
