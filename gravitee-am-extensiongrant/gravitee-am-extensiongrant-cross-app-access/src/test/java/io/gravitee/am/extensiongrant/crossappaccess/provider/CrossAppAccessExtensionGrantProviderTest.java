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
package io.gravitee.am.extensiongrant.crossappaccess.provider;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jwt.JWTClaimsSet;
import io.gravitee.am.extensiongrant.api.ResolvedEndUser;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.identityprovider.api.trustedissuer.AssertionVerifier;
import io.gravitee.am.identityprovider.api.trustedissuer.OAuthTrustedIssuer;
import io.gravitee.am.identityprovider.api.trustedissuer.ResolvedTrustedIssuer;
import io.gravitee.am.identityprovider.api.trustedissuer.TrustedIssuerResolver;
import io.gravitee.am.repository.oauth2.model.request.TokenRequest;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CrossAppAccessExtensionGrantProviderTest {

    private static final String ISSUER = "https://idp.example.com";

    @Mock
    private TrustedIssuerResolver trustedIssuerResolver;

    @Mock
    private AssertionVerifier assertionVerifier;

    @InjectMocks
    private CrossAppAccessExtensionGrantProvider provider;

    @Test
    void shouldSupportIdJagTypedAssertion() {
        assertTrue(provider.supports(request(assertion(new JWTClaimsSet.Builder().issuer(ISSUER).build()))));
    }

    @Test
    void shouldDeclineJwtTypedAssertion() {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(JOSEObjectType.JWT).build();
        assertFalse(provider.supports(request(header.toBase64URL() + "." + Base64URL.encode("{}") + "." + Base64URL.encode("signature"))));
    }

    @Test
    void shouldThrowUnsupportedOperationOnGrant() {
        assertThrows(UnsupportedOperationException.class, () -> provider.grant(new TokenRequest()));
    }

    @Test
    void shouldResolveEndUserFromVerifiedAssertionWithTheVouchingIdentityProvider() {
        String assertion = assertion(new JWTClaimsSet.Builder().issuer(ISSUER).subject("alice").build());
        givenTrustedIssuer();
        when(assertionVerifier.verify(assertion)).thenReturn(Single.just(new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("alice")
                .claim("preferred_username", "alice.smith")
                .claim("email", "alice@example.com")
                .build()));

        ResolvedEndUser resolved = provider.resolveEndUser(request(assertion)).blockingGet();

        assertEquals("idp-id", resolved.identityProvider());
        User endUser = resolved.endUser();
        assertEquals("alice", endUser.getId());
        assertEquals("alice.smith", endUser.getUsername());
        assertEquals("alice@example.com", endUser.getEmail());
    }

    @Test
    void shouldProjectOnlySubjectPreferredUsernameAndEmail() {
        String assertion = assertion(new JWTClaimsSet.Builder().issuer(ISSUER).subject("alice").build());
        givenTrustedIssuer();
        when(assertionVerifier.verify(assertion)).thenReturn(Single.just(new JWTClaimsSet.Builder()
                .issuer(ISSUER)
                .subject("alice")
                .audience("https://as.example.com")
                .jwtID("jti-1")
                .claim("client_id", "agent")
                .claim("scope", "calendar.read")
                .claim("email", "alice@example.com")
                .build()));

        User endUser = provider.resolveEndUser(request(assertion)).blockingGet().endUser();

        assertEquals(Map.of("sub", "alice", "email", "alice@example.com"), endUser.getAdditionalInformation());
    }

    @Test
    void shouldFallBackToSubjectAsUsername() {
        String assertion = assertion(new JWTClaimsSet.Builder().issuer(ISSUER).subject("alice").build());
        givenTrustedIssuer();
        when(assertionVerifier.verify(assertion)).thenReturn(Single.just(new JWTClaimsSet.Builder().issuer(ISSUER).subject("alice").build()));

        assertEquals("alice", provider.resolveEndUser(request(assertion)).blockingGet().endUser().getUsername());
    }

    @Test
    void shouldBuildEndUserFromVerifiedClaimsNotFromParsedOnes() {
        String assertion = assertion(new JWTClaimsSet.Builder().issuer(ISSUER).subject("mallory").claim("email", "mallory@example.com").build());
        givenTrustedIssuer();
        when(assertionVerifier.verify(assertion)).thenReturn(Single.just(new JWTClaimsSet.Builder().issuer(ISSUER).subject("alice").build()));

        User endUser = provider.resolveEndUser(request(assertion)).blockingGet().endUser();

        assertEquals("alice", endUser.getId());
        assertEquals(Map.of("sub", "alice"), endUser.getAdditionalInformation());
    }

    @Test
    void shouldRefuseMissingAssertion() {
        provider.resolveEndUser(request(null))
                .test()
                .assertError(error -> error instanceof InvalidGrantException && "Assertion value is missing".equals(error.getMessage()));

        verifyNoInteractions(trustedIssuerResolver);
    }

    @Test
    void shouldRefuseUnparsableAssertion() {
        provider.resolveEndUser(request("not.a.jwt"))
                .test()
                .assertError(error -> error instanceof InvalidGrantException && "Assertion cannot be parsed".equals(error.getMessage()));

        verifyNoInteractions(trustedIssuerResolver);
    }

    @Test
    void shouldRefuseAssertionWithoutIssuer() {
        provider.resolveEndUser(request(assertion(new JWTClaimsSet.Builder().subject("alice").build())))
                .test()
                .assertError(error -> error instanceof InvalidGrantException && "Assertion issuer is missing".equals(error.getMessage()));

        verifyNoInteractions(trustedIssuerResolver);
    }

    @Test
    void shouldRefuseUntrustedIssuerWithoutEchoingIt() {
        when(trustedIssuerResolver.resolve("https://evil.example.com")).thenReturn(Maybe.empty());

        provider.resolveEndUser(request(assertion(new JWTClaimsSet.Builder().issuer("https://evil.example.com").subject("alice").build())))
                .test()
                .assertError(error -> error instanceof InvalidGrantException && "Assertion issuer is not trusted".equals(error.getMessage()));
    }

    @Test
    void shouldPassResolverRefusalThrough() {
        IllegalStateException ambiguity = new IllegalStateException("ambiguous");
        when(trustedIssuerResolver.resolve(ISSUER)).thenReturn(Maybe.error(ambiguity));

        provider.resolveEndUser(request(assertion(new JWTClaimsSet.Builder().issuer(ISSUER).subject("alice").build())))
                .test()
                .assertError(ambiguity);
    }

    @Test
    void shouldRefuseAssertionTheVerifierRejectsWithFixedDescription() {
        String assertion = assertion(new JWTClaimsSet.Builder().issuer(ISSUER).subject("alice").build());
        givenTrustedIssuer();
        when(assertionVerifier.verify(assertion)).thenReturn(Single.error(new BadJOSEException("Signed JWT rejected: Invalid signature")));

        provider.resolveEndUser(request(assertion))
                .test()
                .assertError(error -> error instanceof InvalidGrantException && "Assertion verification failed".equals(error.getMessage()));

        verify(assertionVerifier).verify(assertion);
    }

    @Test
    void shouldRefuseWithFixedDescriptionWhenVerifierThrows() {
        String assertion = assertion(new JWTClaimsSet.Builder().issuer(ISSUER).subject("alice").build());
        givenTrustedIssuer();
        when(assertionVerifier.verify(assertion)).thenThrow(new IllegalStateException("key source broken for alice"));

        provider.resolveEndUser(request(assertion))
                .test()
                .assertError(error -> error instanceof InvalidGrantException && "Assertion verification failed".equals(error.getMessage()));
    }

    @Test
    void shouldRefuseWithFixedDescriptionWhenAssertionPayloadIsNotJson() {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("oauth-id-jag+jwt")).build();
        String assertion = header.toBase64URL() + "." + Base64URL.encode("[\"alice\"]") + "." + Base64URL.encode("signature");

        provider.resolveEndUser(request(assertion))
                .test()
                .assertError(error -> error instanceof InvalidGrantException && "Assertion cannot be parsed".equals(error.getMessage()));

        verifyNoInteractions(trustedIssuerResolver);
    }

    private void givenTrustedIssuer() {
        when(trustedIssuerResolver.resolve(ISSUER))
                .thenReturn(Maybe.just(new ResolvedTrustedIssuer("idp-id", new OAuthTrustedIssuer(ISSUER, assertionVerifier))));
    }

    private static TokenRequest request(String assertion) {
        TokenRequest request = new TokenRequest();
        request.setClientId("agent");
        request.setGrantType("urn:ietf:params:oauth:grant-type:jwt-bearer");
        Map<String, String> parameters = new HashMap<>();
        if (assertion != null) {
            parameters.put("assertion", assertion);
        }
        request.setRequestParameters(parameters);
        return request;
    }

    private static String assertion(JWTClaimsSet claims) {
        JWSHeader header = new JWSHeader.Builder(JWSAlgorithm.RS256).type(new JOSEObjectType("oauth-id-jag+jwt")).build();
        return header.toBase64URL() + "." + Base64URL.encode(claims.toString()) + "." + Base64URL.encode("signature");
    }
}
