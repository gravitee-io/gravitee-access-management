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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import io.vertx.core.http.impl.CookieImpl;
import io.vertx.core.http.impl.ServerCookie;
import io.vertx.rxjava3.core.http.HttpServerRequest;
import io.vertx.rxjava3.ext.web.RoutingContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
public class CookieTracerTest {

    private static final String TRACE_LOGGER = "io.gravitee.am.cookie.trace";
    private static final String PATH = "/my-domain/oauth/authorize";
    private static final String TRACED = "GRAVITEE_IO_REMEMBER_DEVICE";
    private static final String OTHER = "GRAVITEE_IO_AM_SESSION";

    private ch.qos.logback.classic.Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private Level previousLevel;

    @BeforeEach
    void setUp() {
        logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(TRACE_LOGGER);
        previousLevel = logger.getLevel();
        logger.setLevel(Level.DEBUG);
        logger.setAdditive(false);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
        logger.setLevel(previousLevel);
        logger.setAdditive(true);
    }

    @Test
    void shouldNotBeActiveWhenNamesAreNull() {
        assertThat(new CookieTracer(null).isActive()).isFalse();
    }

    @Test
    void shouldNotBeActiveWhenNamesAreBlank() {
        assertThat(new CookieTracer("  ").isActive()).isFalse();
        assertThat(new CookieTracer(" , ,, ").isActive()).isFalse();
    }

    @Test
    void shouldNotBeActiveWhenDebugIsDisabled() {
        logger.setLevel(Level.INFO);
        assertThat(new CookieTracer(TRACED).isActive()).isFalse();
    }

    @Test
    void shouldBeActiveWhenNamesAreConfiguredAndDebugIsEnabled() {
        assertThat(new CookieTracer(TRACED).isActive()).isTrue();
    }

    @Test
    void shouldTraceCookieCarriedByRequest() {
        new CookieTracer(TRACED).traceRequest(context(cookie(TRACED, "abcdef")));

        assertThat(messages()).singleElement().asString()
                .startsWith("[" + PATH + "] request carries cookie '" + TRACED + "'")
                .contains("path=null", "domain=null", "secure=false", "httpOnly=false", "sameSite=null", "fromUserAgent=false");
    }

    @Test
    void shouldTraceCookieMissingFromRequest() {
        new CookieTracer(TRACED).traceRequest(context(cookie(OTHER, "abcdef")));

        assertThat(messages()).containsExactly("[" + PATH + "] request does not carry cookie '" + TRACED + "'");
    }

    @Test
    void shouldTraceEveryConfiguredNameAndIgnoreBlankOnes() {
        new CookieTracer(" " + TRACED + " , ,other-cookie ").traceRequest(context(cookie(TRACED, "abcdef")));

        assertThat(messages()).hasSize(2)
                .anySatisfy(message -> assertThat(message).contains("request carries cookie '" + TRACED + "'"))
                .anySatisfy(message -> assertThat(message).contains("request does not carry cookie 'other-cookie'"));
    }

    @Test
    void shouldNotTraceRequestWhenInactive() {
        new CookieTracer("").traceRequest(context(cookie(TRACED, "abcdef")));

        assertThat(messages()).isEmpty();
    }

    @Test
    void shouldNotLogTheCookieValue() {
        new CookieTracer(TRACED).traceRequest(context(cookie(TRACED, "super-secret")));

        assertThat(messages()).singleElement().asString()
                .contains("value=length:12/hash:" + Integer.toHexString("super-secret".hashCode()))
                .doesNotContain("super-secret");
    }

    @Test
    void shouldReportEmptyCookieValue() {
        new CookieTracer(TRACED).traceRequest(context(cookie(TRACED, "")));

        assertThat(messages()).singleElement().asString().contains("value=empty");
    }

    @Test
    void shouldReportCookieComingFromUserAgent() {
        ServerCookie cookie = mock(ServerCookie.class);
        when(cookie.getName()).thenReturn(TRACED);
        when(cookie.getValue()).thenReturn(null);
        when(cookie.isFromUserAgent()).thenReturn(true);

        new CookieTracer(TRACED).traceRequest(context(cookie));

        assertThat(messages()).singleElement().asString().contains("value=null", "fromUserAgent=true");
    }

    @Test
    void shouldTraceCookieSentInResponse() {
        ServerCookie cookie = (ServerCookie) cookie(TRACED, "abcdef").setMaxAge(3600).setPath("/my-domain").setSecure(true).setHttpOnly(true).setSameSite(CookieSameSite.LAX);

        new CookieTracer(TRACED).traceResponse(context(), cookie);

        assertThat(messages()).singleElement().asString()
                .startsWith("[" + PATH + "] sending cookie '" + TRACED + "', Max-Age=3600")
                .contains("path=/my-domain", "secure=true", "httpOnly=true", "sameSite=Lax");
    }

    @Test
    void shouldTraceCookieDeletionWhenMaxAgeIsZero() {
        ServerCookie cookie = (ServerCookie) cookie(TRACED, "abcdef").setMaxAge(0);

        new CookieTracer(TRACED).traceResponse(context(), cookie);

        assertThat(messages()).singleElement().asString()
                .startsWith("[" + PATH + "] sending DELETION of cookie '" + TRACED + "', Max-Age=0");
    }

    @Test
    void shouldNotTraceResponseCookieWithUntracedName() {
        new CookieTracer(TRACED).traceResponse(context(), (ServerCookie) cookie(OTHER, "abcdef").setMaxAge(3600));

        assertThat(messages()).isEmpty();
    }

    @Test
    void shouldTraceInvalidatingRemoval() {
        new CookieTracer(TRACED).traceRemoval(context(), TRACED, true, "logout");

        assertThat(messages()).singleElement().asString()
                .isEqualTo("[" + PATH + "] removeCookie('" + TRACED + "') invalidated the cookie, a Max-Age=0 will be sent, reason: logout");
    }

    @Test
    void shouldTraceNoOpRemoval() {
        new CookieTracer(TRACED).traceRemoval(context(), TRACED, false, "logout");

        assertThat(messages()).singleElement().asString()
                .isEqualTo("[" + PATH + "] removeCookie('" + TRACED + "') was a no-op, the cookie was absent from the jar, no Set-Cookie will be sent, reason: logout");
    }

    @Test
    void shouldNotTraceRemovalOfUntracedName() {
        new CookieTracer(TRACED).traceRemoval(context(), OTHER, true, "logout");

        assertThat(messages()).isEmpty();
    }

    private List<String> messages() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private static Cookie cookie(String name, String value) {
        return new CookieImpl(name, value);
    }

    private static RoutingContext context(Cookie... cookies) {
        RoutingContext context = mock(RoutingContext.class);
        io.vertx.ext.web.RoutingContext delegate = mock(io.vertx.ext.web.RoutingContext.class);
        HttpServerRequest request = mock(HttpServerRequest.class);
        Map<String, Cookie> cookieMap = Arrays.stream(cookies).collect(Collectors.toMap(Cookie::getName, Function.identity()));
        when(context.getDelegate()).thenReturn(delegate);
        when(context.request()).thenReturn(request);
        when(request.path()).thenReturn(PATH);
        when(delegate.cookieMap()).thenReturn(cookieMap);
        return context;
    }
}
