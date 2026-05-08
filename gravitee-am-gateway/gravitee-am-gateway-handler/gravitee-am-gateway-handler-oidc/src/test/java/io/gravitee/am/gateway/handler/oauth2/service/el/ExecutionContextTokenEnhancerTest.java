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
package io.gravitee.am.gateway.handler.oauth2.service.el;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.oauth2.TokenTypeHint;
import io.gravitee.am.model.TokenClaim;
import io.gravitee.am.model.UserInfoClaim;
import io.gravitee.el.TemplateEngine;
import io.gravitee.gateway.api.ExecutionContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class ExecutionContextTokenEnhancerTest {

    @Mock
    private ExecutionContext executionContext;

    @Mock
    private TemplateEngine templateEngine;

    private ExecutionContextTokenEnhancer enhancer;

    @Before
    public void setUp() {
        enhancer = new ExecutionContextTokenEnhancer();
        when(executionContext.getTemplateEngine()).thenReturn(templateEngine);
    }

    // ---- TokenClaim overload --------------------------------------------------

    @Test
    public void shouldEnhance_tokenClaim_addsClaimWithEvaluatedValue() {
        JWT jwt = new JWT();
        TokenClaim claim = TokenClaim.of(TokenTypeHint.ACCESS_TOKEN, "iss", "https://custom-iss");
        when(templateEngine.getValue("https://custom-iss", Object.class)).thenReturn("https://custom-iss");

        enhancer.enhanceToken(jwt, TokenTypeHint.ACCESS_TOKEN, List.of(claim), executionContext);

        assertEquals("https://custom-iss", jwt.get("iss"));
    }

    @Test
    public void shouldEnhance_tokenClaim_ignoresClaimsWithDifferentTokenType() {
        JWT jwt = new JWT();
        TokenClaim idTokenClaim = TokenClaim.of(TokenTypeHint.ID_TOKEN, "iss", "https://custom-iss");

        enhancer.enhanceToken(jwt, TokenTypeHint.ACCESS_TOKEN, List.of(idTokenClaim), executionContext);

        assertFalse(jwt.containsKey("iss"));
        verify(templateEngine, never()).getValue(any(String.class), eq(Object.class));
    }

    @Test
    public void shouldEnhance_tokenClaim_audWithStringArray_mergesPreservingClientIdFirst() {
        JWT jwt = new JWT();
        jwt.setAud("client-id");
        String expr = "{T(java.lang.String).valueOf(\"a,b,client-id\").split(\",\")}";
        TokenClaim claim = TokenClaim.of(TokenTypeHint.ACCESS_TOKEN, Claims.AUD, expr);
        when(templateEngine.getValue(expr, Object.class)).thenReturn(new String[]{"a", "b", "client-id"});

        enhancer.enhanceToken(jwt, TokenTypeHint.ACCESS_TOKEN, List.of(claim), executionContext);

        Object aud = jwt.get(Claims.AUD);
        assertTrue(aud instanceof List);
        List<?> audList = (List<?>) aud;
        assertEquals(3, audList.size());
        assertEquals("client-id", audList.get(0));
        assertTrue(audList.containsAll(Arrays.asList("a", "b", "client-id")));
    }

    @Test
    public void shouldEnhance_tokenClaim_audWithList_mergesPreservingClientIdFirst() {
        JWT jwt = new JWT();
        jwt.setAud("client-id");
        TokenClaim claim = TokenClaim.of(TokenTypeHint.ACCESS_TOKEN, Claims.AUD, "#values");
        when(templateEngine.getValue("#values", Object.class)).thenReturn(Arrays.asList("a", "b", "client-id"));

        enhancer.enhanceToken(jwt, TokenTypeHint.ACCESS_TOKEN, List.of(claim), executionContext);

        Object aud = jwt.get(Claims.AUD);
        assertTrue(aud instanceof List);
        List<?> audList = (List<?>) aud;
        assertEquals(3, audList.size());
        assertEquals("client-id", audList.get(0));
    }

    @Test
    public void shouldEnhance_tokenClaim_nullList_isNoOp() {
        JWT jwt = new JWT();

        enhancer.enhanceToken(jwt, TokenTypeHint.ACCESS_TOKEN, null, executionContext);

        assertTrue(jwt.isEmpty());
        verify(executionContext, never()).getTemplateEngine();
    }

    @Test
    public void shouldEnhance_tokenClaim_emptyList_isNoOp() {
        JWT jwt = new JWT();

        enhancer.enhanceToken(jwt, TokenTypeHint.ACCESS_TOKEN, Collections.emptyList(), executionContext);

        assertTrue(jwt.isEmpty());
        verify(executionContext, never()).getTemplateEngine();
    }

    @Test
    public void shouldEnhance_tokenClaim_nullClaimExpression_doesNotAddClaim() {
        JWT jwt = new JWT();
        TokenClaim claim = TokenClaim.of(TokenTypeHint.ACCESS_TOKEN, "iss", null);

        enhancer.enhanceToken(jwt, TokenTypeHint.ACCESS_TOKEN, List.of(claim), executionContext);

        assertFalse(jwt.containsKey("iss"));
        verify(templateEngine, never()).getValue(any(String.class), eq(Object.class));
    }

    @Test
    public void shouldEnhance_tokenClaim_evaluationReturningNull_doesNotAddClaim() {
        JWT jwt = new JWT();
        TokenClaim claim = TokenClaim.of(TokenTypeHint.ACCESS_TOKEN, "iss", "#missing");
        when(templateEngine.getValue("#missing", Object.class)).thenReturn(null);

        enhancer.enhanceToken(jwt, TokenTypeHint.ACCESS_TOKEN, List.of(claim), executionContext);

        assertFalse(jwt.containsKey("iss"));
    }

    @Test
    public void shouldEnhance_tokenClaim_evaluationThrowing_isSwallowedAndClaimSkipped() {
        JWT jwt = new JWT();
        TokenClaim claim = TokenClaim.of(TokenTypeHint.ACCESS_TOKEN, "iss", "#broken");
        when(templateEngine.getValue("#broken", Object.class)).thenThrow(new RuntimeException("boom"));

        enhancer.enhanceToken(jwt, TokenTypeHint.ACCESS_TOKEN, List.of(claim), executionContext);

        assertFalse(jwt.containsKey("iss"));
    }

    // ---- UserInfoClaim overload ----------------------------------------------

    @Test
    public void shouldEnhance_userInfoClaim_addsClaimWithEvaluatedValue() {
        JWT jwt = new JWT();
        UserInfoClaim claim = UserInfoClaim.of("custom_claim", "#{'value'}");
        when(templateEngine.getValue("#{'value'}", Object.class)).thenReturn("value");

        enhancer.enhanceToken(jwt, List.of(claim), executionContext);

        assertEquals("value", jwt.get("custom_claim"));
    }

    @Test
    public void shouldEnhance_userInfoClaim_appliesAllClaims() {
        JWT jwt = new JWT();
        UserInfoClaim c1 = UserInfoClaim.of("a", "#a");
        UserInfoClaim c2 = UserInfoClaim.of("b", "#b");
        when(templateEngine.getValue("#a", Object.class)).thenReturn("1");
        when(templateEngine.getValue("#b", Object.class)).thenReturn("2");

        enhancer.enhanceToken(jwt, List.of(c1, c2), executionContext);

        assertEquals("1", jwt.get("a"));
        assertEquals("2", jwt.get("b"));
    }

    @Test
    public void shouldEnhance_userInfoClaim_nullList_isNoOp() {
        JWT jwt = new JWT();

        enhancer.enhanceToken(jwt, (List<UserInfoClaim>) null, executionContext);

        assertTrue(jwt.isEmpty());
        verify(executionContext, never()).getTemplateEngine();
    }

    @Test
    public void shouldEnhance_userInfoClaim_emptyList_isNoOp() {
        JWT jwt = new JWT();

        enhancer.enhanceToken(jwt, Collections.<UserInfoClaim>emptyList(), executionContext);

        assertTrue(jwt.isEmpty());
        verify(executionContext, never()).getTemplateEngine();
    }

    @Test
    public void shouldEnhance_userInfoClaim_audWithList_mergesPreservingClientIdFirst() {
        JWT jwt = new JWT();
        jwt.setAud("client-id");
        UserInfoClaim claim = UserInfoClaim.of(Claims.AUD, "#values");
        when(templateEngine.getValue("#values", Object.class)).thenReturn(Arrays.asList("other", "client-id"));

        enhancer.enhanceToken(jwt, List.of(claim), executionContext);

        Object aud = jwt.get(Claims.AUD);
        assertTrue(aud instanceof List);
        List<?> audList = (List<?>) aud;
        assertEquals(2, audList.size());
        assertEquals("client-id", audList.get(0));
        assertTrue(audList.contains("other"));
    }

    @Test
    public void shouldEnhance_userInfoClaim_evaluationThrowing_isSwallowedAndClaimSkipped() {
        JWT jwt = new JWT();
        UserInfoClaim claim = UserInfoClaim.of("custom_claim", "#broken");
        when(templateEngine.getValue("#broken", Object.class)).thenThrow(new RuntimeException("boom"));

        enhancer.enhanceToken(jwt, List.of(claim), executionContext);

        assertNull(jwt.get("custom_claim"));
    }
}
