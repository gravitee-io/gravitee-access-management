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
import io.gravitee.am.authdevice.notifier.api.model.*;
import io.gravitee.am.authdevice.notifier.cibafederation.CibaFederationAuthenticationDeviceNotifierConfiguration;
import io.gravitee.am.authdevice.notifier.cibafederation.provider.spring.CibaFederationProviderSpringConfiguration;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.Vertx;
import io.vertx.rxjava3.ext.web.client.WebClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import static org.springframework.util.StringUtils.hasText;

@Import(CibaFederationProviderSpringConfiguration.class)
public class CibaFederationAuthenticationDeviceNotifierProvider implements AuthenticationDeviceNotifierProvider {

    public static final String TRANSACTION_ID = "tid";
    public static final String STATE = "state";
    public static final String CALLBACK_VALIDATE = "validated";
    public static final String ID_TOKEN = "id_token";
    public static final String ACCESS_TOKEN = "access_token";

    @Autowired(required = false) private CibaFederationAuthenticationDeviceNotifierConfiguration configuration;
    @Autowired(required = false) @Qualifier("cibaFederationWebClient") private WebClient webClient;
    @Autowired(required = false) private Vertx vertx;
    @Autowired(required = false) private OidcDiscoveryResolver discoveryResolver;

    /** Non-null only when injected via forTest seam; null in production (client built per-request). */
    private CibaClient testAuth0;
    private ConsentRelayStrategy consentStrategy;
    private PendingAuthStore store;
    private AuthorizationPoller poller;
    private int maxLifetimeSeconds;
    private boolean wired;

    public CibaFederationAuthenticationDeviceNotifierProvider() {}

    /** Test seam — inject collaborators directly. When auth0 is non-null it is used for every notify call. */
    static CibaFederationAuthenticationDeviceNotifierProvider forTest(
            CibaClient auth0, ConsentRelayStrategy consentStrategy, PendingAuthStore store,
            AuthorizationPoller poller, Vertx vertx, int maxLifetimeSeconds) {
        var p = new CibaFederationAuthenticationDeviceNotifierProvider();
        p.testAuth0 = auth0; p.consentStrategy = consentStrategy; p.store = store; p.poller = poller; p.vertx = vertx;
        p.maxLifetimeSeconds = maxLifetimeSeconds; p.wired = true;
        return p;
    }

    private synchronized void ensureWired() {
        if (wired) return;
        this.consentStrategy = switch (ConsentRelayStrategy.Type.fromConfig(configuration.getConsentRelayStrategy())) {
            case PASSTHROUGH -> new PassthroughRelayStrategy();
            case AUTH0_USER_PROFILE -> new Auth0UserProfileRelayStrategy(configuration.getRecipientDisplayName());
        };
        this.store = new PendingAuthStore();
        var callbackClient = new GatewayCallbackClient(webClient, configuration.getCallbackClientId(), configuration.getCallbackClientSecret());
        // callbackUrl is not a static config value; it is provided per-request via ADNotificationRequest
        // and threaded through PendingAuthStore.Pending so the poller can use the correct URL per tid.
        this.poller = new AuthorizationPoller(callbackClient, store, () -> Instant.now().getEpochSecond());
        this.maxLifetimeSeconds = configuration.getMaxLifetimeSeconds() == null ? 120 : configuration.getMaxLifetimeSeconds();
        this.wired = true;
    }

    /** Intrinsic to the CIBA Federation notifier type (§2.2): a federation notifier always relays
     *  hints upstream and accepts authorization_details. These behaviours are always-on; they are
     *  NOT config-driven checkboxes. */
    @Override
    public Set<NotifierCapability> capabilities() {
        return Set.of(NotifierCapability.FEDERATED_HINT_RESOLUTION, NotifierCapability.AUTHORIZATION_DETAILS);
    }

    @Override
    public Single<ADNotificationResponse> notify(ADNotificationRequest request) {
        ensureWired();
        final FederatedConnection conn = Objects.requireNonNull(request.getConnection(),
                "FederatedConnection must be supplied by the gateway");
        final String tid = Objects.requireNonNull(request.getTransactionId(), "transactionId must not be null");
        final String callbackUrl = Objects.requireNonNull(request.getCallbackUrl(),
                "callbackUrl must be supplied by the gateway on ADNotificationRequest");
        final java.util.List<java.util.Map<String, Object>> relayedRar = request.getAuthorizationDetails() == null ? null
                : consentStrategy.relay(request.getAuthorizationDetails());
        final String scope = conn.scope();
        // resourceAudience is a notifier-config value sent as the downstream `audience` form parameter;
        // the IdP config cannot carry it. Only resolved when building the per-request client (not used
        // when testAuth0 is injected).
        final CibaClient auth0 = testAuth0 != null ? testAuth0
                : new CibaClient(webClient, discoveryResolver, conn.wellKnownUri(), conn.clientId(), conn.clientSecret(),
                        configuration.getResourceAudience());
        return auth0.bcAuthorize(request.getLoginHint(), request.getLoginHintToken(), scope, request.getMessage(), relayedRar)
                .map(res -> {
                    long expiresAt = Instant.now().getEpochSecond() + Math.min(res.expiresInSeconds(), maxLifetimeSeconds);
                    // Null hash when nothing was relayed: adHashPreSend != null then means exactly
                    // "RAR was sent", which is the poller's cross-witness enforcement guard.
                    final String adHashPreSend = relayedRar == null ? null : CrossWitness.hash(relayedRar);
                    store.put(new PendingAuthStore.Pending(tid, request.getState(), res.authReqId(),
                            res.intervalSeconds(), expiresAt, adHashPreSend, callbackUrl));
                    poller.schedule(vertx, tid, res.intervalSeconds(), auth0);
                    return new ADNotificationResponse(tid);
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
                callbackContext.getParam(ID_TOKEN), callbackContext.getParam(ACCESS_TOKEN))));
    }
}
