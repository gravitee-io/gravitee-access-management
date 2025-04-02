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
package io.gravitee.am.gateway.handler.common.vertx.web.handler;

import io.gravitee.am.gateway.handler.common.vertx.RxWebTestBase;
import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.CSRFHandlerImpl;
import io.gravitee.common.http.HttpStatusCode;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.impl.CookieImpl;
import io.vertx.rxjava3.core.http.Cookie;
import io.vertx.rxjava3.ext.web.Session;
import io.vertx.rxjava3.ext.web.handler.CSRFHandler;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class CSRFHandlerTest extends RxWebTestBase {
    private final String CSRF_SECRET = "s3cR3ts3cR3ts3cR3ts3cR3ts3cR3ts3";
    private final String CSRF_COOKIE = "x-csrf-cookie";
    private final String CSRF_HEADER = "x-csrf-header";
    private final int CSRF_TIMEOUT = 5_000;

    @Override
    public void setUp() throws Exception {
        super.setUp();
        CSRFHandlerImpl handlerUnderTest = new CSRFHandlerImpl(vertx.getDelegate(), CSRF_SECRET, CSRF_TIMEOUT);
        handlerUnderTest.setCookieName(CSRF_COOKIE);
        handlerUnderTest.setHeaderName(CSRF_HEADER);

        router.route("/login")
                .handler(CSRFHandler.newInstance(handlerUnderTest))
                .handler(rc -> {
                    if (rc.session().isDestroyed()) {
                        rc.response().setStatusCode(401).end();
                    } else {
                        rc.response().setStatusCode(200).end();
                    }
                })
                .failureHandler(new ErrorHandler());
    }

    @Test
    public void shouldKeep_CSRFToken_if_not_expired() throws Exception {
        io.vertx.ext.web.Session mockSession = mock(io.vertx.ext.web.Session.class);
        Session session = Session.newInstance(mockSession);
        router.route().order(-1).handler(routingContext -> {
            routingContext.setSession(session);
            routingContext.next();
        });

        AtomicReference<String> csrfToken = new AtomicReference<>();
        testRequest(
                HttpMethod.GET,
                "/login",
                req -> {},
                resp -> {
                    var optCsrfCookie = resp.cookies().stream().filter(cookie -> cookie.startsWith(CSRF_COOKIE)).findFirst();
                    assertTrue(optCsrfCookie.isPresent());
                    csrfToken.set(optCsrfCookie.get());
                },
                HttpStatusCode.OK_200, "OK", null);

        router.route().order(-1).handler(routingContext -> {
            when(session.id()).thenReturn("sid");
            when(session.get(CSRF_HEADER)).thenReturn("sid/" + extractCookieValue(csrfToken.get()));
            routingContext.setSession(session);
            CookieImpl cookie = new CookieImpl(CSRF_COOKIE, extractCookieValue(csrfToken.get()));
            cookie.setPath("/");
            routingContext.addCookie(Cookie.newInstance(cookie));
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/login",
                req -> {

                },
                resp -> {
                    var optCsrfCookie = resp.cookies().stream().filter(cookie -> cookie.startsWith(CSRF_COOKIE)).findFirst();
                    assertTrue(optCsrfCookie.isPresent());
                    assertTrue(optCsrfCookie.get().equals(csrfToken.get()));
                },
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldRegenerate_CSRFToken_if_expired() throws Exception {
        io.vertx.ext.web.Session mockSession = mock(io.vertx.ext.web.Session.class);
        Session session = Session.newInstance(mockSession);
        router.route().order(-1).handler(routingContext -> {
            routingContext.setSession(session);
            routingContext.next();
        });

        AtomicReference<String> csrfToken = new AtomicReference<>();
        testRequest(
                HttpMethod.GET,
                "/login",
                req -> {},
                resp -> {
                    var optCsrfCookie = resp.cookies().stream().filter(cookie -> cookie.startsWith(CSRF_COOKIE)).findFirst();
                    assertTrue(optCsrfCookie.isPresent());
                    csrfToken.set(optCsrfCookie.get());
                },
                HttpStatusCode.OK_200, "OK", null);

        Thread.sleep(CSRF_TIMEOUT);

        router.route().order(-1).handler(routingContext -> {
            when(session.id()).thenReturn("sid");
            when(session.get(CSRF_HEADER)).thenReturn("sid/" + extractCookieValue(csrfToken.get()));
            routingContext.setSession(session);
            CookieImpl cookie = new CookieImpl(CSRF_COOKIE, extractCookieValue(csrfToken.get()));
            cookie.setPath("/");
            routingContext.addCookie(Cookie.newInstance(cookie));
            routingContext.next();
        });
        testRequest(
                HttpMethod.GET,
                "/login",
                req -> {},
                resp -> {
                    var optCsrfCookie = resp.cookies().stream().filter(cookie -> cookie.startsWith(CSRF_COOKIE)).findFirst();
                    assertTrue(optCsrfCookie.isPresent());
                    assertFalse(optCsrfCookie.get().equals(csrfToken.get()));
                },
                HttpStatusCode.OK_200, "OK", null);
    }

    @Test
    public void shouldAddErrorParams_CSRFToken_if_expired() throws Exception {
        io.vertx.ext.web.Session mockSession = mock(io.vertx.ext.web.Session.class);
        Session session = Session.newInstance(mockSession);
        router.route().order(-1).handler(routingContext -> {
            routingContext.setSession(session);
            routingContext.next();
        });

        AtomicReference<String> csrfToken = new AtomicReference<>();
        testRequest(
                HttpMethod.GET,
                "/login",
                req -> {},
                resp -> {
                    var optCsrfCookie = resp.cookies().stream().filter(cookie -> cookie.startsWith(CSRF_COOKIE)).findFirst();
                    assertTrue(optCsrfCookie.isPresent());
                    csrfToken.set(optCsrfCookie.get());
                },
                HttpStatusCode.OK_200, "OK", null);

        Thread.sleep(CSRF_TIMEOUT);

        router.route().order(-1).handler(routingContext -> {
            when(session.id()).thenReturn("sid");
            when(session.get(CSRF_HEADER)).thenReturn("sid/" + extractCookieValue(csrfToken.get()));
            routingContext.setSession(session);
            CookieImpl cookie = new CookieImpl(CSRF_COOKIE, extractCookieValue(csrfToken.get()));
            cookie.setPath("/");
            routingContext.addCookie(Cookie.newInstance(cookie));
            routingContext.next();
        });
        testRequest(
                HttpMethod.POST,
                "/login",
                req -> {},
                resp -> {
                    assertTrue(resp.headers().get("Location").contains("error=session_expired"));
                },
                HttpStatusCode.MOVED_TEMPORARILY_302, "Found", null);
    }

    private String extractCookieValue(String cookie) {
        return cookie.substring(CSRF_COOKIE.length() + 1).split(";")[0];
    }

    @Test
    public void shouldGenerate_CSRFToken() throws Exception {
        router.route().order(-1).handler(routingContext -> {
            routingContext.setSession(Session.newInstance(mock(io.vertx.ext.web.Session.class)));
            routingContext.next();
        });

        testRequest(
                HttpMethod.GET,
                "/login",
                req -> {},
                resp -> {
                    assertTrue(resp.cookies().stream().anyMatch(cookie -> cookie.startsWith(CSRF_COOKIE)));
                },
                HttpStatusCode.OK_200, "OK", null);
    }
}
