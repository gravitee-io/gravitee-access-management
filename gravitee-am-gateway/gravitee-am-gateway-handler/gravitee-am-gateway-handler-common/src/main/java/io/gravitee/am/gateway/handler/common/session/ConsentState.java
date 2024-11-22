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

package io.gravitee.am.gateway.handler.common.session;


import java.util.BitSet;

import static io.gravitee.am.gateway.handler.common.session.SessionState.Flags.IDX_CONSENT_APPROVED;
import static io.gravitee.am.gateway.handler.common.session.SessionState.Flags.IDX_CONSENT_COMPLETED;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ConsentState {
    private final BitSet state;

    private ConsentState(BitSet state) {
        this.state = state;
    }

    static ConsentState load(BitSet state) {
        return new ConsentState(state);
    }

    public void consentComplete() {
        this.state.flip(IDX_CONSENT_COMPLETED.ordinal());
    }

    public void clearConsentComplete() {
        this.state.clear(IDX_CONSENT_COMPLETED.ordinal());
    }

    public boolean isConsentComplete() {
        return this.state.get(IDX_CONSENT_COMPLETED.ordinal());
    }

    public void approve() {
        this.state.flip(IDX_CONSENT_APPROVED.ordinal());
    }

    public boolean isApproved() {
        return this.state.get(IDX_CONSENT_APPROVED.ordinal());
    }
}
