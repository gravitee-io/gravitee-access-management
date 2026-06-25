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
package io.gravitee.am.management.handlers.management.api.authentication.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;

import static io.gravitee.am.management.handlers.management.api.authentication.provider.generator.RedirectCookieGenerator.DEFAULT_REDIRECT_COOKIE_NAME;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
public class CheckRedirectionCookieFilterTest {

    private static final String DEFAULT_REDIRECT_URL = "/auth/authorize?redirect_uri=https://am.example.com/login/callback";

    @Test
    public void should_fallback_to_default_when_redirect_cookie_absent() throws Exception {
        CheckRedirectionCookieFilter filter = new CheckRedirectionCookieFilter(DEFAULT_REDIRECT_URL);

        HttpServletRequest request = mock(HttpServletRequest.class);
        Cookie otherCookie = mock(Cookie.class);
        when(otherCookie.getName()).thenReturn("Some-Other-Cookie");
        when(request.getCookies()).thenReturn(new Cookie[]{otherCookie});

        filter.doFilter(request, mock(HttpServletResponse.class), mock(FilterChain.class));

        verify(request).setAttribute(DEFAULT_REDIRECT_COOKIE_NAME, DEFAULT_REDIRECT_URL);
    }

    @Test
    public void should_use_cookie_value_when_redirect_cookie_present() throws Exception {
        CheckRedirectionCookieFilter filter = new CheckRedirectionCookieFilter(DEFAULT_REDIRECT_URL);

        String cookieValue = "/auth/authorize?redirect_uri=http://localhost:4201/login/callback";
        HttpServletRequest request = mock(HttpServletRequest.class);
        Cookie redirectCookie = mock(Cookie.class);
        when(redirectCookie.getName()).thenReturn(DEFAULT_REDIRECT_COOKIE_NAME);
        when(redirectCookie.getValue()).thenReturn(cookieValue);
        when(request.getCookies()).thenReturn(new Cookie[]{redirectCookie});

        filter.doFilter(request, mock(HttpServletResponse.class), mock(FilterChain.class));

        verify(request).setAttribute(DEFAULT_REDIRECT_COOKIE_NAME, cookieValue);
    }
}
