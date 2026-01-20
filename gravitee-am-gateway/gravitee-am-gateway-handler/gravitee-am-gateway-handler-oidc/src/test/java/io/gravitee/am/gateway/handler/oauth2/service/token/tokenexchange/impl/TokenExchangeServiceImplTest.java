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
import io.gravitee.am.gateway.handler.oauth2.service.request.TokenRequest;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.TokenValidator;
import io.gravitee.am.gateway.handler.oauth2.service.token.tokenexchange.ValidatedToken;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.TokenExchangeSettings;
import io.gravitee.am.model.User;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.common.util.LinkedMultiValueMap;
import io.gravitee.common.util.MultiValueMap;
import io.reactivex.rxjava3.core.Single;
import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class TokenExchangeServiceImplTest {

    private TokenExchangeServiceImpl service;

    @Before
    public void setUp() throws Exception {
        service = new TokenExchangeServiceImpl();
        TokenValidator validator = new FixedSubjectTokenValidator();
        var validatorsField = TokenExchangeServiceImpl.class.getDeclaredField("validators");
        validatorsField.setAccessible(true);
        validatorsField.set(service, List.of(validator));
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

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain).blockingGet())
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

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain).blockingGet())
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

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain).blockingGet())
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

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain).blockingGet())
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

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("Unsupported subject_token_type");
    }

    @Test
    public void shouldFailWhenRequestedTokenTypeUnsupported() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ID_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain).blockingGet())
                .isInstanceOf(InvalidRequestException.class)
                .hasMessageContaining("requested_token_type");
    }

    @Test
    public void shouldSucceedWhenRequestedTokenTypeAccessToken() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        service.exchange(tokenRequest, client, domain)
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

        var result = service.exchange(tokenRequest, client, domain).blockingGet();

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

        var result = service.exchange(tokenRequest, client, domain).blockingGet();
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

        var validatorsField = TokenExchangeServiceImpl.class.getDeclaredField("validators");
        validatorsField.setAccessible(true);
        validatorsField.set(service, List.of(validatorWithUsername));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain).blockingGet();
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

        var result = service.exchange(tokenRequest, client, domain).blockingGet();
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

        var result = service.exchange(tokenRequest, client, domain).blockingGet();
        User user = result.user();
        Map<String, Object> additionalInfo = user.getAdditionalInformation();

        assertThat(additionalInfo.get("token_exchange")).isEqualTo(true);
        assertThat(additionalInfo.get("impersonation")).isEqualTo(true);
        assertThat(additionalInfo.get("subject_token_type")).isEqualTo(TokenType.ACCESS_TOKEN);
        assertThat(additionalInfo.get("requested_token_type")).isEqualTo(TokenType.ACCESS_TOKEN);
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

        var validatorsField = TokenExchangeServiceImpl.class.getDeclaredField("validators");
        validatorsField.setAccessible(true);
        validatorsField.set(service, List.of(jwtValidator));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.JWT, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange(List.of(TokenType.JWT));
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain).blockingGet();
        assertThat(result.user().getAdditionalInformation().get("subject_token_type")).isEqualTo(TokenType.JWT);
    }

    @Test
    public void shouldPreserveGioInternalSub() throws Exception {
        // Create validator that returns gis claim
        TokenValidator validatorWithGis = new TokenValidator() {
            @Override
            public Single<ValidatedToken> validate(String token, TokenExchangeSettings settings, Domain domain) {
                Map<String, Object> claims = new HashMap<>();
                claims.put(Claims.GIO_INTERNAL_SUB, "internal-user-id");

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

        var validatorsField = TokenExchangeServiceImpl.class.getDeclaredField("validators");
        validatorsField.setAccessible(true);
        validatorsField.set(service, List.of(validatorWithGis));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain).blockingGet();
        User user = result.user();

        assertThat(user.getAdditionalInformation().get(Claims.GIO_INTERNAL_SUB)).isEqualTo("internal-user-id");
    }

    @Test
    public void shouldSetScopesOnTokenRequest() {
        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        service.exchange(tokenRequest, client, domain).blockingGet();

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

        var result = service.exchange(tokenRequest, client, domain).blockingGet();
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

        var result = service.exchange(tokenRequest, client, domain).blockingGet();

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

        var validatorsField = TokenExchangeServiceImpl.class.getDeclaredField("validators");
        validatorsField.setAccessible(true);
        validatorsField.set(service, List.of(validatorWithTokenId));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain).blockingGet();
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

        var validatorsField = TokenExchangeServiceImpl.class.getDeclaredField("validators");
        validatorsField.setAccessible(true);
        validatorsField.set(service, List.of(differentValidator));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain).blockingGet())
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

        var validatorsField = TokenExchangeServiceImpl.class.getDeclaredField("validators");
        validatorsField.setAccessible(true);
        validatorsField.set(service, List.of(failingValidator));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        assertThatThrownBy(() -> service.exchange(tokenRequest, client, domain).blockingGet())
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

        var validatorsField = TokenExchangeServiceImpl.class.getDeclaredField("validators");
        validatorsField.setAccessible(true);
        validatorsField.set(service, List.of(validatorNoScopes));

        TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setClientId("client-id");
        tokenRequest.setParameters(buildParameters(TokenType.ACCESS_TOKEN, TokenType.ACCESS_TOKEN));

        Domain domain = domainWithTokenExchange();
        Client client = new Client();
        client.setClientId("client-id");

        var result = service.exchange(tokenRequest, client, domain).blockingGet();

        assertThat(tokenRequest.getScopes()).isEmpty();
        // scope claim should not be in additional info when empty
        assertThat(result.user().getAdditionalInformation()).doesNotContainKey(Claims.SCOPE);
    }

    private Domain domainWithTokenExchange() {
        return domainWithTokenExchange(Collections.singletonList(TokenType.ACCESS_TOKEN));
    }

    private Domain domainWithTokenExchange(List<String> allowedTypes) {
        TokenExchangeSettings settings = new TokenExchangeSettings();
        settings.setEnabled(true);
        settings.setAllowedSubjectTokenTypes(allowedTypes);

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
}
