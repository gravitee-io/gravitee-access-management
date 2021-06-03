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
package io.gravitee.am.gateway.handler.oidc.resources.endpoint;

import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.oauth2.resources.handler.ExceptionHandler;
import io.gravitee.am.model.jose.JWK;
import io.gravitee.am.model.jose.RSAKey;
import io.gravitee.am.model.oidc.JWKSet;
import io.gravitee.common.http.HttpStatusCode;
import io.reactivex.Single;
import io.vertx.core.http.HttpMethod;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Collections;

import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ProviderJWKSetEndpointHandlerTest extends RxWebTestBase {

    @Mock
    private JWKService jwkService;

    @InjectMocks
    private ProviderJWKSetEndpoint providerJWKSetEndpoint = new ProviderJWKSetEndpoint(jwkService);

    @Override
    public void setUp() throws Exception {
        super.setUp();

        router.route(HttpMethod.GET, "/.well-known/jwks.json")
                .handler(providerJWKSetEndpoint);
        router.route().failureHandler(new ExceptionHandler());
    }

    @Test
    public void shouldInvokeJWKSetEndpoint() throws Exception {
        JWK jwk = new RSAKey();
        jwk.setKty("RSA");
        jwk.setKid("my-test-key");

        JWKSet jwkSet = new JWKSet();
        jwkSet.setKeys(Collections.singletonList(jwk));

        when(jwkService.getKeys()).thenReturn(Single.just(jwkSet));

        testRequest(
                HttpMethod.GET, "/.well-known/jwks.json",
                HttpStatusCode.OK_200, "OK", "{\n" +
                        "  \"keys\" : [ {\n" +
                        "    \"kty\" : \"RSA\",\n" +
                        "    \"kid\" : \"my-test-key\"\n" +
                        "  } ]\n" +
                        "}");
    }




    @Test
    public void shouldNotInvokeJWKSetEndpoint_runtimeException() throws Exception {
        when(jwkService.getKeys()).thenReturn(Single.error(new RuntimeException()));

        testRequest(
                HttpMethod.GET, "/.well-known/jwks.json",
                HttpStatusCode.INTERNAL_SERVER_ERROR_500, "Internal Server Error");
    }
}
