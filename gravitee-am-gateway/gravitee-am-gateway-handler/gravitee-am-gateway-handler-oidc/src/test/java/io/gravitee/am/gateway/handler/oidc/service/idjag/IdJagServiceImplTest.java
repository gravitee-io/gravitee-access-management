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
package io.gravitee.am.gateway.handler.oidc.service.idjag;

import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.common.jwt.JwtType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.gateway.handler.common.jwt.JWTService;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.oauth2.service.request.OAuth2Request;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.IdJagTarget;
import io.gravitee.am.gateway.handler.oidc.service.discovery.OpenIDDiscoveryService;
import io.gravitee.am.gateway.handler.oidc.service.idjag.impl.IdJagServiceImpl;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IdJagServiceImplTest {

    private static final String ISSUER = "https://am.example.com/domain";
    private static final String AUDIENCE = "https://auth.acme.com";
    private static final String RESOURCE = "https://calendar.acme.com";

    @InjectMocks
    private final IdJagServiceImpl service = new IdJagServiceImpl();

    @Mock
    private JWTService jwtService;

    @Mock
    private OpenIDDiscoveryService openIDDiscoveryService;

    private final ArgumentCaptor<JWT> assertionCaptor = ArgumentCaptor.forClass(JWT.class);

    @BeforeEach
    void setUp() {
        lenient().when(openIDDiscoveryService.getIssuer(any())).thenReturn(ISSUER);
        lenient().when(jwtService.encode(any(JWT.class), any(Client.class))).thenReturn(Single.just("signed-assertion"));
    }

    private static Client client(int idJagValiditySeconds) {
        Client client = new Client();
        client.setClientId("agent-at-am");
        client.setIdJagValiditySeconds(idJagValiditySeconds);
        return client;
    }

    private static User user() {
        User user = new User();
        user.setId("user-id");
        return user;
    }

    private static OAuth2Request request() {
        OAuth2Request oAuth2Request = new OAuth2Request();
        oAuth2Request.setClientId("agent-at-am");
        oAuth2Request.setIdJagTarget(new IdJagTarget(AUDIENCE, RESOURCE, "agent-at-acme",
                Map.of("calendar.read", "read:calendar", "calendar.write", "write:calendar")));
        return oAuth2Request;
    }

    private static OAuth2Request requestGranting(String... domainScopes) {
        OAuth2Request oAuth2Request = request();
        oAuth2Request.setScopes(Set.of(domainScopes));
        return oAuth2Request;
    }

    private JWT mintedAssertion() {
        service.create(request(), client(300), user()).blockingGet();
        return capturedAssertion();
    }

    private JWT capturedAssertion() {
        verify(jwtService).encode(assertionCaptor.capture(), any(Client.class));
        return assertionCaptor.getValue();
    }

    @Test
    void shouldSignWithTheIdJagJoseType() {
        assertThat(mintedAssertion().getType()).isEqualTo(JwtType.ID_JAG);
    }

    @Test
    void shouldCarryTheDraftsRequiredClaims() {
        JWT assertion = mintedAssertion();

        assertThat(assertion.getIss()).isEqualTo(ISSUER);
        assertThat(assertion.getSub()).isEqualTo("user-id");
        assertThat(assertion.getAudList()).containsExactly(AUDIENCE);
        assertThat(assertion.get(Claims.CLIENT_ID)).isEqualTo("agent-at-acme");
        assertThat(assertion.get(Parameters.RESOURCE)).isEqualTo(RESOURCE);
        assertThat(assertion.getJti()).isNotBlank();
        assertThat(assertion.getIat()).isPositive();
        assertThat(assertion.getExp()).isGreaterThan(assertion.getIat());
    }

    @Test
    void shouldSignWithTheApplicationCertificate() {
        Client client = client(300);

        service.create(request(), client, user()).blockingGet();

        verify(jwtService).encode(any(JWT.class), eq(client));
    }

    @Test
    void shouldOmitTheScopeClaimWhenNoScopeIsGranted() {
        IdJag idJag = service.create(request(), client(300), user()).blockingGet();

        assertThat(capturedAssertion()).doesNotContainKey(Claims.SCOPE);
        assertThat(idJag.scope()).isNull();
    }

    @Test
    void shouldCarryTheGrantedScopesInThePartnersVocabulary() {
        IdJag idJag = service.create(requestGranting("calendar.read", "calendar.write"), client(300), user()).blockingGet();
        Object scopeClaim = capturedAssertion().get(Claims.SCOPE);

        assertThat(((String) scopeClaim).split(" ")).containsExactlyInAnyOrder("read:calendar", "write:calendar");
        assertThat(idJag.scope()).isEqualTo(scopeClaim);
    }

    @Test
    void shouldRefuseToMintAGrantedScopeWithNoMapping() {
        service.create(requestGranting("calendar.read", "calendar.delete"), client(300), user())
                .test()
                .assertError(error -> error instanceof InvalidScopeException && error.getMessage().contains("calendar.delete"));

        verify(jwtService, never()).encode(any(JWT.class), any(Client.class));
    }

    @Test
    void shouldTakeItsLifetimeFromTheApplicationSetting() {
        service.create(request(), client(120), user()).blockingGet();
        JWT assertion = capturedAssertion();

        assertThat(assertion.getExp() - assertion.getIat()).isEqualTo(120);
    }

    @Test
    void shouldReportTheMintedLifetimeAsExpiresIn() {
        IdJag idJag = service.create(request(), client(120), user()).blockingGet();

        assertThat(idJag.expiresIn()).isEqualTo(120);
        assertThat(idJag.value()).isEqualTo("signed-assertion");
        assertThat(idJag.tokenId()).isEqualTo(capturedAssertion().getJti());
    }

    @Test
    void shouldClampTheExpClaimToAShortLivedSubjectToken() {
        OAuth2Request oAuth2Request = request();
        oAuth2Request.setExchangeExpiration(Date.from(Instant.now().plusSeconds(30)));

        IdJag idJag = service.create(oAuth2Request, client(300), user()).blockingGet();
        JWT assertion = capturedAssertion();

        assertThat(assertion.getExp() - assertion.getIat()).isLessThanOrEqualTo(30);
        assertThat(idJag.expiresIn()).isLessThanOrEqualTo(30);
    }

    @Test
    void shouldNotExtendLifetimeWhenTheSubjectTokenOutlivesTheSetting() {
        OAuth2Request oAuth2Request = request();
        oAuth2Request.setExchangeExpiration(Date.from(Instant.now().plusSeconds(3600)));

        service.create(oAuth2Request, client(300), user()).blockingGet();
        JWT assertion = capturedAssertion();

        assertThat(assertion.getExp() - assertion.getIat()).isEqualTo(300);
    }

    @Test
    void shouldNeverOutliveTheSubjectTokenEvenWhenItHasAlreadyExpired() {
        Date subjectExpiration = Date.from(Instant.now().minusSeconds(600));
        OAuth2Request oAuth2Request = request();
        oAuth2Request.setExchangeExpiration(subjectExpiration);

        service.create(oAuth2Request, client(300), user()).blockingGet();

        assertThat(capturedAssertion().getExp()).isEqualTo(subjectExpiration.toInstant().getEpochSecond());
    }

    @Test
    void shouldCarryAnActClaimWhenTheRequestCarriedAnActorToken() {
        OAuth2Request oAuth2Request = request();
        oAuth2Request.setDelegation(true);
        oAuth2Request.setActClaim(Map.of(Claims.SUB, "actor-sub"));

        service.create(oAuth2Request, client(300), user()).blockingGet();

        assertThat(capturedAssertion().get(Claims.ACT)).isEqualTo(Map.of(Claims.SUB, "actor-sub"));
    }

    @Test
    void shouldOmitTheActClaimOnImpersonation() {
        assertThat(mintedAssertion()).doesNotContainKey(Claims.ACT);
    }

    @Test
    void shouldMintADistinctJtiPerAssertion() {
        service.create(request(), client(300), user()).blockingGet();
        service.create(request(), client(300), user()).blockingGet();

        verify(jwtService, times(2)).encode(assertionCaptor.capture(), any(Client.class));
        assertThat(assertionCaptor.getAllValues().get(0).getJti())
                .isNotEqualTo(assertionCaptor.getAllValues().get(1).getJti());
    }

    @Test
    void shouldIssueUnderTheIssuerAdvertisedForTheRequestOrigin() {
        OAuth2Request oAuth2Request = request();
        oAuth2Request.setOrigin("https://gateway.example.com/domain");
        when(openIDDiscoveryService.getIssuer("https://gateway.example.com/domain")).thenReturn("https://gateway.example.com/domain/oidc");

        service.create(oAuth2Request, client(300), user()).blockingGet();

        assertThat(capturedAssertion().getIss()).isEqualTo("https://gateway.example.com/domain/oidc");
    }
}
