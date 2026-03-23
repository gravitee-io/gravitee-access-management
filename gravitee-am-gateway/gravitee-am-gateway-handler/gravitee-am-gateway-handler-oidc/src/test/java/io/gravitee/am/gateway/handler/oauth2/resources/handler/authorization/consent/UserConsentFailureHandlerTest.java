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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization.consent;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.common.utils.HashUtil;
import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.Handler;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.ext.auth.User;
import io.vertx.rxjava3.ext.web.RoutingContext;
import io.vertx.rxjava3.ext.web.handler.SessionHandler;
import io.vertx.rxjava3.ext.web.sstore.LocalSessionStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.atomic.AtomicReference;

import static io.gravitee.am.common.utils.ConstantKeys.USER_CONSENT_FAILED;
import static io.gravitee.am.common.utils.ConstantKeys.ERROR_HASH;
import static io.gravitee.am.gateway.handler.common.vertx.utils.UriBuilderRequest.CONTEXT_PATH;
import static org.mockito.Mockito.mock;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class UserConsentFailureHandlerTest extends RxWebTestBase {

    private Handler<RoutingContext> failingHandler;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        LocalSessionStore localSessionStore = LocalSessionStore.create(vertx);
        router.route().order(-2).handler(SessionHandler.create(localSessionStore));

        router.route().order(-1).handler(rc -> {
            rc.put(CONTEXT_PATH, "/test-domain");
            rc.next();
        });

        router.route(HttpMethod.POST, "/oauth/consent")
                .handler(rc -> failingHandler.handle(rc))
                .failureHandler(new UserConsentFailureHandler());
    }

    @Test
    public void shouldRedirectToLoginPage_policyChainException() throws Exception {
        failingHandler = rc -> {
            PolicyChainException exception = new PolicyChainException("policy_error", 500, "POLICY_ERROR", null, "text/plain");
            rc.fail(exception);
        };

        testRequest(
                HttpMethod.POST,
                "/oauth/consent",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test-domain/login"));
                    assertTrue(location.contains("error=" + USER_CONSENT_FAILED));
                    assertTrue(location.contains("error_code=POLICY_ERROR"));
                    assertTrue(location.contains("error_description=policy_error"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToLoginPage_oauth2Exception() throws Exception {
        failingHandler = rc -> {
            InvalidRequestException exception = new InvalidRequestException("Invalid client_id");
            rc.fail(exception);
        };

        testRequest(
                HttpMethod.POST,
                "/oauth/consent",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test-domain/login"));
                    assertTrue(location.contains("error=" + USER_CONSENT_FAILED));
                    assertTrue(location.contains("error_code=invalid_request"));
                    assertTrue(location.contains("error_description=Invalid+client_id"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldRedirectToLoginPage_genericException() throws Exception {
        failingHandler = rc -> {
            RuntimeException exception = new RuntimeException("Unexpected error occurred");
            rc.fail(exception);
        };

        testRequest(
                HttpMethod.POST,
                "/oauth/consent",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test-domain/login"));
                    assertTrue(location.contains("error=" + USER_CONSENT_FAILED));
                    assertTrue(location.contains("error_code=internal_server_error"));
                    assertTrue(location.contains("error_description=Unexpected+error"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }

    @Test
    public void shouldPutErrorHashInSession() throws Exception {
        AtomicReference<RoutingContext> contextRef = new AtomicReference<>();

        failingHandler = rc -> {
            contextRef.set(rc);
            PolicyChainException exception = new PolicyChainException("policy_error", 500, "POLICY_ERROR", null, "text/plain");
            rc.fail(exception);
        };

        testRequest(
                HttpMethod.POST,
                "/oauth/consent",
                null,
                null,
                HttpStatusCode.FOUND_302, "Found", null);

        String expectedHash = HashUtil.generateSHA256(USER_CONSENT_FAILED + "$policy_error");
        RoutingContext capturedContext = contextRef.get();
        assertNotNull(capturedContext);
        assertNotNull(capturedContext.session());
        Object errorHash = capturedContext.session().get(ERROR_HASH);
        assertNotNull(errorHash);
        assertEquals(expectedHash, errorHash);
    }

    @Test
    public void shouldClearUserOnFailure() throws Exception {
        AtomicReference<RoutingContext> contextRef = new AtomicReference<>();

        failingHandler = rc -> {
            rc.setUser(mock(User.class));
            contextRef.set(rc);
            PolicyChainException exception = new PolicyChainException("policy_error");
            rc.fail(exception);
        };

        testRequest(
                HttpMethod.POST,
                "/oauth/consent",
                null,
                null,
                HttpStatusCode.FOUND_302, "Found", null);

        RoutingContext capturedContext = contextRef.get();
        assertNotNull(capturedContext);
        assertNull(capturedContext.user());
    }

    @Test
    public void shouldPreserveQueryParameters() throws Exception {
        failingHandler = rc -> {
            InvalidRequestException exception = new InvalidRequestException("Invalid request");
            rc.fail(exception);
        };

        testRequest(
                HttpMethod.POST,
                "/oauth/consent?param1=value1&param2=value2",
                null,
                resp -> {
                    String location = resp.headers().get("location");
                    assertNotNull(location);
                    assertTrue(location.contains("/test-domain/login"));
                    assertTrue(location.contains("param1=value1") && location.contains("param2=value2"));
                },
                HttpStatusCode.FOUND_302, "Found", null);
    }
}
