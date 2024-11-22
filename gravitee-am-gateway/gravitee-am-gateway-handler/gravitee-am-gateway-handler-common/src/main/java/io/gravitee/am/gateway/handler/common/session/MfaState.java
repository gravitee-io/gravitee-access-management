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

import static io.gravitee.am.gateway.handler.common.session.SessionState.Flags.*;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MfaState {
    private final BitSet state;

    private MfaState(BitSet state) {
        this.state = state;
    }

    static MfaState load(BitSet state) {
        return new MfaState(state);
    }

    public boolean isEnrollmentOngoing() {
        return this.state.get(IDX_MFA_STEP_ENROLLMENT_ONGOING.ordinal());
    }

    public void enrollment() {
        this.state.flip(IDX_MFA_STEP_ENROLLMENT_ONGOING.ordinal());
    }

    public boolean isChallengeOngoing() {
        return this.state.get(IDX_MFA_STEP_CHALLENGE_ONGOING.ordinal());
    }

    public void challenge() {
        this.state.flip(IDX_MFA_STEP_CHALLENGE_ONGOING.ordinal());
    }

    public void reset() {
        this.state.clear(IDX_MFA_STEP_ENROLLMENT_ONGOING.ordinal());
        this.state.clear(IDX_MFA_STEP_CHALLENGE_ONGOING.ordinal());
    }
}
