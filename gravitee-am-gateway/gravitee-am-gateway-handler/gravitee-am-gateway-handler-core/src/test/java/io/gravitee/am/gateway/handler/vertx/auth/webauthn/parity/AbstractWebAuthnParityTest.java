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
package io.gravitee.am.gateway.handler.vertx.auth.webauthn.parity;

import com.webauthn4j.data.UserVerificationRequirement;
import com.webauthn4j.data.attestation.authenticator.AAGUID;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.test.TestAttestationUtil;
import com.webauthn4j.test.authenticator.webauthn.NoneAttestationAuthenticator;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.parity.ChainShapeAuthenticators.Shape;
import com.webauthn4j.test.authenticator.webauthn.WebAuthnModelAuthenticator;
import com.webauthn4j.converter.util.ObjectConverter;
import io.gravitee.am.common.webauthn.AttestationConveyancePreference;
import io.gravitee.am.common.webauthn.UserVerification;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.parity.WebAuthnCeremonies.Ceremony;
import io.gravitee.am.gateway.handler.vertx.auth.webauthn.parity.WebAuthnCeremonies.CeremonyFailedException;
import io.gravitee.am.jwt.JWTBuilder;
import io.gravitee.am.model.Credential;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.login.WebAuthnSettings;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.Vertx;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.cert.X509Certificate;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Behavior of the AM WebAuthn relying party, written against {@link WebAuthnCeremonies} so it can be run
 * against each implementation. The expectations are the behavior of vertx-auth-webauthn 5.0.0.CR2 as AM
 * uses it (AM-5812 step 0): an implementation replacing vertx-auth-webauthn has to pass the same
 * scenarios, except where a deviation hook documents an accepted difference.
 *
 * <p>Browsers and authenticators are emulated with webauthn4j-test. Attested authenticators chain to the
 * webauthn4j "3tier" test root CA, which is not trusted unless a scenario configures it.</p>
 *
 * @author GraviteeSource Team
 */
public abstract class AbstractWebAuthnParityTest {

    protected static final String ORIGIN = "https://auth.example.com";
    protected static final String RP_ID = "auth.example.com";
    protected static final String ENTRYPOINT_ORIGIN = "https://login.example.org";
    protected static final String ENTRYPOINT_RP_ID = "login.example.org";
    protected static final String USERNAME = "john.doe";
    protected static final String ZERO_AAGUID = "00000000-0000-0000-0000-000000000000";

    protected static Vertx vertx;

    protected InMemoryCredentialGatewayService credentialService;
    protected JWTBuilder jwtBuilder;
    /** {@code user.webAuthn.maxAllowCredentials} */
    protected int maxAllowCredentials;
    protected WebAuthnSettings settings;
    protected Domain domain;
    private WebAuthnCeremonies ceremonies;

    /**
     * Build the implementation under test for the domain, with its credential store set up by
     * {@link #configureCredentialStore(Object)}. Called after the scenario has adjusted the domain settings.
     */
    protected abstract WebAuthnCeremonies createCeremonies(Vertx vertx, Domain domain);

    /**
     * Deviation E1 of the AM-5812 plan: vertx-auth-webauthn never verifies the rpIdHash because the
     * handlers do not set {@code WebAuthnCredentials.domain}; webauthn4j always does.
     */
    protected boolean acceptsAssertionSignedForAnotherRelyingPartyId() {
        return false;
    }

    /**
     * {@code GraviteeAndroidKeyAttestation} means to validate a chain the device sent without its root
     * against the configured roots, but vertx-auth-webauthn first runs its metadata check, which requires
     * the last certificate of a chain to be self-signed when no MDS statement is known: such a chain never
     * reaches the PKIX validation. Fixed by the replacement implementation.
     */
    protected boolean acceptsAndroidKeyChainWithoutItsRoot() {
        return true;
    }

    /**
     * Verdict expected for a recorded attestation of {@code recorded-attestations.json}. The file holds
     * the vertx-auth-webauthn verdict; an implementation overrides this for the vectors it is meant to
     * judge differently.
     */
    protected boolean acceptsRecordedAttestation(String name, boolean acceptedByVertxWebAuthn) {
        return acceptedByVertxWebAuthn;
    }

    @BeforeAll
    static void startVertx() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void stopVertx() {
        vertx.close().blockingAwait();
    }

    @BeforeEach
    void setUpDomain() throws Exception {
        credentialService = new InMemoryCredentialGatewayService();

        jwtBuilder = mock(JWTBuilder.class);
        MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
        when(jwtBuilder.sign(any())).thenAnswer(invocation -> "header.payload." + Base64.getUrlEncoder().withoutPadding()
                .encodeToString(sha256.digest(invocation.getArgument(0).toString().getBytes(StandardCharsets.UTF_8))));

        settings = new WebAuthnSettings();
        settings.setOrigin(ORIGIN);
        settings.setRelyingPartyName("Gravitee.io Access Management");
        domain = new Domain();
        domain.setId("domain-id");
        domain.setWebAuthnSettings(settings);

        maxAllowCredentials = -1;
        ceremonies = null;
    }

    /**
     * Inject the in-memory credential service and the store settings of the scenario into a credential
     * store of the implementation (both implementations use the same field names).
     */
    protected <T> T configureCredentialStore(T credentialStore) {
        ReflectionTestUtils.setField(credentialStore, "credentialService", credentialService);
        ReflectionTestUtils.setField(credentialStore, "jwtBuilder", jwtBuilder);
        ReflectionTestUtils.setField(credentialStore, "domain", domain);
        ReflectionTestUtils.setField(credentialStore, "maxAllowCredentials", maxAllowCredentials);
        return credentialStore;
    }

    protected WebAuthnCeremonies ceremonies() {
        if (ceremonies == null) {
            ceremonies = createCeremonies(vertx, domain);
        }
        return ceremonies;
    }

    /**
     * Rebuild the implementation, to apply settings changed after the first ceremony.
     */
    protected void rebuildCeremonies() {
        ceremonies = null;
    }

    // ---------------------------------------------------------------------------------------------
    // scenario helpers, following what the AM handlers do between the browser and the library
    // ---------------------------------------------------------------------------------------------

    protected Credential register(EmulatedBrowser browser, String username) {
        return register(browser, username, ORIGIN, null);
    }

    /**
     * @param relyingPartyId the relying party id the handler writes in the options, null to keep the generated one
     */
    protected Credential register(EmulatedBrowser browser, String username, String origin, String relyingPartyId) {
        JsonObject options = ceremonies().registrationOptions(username);
        if (relyingPartyId != null) {
            options.getJsonObject("rp").put("id", relyingPartyId);
        }
        JsonObject response = browser.origin(origin).create(options);
        ceremonies().register(new Ceremony(username, options.getString("challenge"), origin, options.getJsonObject("rp").getString("id")), response);
        return credentialService.byCredentialId(response.getString("id"));
    }

    protected void login(EmulatedBrowser browser, String username) {
        login(browser, username, ORIGIN, null);
    }

    protected void login(EmulatedBrowser browser, String username, String origin, String relyingPartyId) {
        JsonObject options = ceremonies().loginOptions(username);
        if (relyingPartyId != null) {
            options.put("rpId", relyingPartyId);
        }
        JsonObject assertion = browser.origin(origin).get(options);
        ceremonies().login(new Ceremony(username, options.getString("challenge"), origin, options.getString("rpId")), assertion);
    }

    protected static EmulatedBrowser browser(WebAuthnModelAuthenticator authenticator) {
        return EmulatedBrowser.of(ORIGIN, authenticator);
    }

    protected static NoneAttestationAuthenticator passkey(int counter) {
        return new NoneAttestationAuthenticator(AAGUID.ZERO, counter, true, new ObjectConverter());
    }

    protected static String testRootCertificate() throws Exception {
        return Base64.getEncoder().encodeToString(TestAttestationUtil.load3tierTestRootCACertificate().getEncoded());
    }

    // ---------------------------------------------------------------------------------------------

    @Nested
    class Registration {

        @Test
        void stores_none_attestation_credential() {
            Credential credential = register(browser(passkey(0)), USERNAME);

            assertThat(credential.getUsername()).isEqualTo(USERNAME);
            assertThat(credential.getCredentialId()).isNotBlank();
            assertThat(credential.getAttestationStatementFormat()).isEqualTo("none");
            assertThat(credential.getAaguid()).isEqualTo(ZERO_AAGUID);
            assertThat(credential.getCounter()).isEqualTo(1L);
            assertThat(new JsonObject(credential.getAttestationStatement())).isEqualTo(new JsonObject());
            // the public key is the COSE_Key of the authenticator data, base64url without padding
            byte[] coseKey = Base64.getUrlDecoder().decode(credential.getPublicKey());
            assertThat(credential.getPublicKey()).doesNotContain("=", "+", "/");
            assertThat(coseKey[0] & 0xE0).as("CBOR major type map").isEqualTo(0xA0);
        }

        @Test
        void forces_zero_aaguid_for_none_attestation() {
            NoneAttestationAuthenticator authenticator = new NoneAttestationAuthenticator(
                    new AAGUID("fbfc3007-154e-4ecc-8c0b-6e020557d7bd"), 0, true, new ObjectConverter());

            Credential credential = register(browser(authenticator), USERNAME);

            assertThat(credential.getAaguid()).isEqualTo(ZERO_AAGUID);
        }

        @Test
        void turns_attested_authenticator_into_none_when_attestation_is_not_requested() {
            Credential credential = register(browser(new ChainShapeAuthenticators.Packed(Shape.UP_TO_ROOT)), USERNAME);

            assertThat(credential.getAttestationStatementFormat()).isEqualTo("none");
        }

        @Test
        void direct_attestation_accepts_none_format_from_passkeys() {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);

            Credential credential = register(browser(passkey(0)), USERNAME);

            assertThat(credential.getAttestationStatementFormat()).isEqualTo("none");
        }

        @Test
        void direct_attestation_accepts_packed_single_certificate_without_configured_root() {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);

            Credential credential = register(browser(new ChainShapeAuthenticators.Packed(Shape.LEAF_ONLY)), USERNAME);

            assertThat(credential.getAttestationStatementFormat()).isEqualTo("packed");
            JsonObject statement = new JsonObject(credential.getAttestationStatement());
            assertThat(statement.getString("alg")).isEqualTo("ES256");
            assertThat(statement.getJsonArray("x5c")).hasSize(1);
        }

        @Test
        void direct_attestation_accepts_packed_chain_up_to_a_self_signed_root_without_configured_root() {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);

            Credential credential = register(browser(new ChainShapeAuthenticators.Packed(Shape.UP_TO_ROOT)), USERNAME);

            assertThat(credential.getAttestationStatementFormat()).isEqualTo("packed");
            assertThat(new JsonObject(credential.getAttestationStatement()).getJsonArray("x5c")).hasSize(3);
        }

        /**
         * vertx-auth-webauthn without MDS data checks a chain of two or more certificates as ending with its
         * root: a chain stopping at an intermediate fails on the self-signature check of its last certificate.
         */
        @Test
        void direct_attestation_rejects_packed_chain_ending_with_an_intermediate() {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);

            assertThatThrownBy(() -> register(browser(new ChainShapeAuthenticators.Packed(Shape.UP_TO_INTERMEDIATE)), USERNAME))
                    .isInstanceOf(CeremonyFailedException.class);
            assertThat(credentialService.all()).isEmpty();
        }

        @Test
        void direct_attestation_accepts_fido_u2f() {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);

            Credential credential = register(EmulatedBrowser.ofU2f(ORIGIN), USERNAME);

            assertThat(credential.getAttestationStatementFormat()).isEqualTo("fido-u2f");
        }

        @Test
        void direct_attestation_rejects_android_key_not_chaining_to_a_trusted_root() {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);

            assertThatThrownBy(() -> register(browser(new ChainShapeAuthenticators.AndroidKey(Shape.UP_TO_ROOT)), USERNAME))
                    .isInstanceOf(CeremonyFailedException.class);
            assertThat(credentialService.all()).isEmpty();
        }

        @Test
        void direct_attestation_rejects_android_key_chaining_to_the_root_of_another_format() throws Exception {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);
            settings.setCertificates(Map.of("apple", testRootCertificate()));

            assertThatThrownBy(() -> register(browser(new ChainShapeAuthenticators.AndroidKey(Shape.UP_TO_ROOT)), USERNAME))
                    .isInstanceOf(CeremonyFailedException.class);
            assertThat(credentialService.all()).isEmpty();
        }

        @Test
        void direct_attestation_accepts_android_key_chain_up_to_a_domain_root() throws Exception {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);
            settings.setCertificates(Map.of("android-key", testRootCertificate()));

            Credential credential = register(browser(new ChainShapeAuthenticators.AndroidKey(Shape.UP_TO_ROOT)), USERNAME);

            assertThat(credential.getAttestationStatementFormat()).isEqualTo("android-key");
        }

        /**
         * A chain without its root, as RKP-enabled Android devices may send it, issued under a trusted root.
         * See {@link #acceptsAndroidKeyChainWithoutItsRoot()}.
         */
        @Test
        void direct_attestation_android_key_chain_without_its_root() throws Exception {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);
            settings.setCertificates(Map.of("android-key", testRootCertificate()));
            EmulatedBrowser browser = browser(new ChainShapeAuthenticators.AndroidKey(Shape.UP_TO_INTERMEDIATE));

            if (acceptsAndroidKeyChainWithoutItsRoot()) {
                assertThat(register(browser, USERNAME).getAttestationStatementFormat()).isEqualTo("android-key");
            } else {
                assertThatThrownBy(() -> register(browser, USERNAME)).isInstanceOf(CeremonyFailedException.class);
            }
        }

        @Test
        void rejects_wrong_challenge() {
            JsonObject options = ceremonies().registrationOptions(USERNAME);
            JsonObject response = browser(passkey(0)).create(options);

            assertThatThrownBy(() -> ceremonies().register(new Ceremony(USERNAME, "another-challenge-another-challenge-another-challenge", ORIGIN, RP_ID), response))
                    .isInstanceOf(CeremonyFailedException.class);
            assertThat(credentialService.all()).isEmpty();
        }

        @Test
        void rejects_response_from_another_origin() {
            JsonObject options = ceremonies().registrationOptions(USERNAME);
            JsonObject response = browser(passkey(0)).origin("https://evil.example.net").create(options);

            assertThatThrownBy(() -> ceremonies().register(new Ceremony(USERNAME, options.getString("challenge"), ORIGIN, RP_ID), response))
                    .isInstanceOf(CeremonyFailedException.class);
        }

        @Test
        void user_verification_required_accepts_user_presence_only_at_registration() {
            settings.setUserVerification(UserVerification.REQUIRED);

            Credential credential = register(browser(passkey(0)).userVerification(UserVerificationRequirement.DISCOURAGED), USERNAME);

            assertThat(credential).isNotNull();
        }

        @Test
        void binds_credential_to_the_entrypoint_relying_party_id() {
            Credential credential = register(browser(passkey(0)), USERNAME, ENTRYPOINT_ORIGIN, ENTRYPOINT_RP_ID);

            assertThat(credential).isNotNull();
        }
    }

    @Nested
    class Login {

        @Test
        void authenticates_and_stores_the_new_counter() {
            NoneAttestationAuthenticator authenticator = passkey(0);
            EmulatedBrowser browser = browser(authenticator);
            Credential credential = register(browser, USERNAME);

            login(browser, USERNAME);

            assertThat(credentialService.byCredentialId(credential.getCredentialId()).getCounter()).isEqualTo(2L);
        }

        @Test
        void authenticates_attested_credential() {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);
            EmulatedBrowser browser = browser(new ChainShapeAuthenticators.Packed(Shape.UP_TO_ROOT));
            register(browser, USERNAME);

            assertThatCode(() -> login(browser, USERNAME)).doesNotThrowAnyException();
        }

        @Test
        void accepts_counter_zero_after_a_positive_counter() {
            NoneAttestationAuthenticator authenticator = passkey(0);
            authenticator.setCountUpEnabled(false);
            EmulatedBrowser browser = browser(authenticator);
            Credential credential = register(browser, USERNAME);
            credential.setCounter(5L);

            login(browser, USERNAME);

            assertThat(credentialService.byCredentialId(credential.getCredentialId()).getCounter()).isZero();
        }

        @Test
        void rejects_counter_going_backwards() {
            EmulatedBrowser browser = browser(passkey(0));
            Credential credential = register(browser, USERNAME);
            credential.setCounter(10L);

            assertThatThrownBy(() -> login(browser, USERNAME)).isInstanceOf(CeremonyFailedException.class);
            assertThat(credentialService.byCredentialId(credential.getCredentialId()).getCounter()).isEqualTo(10L);
        }

        @Test
        void rejects_wrong_challenge() {
            EmulatedBrowser browser = browser(passkey(0));
            register(browser, USERNAME);
            JsonObject assertion = browser.get(ceremonies().loginOptions(USERNAME));

            assertThatThrownBy(() -> ceremonies().login(new Ceremony(USERNAME, "another-challenge-another-challenge-another-challenge", ORIGIN, RP_ID), assertion))
                    .isInstanceOf(CeremonyFailedException.class);
        }

        @Test
        void rejects_assertion_from_another_origin() {
            EmulatedBrowser browser = browser(passkey(0));
            register(browser, USERNAME);
            JsonObject options = ceremonies().loginOptions(USERNAME);
            JsonObject assertion = browser.origin("https://evil.example.net").get(options);

            assertThatThrownBy(() -> ceremonies().login(new Ceremony(USERNAME, options.getString("challenge"), ORIGIN, RP_ID), assertion))
                    .isInstanceOf(CeremonyFailedException.class);
        }

        @Test
        void user_verification_required_rejects_assertion_without_user_verification() {
            settings.setUserVerification(UserVerification.REQUIRED);
            EmulatedBrowser browser = browser(passkey(0));
            register(browser, USERNAME);

            browser.userVerification(UserVerificationRequirement.DISCOURAGED);

            assertThatThrownBy(() -> login(browser, USERNAME)).isInstanceOf(CeremonyFailedException.class);
        }

        @Test
        void authenticates_discoverable_credential_without_username() {
            settings.setRequireResidentKey(true);
            EmulatedBrowser browser = browser(passkey(0));
            register(browser, USERNAME);

            assertThatCode(() -> login(browser, null)).doesNotThrowAnyException();
        }

        @Test
        void authenticates_on_the_entrypoint_relying_party_id() {
            EmulatedBrowser browser = browser(passkey(0));
            register(browser, USERNAME, ENTRYPOINT_ORIGIN, ENTRYPOINT_RP_ID);

            assertThatCode(() -> login(browser, USERNAME, ENTRYPOINT_ORIGIN, ENTRYPOINT_RP_ID)).doesNotThrowAnyException();
        }

        @Test
        void assertion_signed_for_another_relying_party_id() {
            EmulatedBrowser browser = browser(passkey(0));
            register(browser, USERNAME, ORIGIN, "example.com");
            JsonObject options = ceremonies().loginOptions(USERNAME).put("rpId", "example.com");
            JsonObject assertion = browser.get(options);
            Ceremony ceremony = new Ceremony(USERNAME, options.getString("challenge"), ORIGIN, RP_ID);

            if (acceptsAssertionSignedForAnotherRelyingPartyId()) {
                assertThatCode(() -> ceremonies().login(ceremony, assertion)).doesNotThrowAnyException();
            } else {
                assertThatThrownBy(() -> ceremonies().login(ceremony, assertion)).isInstanceOf(CeremonyFailedException.class);
            }
        }
    }

    @Nested
    class LoginOptions {

        @Test
        void list_the_registered_credentials_of_the_user() {
            Credential first = register(browser(passkey(0)), USERNAME);
            Credential second = register(browser(passkey(0)), USERNAME);
            register(browser(passkey(0)), "jane.doe");

            JsonObject options = ceremonies().loginOptions(USERNAME);

            assertThat(options.getString("rpId")).isEqualTo(RP_ID);
            assertThat(options.getString("challenge")).isNotBlank();
            assertThat(allowedCredentialIds(options)).containsExactlyInAnyOrder(first.getCredentialId(), second.getCredentialId());
        }

        @Test
        void honor_max_allow_credentials() {
            register(browser(passkey(0)), USERNAME);
            register(browser(passkey(0)), USERNAME);
            register(browser(passkey(0)), USERNAME);
            maxAllowCredentials = 2;
            rebuildCeremonies();

            assertThat(allowedCredentialIds(ceremonies().loginOptions(USERNAME))).hasSize(2);
        }

        @Test
        void return_stable_plausible_credentials_for_an_unknown_user() {
            JsonArray first = allowedCredentialIds(ceremonies().loginOptions("unknown.user"));
            JsonArray second = allowedCredentialIds(ceremonies().loginOptions("unknown.user"));

            assertThat(first).isNotEmpty();
            assertThat(first).isEqualTo(second);
        }

        @Test
        void use_the_configured_user_verification() {
            settings.setUserVerification(UserVerification.REQUIRED);

            assertThat(ceremonies().loginOptions(USERNAME).getString("userVerification")).isEqualTo("required");
        }

        private JsonArray allowedCredentialIds(JsonObject options) {
            JsonArray ids = new JsonArray();
            options.getJsonArray("allowCredentials", new JsonArray())
                    .forEach(descriptor -> ids.add(((JsonObject) descriptor).getString("id")));
            return ids;
        }
    }

    @Nested
    class RegistrationOptions {

        @Test
        void carry_the_domain_settings() {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);
            settings.setUserVerification(UserVerification.REQUIRED);
            settings.setRequireResidentKey(true);

            JsonObject options = ceremonies().registrationOptions(USERNAME);

            assertThat(options.getJsonObject("rp").getString("id")).isEqualTo(RP_ID);
            assertThat(options.getJsonObject("rp").getString("name")).isEqualTo("Gravitee.io Access Management");
            assertThat(options.getJsonObject("user").getString("name")).isEqualTo(USERNAME);
            assertThat(options.getString("attestation")).isEqualTo("direct");
            assertThat(options.getJsonObject("authenticatorSelection").getString("userVerification")).isEqualTo("required");
            assertThat(options.getJsonObject("authenticatorSelection").getBoolean("requireResidentKey")).isTrue();
            assertThat(options.getJsonArray("pubKeyCredParams").stream().map(p -> ((JsonObject) p).getInteger("alg")))
                    .containsExactly((int) COSEAlgorithmIdentifier.ES256.getValue(), (int) COSEAlgorithmIdentifier.RS256.getValue());
        }

        @Test
        void exclude_the_credentials_already_registered_by_the_user() {
            Credential credential = register(browser(passkey(0)), USERNAME);

            JsonObject options = ceremonies().registrationOptions(USERNAME);

            assertThat(options.getJsonArray("excludeCredentials").stream().map(d -> ((JsonObject) d).getString("id")))
                    .containsExactly(credential.getCredentialId());
        }
    }

    /**
     * Registration responses recorded from real authenticators (webauthn.org samples, formerly in
     * {@code AttestationTest}), verified with DIRECT attestation and no configured root. The expected
     * verdict of each vector is the one observed with vertx-auth-webauthn ({@code acceptedByVertxWebAuthn}).
     */
    @Nested
    class RecordedAttestations {

        static final String RECORDED = "/webauthn/parity/recorded-attestations.json";

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> recorded() throws Exception {
            try (var input = AbstractWebAuthnParityTest.class.getResourceAsStream(RECORDED)) {
                JsonArray recorded = new JsonArray(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                return recorded.stream().map(r -> org.junit.jupiter.params.provider.Arguments.of(((JsonObject) r).getString("name"), r));
            }
        }

        @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.MethodSource("recorded")
        void register_recorded_attestation(String name, JsonObject recorded) {
            String validUntil = recorded.getString("validUntil");
            org.junit.jupiter.api.Assumptions.assumeTrue(validUntil == null || java.time.LocalDate.now().isBefore(java.time.LocalDate.parse(validUntil)),
                    () -> name + " carries certificates expired since " + validUntil + ", record a new sample");
            String origin = recorded.getString("origin");
            settings.setOrigin(origin);
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);
            Ceremony ceremony = new Ceremony(USERNAME, recorded.getString("challenge"), origin, java.net.URI.create(origin).getHost());

            if (acceptsRecordedAttestation(name, recorded.getBoolean("acceptedByVertxWebAuthn"))) {
                assertThatCode(() -> ceremonies().register(ceremony, recorded.getJsonObject("credential"))).doesNotThrowAnyException();
                assertThat(credentialService.all()).hasSize(1);
            } else {
                assertThatThrownBy(() -> ceremonies().register(ceremony, recorded.getJsonObject("credential")))
                        .isInstanceOf(CeremonyFailedException.class);
                assertThat(credentialService.all()).isEmpty();
            }
        }
    }

    /**
     * Credentials as vertx-auth-webauthn stored them, each with an assertion signed beforehand, generated by
     * {@code VertxWebAuthnParityTest#generateVertxWebAuthnCredentialFixtures}. Replaying them proves a
     * replacement implementation reads the records already in the database: no data migration.
     */
    @Nested
    class VertxWebAuthnStoredCredentials {

        static final String FIXTURES = "/webauthn/parity/vertx-webauthn-credentials.json";

        static java.util.stream.Stream<org.junit.jupiter.params.provider.Arguments> fixtures() throws Exception {
            try (var input = AbstractWebAuthnParityTest.class.getResourceAsStream(FIXTURES)) {
                JsonArray fixtures = new JsonArray(new String(input.readAllBytes(), StandardCharsets.UTF_8));
                return fixtures.stream().map(f -> org.junit.jupiter.params.provider.Arguments.of(((JsonObject) f).getString("name"), f));
            }
        }

        @org.junit.jupiter.params.ParameterizedTest(name = "{0}")
        @org.junit.jupiter.params.provider.MethodSource("fixtures")
        void authenticate(String name, JsonObject fixture) {
            Credential credential = VertxWebAuthnCredentialFixtures.credential(fixture.getJsonObject("credential"));
            credentialService.put(credential);
            JsonObject ceremony = fixture.getJsonObject("ceremony");

            ceremonies().login(
                    new Ceremony(ceremony.getString("username"), ceremony.getString("challenge"), ceremony.getString("origin"), ceremony.getString("relyingPartyId")),
                    fixture.getJsonObject("assertion"));

            assertThat(credentialService.byCredentialId(credential.getCredentialId()).getCounter())
                    .isEqualTo(fixture.getLong("expectedCounter"));
        }
    }

    @Nested
    class AuthenticatorIntegrity {

        @Test
        void accepts_a_valid_attestation_chain() {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);
            Credential credential = register(browser(new ChainShapeAuthenticators.Packed(Shape.UP_TO_ROOT)), USERNAME);

            assertThatCode(() -> ceremonies().checkIntegrity(credential)).doesNotThrowAnyException();
        }

        @Test
        void rejects_an_expired_attestation_certificate() throws Exception {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);
            Credential credential = register(browser(new ChainShapeAuthenticators.Packed(Shape.UP_TO_ROOT)), USERNAME);
            JsonObject statement = new JsonObject(credential.getAttestationStatement());
            statement.put("x5c", new JsonArray().add(Base64.getUrlEncoder().withoutPadding().encodeToString(expiredCertificate().getEncoded())));
            credential.setAttestationStatement(statement.encode());

            assertThatThrownBy(() -> ceremonies().checkIntegrity(credential)).isInstanceOf(CeremonyFailedException.class);
        }

        @Test
        void rejects_a_broken_attestation_chain() throws Exception {
            settings.setAttestationConveyancePreference(AttestationConveyancePreference.DIRECT);
            Credential credential = register(browser(new ChainShapeAuthenticators.Packed(Shape.UP_TO_ROOT)), USERNAME);
            JsonObject statement = new JsonObject(credential.getAttestationStatement());
            JsonArray x5c = statement.getJsonArray("x5c");
            // the leaf is no longer signed by the next certificate of the chain
            x5c.set(1, Base64.getUrlEncoder().withoutPadding().encodeToString(TestAttestationUtil.load2tierTestRootCACertificate().getEncoded()));
            credential.setAttestationStatement(statement.encode());

            assertThatThrownBy(() -> ceremonies().checkIntegrity(credential)).isInstanceOf(CeremonyFailedException.class);
        }

        private X509Certificate expiredCertificate() throws Exception {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            var keyPair = generator.generateKeyPair();
            Instant notAfter = Instant.now().minus(1, ChronoUnit.DAYS);
            X500Name name = new X500Name("CN=Expired attestation, O=Gravitee");
            var builder = new JcaX509v3CertificateBuilder(name, BigInteger.ONE,
                    Date.from(notAfter.minus(365, ChronoUnit.DAYS)), Date.from(notAfter), name, keyPair.getPublic());
            return new JcaX509CertificateConverter().getCertificate(
                    builder.build(new JcaContentSignerBuilder("SHA256withECDSA").build(keyPair.getPrivate())));
        }
    }
}
