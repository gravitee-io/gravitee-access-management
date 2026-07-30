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
package io.gravitee.am.gateway.handler.root.resources.handler.webauthn;

import io.gravitee.am.service.DomainDataPlane;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The relying party id the browser is handed has to follow the origin the ceremony is verified
 * against, otherwise it refuses the ceremony outright.
 *
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class WebAuthnHandlerRelyingPartyTest {

    @Mock
    private DomainDataPlane domainDataPlane;

    private final RoutingContext ctx = mock(RoutingContext.class);
    private WebAuthnHandler cut;

    @Before
    public void before() {
        when(ctx.request()).thenReturn(mock(HttpServerRequest.class));
        cut = new WebAuthnHandler() {
            @Override
            public void handle(RoutingContext event) {
                // nothing to route, only the shared helpers are under test
            }
        };
        cut.setDomainDataplane(domainDataPlane);
    }

    @Test
    public void shouldRewriteTheRegistrationRelyingPartyId() {
        inManagedCloudOn("https://auth.acme.com");
        JsonObject options = new JsonObject().put("rp", new JsonObject().put("id", "localhost").put("name", "Gravitee"));

        cut.applyRelyingPartyId(ctx, options);

        assertEquals("auth.acme.com", options.getJsonObject("rp").getString("id"));
        assertEquals("Gravitee", options.getJsonObject("rp").getString("name"));
    }

    @Test
    public void shouldRewriteTheAssertionRelyingPartyId() {
        inManagedCloudOn("https://auth.acme.com:8443");
        JsonObject options = new JsonObject().put("rpId", "localhost");

        cut.applyRelyingPartyId(ctx, options);

        // The port is part of the origin but never part of the relying party id.
        assertEquals("auth.acme.com", options.getString("rpId"));
    }

    @Test
    public void shouldLeaveTheOptionsAloneOutsideManagedCloud() {
        when(domainDataPlane.isManagedCloud()).thenReturn(false);
        JsonObject options = new JsonObject().put("rpId", "configured.acme.com");

        cut.applyRelyingPartyId(ctx, options);

        assertEquals("configured.acme.com", options.getString("rpId"));
    }

    @Test
    public void shouldLeaveTheOptionsAloneWhenTheOriginCarriesNoHost() {
        inManagedCloudOn("not-a-url");
        JsonObject options = new JsonObject().put("rpId", "configured.acme.com");

        cut.applyRelyingPartyId(ctx, options);

        assertEquals("configured.acme.com", options.getString("rpId"));
    }

    private void inManagedCloudOn(String origin) {
        when(domainDataPlane.isManagedCloud()).thenReturn(true);
        when(domainDataPlane.getWebAuthnOrigin(any())).thenReturn(origin);
    }
}
