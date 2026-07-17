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
package io.gravitee.am.authdevice.notifier.cibafederation.provider;

import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierProvider;
import io.gravitee.am.authdevice.notifier.api.IdentityProviderDependent;
import io.gravitee.am.authdevice.notifier.api.model.*;
import io.gravitee.am.authdevice.notifier.cibafederation.CibaFederationAuthenticationDeviceNotifierConfiguration;
import io.gravitee.am.authdevice.notifier.cibafederation.provider.spring.CibaFederationProviderSpringConfiguration;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.springframework.util.StringUtils.hasText;

@Import(CibaFederationProviderSpringConfiguration.class)
public class CibaFederationAuthenticationDeviceNotifierProvider
        implements AuthenticationDeviceNotifierProvider, IdentityProviderDependent, InitializingBean {

    public static final String TRANSACTION_ID = "tid";
    public static final String STATE = "state";
    public static final String CALLBACK_VALIDATE = "validated";
    public static final String ID_TOKEN = "id_token";
    public static final String ACCESS_TOKEN = "access_token";

    @Autowired(required = false) private CibaFederationAuthenticationDeviceNotifierConfiguration configuration;
    @Autowired(required = false) @Qualifier("cibaFederationWebClient") private WebClient webClient;
    @Autowired(required = false) private Vertx vertx;
    @Autowired(required = false) private CibaClientFactory cibaClientFactory;
    @Autowired(required = false) private ConsentRelayStrategyRegistry consentRelayStrategyRegistry;
    @Autowired(required = false) private OidcDiscoveryResolver discoveryResolver;
    @Autowired(required = false) private HintDecorationStrategyRegistry hintDecorationStrategyRegistry;

    private ConsentRelayStrategy consentStrategy;
    private HintDecorationStrategy hintStrategy;
    private ConsentRelayContext consentRelayContext;
    private PendingAuthStore store;
    private AuthorizationPoller poller;
    private int maxLifetimeSeconds;
    private boolean wired;

    public CibaFederationAuthenticationDeviceNotifierProvider() {}

    /** Test seam — set configuration directly without full Spring wiring. */
    void setConfiguration(CibaFederationAuthenticationDeviceNotifierConfiguration configuration) {
        this.configuration = configuration;
    }

    /** Test seam — inject collaborators directly, supplying the per-request client via a stub factory. */
    static CibaFederationAuthenticationDeviceNotifierProvider forTest(
            CibaClientFactory cibaClientFactory, OidcDiscoveryResolver discoveryResolver,
            ConsentRelayStrategy consentStrategy, HintDecorationStrategy hintStrategy,
            PendingAuthStore store, AuthorizationPoller poller, Vertx vertx, int maxLifetimeSeconds) {
        var p = new CibaFederationAuthenticationDeviceNotifierProvider();
        p.cibaClientFactory = cibaClientFactory; p.discoveryResolver = discoveryResolver;
        p.consentStrategy = consentStrategy; p.hintStrategy = hintStrategy;
        p.store = store; p.poller = poller; p.vertx = vertx;
        p.maxLifetimeSeconds = maxLifetimeSeconds; p.wired = true;
        p.consentRelayContext = new ConsentRelayContext(null);
        return p;
    }

    /** Config→strategy selection glue: blank/null id → {@code null} (raw relay, no transform);
     *  non-blank id → resolved via {@code registry}, which must be wired (fails fast otherwise). */
    static ConsentRelayStrategy selectStrategy(String stratId, ConsentRelayStrategyRegistry registry) {
        if (stratId == null || stratId.isBlank()) {
            return null; // no transform → relay raw RAR
        }
        return Objects.requireNonNull(registry, "consentRelayStrategyRegistry not wired").resolve(stratId);
    }

    /** Config→strategy selection glue for hint decoration; same semantics as {@link #selectStrategy}. */
    static HintDecorationStrategy selectHintStrategy(String stratId, HintDecorationStrategyRegistry registry) {
        if (stratId == null || stratId.isBlank()) {
            return null; // no decoration → relay hint verbatim
        }
        return Objects.requireNonNull(registry, "hintDecorationStrategyRegistry not wired").resolve(stratId);
    }

    /** CIBA Core §7.1: exactly one of login_hint / login_hint_token / id_token_hint must be present.
     *  Enforced post-decoration so a mis-behaving hint strategy fails closed here, not as an opaque
     *  upstream invalid_request. */
    static void requireSingleHint(CibaHints h) {
        int n = 0;
        if (hasText(h.loginHint())) n++;
        if (hasText(h.loginHintToken())) n++;
        if (hasText(h.idTokenHint())) n++;
        if (n != 1) {
            throw new IllegalStateException(
                "CIBA federation: exactly one of login_hint, login_hint_token, id_token_hint must be present (found " + n + ")");
        }
    }

    private synchronized void ensureWired() {
        if (wired) return;
        this.consentStrategy = selectStrategy(configuration.getConsentRelayStrategy(), consentRelayStrategyRegistry);
        this.hintStrategy = selectHintStrategy(configuration.getHintDecorationStrategy(), hintDecorationStrategyRegistry);
        this.consentRelayContext = new ConsentRelayContext(configuration.getRecipientDisplayName());
        this.store = new PendingAuthStore();
        var callbackClient = new GatewayCallbackClient(webClient, configuration.getCallbackClientId(),
                configuration.getCallbackClientSecret(), configuration.getCallbackClientAuthMethod());
        // callbackUrl is not a static config value; it is provided per-request via ADNotificationRequest
        // and threaded through PendingAuthStore.Pending so the poller can use the correct URL per tid.
        this.poller = new AuthorizationPoller(callbackClient, store, () -> Instant.now().getEpochSecond());
        this.maxLifetimeSeconds = configuration.getMaxLifetimeSeconds() == null ? 120 : configuration.getMaxLifetimeSeconds();
        this.wired = true;
    }

    /** Intrinsic to the CIBA Federation notifier type: a federation notifier always accepts
     *  authorization_details. This behaviour is always-on; it is NOT a config-driven checkbox.
     *  Federated-hint relaying is no longer signaled here — it is expressed via
     *  {@link IdentityProviderDependent#getIdentityProviderId()} instead (see below). */
    @Override
    public Set<NotifierCapability> capabilities() {
        return Set.of(NotifierCapability.AUTHORIZATION_DETAILS);
    }

    /** A CIBA-federation notifier's identity is always established by a registered AM identity
     *  provider; core reads this to resolve the federated connection. */
    @Override
    public Optional<String> getIdentityProviderId() {
        return Optional.ofNullable(configuration).map(CibaFederationAuthenticationDeviceNotifierConfiguration::getIdentityProviderId);
    }

    /** Fail closed at bind: a CIBA-federation notifier is intrinsically IdP-dependent and must
     *  not deploy without a configured identityProviderId, nor without a usable callback client
     *  configuration (id, secret, and a supported client-authentication method). */
    @Override
    public void afterPropertiesSet() {
        if (configuration == null) {
            throw new IllegalStateException("CIBA federation notifier requires configuration");
        }
        final String id = configuration.getIdentityProviderId();
        if (id == null || id.isBlank()) {
            throw new IllegalStateException("CIBA federation notifier requires identityProviderId");
        }
        if (isBlank(configuration.getCallbackClientId())) {
            throw new IllegalStateException("CIBA federation notifier requires callbackClientId");
        }
        if (isBlank(configuration.getCallbackClientSecret())) {
            throw new IllegalStateException("CIBA federation notifier requires callbackClientSecret");
        }
        if (!ClientAuthentication.isSupported(configuration.getCallbackClientAuthMethod())) {
            throw new IllegalStateException(ClientAuthentication.unsupportedMessage(configuration.getCallbackClientAuthMethod()));
        }
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Override
    public Single<ADNotificationResponse> notify(ADNotificationRequest request) {
        ensureWired();
        final FederatedConnection conn = Objects.requireNonNull(request.getConnection(),
                "FederatedConnection must be supplied by the gateway");
        final String tid = Objects.requireNonNull(request.getTransactionId(), "transactionId must not be null");
        final String callbackUrl = Objects.requireNonNull(request.getCallbackUrl(),
                "callbackUrl must be supplied by the gateway on ADNotificationRequest");
        final java.util.List<java.util.Map<String, Object>> rar = request.getAuthorizationDetails();
        final String scope = conn.scope();
        final String resourceAudience = configuration != null ? configuration.getResourceAudience() : null;
        // Y flow: resolve provider metadata once (issuer + endpoints), PREPARE (both IdP-adapter
        // transforms) with it, then hand a fully-formed request to pure transport.
        return discoveryResolver.resolve(conn.wellKnownUri()).flatMap(metadata -> {
            final java.util.List<java.util.Map<String, Object>> relayedRar =
                    (rar == null || consentStrategy == null) ? rar : consentStrategy.relay(rar, consentRelayContext);
            final CibaHints inbound = new CibaHints(request.getLoginHint(), request.getLoginHintToken(), null);
            final CibaHints hints = (hintStrategy == null) ? inbound
                    : hintStrategy.decorate(inbound, new HintDecorationContext(metadata));
            requireSingleHint(hints);
            final CibaClient cibaClient = cibaClientFactory.create(conn, resourceAudience, metadata);
            return cibaClient.bcAuthorize(hints, scope, request.getMessage(), relayedRar)
                    .map(res -> {
                        long expiresAt = Instant.now().getEpochSecond() + Math.min(res.expiresInSeconds(), maxLifetimeSeconds);
                        final String adHashPreSend = relayedRar == null ? null : CrossWitness.hash(relayedRar);
                        store.put(new PendingAuthStore.Pending(tid, request.getState(), res.authReqId(),
                                res.intervalSeconds(), expiresAt, adHashPreSend, callbackUrl));
                        poller.schedule(vertx, tid, res.intervalSeconds(), cibaClient);
                        return new ADNotificationResponse(tid);
                    });
        });
    }

    @Override
    public Single<Optional<ADUserResponse>> extractUserResponse(ADCallbackContext callbackContext) {
        final String state = callbackContext.getParam(STATE);
        final String tid = callbackContext.getParam(TRANSACTION_ID);
        final String validated = callbackContext.getParam(CALLBACK_VALIDATE);
        if (!hasText(state) || !hasText(tid) || !hasText(validated)) {
            return Single.just(Optional.empty());
        }
        return Single.just(Optional.of(new ADUserResponse(tid, state, Boolean.parseBoolean(validated),
                callbackContext.getParam(ID_TOKEN), callbackContext.getParam(ACCESS_TOKEN),
                getIdentityProviderId().orElse(null))));
    }
}
