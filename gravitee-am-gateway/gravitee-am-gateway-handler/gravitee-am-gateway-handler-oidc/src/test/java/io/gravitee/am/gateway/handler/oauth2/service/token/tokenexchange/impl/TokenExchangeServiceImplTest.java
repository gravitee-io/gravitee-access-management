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
package io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.impl;

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.jwt.Claims;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.common.oauth2.TokenType;
import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidGrantException;
import io.gravitee.am.gateway.handler.oauth2.exception.InvalidScopeException;
import io.gravitee.am.gateway.handler.common.protectedresource.ProtectedResourceManager;
import io.gravitee.am.gateway.handler.common.user.UserGatewayService;
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeResult;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenExchangeUserResolver;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenValidator;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.model.User;
import io.gravitee.am.model.UserBindingCriterion;
import io.gravitee.am.model.application.ApplicationScopeSettings;
import io.gravitee.am.model.application.TokenExchangeOAuthSettings;
import io.gravitee.am.model.application.TokenExchangeScopeHandling;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Single;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class TokenExchangeServiceImplTest {

    @Mock
    private SubjectManager subjectManager;

    @Mock
    private ProtectedResourceManager protectedResourceManager;

    @Mock
    private UserGatewayService userGatewayService;

    @Mock
    private TokenExchangeUserResolver userResolver;

    private TokenExchangeServiceImpl service;

    @BeforeEach
    public void setUp() {
        service = createService(List.of(new FixedSubjectTokenValidator()));
    }

    private TokenExchangeServiceImpl createService(List<TokenValidator> validators) {
        return new TokenExchangeServiceImpl(validators, subjectManager, protectedResourceManager, userResolver);
    }

    @Test
    public void shouldFailWhenTokenExchangeNotEnabled() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = new Domain();
        domain.setId("domain-id");
        // No token exchange settings

        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Token exchange is not enabled");
    }

    @Test
    public void shouldFailWhenTokenExchangeDisabled() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(false);

        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setTokenExchangeSettings(settings);

        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Token exchange is not enabled");
    }

    @Test
    public void shouldFailWhenSubjectTokenMissing() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(Parameters.SUBJECT_TOKEN_TYPE, TokenType.ACCESS_TOKEN);
        tokenRequest.setParameters(params);

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("subject_token");
    }

    @Test
    public void shouldFailWhenSubjectTokenTypeMissing() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(Parameters.SUBJECT_TOKEN, "subject-token");
        tokenRequest.setParameters(params);

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("subject_token_type");
    }

    @Test
    public void shouldFailWhenSubjectTokenTypeNotAllowed() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ID_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unsupported subject_token_type");
    }

    @Test
    public void shouldFailWhenRequestedTokenTypeUnsupported() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.SAML_2));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unsupported requested_token_type");
    }

    @Test
    public void shouldFailWhenRequestedTokenTypeNotAllowed() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ID_TOKEN));

        // Domain only allows ACCESS_TOKEN as requested type
        Domain domain = domainWithTokenExchange(List.of(TokenType.ACCESS_TOKEN), List.of(TokenType.ACCESS_TOKEN));
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("requested_token_type not allowed");
    }

    @Test
    public void shouldSucceedWhenRequestedTokenTypeIdToken() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ID_TOKEN));

        // Domain allows ID_TOKEN as requested type
        Domain domain = domainWithTokenExchange(List.of(TokenType.ACCESS_TOKEN), List.of(TokenType.ACCESS_TOKEN, TokenType.ID_TOKEN));
        Client client = new Client();
        client.setClientId("client-id");

        service.exchange(tokenRequest, client, domain, userGatewayService)
                .test()
                .assertValue(result -> result.user() != null && result.issuedTokenType().equals(TokenType.ID_TOKEN));
    }

    @Test
    public void shouldFailWhenAccessTokenNotAllowedAndNoRequestedTokenType() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(Parameters.SUBJECT_TOKEN, "subject-token");
        params.add(Parameters.SUBJECT_TOKEN_TYPE, TokenType.ACCESS_TOKEN);
        // No requested_token_type
        tokenRequest.setParameters(params);

        // Domain only allows ID_TOKEN as requested type (not ACCESS_TOKEN)
        Domain domain = domainWithTokenExchange(List.of(TokenType.ACCESS_TOKEN), List.of(TokenType.ID_TOKEN));
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("requested_token_type is required when access_token is not allowed");
    }

    @Test
    public void shouldSucceedWhenRequestedTokenTypeAccessToken() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        service.exchange(tokenRequest, client, domain, userGatewayService)
                .test()
                .assertValue(result -> result.user() != null && result.issuedTokenType().equals(TokenType.ACCESS_TOKEN));
    }

    @Test
    public void shouldDefaultToAccessTokenWhenRequestedTokenTypeNotProvided() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(Parameters.SUBJECT_TOKEN, "subject-token");
        params.add(Parameters.SUBJECT_TOKEN_TYPE, TokenType.ACCESS_TOKEN);
        // No requested_token_type
        tokenRequest.setParameters(params);

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(result.issuedTokenType()).isEqualTo(TokenType.ACCESS_TOKEN);
    }

    @Test
    public void shouldPreserveSubjectAndUsername() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();
        User user = result.user();

        assertThat(user.getId()).isEqualTo("subject");
        assertThat(user.getUsername()).isEqualTo("subject");
        assertThat(user.getAdditionalInformation().get(Claims.SUB)).isEqualTo("subject");
    }

    @Test
    public void shouldUsePreferredUsernameWhenAvailable() throws Exception {
        // Create validator that returns preferred_username
        TokenValidator validatorWithUsername = new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                Map<String, Object> claims = new HashMap<>();
                claims.put(StandardClaims.PREFERRED_USERNAME, "john.doe");

                return Single.just(ValidatedToken.builder()
                        .subject("user-id-123")
                        .claims(claims)
                        .scopes(Set.of("openid"))
                        .expiration(Date.from(Instant.now().plusSeconds(60)))
                        .tokenType(TokenType.ACCESS_TOKEN)
                        .build());
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.ACCESS_TOKEN;
            }
        };

        service = createService(List.of(validatorWithUsername));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();
        User user = result.user();

        assertThat(user.getId()).isEqualTo("user-id-123");
        assertThat(user.getUsername()).isEqualTo("john.doe");
    }

    @Test
    public void shouldIncludeClientIdClaim() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("my-client");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("my-client");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();
        User user = result.user();

        assertThat(user.getAdditionalInformation().get(Claims.CLIENT_ID)).isEqualTo("my-client");
    }

    @Test
    public void shouldIncludeTokenExchangeMetadata() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();
        User user = result.user();
        Map<String, Object> additionalInfo = user.getAdditionalInformation();

        assertThat(additionalInfo.get("token_exchange")).isEqualTo(true);
        assertThat(additionalInfo.get("delegation")).isEqualTo(false);
        assertThat(result.isDelegation()).isFalse();
        assertThat(result.subjectTokenType()).isEqualTo(TokenType.ACCESS_TOKEN);
    }

    @Test
    public void shouldSupportJwtSubjectTokenType() throws Exception {
        TokenValidator jwtValidator = new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                return Single.just(ValidatedToken.builder()
                        .subject("subject")
                        .scopes(Set.of("openid"))
                        .expiration(Date.from(Instant.now().plusSeconds(120)))
                        .tokenType(TokenType.JWT)
                        .build());
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.JWT;
            }
        };

        service = createService(List.of(jwtValidator));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.JWT, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange(List.of(TokenType.JWT));
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();
        assertThat(result.subjectTokenType()).isEqualTo(TokenType.JWT);
    }

    @Test
    public void shouldPreserveGioInternalSub() throws Exception {
        when(subjectManager.hasValidInternalSub(anyString())).thenReturn(true);
        when(subjectManager.extractSourceId("source-id:external-id")).thenReturn("source-id");
        when(subjectManager.extractUserId("source-id:external-id")).thenReturn("external-id");

        // Create validator that returns gis claim
        TokenValidator validatorWithGis = new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                Map<String, Object> claims = new HashMap<>();
                claims.put(Claims.GIO_INTERNAL_SUB, "source-id:external-id");

                return Single.just(ValidatedToken.builder()
                        .subject("subject")
                        .claims(claims)
                        .scopes(Set.of("openid"))
                        .expiration(Date.from(Instant.now().plusSeconds(60)))
                        .tokenType(TokenType.ACCESS_TOKEN)
                        .build());
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.ACCESS_TOKEN;
            }
        };

        service = createService(List.of(validatorWithGis));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();
        User user = result.user();

        assertThat(user.getSource()).isEqualTo("source-id");
        assertThat(user.getExternalId()).isEqualTo("external-id");
    }

    @Test
    public void shouldSetScopesOnTokenRequest() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");
        client.setScopeSettings(clientScopeSettings("openid"));

        service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        // Scopes are now set on the tokenRequest
        assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("openid");
    }

    @Test
    public void shouldIncludeScopesInUserAdditionalInfo() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");
        client.setScopeSettings(clientScopeSettings("openid"));

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();
        User user = result.user();

        assertThat(user.getAdditionalInformation().get(Claims.SCOPE)).isEqualTo("openid");
    }

    @Test
    public void shouldCopyTokenExpiration() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(result.exchangeExpiration()).isNotNull();
        assertThat(result.exchangeExpiration()).isAfter(new Date());
    }

    @Test
    public void shouldIncludeSubjectTokenIdWhenAvailable() throws Exception {
        // Create validator that returns token ID
        TokenValidator validatorWithTokenId = new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                return Single.just(ValidatedToken.builder()
                        .subject("subject")
                        .tokenId("token-jti-123")
                        .scopes(Set.of("openid"))
                        .expiration(Date.from(Instant.now().plusSeconds(60)))
                        .tokenType(TokenType.ACCESS_TOKEN)
                        .build());
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.ACCESS_TOKEN;
            }
        };

        service = createService(List.of(validatorWithTokenId));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();
        User user = result.user();

        assertThat(user.getAdditionalInformation().get("subject_token_id")).isEqualTo("token-jti-123");
    }

    @Test
    public void shouldFailWhenNoValidatorFoundForTokenType() throws Exception {
        // Replace with validator for different token type
        TokenValidator differentValidator = new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                return Single.just(ValidatedToken.builder().build());
            }

            @Override
            public String getSupportedTokenType() {
                return "urn:ietf:params:oauth:token-type:saml2";
            }
        };

        service = createService(List.of(differentValidator));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidGrantException.class)
                .hasMessageContaining("No validator found");
    }

    @Test
    public void shouldHandleValidationFailure() throws Exception {
        // Create validator that fails
        TokenValidator failingValidator = new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                return Single.error(new InvalidGrantException("Token validation failed"));
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.ACCESS_TOKEN;
            }
        };

        service = createService(List.of(failingValidator));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidGrantException.class)
                .hasMessageContaining("Token validation failed");
    }

    @Test
    public void shouldHandleEmptyScopes() throws Exception {
        // Create validator with no scopes
        TokenValidator validatorNoScopes = new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                return Single.just(ValidatedToken.builder()
                        .subject("subject")
                        .scopes(Collections.emptySet())
                        .expiration(Date.from(Instant.now().plusSeconds(60)))
                        .tokenType(TokenType.ACCESS_TOKEN)
                        .build());
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.ACCESS_TOKEN;
            }
        };

        service = createService(List.of(validatorNoScopes));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(tokenRequest.getScopes()).isEmpty();
        // scope claim should not be in additional info when empty
        assertThat(result.user().getAdditionalInformation()).doesNotContainKey(Claims.SCOPE);
    }

    @Test
    public void shouldFailWhenImpersonationNotAllowed() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(Collections.singletonList(TokenType.ACCESS_TOKEN));
        settings.setAllowImpersonation(false);

        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setTokenExchangeSettings(settings);

        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Impersonation is not allowed");
    }

    @Test
    public void shouldFailWhenDelegationOnlyAndNoActorTokenProvided() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(Collections.singletonList(TokenType.ACCESS_TOKEN));
        settings.setAllowedActorTokenTypes(Collections.singletonList(TokenType.ACCESS_TOKEN));
        settings.setAllowDelegation(true);
        settings.setAllowImpersonation(false);

        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setTokenExchangeSettings(settings);

        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Impersonation is not allowed");
    }

    @Test
    public void shouldFailWhenActorTokenTypeProvidedWithoutActorToken() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(Parameters.SUBJECT_TOKEN, "subject-token");
        params.add(Parameters.SUBJECT_TOKEN_TYPE, TokenType.ACCESS_TOKEN);
        params.add(Parameters.ACTOR_TOKEN_TYPE, TokenType.ACCESS_TOKEN);
        tokenRequest.setParameters(params);

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("actor_token_type must not be provided when actor_token is not provided");
    }

    // ==================== Delegation Tests ====================

    @Test
    public void shouldSucceedWithDelegation() throws Exception {
        // Set up validators for both subject and actor tokens
        service = createService(List.of(new FixedSubjectTokenValidator()));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildDelegationParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithDelegation();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(result).isNotNull();
        assertThat(result.isDelegation()).isTrue();
        assertThat(result.actorInfo()).isNotNull();
        assertThat(result.actorInfo().subject()).isEqualTo("subject");
        assertThat(result.user().getAdditionalInformation().get("delegation")).isEqualTo(true);
    }

    @Test
    public void shouldFailWhenDelegationNotAllowed() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildDelegationParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        // Domain with delegation disabled
        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(Collections.singletonList(TokenType.ACCESS_TOKEN));
        settings.setAllowedActorTokenTypes(Collections.singletonList(TokenType.ACCESS_TOKEN));
        settings.setAllowDelegation(false);

        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setTokenExchangeSettings(settings);

        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Delegation is not allowed");
    }

    @Test
    public void shouldFailWhenActorTokenTypeNotAllowed() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(Parameters.SUBJECT_TOKEN, "subject-token");
        params.add(Parameters.SUBJECT_TOKEN_TYPE, TokenType.ACCESS_TOKEN);
        params.add(Parameters.ACTOR_TOKEN, "actor-token");
        params.add(Parameters.ACTOR_TOKEN_TYPE, TokenType.ID_TOKEN); // ID_TOKEN not in allowed types
        tokenRequest.setParameters(params);

        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(Collections.singletonList(TokenType.ACCESS_TOKEN));
        settings.setAllowedActorTokenTypes(Collections.singletonList(TokenType.ACCESS_TOKEN)); // Only ACCESS_TOKEN allowed
        settings.setAllowDelegation(true);

        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setTokenExchangeSettings(settings);

        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unsupported actor_token_type");
    }

    @Test
    public void shouldFailWhenActorTokenTypeMissing() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(Parameters.SUBJECT_TOKEN, "subject-token");
        params.add(Parameters.SUBJECT_TOKEN_TYPE, TokenType.ACCESS_TOKEN);
        params.add(Parameters.ACTOR_TOKEN, "actor-token");
        // Missing ACTOR_TOKEN_TYPE
        tokenRequest.setParameters(params);

        Domain domain = domainWithDelegation();
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("actor_token_type");
    }

    @Test
    public void shouldFailWhenMaxDelegationDepthExceeded() throws Exception {
        // Per RFC 8693 Section 4.1, delegation depth is based on the subject token's "act" claim chain.
        // Create validators: subject token with existing "act" claim, actor token without.
        TokenValidator subjectValidator = new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                if ("subject-token".equals(token)) {
                    Map<String, Object> claims = new HashMap<>();
                    // Subject token has existing act claim from previous delegation
                    Map<String, Object> existingAct = new HashMap<>();
                    existingAct.put(Claims.SUB, "original-actor");
                    claims.put(Claims.ACT, existingAct);

                    return Single.just(ValidatedToken.builder()
                            .subject("subject")
                            .claims(claims)
                            .scopes(Set.of("openid"))
                            .expiration(Date.from(Instant.now().plusSeconds(60)))
                            .tokenType(TokenType.ACCESS_TOKEN)
                            .build());
                } else {
                    // Actor token has no act claim
                    return Single.just(ValidatedToken.builder()
                            .subject("actor")
                            .scopes(Set.of("openid"))
                            .expiration(Date.from(Instant.now().plusSeconds(60)))
                            .tokenType(TokenType.ACCESS_TOKEN)
                            .build());
                }
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.ACCESS_TOKEN;
            }
        };

        service = createService(List.of(subjectValidator));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildDelegationParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        // Domain with maxDelegationDepth=1 (no re-delegation)
        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(Collections.singletonList(TokenType.ACCESS_TOKEN));
        settings.setAllowedActorTokenTypes(Collections.singletonList(TokenType.ACCESS_TOKEN));
        settings.setAllowDelegation(true);
        settings.setMaxDelegationDepth(1); // Only allows direct delegation

        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setTokenExchangeSettings(settings);

        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Maximum delegation depth exceeded");
    }

    @Test
    public void shouldSucceedWhenDelegationDepthWithinLimit() throws Exception {
        // Per RFC 8693 Section 4.1, delegation depth is based on the subject token's "act" claim chain.
        // Create validators: subject token with "act" chain at depth 4, actor token without.
        // With maxDelegationDepth=5, resulting depth of 5 should succeed.
        TokenValidator validatorWithDeepActChain = new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                if ("subject-token".equals(token)) {
                    Map<String, Object> claims = new HashMap<>();
                    // Create a delegation chain (depth 4) on the subject token
                    Map<String, Object> act1 = new HashMap<>();
                    act1.put(Claims.SUB, "actor-1");
                    Map<String, Object> act2 = new HashMap<>();
                    act2.put(Claims.SUB, "actor-2");
                    act2.put(Claims.ACT, act1);
                    Map<String, Object> act3 = new HashMap<>();
                    act3.put(Claims.SUB, "actor-3");
                    act3.put(Claims.ACT, act2);
                    Map<String, Object> act4 = new HashMap<>();
                    act4.put(Claims.SUB, "actor-4");
                    act4.put(Claims.ACT, act3);
                    claims.put(Claims.ACT, act4);

                    return Single.just(ValidatedToken.builder()
                            .subject("subject")
                            .claims(claims)
                            .scopes(Set.of("openid"))
                            .expiration(Date.from(Instant.now().plusSeconds(60)))
                            .tokenType(TokenType.ACCESS_TOKEN)
                            .build());
                } else {
                    // Actor token has no act claim
                    return Single.just(ValidatedToken.builder()
                            .subject("current-actor")
                            .scopes(Set.of("openid"))
                            .expiration(Date.from(Instant.now().plusSeconds(60)))
                            .tokenType(TokenType.ACCESS_TOKEN)
                            .build());
                }
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.ACCESS_TOKEN;
            }
        };

        service = createService(List.of(validatorWithDeepActChain));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildDelegationParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(Collections.singletonList(TokenType.ACCESS_TOKEN));
        settings.setAllowedActorTokenTypes(Collections.singletonList(TokenType.ACCESS_TOKEN));
        settings.setAllowDelegation(true);
        settings.setMaxDelegationDepth(5); // depth 4 + 1 new = 5, within limit

        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setTokenExchangeSettings(settings);

        Client client = new Client();
        client.setClientId("client-id");

        // Should succeed because resulting depth (5) equals maxDelegationDepth (5)
        TokenExchangeResult result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();
        assertThat(result).isNotNull();
        assertThat(result.isDelegation()).isTrue();
        assertThat(result.actorInfo()).isNotNull();
        // The subject token's act claim should be passed through as subjectTokenActClaim
        assertThat(result.actorInfo().hasSubjectTokenActClaim()).isTrue();
    }

    @Test
    public void shouldIncludeGisInActorInfo() throws Exception {
        // Actor token with gis claim should include it in actorInfo
        TokenValidator validatorWithGis = new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                if ("subject-token".equals(token)) {
                    return Single.just(ValidatedToken.builder()
                            .subject("subject-user")
                            .scopes(Set.of("openid"))
                            .expiration(Date.from(Instant.now().plusSeconds(60)))
                            .tokenType(TokenType.ACCESS_TOKEN)
                            .build());
                } else {
                    // Actor token has gis claim
                    Map<String, Object> claims = new HashMap<>();
                    claims.put(Claims.GIO_INTERNAL_SUB, "source-id:actor-external-id");

                    return Single.just(ValidatedToken.builder()
                            .subject("actor-sub")
                            .claims(claims)
                            .scopes(Set.of("openid"))
                            .expiration(Date.from(Instant.now().plusSeconds(60)))
                            .tokenType(TokenType.ACCESS_TOKEN)
                            .build());
                }
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.ACCESS_TOKEN;
            }
        };

        service = createService(List.of(validatorWithGis));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildDelegationParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithDelegation();
        Client client = new Client();
        client.setClientId("client-id");

        TokenExchangeResult result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(result.isDelegation()).isTrue();
        assertThat(result.actorInfo()).isNotNull();
        assertThat(result.actorInfo().subject()).isEqualTo("actor-sub");
        assertThat(result.actorInfo().gis()).isEqualTo("source-id:actor-external-id");
        assertThat(result.actorInfo().hasGis()).isTrue();
    }

    @Test
    public void shouldHandleActorTokenWithoutGis() throws Exception {
        // Actor token without gis claim should have null gis in actorInfo
        service = createService(List.of(new FixedSubjectTokenValidator()));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildDelegationParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithDelegation();
        Client client = new Client();
        client.setClientId("client-id");

        TokenExchangeResult result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(result.isDelegation()).isTrue();
        assertThat(result.actorInfo()).isNotNull();
        assertThat(result.actorInfo().gis()).isNull();
        assertThat(result.actorInfo().hasGis()).isFalse();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldNestSubjectTokenActClaimInDelegationChain() throws Exception {
        // RFC 8693 Section 4.1: When the subject token has an "act" claim,
        // it represents the prior delegation chain and should be nested under
        // the current actor in the new token.
        TokenValidator chainValidator = new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                if ("subject-token".equals(token)) {
                    // Subject token has an existing act claim from first delegation
                    Map<String, Object> claims = new HashMap<>();
                    Map<String, Object> existingAct = new HashMap<>();
                    existingAct.put(Claims.SUB, "token1");
                    claims.put(Claims.ACT, existingAct);

                    return Single.just(ValidatedToken.builder()
                            .subject("original-user")
                            .claims(claims)
                            .scopes(Set.of("openid"))
                            .expiration(Date.from(Instant.now().plusSeconds(60)))
                            .tokenType(TokenType.ACCESS_TOKEN)
                            .build());
                } else {
                    // Actor token (token2's client credentials)
                    return Single.just(ValidatedToken.builder()
                            .subject("token2")
                            .scopes(Set.of("openid"))
                            .expiration(Date.from(Instant.now().plusSeconds(60)))
                            .tokenType(TokenType.ACCESS_TOKEN)
                            .build());
                }
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.ACCESS_TOKEN;
            }
        };

        service = createService(List.of(chainValidator));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildDelegationParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithDelegation();
        Client client = new Client();
        client.setClientId("client-id");

        TokenExchangeResult result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(result.isDelegation()).isTrue();
        assertThat(result.actorInfo()).isNotNull();
        // Current actor is "token2"
        assertThat(result.actorInfo().subject()).isEqualTo("token2");
        // The subject token's existing "act" claim should be captured
        assertThat(result.actorInfo().hasSubjectTokenActClaim()).isTrue();
        // The existing act claim from subject token should be: {sub: "token1"}
        Map<String, Object> existingAct = (Map<String, Object>) result.actorInfo().subjectTokenActClaim();
        assertThat(existingAct.get(Claims.SUB)).isEqualTo("token1");
    }

    @Test
    @SuppressWarnings("unchecked")
    public void shouldExtractActorTokenActClaimWhenActorIsDelegated() throws Exception {
        // When the actor token itself is a delegated token (has an "act" claim),
        // we should capture it as actorTokenActClaim for complete audit traceability.
        TokenValidator delegatedActorValidator = new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                if ("subject-token".equals(token)) {
                    // Subject token - normal token without delegation
                    return Single.just(ValidatedToken.builder()
                            .subject("original-user")
                            .scopes(Set.of("openid"))
                            .expiration(Date.from(Instant.now().plusSeconds(60)))
                            .tokenType(TokenType.ACCESS_TOKEN)
                            .build());
                } else {
                    // Actor token is itself a delegated token with its own act claim
                    Map<String, Object> claims = new HashMap<>();
                    Map<String, Object> actorActClaim = new HashMap<>();
                    actorActClaim.put(Claims.SUB, "original-actor-delegatee");
                    actorActClaim.put(Claims.GIO_INTERNAL_SUB, "source:original-actor-delegatee-id");
                    claims.put(Claims.ACT, actorActClaim);
                    claims.put(Claims.GIO_INTERNAL_SUB, "source:actor-id");

                    return Single.just(ValidatedToken.builder()
                            .subject("actor-sub")
                            .claims(claims)
                            .scopes(Set.of("openid"))
                            .expiration(Date.from(Instant.now().plusSeconds(60)))
                            .tokenType(TokenType.ACCESS_TOKEN)
                            .build());
                }
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.ACCESS_TOKEN;
            }
        };

        service = createService(List.of(delegatedActorValidator));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildDelegationParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithDelegation();
        Client client = new Client();
        client.setClientId("client-id");

        TokenExchangeResult result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(result.isDelegation()).isTrue();
        assertThat(result.actorInfo()).isNotNull();
        assertThat(result.actorInfo().subject()).isEqualTo("actor-sub");
        assertThat(result.actorInfo().gis()).isEqualTo("source:actor-id");

        // The actor token's own act claim should be captured
        assertThat(result.actorInfo().hasActorTokenActClaim()).isTrue();
        Map<String, Object> actorAct = (Map<String, Object>) result.actorInfo().actorTokenActClaim();
        assertThat(actorAct.get(Claims.SUB)).isEqualTo("original-actor-delegatee");
        assertThat(actorAct.get(Claims.GIO_INTERNAL_SUB)).isEqualTo("source:original-actor-delegatee-id");
    }

    @Test
    public void shouldNotHaveActorTokenActClaimWhenActorIsNotDelegated() throws Exception {
        // When the actor token is a normal token (not delegated),
        // actorTokenActClaim should be null.
        service = createService(List.of(new FixedSubjectTokenValidator()));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildDelegationParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithDelegation();
        Client client = new Client();
        client.setClientId("client-id");

        TokenExchangeResult result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(result.isDelegation()).isTrue();
        assertThat(result.actorInfo()).isNotNull();
        assertThat(result.actorInfo().hasActorTokenActClaim()).isFalse();
        assertThat(result.actorInfo().actorTokenActClaim()).isNull();
    }

    /** No client scope settings and no scope in request → empty pool → granted = empty (both modes). */
    @Test
    public void shouldGrantNoScopesWhenClientHasNoScopeSettingsAndNoRequestScope() throws Exception {
        TokenValidator validatorABC = scopeValidator(Set.of("A", "B", "C"));
        service = createService(List.of(validatorABC));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, null));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(tokenRequest.getScopes()).isEmpty();
    }

    @Test
    public void shouldFailWithInvalidScopeWhenClientHasNoScopeSettingsAndRequestedScope() throws Exception {
        TokenValidator validatorABC = scopeValidator(Set.of("A", "B", "C"));
        service = createService(List.of(validatorABC));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "A"));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidScopeException.class);
    }

    /** Requested scope ⊆ allowed pool: granted (both modes — subject = client here so modes agree). */
    @Test
    public void shouldGrantRequestedScopeWhenSubsetOfAllowed() throws Exception {
        TokenValidator validatorABC = scopeValidator(Set.of("A", "B", "C"));
        service = createService(List.of(validatorABC));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "B C"));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");
        client.setScopeSettings(clientScopeSettings("A", "B", "C"));

        service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("B", "C");
    }

    @Test
    public void shouldFailWithInvalidScopeWhenRequestedScopeNotAllowed() throws Exception {
        TokenValidator validatorABC = scopeValidator(Set.of("A", "B", "C"));
        service = createService(List.of(validatorABC));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "D"));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");
        client.setScopeSettings(clientScopeSettings("A", "B", "C"));

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidScopeException.class);
    }

    /** Resource present; scope in client but not in resource is still allowed (pool = client ∪ resource). Applies to both modes. */
    @Test
    public void shouldAllowRequestedScopeFromClientWhenNotInResourceScopes() throws Exception {
        TokenValidator validatorABC = scopeValidator(Set.of("A", "B", "C"));
        service = createService(List.of(validatorABC));

        Set<String> resourceUris = Set.of("https://mcp.example.com");
        when(protectedResourceManager.getScopesForResources(resourceUris)).thenReturn(Set.of("A", "B"));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "C"));
        tokenRequest.setResources(resourceUris);

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");
        client.setScopeSettings(clientScopeSettings("A", "B", "C"));

        service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("C");
    }

    /** Resource present; scope C comes from resource even though client only has A,B. Applies to both modes. */
    @Test
    public void shouldAllowResourceScopeWhenClientDoesNotHaveItAndResourceHasIt() throws Exception {
        TokenValidator validatorABC = scopeValidator(Set.of("A", "B", "C"));
        service = createService(List.of(validatorABC));

        Set<String> resourceUris = Set.of("https://mcp.example.com");
        when(protectedResourceManager.getScopesForResources(resourceUris)).thenReturn(Set.of("A", "B", "C"));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, null));
        tokenRequest.setResources(resourceUris);

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");
        client.setScopeSettings(clientScopeSettings("A", "B"));

        service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("A", "B", "C");
    }

    /** Scope absent from client and resource is rejected regardless of mode. */
    @Test
    public void shouldFailWithInvalidScopeWhenRequestedScopeNotInClientOrResourceWithResourcePresent() throws Exception {
        TokenValidator validatorABC = scopeValidator(Set.of("A", "B", "C"));
        service = createService(List.of(validatorABC));

        Set<String> resourceUris = Set.of("https://mcp.example.com");
        when(protectedResourceManager.getScopesForResources(resourceUris)).thenReturn(Set.of("A", "B"));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "C"));
        tokenRequest.setResources(resourceUris);

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");
        client.setScopeSettings(clientScopeSettings("A", "B"));

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidScopeException.class);
    }

    /** Scope absent from client and resource (which returns empty) is rejected regardless of mode. */
    @Test
    public void shouldFailWithInvalidScopeWhenResourcePresentButReturnsNoScopesAndRequestedScopeNotInClient() throws Exception {
        TokenValidator validatorABC = scopeValidator(Set.of("A", "B", "C"));
        service = createService(List.of(validatorABC));

        Set<String> resourceUris = Set.of("https://mcp.example.com");
        when(protectedResourceManager.getScopesForResources(resourceUris)).thenReturn(Collections.emptySet());

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "C"));
        tokenRequest.setResources(resourceUris);

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");
        client.setScopeSettings(clientScopeSettings("A", "B"));

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidScopeException.class);
    }

    // ==================== User Binding Tests ====================

    @Test
    public void shouldUseDomainUserWhenUserBindingEnabledAndUserFound() throws Exception {
        String externalIssuer = "https://external-idp.example.com";
        String userEmail = "john@example.com";

        TokenValidator trustedValidator = trustedIssuerValidator(externalIssuer, Map.of("email", userEmail));
        service = createService(List.of(trustedValidator));

        User domainUser = new User();
        domainUser.setId("domain-user-id");
        domainUser.setUsername("john.doe");
        domainUser.setEmail(userEmail);
        domainUser.setAdditionalInformation(new HashMap<>());

        when(userResolver.resolve(any(), any(), any())).thenReturn(Single.just(Optional.of(domainUser)));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.JWT, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTrustedIssuerBinding(externalIssuer, List.of(criterion("email", "email")));
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(result.user().getId()).isEqualTo("domain-user-id");
        assertThat(result.user().getUsername()).isEqualTo("john.doe");
        verify(userResolver).resolve(any(), any(), any());
    }

    @Test
    public void shouldFailWhenUserBindingEnabledAndNoUserFound() throws Exception {
        String externalIssuer = "https://external-idp.example.com";

        TokenValidator trustedValidator = trustedIssuerValidator(externalIssuer, Map.of("email", "unknown@example.com"));
        service = createService(List.of(trustedValidator));

        when(userResolver.resolve(any(), any(), any()))
                .thenReturn(Single.error(new InvalidGrantException("No domain user found for token binding")));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.JWT, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTrustedIssuerBinding(externalIssuer, List.of(criterion("email", "email")));
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidGrantException.class)
                .hasMessageContaining("No domain user found");
    }

    @Test
    public void shouldFailWhenUserBindingEnabledAndMultipleUsersFound() throws Exception {
        String externalIssuer = "https://external-idp.example.com";

        TokenValidator trustedValidator = trustedIssuerValidator(externalIssuer, Map.of("email", "shared@example.com"));
        service = createService(List.of(trustedValidator));

        when(userResolver.resolve(any(), any(), any()))
                .thenReturn(Single.error(new InvalidGrantException("Multiple domain users match token binding")));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.JWT, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTrustedIssuerBinding(externalIssuer, List.of(criterion("email", "email")));
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidGrantException.class)
                .hasMessageContaining("Multiple domain users match");
    }

    @Test
    public void shouldUseSyntheticUserWhenUserBindingDisabled() throws Exception {
        String externalIssuer = "https://external-idp.example.com";

        TokenValidator trustedValidator = trustedIssuerValidator(externalIssuer, Map.of("email", "john@example.com"));
        service = createService(List.of(trustedValidator));

        when(userResolver.resolve(any(), any(), any())).thenReturn(Single.just(Optional.empty()));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.JWT, TokenType.ACCESS_TOKEN));

        // Trusted issuer without user binding enabled
        Domain domain = domainWithTrustedIssuer(externalIssuer, false);
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        // Should use synthetic user (subject from token claims)
        assertThat(result.user().getId()).isEqualTo("external-subject");
        verify(userResolver).resolve(any(), any(), any());
    }

    @Test
    public void shouldUseSyntheticUserWhenNotTrustedIssuerValidated() throws Exception {
        // Default validator (not trusted issuer validated)
        service = createService(List.of(new FixedSubjectTokenValidator()));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(result.user().getId()).isEqualTo("subject");
        verify(userResolver, never()).resolve(any(), any(), any());
    }

    @Test
    public void shouldDelegateToUserResolverForElExpressionMapping() throws Exception {
        String externalIssuer = "https://external-idp.example.com";

        TokenValidator trustedValidator = trustedIssuerValidator(externalIssuer, Map.of("mail", "john@example.com"));
        service = createService(List.of(trustedValidator));

        User domainUser = new User();
        domainUser.setId("domain-user-id");
        domainUser.setUsername("john.doe");
        domainUser.setAdditionalInformation(new HashMap<>());

        when(userResolver.resolve(any(), any(), any())).thenReturn(Single.just(Optional.of(domainUser)));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.JWT, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTrustedIssuerBinding(externalIssuer, List.of(criterion("email", "{#token['mail']}")));
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(result.user().getId()).isEqualTo("domain-user-id");
        verify(userResolver).resolve(any(), any(), any());
    }

    @Test
    public void shouldDelegateToUserResolverForSimpleClaimMapping() throws Exception {
        String externalIssuer = "https://external-idp.example.com";

        TokenValidator trustedValidator = trustedIssuerValidator(externalIssuer, Map.of("email", "john@example.com"));
        service = createService(List.of(trustedValidator));

        User domainUser = new User();
        domainUser.setId("domain-user-id");
        domainUser.setUsername("john.doe");
        domainUser.setAdditionalInformation(new HashMap<>());

        when(userResolver.resolve(any(), any(), any())).thenReturn(Single.just(Optional.of(domainUser)));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.JWT, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTrustedIssuerBinding(externalIssuer, List.of(criterion("email", "email")));
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

        assertThat(result.user().getId()).isEqualTo("domain-user-id");
        verify(userResolver).resolve(any(), any(), any());
    }

    @Test
    public void shouldPropagateResolverErrorForFailedBinding() throws Exception {
        String externalIssuer = "https://external-idp.example.com";

        TokenValidator trustedValidator = trustedIssuerValidator(externalIssuer, Map.of("email", "john@example.com"));
        service = createService(List.of(trustedValidator));

        when(userResolver.resolve(any(), any(), any()))
                .thenReturn(Single.error(new InvalidGrantException("Token binding: expression evaluation failed")));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.JWT, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTrustedIssuerBinding(externalIssuer, List.of(criterion("email", "nonexistent_claim")));
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                .isInstanceOf(InvalidGrantException.class)
                .hasMessageContaining("expression evaluation failed");
    }

    @Nested
    class DownscopingScopeHandling {

        private Client downscopingClient(String... scopes) {
            Client client = new Client();
            client.setClientId("client-id");
            // tokenExchangeScopeHandling null → DOWNSCOPING (default)
            client.setScopeSettings(clientScopeSettings(scopes));
            return client;
        }

        /** No scope in request: granted = subject ∩ client (subject acts as an upper bound). */
        @Test
        public void shouldNarrowGrantedScopesToIntersectionOfSubjectAndClientScopes() throws Exception {
            // Subject {A,B,C}; client {A,B,D} → intersection {A,B}.
            TokenValidator validator = scopeValidator(Set.of("A", "B", "C"));
            service = createService(List.of(validator));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, null));

            Domain domain = domainWithTokenExchange();
            Client client = downscopingClient("A", "B", "D");

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("A", "B");
        }

        /** Requested scope ⊆ subject ∩ client: granted. */
        @Test
        public void shouldGrantRequestedScopeWhenSubsetOfSubjectAndClientScopes() throws Exception {
            // Subject {A,B,C}; client {A,B,C}; request {B,C}.
            TokenValidator validatorABC = scopeValidator(Set.of("A", "B", "C"));
            service = createService(List.of(validatorABC));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "B C"));

            Domain domain = domainWithTokenExchange();
            Client client = downscopingClient("A", "B", "C");

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("B", "C");
        }

        /** Scope present in subject but absent from client is rejected. */
        @Test
        public void shouldRejectScopeInSubjectButAbsentFromClientWithNoResource() throws Exception {
            // Subject {A,B,C}; client {A,B}; request C → C ∉ (subject ∩ client) → reject.
            TokenValidator validatorABC = scopeValidator(Set.of("A", "B", "C"));
            service = createService(List.of(validatorABC));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "C"));

            Domain domain = domainWithTokenExchange();
            Client client = downscopingClient("A", "B");

            assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                    .isInstanceOf(InvalidScopeException.class);
        }

        /** Subject token acts as a ceiling: scope in resource but absent from subject is rejected. */
        @Test
        public void shouldRejectScopeInResourceButAbsentFromSubjectToken() throws Exception {
            // Subject {A,B}; resource {A,B,C}; client {A,B,C}; request C → C ∉ subject → reject.
            TokenValidator validatorAB = scopeValidator(Set.of("A", "B"));
            service = createService(List.of(validatorAB));

            Set<String> resourceUris = Set.of("https://mcp.example.com");
            when(protectedResourceManager.getScopesForResources(resourceUris)).thenReturn(Set.of("A", "B", "C"));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "C"));
            tokenRequest.setResources(resourceUris);

            Domain domain = domainWithTokenExchange();
            Client client = downscopingClient("A", "B", "C");

            assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                    .isInstanceOf(InvalidScopeException.class);
        }

        /** Disjoint subject and client scopes → no scope in request → granted = empty. */
        @Test
        public void shouldGrantEmptyScopesWhenSubjectAndClientScopesAreDisjoint() throws Exception {
            // Subject {A,B}; client {C,D} → intersection = {}.
            TokenValidator validatorAB = scopeValidator(Set.of("A", "B"));
            service = createService(List.of(validatorAB));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, null));

            Domain domain = domainWithTokenExchange();
            Client client = downscopingClient("C", "D");

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            assertThat(tokenRequest.getScopes()).isEmpty();
        }

        /** Delegation: no scope in request → granted = (subject ∩ actor) ∩ client. */
        @Test
        public void shouldGrantSubjectActorIntersectionWhenDelegationAndNoScopeRequested() throws Exception {
            // Subject {A,B,C}; actor {B,C,E} → intersection {B,C}; client {B,C}.
            TokenValidator subjectABCactorBCE = delegationScopeValidator(Set.of("A", "B", "C"), Set.of("B", "C", "E"));
            service = createService(List.of(subjectABCactorBCE));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildDelegationParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, null));

            Domain domain = domainWithDelegation();
            Client client = downscopingClient("B", "C");

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("B", "C");
        }

        /** Delegation: requested scope ⊆ subject ∩ actor ∩ client → granted. */
        @Test
        public void shouldGrantRequestedScopeWhenSubsetOfSubjectActorIntersection() throws Exception {
            // Subject {A,B,C}; actor {B,C,D}; intersection {B,C}; client {B,C}; request B.
            TokenValidator subjectABCactorBCD = delegationScopeValidator(Set.of("A", "B", "C"), Set.of("B", "C", "D"));
            service = createService(List.of(subjectABCactorBCD));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildDelegationParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "B"));

            Domain domain = domainWithDelegation();
            Client client = downscopingClient("B", "C");

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("B");
        }

        /** Delegation: subject ∩ actor is empty → no scope in request → granted = empty. */
        @Test
        public void shouldGrantEmptyScopesWhenSubjectActorIntersectionIsEmpty() throws Exception {
            // Subject {A,B}; actor {C,D} → intersection = {}; client has all.
            TokenValidator disjointDelegation = delegationScopeValidator(Set.of("A", "B"), Set.of("C", "D"));
            service = createService(List.of(disjointDelegation));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildDelegationParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, null));

            Domain domain = domainWithDelegation();
            Client client = downscopingClient("A", "B", "C", "D");

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            assertThat(tokenRequest.getScopes()).isEmpty();
        }
    }

    @Nested
    class PermissiveScopeHandling {

        private Client permissiveClient(String... scopes) {
            Client client = new Client();
            client.setClientId("client-id");
            TokenExchangeOAuthSettings teSettings = new TokenExchangeOAuthSettings();
            teSettings.setInherited(false);
            teSettings.setScopeHandling(TokenExchangeScopeHandling.PERMISSIVE);
            client.setTokenExchangeOAuthSettings(teSettings);
            client.setScopeSettings(clientScopeSettings(scopes));
            return client;
        }

        /** no request scope, no resource: granted = all client scopes regardless of subject scopes. */
        @Test
        public void shouldGrantAllClientScopesWhenNoScopeRequested() throws Exception {
            // Subject has only {X} — completely disjoint from client. Permissive ignores subject scopes.
            TokenValidator narrowSubject = scopeValidator(Set.of("X"));
            service = createService(List.of(narrowSubject));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, null));

            Domain domain = domainWithTokenExchange();
            Client client = permissiveClient("A", "B", "C");

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("A", "B", "C");
        }

        /** requested scope in client but NOT in subject token → granted (downscoping would reject). */
        @Test
        public void shouldGrantRequestedScopeFromClientEvenIfAbsentFromSubjectToken() throws Exception {
            // Subject has {openid}; client has {openid, profile}; request profile.
            TokenValidator narrowSubject = scopeValidator(Set.of("openid"));
            service = createService(List.of(narrowSubject));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "profile"));

            Domain domain = domainWithTokenExchange();
            Client client = permissiveClient("openid", "profile");

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("profile");
        }

        /** no request scope, resource present: granted = client ∪ resource (regardless of subject scopes). */
        @Test
        public void shouldGrantClientAndResourceScopesWhenNoScopeRequestedAndResourcePresent() throws Exception {
            // Subject has {X}; client has {A, B}; resource has {B, C}.
            TokenValidator narrowSubject = scopeValidator(Set.of("X"));
            service = createService(List.of(narrowSubject));

            Set<String> resourceUris = Set.of("https://mcp.example.com");
            when(protectedResourceManager.getScopesForResources(resourceUris)).thenReturn(Set.of("B", "C"));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, null));
            tokenRequest.setResources(resourceUris);

            Domain domain = domainWithTokenExchange();
            Client client = permissiveClient("A", "B");

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            // Granted = {A, B} ∪ {B, C} = {A, B, C}
            assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("A", "B", "C");
        }

        /** requested scope from resource even if subject token lacks it. */
        @Test
        public void shouldGrantRequestedScopeFromResourceEvenIfAbsentFromSubjectToken() throws Exception {
            // Subject has {A}; client has {A}; resource has {A, B}; request B.
            TokenValidator narrowSubject = scopeValidator(Set.of("A"));
            service = createService(List.of(narrowSubject));

            Set<String> resourceUris = Set.of("https://mcp.example.com");
            when(protectedResourceManager.getScopesForResources(resourceUris)).thenReturn(Set.of("A", "B"));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "B"));
            tokenRequest.setResources(resourceUris);

            Domain domain = domainWithTokenExchange();
            Client client = permissiveClient("A");

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("B");
        }

        /** scope absent from both client and resource is still rejected (400). */
        @Test
        public void shouldRejectScopeAbsentFromBothClientAndResource() throws Exception {
            // Subject has {A, B, Z}; client has {A, B}; resource has {A}; request Z.
            TokenValidator subjectWithZ = scopeValidator(Set.of("A", "B", "Z"));
            service = createService(List.of(subjectWithZ));

            Set<String> resourceUris = Set.of("https://mcp.example.com");
            when(protectedResourceManager.getScopesForResources(resourceUris)).thenReturn(Set.of("A"));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "Z"));
            tokenRequest.setResources(resourceUris);

            Domain domain = domainWithTokenExchange();
            Client client = permissiveClient("A", "B");

            assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                    .isInstanceOf(InvalidScopeException.class);
        }

        /** no client scopes, no resource: granted = empty (no scope requested). */
        @Test
        public void shouldGrantEmptyScopesWhenClientHasNoScopeSettingsAndNoScopeRequested() throws Exception {
            TokenValidator subjectABC = scopeValidator(Set.of("A", "B", "C"));
            service = createService(List.of(subjectABC));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, null));

            Domain domain = domainWithTokenExchange();
            Client client = new Client();
            client.setClientId("client-id");
            TokenExchangeOAuthSettings teSettings1 = new TokenExchangeOAuthSettings();
            teSettings1.setInherited(false);
            teSettings1.setScopeHandling(TokenExchangeScopeHandling.PERMISSIVE);
            client.setTokenExchangeOAuthSettings(teSettings1);
            // No scope settings

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            assertThat(tokenRequest.getScopes()).isEmpty();
        }

        /** no client scopes, no resource, scope requested → rejected (pool is empty). */
        @Test
        public void shouldRejectScopeWhenClientHasNoScopeSettingsAndScopeRequested() throws Exception {
            TokenValidator subjectABC = scopeValidator(Set.of("A", "B", "C"));
            service = createService(List.of(subjectABC));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "A"));

            Domain domain = domainWithTokenExchange();
            Client client = new Client();
            client.setClientId("client-id");
            TokenExchangeOAuthSettings teSettings2 = new TokenExchangeOAuthSettings();
            teSettings2.setInherited(false);
            teSettings2.setScopeHandling(TokenExchangeScopeHandling.PERMISSIVE);
            client.setTokenExchangeOAuthSettings(teSettings2);

            assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet())
                    .isInstanceOf(InvalidScopeException.class);
        }

        /** delegation: subject ∩ actor is empty but client has scopes → still grants client scopes. */
        @Test
        public void shouldGrantAllClientScopesInDelegationRegardlessOfSubjectActorIntersection() throws Exception {
            // Subject has {A, B}; actor has {C, D} → intersection = {}; client has {A, B, C, D}.
            // Permissive ignores subject/actor scopes entirely.
            TokenValidator disjointDelegation = delegationScopeValidator(Set.of("A", "B"), Set.of("C", "D"));
            service = createService(List.of(disjointDelegation));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildDelegationParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, null));

            Domain domain = domainWithDelegation();
            Client client = permissiveClient("A", "B", "C", "D");

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("A", "B", "C", "D");
        }

        /** delegation: requested scope outside subject ∩ actor but inside client → granted. */
        @Test
        public void shouldGrantRequestedScopeInDelegationEvenIfOutsideSubjectActorIntersection() throws Exception {
            // Subject has {A, B}; actor has {A}; intersection = {A}; client has {A, B}.
            // Request B — B is in client, but NOT in actor → downscoping would reject; permissive grants it.
            TokenValidator validator = delegationScopeValidator(Set.of("A", "B"), Set.of("A"));
            service = createService(List.of(validator));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildDelegationParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, "B"));

            Domain domain = domainWithDelegation();
            Client client = permissiveClient("A", "B");

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("B");
        }
    }

    @Nested
    class DomainInheritanceScopeHandling {

        /** App with inherited=true uses domain's PERMISSIVE scope handling. */
        @Test
        public void shouldUseDomainPermissiveScopeHandlingWhenAppInherits() throws Exception {
            // Subject has {X} only; client has {A, B, C}. Domain is PERMISSIVE → grants all client scopes.
            TokenValidator narrowSubject = scopeValidator(Set.of("X"));
            service = createService(List.of(narrowSubject));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, null));

            TokenExchangeSettings domainSettings = new TokenExchangeSettings();
            domainSettings.setEnabled(true);
            domainSettings.setAllowedSubjectTokenTypes(List.of(TokenType.ACCESS_TOKEN));
            domainSettings.setAllowedRequestedTokenTypes(List.of(TokenType.ACCESS_TOKEN, TokenType.ID_TOKEN));
            TokenExchangeOAuthSettings domainTeSettings = new TokenExchangeOAuthSettings();
            domainTeSettings.setScopeHandling(TokenExchangeScopeHandling.PERMISSIVE);
            domainSettings.setTokenExchangeOAuthSettings(domainTeSettings);

            Domain domain = new Domain();
            domain.setId("domain-id");
            domain.setTokenExchangeSettings(domainSettings);

            // App has inherited=true (default), so it should use domain settings
            Client client = new Client();
            client.setClientId("client-id");
            TokenExchangeOAuthSettings inherited = new TokenExchangeOAuthSettings();
            inherited.setInherited(true);
            client.setTokenExchangeOAuthSettings(inherited);
            client.setScopeSettings(clientScopeSettings("A", "B", "C"));

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("A", "B", "C");
        }

        /** App with inherited=false and DOWNSCOPING overrides domain's PERMISSIVE setting. */
        @Test
        public void shouldUseAppDownscopingWhenNotInheritedEvenIfDomainIsPermissive() throws Exception {
            // Subject has {X} only; client has {A, B, C}. Domain is PERMISSIVE, but app is DOWNSCOPING.
            // Downscoping: allowed = {X} ∩ {A,B,C} = {} → granted = {}
            TokenValidator narrowSubject = scopeValidator(Set.of("X"));
            service = createService(List.of(narrowSubject));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, null));

            TokenExchangeSettings domainSettings = new TokenExchangeSettings();
            domainSettings.setEnabled(true);
            domainSettings.setAllowedSubjectTokenTypes(List.of(TokenType.ACCESS_TOKEN));
            domainSettings.setAllowedRequestedTokenTypes(List.of(TokenType.ACCESS_TOKEN, TokenType.ID_TOKEN));
            TokenExchangeOAuthSettings domainTeSettings = new TokenExchangeOAuthSettings();
            domainTeSettings.setScopeHandling(TokenExchangeScopeHandling.PERMISSIVE);
            domainSettings.setTokenExchangeOAuthSettings(domainTeSettings);

            Domain domain = new Domain();
            domain.setId("domain-id");
            domain.setTokenExchangeSettings(domainSettings);

            // App explicitly sets DOWNSCOPING, not inherited
            Client client = new Client();
            client.setClientId("client-id");
            TokenExchangeOAuthSettings appSettings = new TokenExchangeOAuthSettings();
            appSettings.setInherited(false);
            appSettings.setScopeHandling(TokenExchangeScopeHandling.DOWNSCOPING);
            client.setTokenExchangeOAuthSettings(appSettings);
            client.setScopeSettings(clientScopeSettings("A", "B", "C"));

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            // Downscoping: {X} ∩ {A,B,C} = {} → no scopes granted
            assertThat(tokenRequest.getScopes()).isEmpty();
        }

        /** App with null settings uses system default (DOWNSCOPING). */
        @Test
        public void shouldFallbackToDownscopingWhenAppHasNoSettingsAndDomainHasNoSettings() throws Exception {
            // Subject {A, B}; client {A, B, C}. No overrides → default DOWNSCOPING.
            TokenValidator validator = scopeValidator(Set.of("A", "B"));
            service = createService(List.of(validator));

            TokenRequest tokenRequest = new TokenRequest();
            tokenRequest.setClientId("client-id");
            tokenRequest.setParameters(buildParametersWithScope(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN, null));

            Domain domain = domainWithTokenExchange();

            Client client = new Client();
            client.setClientId("client-id");
            // No tokenExchangeOAuthSettings → defaults to inherited=true
            client.setScopeSettings(clientScopeSettings("A", "B", "C"));

            service.exchange(tokenRequest, client, domain, userGatewayService).blockingGet();

            // Downscoping: {A,B} ∩ {A,B,C} = {A,B}
            assertThat(tokenRequest.getScopes()).containsExactlyInAnyOrder("A", "B");
        }
    }

    private static List<ApplicationScopeSettings> clientScopeSettings(String... scopes) {
        return Stream.of(scopes).map(ApplicationScopeSettings::new).collect(Collectors.toList());
    }

    private static TokenValidator scopeValidator(Set<String> scopes) {
        return new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                return Single.just(ValidatedToken.builder()
                        .subject("subject")
                        .scopes(scopes)
                        .expiration(Date.from(Instant.now().plusSeconds(60)))
                        .tokenType(TokenType.ACCESS_TOKEN)
                        .build());
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.ACCESS_TOKEN;
            }
        };
    }

    private static TokenValidator delegationScopeValidator(Set<String> subjectScopes, Set<String> actorScopes) {
        return new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                Set<String> scopes = "subject-token".equals(token) ? subjectScopes : actorScopes;
                return Single.just(ValidatedToken.builder()
                        .subject("subject")
                        .scopes(scopes)
                        .expiration(Date.from(Instant.now().plusSeconds(60)))
                        .tokenType(TokenType.ACCESS_TOKEN)
                        .build());
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.ACCESS_TOKEN;
            }
        };
    }

    private TokenValidator trustedIssuerValidator(String issuer, Map<String, Object> additionalClaims) {
        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer(issuer);
        return new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                Map<String, Object> claims = new HashMap<>(additionalClaims);
                return Single.just(ValidatedToken.builder()
                        .subject("external-subject")
                        .issuer(issuer)
                        .claims(claims)
                        .scopes(Set.of("openid"))
                        .expiration(Date.from(Instant.now().plusSeconds(60)))
                        .tokenType(TokenType.JWT)
                        .trustedIssuer(ti)
                        .build());
            }

            @Override
            public String getSupportedTokenType() {
                return TokenType.JWT;
            }
        };
    }

    private Domain domainWithTrustedIssuerBinding(String issuerUrl, List<UserBindingCriterion> bindingCriteria) {
        TrustedIssuer trustedIssuer = new TrustedIssuer();
        trustedIssuer.setIssuer(issuerUrl);
        trustedIssuer.setKeyResolutionMethod(KeyResolutionMethod.JWKS_URL);
        trustedIssuer.setJwksUri(issuerUrl + "/.well-known/jwks.json");
        trustedIssuer.setUserBindingEnabled(true);
        trustedIssuer.setUserBindingCriteria(bindingCriteria);

        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(List.of(TokenType.JWT, TokenType.ACCESS_TOKEN));
        settings.setTrustedIssuers(List.of(trustedIssuer));

        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setTokenExchangeSettings(settings);
        return domain;
    }

    private Domain domainWithTrustedIssuer(String issuerUrl, boolean bindingEnabled) {
        TrustedIssuer trustedIssuer = new TrustedIssuer();
        trustedIssuer.setIssuer(issuerUrl);
        trustedIssuer.setKeyResolutionMethod(KeyResolutionMethod.JWKS_URL);
        trustedIssuer.setJwksUri(issuerUrl + "/.well-known/jwks.json");
        trustedIssuer.setUserBindingEnabled(bindingEnabled);
        if (bindingEnabled) {
            trustedIssuer.setUserBindingCriteria(List.of(criterion("email", "email")));
        }

        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(List.of(TokenType.JWT, TokenType.ACCESS_TOKEN));
        settings.setTrustedIssuers(List.of(trustedIssuer));

        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setTokenExchangeSettings(settings);
        return domain;
    }

    private Domain domainWithTokenExchange() {
        return domainWithTokenExchange(
                Collections.singletonList(TokenType.ACCESS_TOKEN),
                List.of(TokenType.ACCESS_TOKEN, TokenType.ID_TOKEN)
        );
    }

    private Domain domainWithTokenExchange(List<String> allowedSubjectTypes) {
        return domainWithTokenExchange(allowedSubjectTypes, List.of(TokenType.ACCESS_TOKEN, TokenType.ID_TOKEN));
    }

    private Domain domainWithTokenExchange(List<String> allowedSubjectTypes, List<String> allowedRequestedTypes) {
        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(allowedSubjectTypes);
        settings.setAllowedRequestedTokenTypes(allowedRequestedTypes);

        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setTokenExchangeSettings(settings);
        return domain;
    }

    private MultiValueMap<String, String> buildParameters(String subjectTokenType, String requestedTokenType) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(Parameters.SUBJECT_TOKEN, "subject-token");
        params.add(Parameters.SUBJECT_TOKEN_TYPE, subjectTokenType);
        params.add(Parameters.REQUESTED_TOKEN_TYPE, requestedTokenType);
        return params;
    }

    private MultiValueMap<String, String> buildDelegationParameters(String subjectTokenType, String actorTokenType) {
        return buildDelegationParametersWithScope(subjectTokenType, actorTokenType, null);
    }

    private MultiValueMap<String, String> buildDelegationParametersWithScope(String subjectTokenType,
                                                                            String actorTokenType,
                                                                            String scope) {
        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add(Parameters.SUBJECT_TOKEN, "subject-token");
        params.add(Parameters.SUBJECT_TOKEN_TYPE, subjectTokenType);
        params.add(Parameters.ACTOR_TOKEN, "actor-token");
        params.add(Parameters.ACTOR_TOKEN_TYPE, actorTokenType);
        if (scope != null) {
            params.add(Parameters.SCOPE, scope);
        }
        return params;
    }

    private MultiValueMap<String, String> buildParametersWithScope(String subjectTokenType,
                                                                    String requestedTokenType,
                                                                    String scope) {
        MultiValueMap<String, String> params = buildParameters(subjectTokenType, requestedTokenType);
        if (scope != null) {
            params.add(Parameters.SCOPE, scope);
        }
        return params;
    }

    private Domain domainWithDelegation() {
        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(Collections.singletonList(TokenType.ACCESS_TOKEN));
        settings.setAllowedActorTokenTypes(Collections.singletonList(TokenType.ACCESS_TOKEN));
        settings.setAllowDelegation(true);
        settings.setMaxDelegationDepth(2);

        Domain domain = new Domain();
        domain.setId("domain-id");
        domain.setTokenExchangeSettings(settings);
        return domain;
    }

    private static class FixedSubjectTokenValidator implements TokenValidator {

        @Override
        public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
            return Single.just(ValidatedToken.builder()
                    .subject("subject")
                    .scopes(Set.of("openid"))
                    .expiration(Date.from(Instant.now().plusSeconds(60)))
                    .tokenType(TokenType.ACCESS_TOKEN)
                    .build());
        }

        @Override
        public String getSupportedTokenType() {
            return TokenType.ACCESS_TOKEN;
        }
    }

    private static UserBindingCriterion criterion(String attribute, String expression) {
        var c = new UserBindingCriterion();
        c.setAttribute(attribute);
        c.setExpression(expression);
        return c;
    }
}
