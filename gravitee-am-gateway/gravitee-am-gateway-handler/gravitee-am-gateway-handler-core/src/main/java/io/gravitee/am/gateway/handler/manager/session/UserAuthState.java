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

package io.gravitee.am.gateway.handler.manager.session;


import java.util.BitSet;
import static io.gravitee.am.gateway.handler.manager.session.SessionState.*;
/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserAuthState {
    private final BitSet state;

    private UserAuthState(BitSet state) {
        this.state = state;
    }

    static UserAuthState load(BitSet state) {
        return new UserAuthState(state);
    }

    public void ongoing() {
        this.state.flip(IDX_ONGOING);
    }

    public boolean isOngoing() {
        return this.state.get(IDX_ONGOING);
    }

    public void finalized() {
        // reset ongoing bit
        this.state.clear(IDX_ONGOING);
        // reset also the states for MFA & WebAuthn phases
        // as this is transitive state which can be clear once
        // the flow is finalized
        this.state.clear(IDX_MFA_STEP_ENROLLMENT_ONGOING);
        this.state.clear(IDX_MFA_STEP_CHALLENGE_ONGOING);
        this.state.clear(IDX_WEB_AUTHN_REGISTER_ONGOING);
        this.state.clear(IDX_WEB_AUTHN_LOGIN_ONGOING);
    }

    public void signedIn() {
        this.state.flip(IDX_SIGNED_IN);
    }

    public boolean isSignedIn() {
        return this.state.get(IDX_SIGNED_IN);
    }

    public void stronglyAuth() {
        this.state.flip(IDX_STRONG_AUTH);
    }

    public boolean isStronglyAuth() {
        return this.state.get(IDX_STRONG_AUTH);
    }

    public void stronglyAuthWitMfa() {
        this.state.flip(IDX_STRONG_AUTH_MFA);
    }

    public boolean isStronglyAuthWitMfa() {
        return this.state.get(IDX_STRONG_AUTH_MFA);
    }

    public void stronglyAuthWitWebAuthn() {
        this.state.flip(IDX_STRONG_AUTH_WEBAUTHN);
    }

    public boolean isStronglyAuthWitWebAuthn() {
        return this.state.get(IDX_STRONG_AUTH_WEBAUTHN);
    }
}
