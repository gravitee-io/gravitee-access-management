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
package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.csp;

import io.vertx.core.http.HttpServerResponse;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Locale;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class CspHandlerImplTest {

    private static final String CSP_HEADER = "Content-Security-Policy";
    private static final String CSP_REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only";

    private io.vertx.rxjava3.ext.web.RoutingContext rxRoutingContext;
    private HttpServerResponse response;

    @Before
    public void setUp() {
        response = mock(HttpServerResponse.class);
        final io.vertx.ext.web.RoutingContext coreRoutingContext = mock(io.vertx.ext.web.RoutingContext.class);
        when(coreRoutingContext.response()).thenReturn(response);

        rxRoutingContext = mock(io.vertx.rxjava3.ext.web.RoutingContext.class);
        when(rxRoutingContext.getDelegate()).thenReturn(coreRoutingContext);
    }

    private String handleAndCaptureHeader(CspHandlerImpl handler, String headerName) {
        handler.handle(rxRoutingContext);

        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response).putHeader(eq(headerName), captor.capture());
        return captor.getValue();
    }

    @Test
    public void shouldResolveDuplicateDirectivesLastWinsInsteadOfThrowing() {
        final CspHandlerImpl handler = new CspHandlerImpl(
                false, List.of("script-src 'self'", "script-src 'none'"), false);

        final String policy = handleAndCaptureHeader(handler, CSP_HEADER);

        assertTrue(policy, policy.contains("script-src 'none'"));
        assertEquals("script-src should appear once", 1, countOccurrences(policy, "script-src"));
    }

    @Test
    public void shouldResolveDuplicatesAcrossDifferentCasing() {
        final CspHandlerImpl handler = new CspHandlerImpl(
                false, List.of("Script-Src 'self'", "script-src 'none'"), false);

        final String policy = handleAndCaptureHeader(handler, CSP_HEADER);

        assertEquals("script-src should appear once", 1, countOccurrences(policy, "script-src"));
        assertTrue(policy, policy.contains("'none'"));
    }

    @Test
    public void shouldEmitDirectivesWithoutAValue() {
        final CspHandlerImpl handler = new CspHandlerImpl(
                false, List.of("default-src 'self'", "upgrade-insecure-requests"), false);

        final String policy = handleAndCaptureHeader(handler, CSP_HEADER);

        assertTrue(policy, policy.contains("upgrade-insecure-requests"));
    }

    @Test
    public void shouldTreatTrailingSemicolonAsOptional() {
        final CspHandlerImpl withSemicolon = new CspHandlerImpl(false, List.of("default-src 'self';"), false);
        final String policy = handleAndCaptureHeader(withSemicolon, CSP_HEADER);

        assertTrue(policy, policy.contains("default-src 'self'"));
        assertEquals("value must not keep the trailing semicolon", 0, countOccurrences(policy, "'self';"));
    }

    @Test
    public void shouldApplyNonceToMixedCaseScriptSrcWithoutDuplicatingIt() {
        final CspHandlerImpl handler = new CspHandlerImpl(
                false, List.of("Script-Src 'self'"), true);

        final String policy = handleAndCaptureHeader(handler, CSP_HEADER);

        // Two script-src entries would make the browser discard the second, silently dropping the nonce.
        assertEquals("script-src should appear once", 1, countOccurrences(policy, "script-src"));
        assertTrue(policy, policy.contains("'self'"));
        assertTrue(policy, policy.contains("'nonce-"));
    }

    @Test
    public void shouldNotAccumulateNoncesAcrossRequests() {
        final CspHandlerImpl handler = new CspHandlerImpl(false, List.of("script-src 'self'"), true);

        handler.handle(rxRoutingContext);
        handler.handle(rxRoutingContext);

        final ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(response, org.mockito.Mockito.times(2)).putHeader(eq(CSP_HEADER), captor.capture());

        final String secondPolicy = captor.getAllValues().get(1);
        assertEquals("only one nonce should be present", 1, countOccurrences(secondPolicy, "'nonce-"));
    }

    @Test
    public void shouldUseReportOnlyHeaderWhenReportOnly() {
        final CspHandlerImpl handler = new CspHandlerImpl(
                true, List.of("default-src 'self'", "report-uri /csp-reports"), false);

        final String policy = handleAndCaptureHeader(handler, CSP_REPORT_ONLY_HEADER);

        assertTrue(policy, policy.contains("report-uri /csp-reports"));
    }

    @Test
    public void shouldIgnoreBlankEntries() {
        final CspHandlerImpl handler = new CspHandlerImpl(
                false, java.util.Arrays.asList("default-src 'self'", "", "   ", ";"), false);

        final String policy = handleAndCaptureHeader(handler, CSP_HEADER);

        assertTrue(policy, policy.contains("default-src 'self'"));
    }

    private static int countOccurrences(String haystack, String needle) {
        final String lowerHaystack = haystack.toLowerCase(Locale.ROOT);
        final String lowerNeedle = needle.toLowerCase(Locale.ROOT);
        int count = 0;
        int index = lowerHaystack.indexOf(lowerNeedle);
        while (index >= 0) {
            count++;
            index = lowerHaystack.indexOf(lowerNeedle, index + lowerNeedle.length());
        }
        return count;
    }
}
