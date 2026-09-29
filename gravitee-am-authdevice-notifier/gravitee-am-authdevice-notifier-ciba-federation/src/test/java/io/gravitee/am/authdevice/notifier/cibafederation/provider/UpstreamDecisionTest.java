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
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class UpstreamDecisionTest {

    static final List<Map<String, Object>> RELAYED = List.of(Map.of("type", "x"));
    static final String RELAYED_HASH = CrossWitness.hash(RELAYED);

    static CibaClient.PollResult token(String idToken, Object authorizationDetails) {
        return new CibaClient.PollResult(CibaClient.PollKind.TOKEN, "AT", idToken, authorizationDetails, null);
    }

    @Test
    void pending_has_no_decision() {
        var result = new CibaClient.PollResult(CibaClient.PollKind.PENDING, null, null, null, "authorization_pending");
        assertEquals(Optional.empty(), UpstreamDecision.of("tid1", RELAYED_HASH, result, "idp"));
    }

    @Test
    void slow_down_has_no_decision() {
        var result = new CibaClient.PollResult(CibaClient.PollKind.SLOW_DOWN, null, null, null, "slow_down");
        assertEquals(Optional.empty(), UpstreamDecision.of("tid1", RELAYED_HASH, result, "idp"));
    }

    @Test
    void token_approves_when_witness_matches() {
        ADUserResponse decision = UpstreamDecision.of("tid1", RELAYED_HASH, token("id.tok", RELAYED), "idp").orElseThrow();
        assertTrue(decision.isValidated());
        assertEquals("tid1", decision.getTid());
        assertEquals("id.tok", decision.getIdToken());
        assertEquals("AT", decision.getAccessToken());
        assertEquals("idp", decision.getIdentityProviderId());
    }

    @Test
    void token_rejects_when_witness_mismatches() {
        ADUserResponse decision = UpstreamDecision.of("tid1", RELAYED_HASH,
                token("id.tok", List.of(Map.of("type", "TAMPERED"))), "idp").orElseThrow();
        assertFalse(decision.isValidated());
        assertNull(decision.getIdToken());
    }

    @Test
    void token_without_rar_skips_witness_and_approves() {
        assertTrue(UpstreamDecision.of("tid1", null, token("id.tok", null), "idp").orElseThrow().isValidated());
    }

    @Test
    void token_without_id_token_rejects() {
        assertFalse(UpstreamDecision.of("tid1", RELAYED_HASH, token(null, RELAYED), "idp").orElseThrow().isValidated());
    }

    @Test
    void access_denied_rejects() {
        var result = new CibaClient.PollResult(CibaClient.PollKind.ERROR, null, null, null, "access_denied");
        assertFalse(UpstreamDecision.of("tid1", RELAYED_HASH, result, "idp").orElseThrow().isValidated());
    }

    @Test
    void any_other_upstream_error_has_no_decision() {
        for (String error : new String[]{"invalid_grant", "expired_token", "invalid_client", "too_many_requests", "503"}) {
            var result = new CibaClient.PollResult(CibaClient.PollKind.ERROR, null, null, null, error);
            assertEquals(Optional.empty(), UpstreamDecision.of("tid1", RELAYED_HASH, result, "idp"), error);
        }
    }
}
