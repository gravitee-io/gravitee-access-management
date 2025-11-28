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
package io.gravitee.am.gateway.handler.oauth2.service.introspection;

import io.gravitee.am.common.exception.jwt.InvalidGISException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.oauth2.service.introspection.impl.IntrospectionServiceImpl;
import io.gravitee.am.gateway.handler.oauth2.service.token.TokenService;
import io.gravitee.am.gateway.handler.oauth2.service.token.impl.AccessToken;
import io.gravitee.am.model.User;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.observers.TestObserver;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@RunWith(MockitoJUnitRunner.class)
public class IntrospectionServiceTest {

    @InjectMocks
    private IntrospectionService introspectionService = new IntrospectionServiceImpl();

    @Mock
    private TokenService tokenService;

    @Mock
    private SubjectManager subjectManager;

    @Test
    public void shouldSearchForAUser() {
        final String token = "token";
        AccessToken accessToken = new AccessToken(token);
        accessToken.setSubject("user");
        accessToken.setClientId("client-id");
        when(tokenService.introspect("token", "client-id")).thenReturn(Maybe.just(accessToken));
        when(subjectManager.findUserBySub(any())).thenReturn(Maybe.just(new User()));

        IntrospectionRequest introspectionRequest = IntrospectionRequest.builder().token("token").callerClientId("client-id").build();
        TestObserver<IntrospectionResponse> testObserver = introspectionService.introspect(introspectionRequest).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(subjectManager, times(1)).findUserBySub(any());
    }

    @Test
    public void shouldConsiderTokenActiveWhenGISIsMissing() {
        final String token = "token";
        AccessToken accessToken = new AccessToken(token);
        accessToken.setSubject("user");
        accessToken.setClientId("client-id");
        accessToken.setCreatedAt(new Date(Instant.now().toEpochMilli()));
        accessToken.setExpireAt(new Date(Instant.now().plus(1, ChronoUnit.HOURS).toEpochMilli()));
        when(tokenService.introspect("token", "client-id")).thenReturn(Maybe.just(accessToken));
        when(subjectManager.findUserBySub(any())).thenReturn(Maybe.error(new InvalidGISException("invalid gis")));

        IntrospectionRequest introspectionRequest = IntrospectionRequest.builder().token("token").callerClientId("client-id").build();
        TestObserver<IntrospectionResponse> testObserver = introspectionService.introspect(introspectionRequest).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(introspectionResponse -> introspectionResponse.isActive());

        verify(subjectManager, times(1)).findUserBySub(any());
    }

    @Test
    public void shouldNotSearchForAUser_clientCredentials() {
        final String token = "token";
        AccessToken accessToken = new AccessToken(token);
        accessToken.setSubject("client-id");
        accessToken.setClientId("client-id");
        when(tokenService.introspect("token", (String) null)).thenReturn(Maybe.just(accessToken));

        IntrospectionRequest introspectionRequest = IntrospectionRequest.builder().token("token").build();
        TestObserver<IntrospectionResponse> testObserver = introspectionService.introspect(introspectionRequest).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        verify(subjectManager, never()).findUserBySub(any());
    }

    @Test
    public void shouldReturnCustomClaims() {
        final String token = "token";
        AccessToken accessToken = new AccessToken(token);
        accessToken.setSubject("client-id");
        accessToken.setClientId("client-id");
        accessToken.setCreatedAt(new Date());
        accessToken.setExpireAt(new Date());
        accessToken.setAdditionalInformation(Collections.singletonMap("custom-claim", "test"));
        when(tokenService.introspect("token", (String) null)).thenReturn(Maybe.just(accessToken));

        IntrospectionRequest introspectionRequest = IntrospectionRequest.builder().token("token").build();
        TestObserver<IntrospectionResponse> testObserver = introspectionService.introspect(introspectionRequest).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(introspectionResponse -> introspectionResponse.get("custom-claim").equals("test"));
    }

    @Test
    public void shouldNotReturnAudClaim() {
        final String token = "token";
        AccessToken accessToken = new AccessToken(token);
        accessToken.setSubject("client-id");
        accessToken.setClientId("client-id");
        accessToken.setCreatedAt(new Date());
        accessToken.setExpireAt(new Date());
        accessToken.setAdditionalInformation(Collections.singletonMap(Claims.AUD, "test-aud"));
        when(tokenService.introspect(token, (String) null)).thenReturn(Maybe.just(accessToken));

        IntrospectionRequest introspectionRequest = IntrospectionRequest.builder().token("token").build();
        TestObserver<IntrospectionResponse> testObserver = introspectionService.introspect(introspectionRequest).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(introspectionResponse -> !introspectionResponse.containsKey(Claims.AUD));
    }


    @Test
    public void shouldReturnAudClaim_IfConfigSetTrue() {
        final String token = "token";
        AccessToken accessToken = new AccessToken(token);
        accessToken.setSubject("client-id");
        accessToken.setClientId("client-id");
        accessToken.setCreatedAt(new Date());
        accessToken.setExpireAt(new Date());
        accessToken.setAdditionalInformation(Collections.singletonMap(Claims.AUD, "test-aud"));
        when(tokenService.introspect(token, "client-id")).thenReturn(Maybe.just(accessToken));

        ReflectionTestUtils.setField(introspectionService, "allowAudience", true);

        IntrospectionRequest introspectionRequest = IntrospectionRequest.builder().token("token").callerClientId("client-id").build();
        TestObserver<IntrospectionResponse> testObserver = introspectionService.introspect(introspectionRequest).test();

        testObserver.awaitDone(10, TimeUnit.SECONDS);
        testObserver.assertComplete();
        testObserver.assertNoErrors();
        testObserver.assertValue(introspectionResponse -> introspectionResponse.containsKey(Claims.AUD));
    }
}
