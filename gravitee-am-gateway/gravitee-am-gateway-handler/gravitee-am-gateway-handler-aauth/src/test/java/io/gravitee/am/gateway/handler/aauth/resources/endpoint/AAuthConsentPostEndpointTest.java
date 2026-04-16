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
package io.gravitee.am.gateway.handler.aauth.resources.endpoint;

import io.gravitee.am.gateway.handler.aauth.model.PendingRequestStatus;
import io.gravitee.am.gateway.handler.aauth.service.pending.AAuthPendingRequestService;
import io.gravitee.am.gateway.handler.aauth.service.token.AAuthTokenService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.gateway.handler.aauth.service.consent.AAuthConsentService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.oidc.model.AAuthPendingRequest;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;

import java.util.Base64;
import java.util.Date;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link AAuthConsentPostEndpoint}.
 */
public class AAuthConsentPostEndpointTest extends RxWebTestBase {

    private AAuthPendingRequestService pendingService;
    private AAuthTokenService tokenService;
    private AAuthConsentService consentService;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        pendingService = mock(AAuthPendingRequestService.class);
        tokenService = mock(AAuthTokenService.class);
        consentService = mock(AAuthConsentService.class);

        Domain domain = new Domain();
        domain.setId("domain-1");

        var endpoint = new AAuthConsentPostEndpoint(pendingService, tokenService, consentService, domain);

        // Note: tests that require an authenticated user (approve flow) need
        // a real session handler, which is an integration test concern.
        // Unit tests here cover: missing code, invalid code, deny flow.
        router.route("/aauth/consent")
                .handler(io.vertx.rxjava3.ext.web.handler.BodyHandler.create())
                .handler(endpoint)
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldReturn400_whenCodeMissing() throws Exception {
        testRequest(
                HttpMethod.POST, "/aauth/consent",
                req -> {
                    req.putHeader("Content-Type", "application/x-www-form-urlencoded");
                    req.end(io.vertx.core.buffer.Buffer.buffer("user_oauth_approval=true"));
                },
                null, 400, "Bad Request", null
        );
    }

    @Test
    public void shouldReturn410_whenCodeNotFound() throws Exception {
        when(pendingService.findByInteractionCode(eq("XXXX-1234"))).thenReturn(Maybe.empty());

        // Without a user session, the endpoint returns 401 before checking the code
        testRequest(
                HttpMethod.POST, "/aauth/consent",
                req -> {
                    req.putHeader("Content-Type", "application/x-www-form-urlencoded");
                    req.end(io.vertx.core.buffer.Buffer.buffer("code=XXXX-1234&user_oauth_approval=true"));
                },
                null, 401, "Unauthorized", null
        );
    }

    private AAuthPendingRequest createPending() {
        var req = new AAuthPendingRequest();
        req.setId("pending-1");
        req.setStatus(PendingRequestStatus.PENDING.name());
        req.setDomain("domain-1");
        req.setAgentId("https://agent.example");
        req.setAgentSub("https://agent.example");
        req.setAgentJkt("thumbprint");
        // Serialize a dummy Ed25519 public key
        try {
            var kp = java.security.KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
            req.setAgentPublicKey(Base64.getEncoder().encodeToString(kp.getPublic().getEncoded()));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        req.setResourceIss("https://resource.example");
        req.setScope("read write");
        req.setPsIssuerUrl("https://ps.example/aauth");
        req.setInteractionCode("XXXX-1234");
        req.setCreatedAt(new Date());
        req.setExpireAt(new Date(System.currentTimeMillis() + 600_000));
        return req;
    }
}
