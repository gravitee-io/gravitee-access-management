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

    public boolean isEnrollmentCompleted() {
        return this.state.get(IDX_MFA_STEP_ENROLLMENT_ONGOING.ordinal());
    }

    public void enrollmentOngoing() {
        this.state.flip(IDX_MFA_STEP_ENROLLMENT_ONGOING.ordinal());
        this.state.clear(IDX_MFA_STEP_ENROLLMENT_COMPLETED.ordinal());
    }

    public void enrollmentCompleted() {
        this.state.clear(IDX_MFA_STEP_ENROLLMENT_ONGOING.ordinal());
        this.state.flip(IDX_MFA_STEP_ENROLLMENT_COMPLETED.ordinal());
    }

    public boolean isEnrollConditionalSkip() {
        return this.state.get(IDX_MFA_ENROLL_CONDITIONAL_SKIPPED.ordinal());
    }

    public void enrollConditionalSkip() {
        this.state.flip(IDX_MFA_ENROLL_CONDITIONAL_SKIPPED.ordinal());
    }

    public void resetEnrollConditionalSkip() {
        this.state.clear(IDX_MFA_ENROLL_CONDITIONAL_SKIPPED.ordinal());
    }

    public boolean isChallengeOngoing() {
        return this.state.get(IDX_MFA_STEP_CHALLENGE_ONGOING.ordinal());
    }

    public boolean isChallengeCompleted() {
        return this.state.get(IDX_MFA_STEP_CHALLENGE_COMPLETED.ordinal());
    }

    public void challengeOngoing() {
        this.state.flip(IDX_MFA_STEP_CHALLENGE_ONGOING.ordinal());
        this.state.flip(IDX_MFA_STEP_CHALLENGE_COMPLETED.ordinal());
    }

    public void challengeCompleted() {
        this.state.clear(IDX_MFA_STEP_CHALLENGE_ONGOING.ordinal());
        this.state.flip(IDX_MFA_STEP_CHALLENGE_COMPLETED.ordinal());
    }

    public boolean isChallengeSkipped() {
        return this.state.get(IDX_MFA_STEP_CHALLENGE_SKIPPED.ordinal());
    }

    public void skipChallenge() {
        this.state.flip(IDX_MFA_STEP_CHALLENGE_SKIPPED.ordinal());
    }

    public boolean isEnrollmentSkipped() {
        return this.state.get(IDX_MFA_STEP_ENROLLMENT_SKIPPED.ordinal());
    }

    public void skipEnrollment() {
        this.state.flip(IDX_MFA_STEP_ENROLLMENT_SKIPPED.ordinal());
    }

    public void stopMfaFlow() {
        this.state.flip(IDX_MFA_STOPPED.ordinal());
    }

    public boolean isMfaFlowStopped() {
        return this.state.get(IDX_MFA_STOPPED.ordinal());
    }

    public void reset() {
        this.state.clear(IDX_MFA_STEP_ENROLLMENT_ONGOING.ordinal());
        this.state.clear(IDX_MFA_STEP_ENROLLMENT_COMPLETED.ordinal());
        this.state.clear(IDX_MFA_STEP_CHALLENGE_ONGOING.ordinal());
        this.state.clear(IDX_MFA_STEP_CHALLENGE_COMPLETED.ordinal());
        this.state.clear(IDX_MFA_STEP_ENROLLMENT_SKIPPED.ordinal());
        this.state.clear(IDX_MFA_STEP_CHALLENGE_SKIPPED.ordinal());
        this.state.clear(IDX_MFA_ENROLL_CONDITIONAL_SKIPPED.ordinal());
        this.state.clear(IDX_MFA_STOPPED.ordinal());
    }
}
