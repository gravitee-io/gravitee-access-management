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

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Session;
import io.vertx.rxjava3.ext.web.templ.thymeleaf.ThymeleafTemplateEngine;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.CLIENT_CONTEXT_KEY;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class DeviceVerificationEndpointTest extends RxWebTestBase {

    @Mock
    private ThymeleafTemplateEngine engine;

    @Mock
    private Session session;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        Client client = new Client();
        client.setId("client-internal-id");
        client.setClientId("client-id");

        router.route(HttpMethod.GET, "/oauth/device")
                .handler(context -> {
                    ((io.vertx.ext.web.impl.RoutingContextInternal) context.getDelegate()).setSession(session);
                    context.put(CLIENT_CONTEXT_KEY, client);
                    context.next();
                })
                .handler(new DeviceVerificationEndpoint(new DeviceFlowPageRenderer(engine, new Domain())))
                .failureHandler(rc -> rc.response().setStatusCode(500).end());
    }

    @Test
    public void shouldRenderTheApplicationSpecificCodeEntryTemplate() throws Exception {
        when(engine.render(org.mockito.ArgumentMatchers.<Map<String, Object>>any(), eq("device_code_entry|client-internal-id")))
                .thenReturn(Single.just(Buffer.buffer("code entry page")));

        testRequest(HttpMethod.GET, "/oauth/device?client_id=client-id", null, 200, "OK", "code entry page");

        verify(session).remove(ConstantKeys.RETURN_URL_KEY);
    }

    @Test
    public void shouldSurfaceTheErrorLeftBySubmissionAndConsumeIt() throws Exception {
        when(session.remove(ConstantKeys.DEVICE_FLOW_ERROR_KEY)).thenReturn("invalid");
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.captor();
        when(engine.render(data.capture(), eq("device_code_entry|client-internal-id")))
                .thenReturn(Single.just(Buffer.buffer("code entry page")));

        testRequest(HttpMethod.GET, "/oauth/device?client_id=client-id", null, 200, "OK", "code entry page");

        assertEquals("invalid", data.getValue().get(ConstantKeys.ERROR_PARAM_KEY));
    }

    @Test
    public void shouldPrefillTheCodeCarriedByTheCompleteVerificationUri() throws Exception {
        assertPrefilledCode("/oauth/device?client_id=client-id&user_code=bcdfghjk", "BCDF-GHJK");
    }

    @Test
    public void shouldPrefillACodeAlreadyHyphenated() throws Exception {
        assertPrefilledCode("/oauth/device?client_id=client-id&user_code=BCDF-GHJK", "BCDF-GHJK");
    }

    @Test
    public void shouldPrefillATamperedCodeSoTheUserSeesWhatFails() throws Exception {
        assertPrefilledCode("/oauth/device?client_id=client-id&user_code=nope", "nope");
    }

    @Test
    public void shouldLeaveTheFieldEmptyWithoutACodeInTheUri() throws Exception {
        assertPrefilledCode("/oauth/device?client_id=client-id", null);
    }

    private void assertPrefilledCode(String uri, String expectedCode) throws Exception {
        ArgumentCaptor<Map<String, Object>> data = ArgumentCaptor.captor();
        when(engine.render(data.capture(), eq("device_code_entry|client-internal-id")))
                .thenReturn(Single.just(Buffer.buffer("code entry page")));

        testRequest(HttpMethod.GET, uri, null, 200, "OK", "code entry page");

        assertEquals(expectedCode, data.getValue().get("user_code"));
    }
}
