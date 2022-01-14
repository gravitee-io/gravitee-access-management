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

package io.gravitee.am.gateway.handler.account.resources;

import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.SignatureAlgorithm;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.account.services.AccountService;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.ErrorHandler;
import io.gravitee.am.jwt.DefaultJWTBuilder;
import io.gravitee.am.jwt.DefaultJWTParser;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.jwt.JWTParser;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.Completable;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.json.Json;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.buffer.Buffer;
import io.vertx.reactivex.ext.web.handler.BodyHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Map;

import static io.gravitee.am.common.utils.ConstantKeys.WEBAUTHN_REDIRECT_URI;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Ashraful Hasan (ashraful.hasan at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class AccountWebAuthnCredentialsEndpointHandlerTest extends RxWebTestBase {

    @Mock
    AccountService accountService;

    private AccountWebAuthnCredentialsEndpointHandler accountWebAuthnCredentialsEndpointHandler;
    private JWTParser jwtParser;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        final User user = new User();
        user.setId("someUserid");
        final Client client = new Client();
        client.setClientId("someClientId");
        client.setId("someAudienceId");

        RSAKey rsaJWK = new RSAKeyGenerator(2048)
                .keyID("123")
                .generate();
        RSAPrivateKey rsaPrivateKey = rsaJWK.toRSAPrivateKey();
        RSAPublicKey rsaPublicKey = rsaJWK.toRSAPublicKey();
        JWTBuilder jwtBuilder = new DefaultJWTBuilder(rsaPrivateKey, SignatureAlgorithm.RS256.getValue(), rsaJWK.getKeyID());

        jwtParser = new DefaultJWTParser(rsaPublicKey);
        accountWebAuthnCredentialsEndpointHandler = new AccountWebAuthnCredentialsEndpointHandler(accountService, jwtBuilder);

        router.route()
                .handler(ctx -> {
                    ctx.put(ConstantKeys.CLIENT_CONTEXT_KEY, client);
                    ctx.put(ConstantKeys.USER_CONTEXT_KEY, user);
                    ctx.next();
                })
                .handler(BodyHandler.create())
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldBeAbleToDelete() throws Exception {
        final String requestPath = "/api/webauthn/credentials/1234";
        when(accountService.removeWebAuthnCredential(any())).thenReturn(Completable.complete());

        router.route(requestPath)
                .handler(accountWebAuthnCredentialsEndpointHandler::deleteWebAuthnCredential)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.DELETE,
                requestPath,
                req -> {
                },
                resp -> resp.bodyHandler(body -> {
                    verify(accountService).removeWebAuthnCredential(any());
                }),
                204,
                "No Content", null);
    }

    @Test
    public void shouldBeAbleToCreateToken() throws Exception {
        final String requestPath = "/self-account-management-test/account/api/token";
        router.route(requestPath)
                .handler(accountWebAuthnCredentialsEndpointHandler::createToken)
                .handler(rc -> rc.response().end());

        testRequest(HttpMethod.POST,
                requestPath,
                req -> {
                    final Buffer buffer = Buffer.buffer();
                    buffer.appendString(createTokenPayLoad());
                    req.headers().set("content-length", String.valueOf(buffer.length()));
                    req.headers().set("content-type", "application/json");
                    req.write(buffer);
                },
                resp -> resp.bodyHandler(body -> {
                    final Map<String, Object> data = Json.decodeValue(body.toString(), Map.class);
                    final JWT parsedJWT = jwtParser.parse((String) data.get("token"));

                    assertTrue("should have 'iat' key", parsedJWT.containsKey(Claims.iat));
                    assertTrue("should have 'aud' key", parsedJWT.containsKey(Claims.aud));
                    assertEquals("subject value should be 'someUserid'", "someUserid", parsedJWT.getSub());
                    assertEquals("audience value should be 'someAudienceId'", "someAudienceId", parsedJWT.getAud());
                    assertEquals("redirect uri value should 'https://someuri.com'", "https://someuri.com", parsedJWT.get(WEBAUTHN_REDIRECT_URI));
                }),
                200,
                "OK", null);
    }

    private String createTokenPayLoad() {
        return new JsonObject()
                .put("redirect_uri", "https://someuri.com").toString();
    }
}
