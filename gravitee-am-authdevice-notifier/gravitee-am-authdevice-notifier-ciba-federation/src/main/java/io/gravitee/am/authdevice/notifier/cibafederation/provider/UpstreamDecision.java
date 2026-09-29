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

import io.gravitee.am.authdevice.notifier.api.model.ADUserResponse;
import lombok.CustomLog;

import java.util.Optional;

@CustomLog
final class UpstreamDecision {

    private UpstreamDecision() {}

    static Optional<ADUserResponse> of(String tid, String relayedAdHash, CibaClient.PollResult result, String identityProviderId) {
        return switch (result.kind()) {
            case PENDING, SLOW_DOWN -> Optional.empty();
            case ERROR -> {
                log.warn("CIBA-FED upstream rejected tid={} error={}", tid, result.error());
                yield Optional.of(rejected(tid));
            }
            case TOKEN -> Optional.of(fromToken(tid, relayedAdHash, result, identityProviderId));
        };
    }

    private static ADUserResponse fromToken(String tid, String relayedAdHash, CibaClient.PollResult result, String identityProviderId) {
        // Cross-witness applies only when we relayed authorization_details: when RAR WAS sent, a mismatch
        // means the OP approved different/absent details than the client requested (consent substitution).
        final boolean rarSent = relayedAdHash != null;
        final boolean witnessOk = !rarSent || CrossWitness.matchesHash(relayedAdHash, result.authorizationDetails());
        final String idToken = result.idToken();
        log.info("CIBA-FED witness tid={} rar_sent={} cross_witness_match={} id_token_present={}",
                tid, rarSent, witnessOk, idToken != null);
        if (!witnessOk) {
            log.error("CIBA-FED consent cross-witness FAILED tid={}: OP authorization_details differ from what was relayed; failing closed", tid);
            return rejected(tid);
        }
        if (idToken == null) {
            // A federation notifier's contract is a federated identity assertion; a TOKEN with
            // no id_token cannot establish identity, independent of any remote userinfo toggle.
            log.error("CIBA-FED TOKEN response without id_token tid={}; failing closed", tid);
            return rejected(tid);
        }
        return new ADUserResponse(tid, null, true, idToken, result.accessToken(), identityProviderId);
    }

    private static ADUserResponse rejected(String tid) {
        return new ADUserResponse(tid, null, false);
    }
}
