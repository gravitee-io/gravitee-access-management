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


import io.gravitee.am.common.utils.ConstantKeys;
import io.vertx.rxjava3.ext.web.Session;

import java.util.BitSet;

/**
 * First byte is about user session state
 * Second byte is split in two part to track the MFA step and the webauthn step
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public final class SessionState {
    private static final int BITS_FOR_LONG = 64;

    private final BitSet state;

    public SessionState() {
       this(0);
    }

    public SessionState(long state) {
        this.state = BitSet.valueOf(new long[]{state});
    }

    public SessionState(Session session) {
        if (session != null && session.data().containsKey(ConstantKeys.SESSION_KEY_STATE)) {
            this.state = BitSet.valueOf(new long[]{session.get(ConstantKeys.SESSION_KEY_STATE)});
        } else {
            this.state = new BitSet(BITS_FOR_LONG);
        }
    }

    public void save(Session session) {
        session.put(ConstantKeys.SESSION_KEY_STATE, state.toLongArray()[0]);
    }

    public void reset() {
        this.state.clear();
    }

    public UserAuthState getUserAuthState() {
        return UserAuthState.load(this.state);
    }

    public MfaState getMfaState() {
        return MfaState.load(this.state);
    }

    public WebAuthnState getWebAuthnState() {
        return WebAuthnState.load(this.state);
    }

    public ConsentState getConsentState() {
        return ConsentState.load(this.state);
    }

    public static enum Flags {
        IDX_ONGOING,
        IDX_SIGNED_IN,
        IDX_STRONG_AUTH,
        IDX_STRONG_AUTH_MFA,
        IDX_STRONG_AUTH_WEBAUTHN,
        // keep 2 additional bit in case other auth methods should be used (Wallet?)
        IDX_RESERVED_1,
        IDX_RESERVED_2,
        IDX_RESERVED_3,

        IDX_CONSENT_COMPLETED,
        IDX_CONSENT_APPROVED,
        IDX_RESERVED_4,
        IDX_RESERVED_5,

        IDX_MFA_STEP_ENROLLMENT_ONGOING,
        IDX_MFA_STEP_ENROLLMENT_COMPLETED,
        IDX_MFA_STEP_ENROLLMENT_SKIPPED,
        IDX_MFA_STEP_CHALLENGE_ONGOING,
        IDX_MFA_STEP_CHALLENGE_COMPLETED,
        IDX_MFA_STEP_CHALLENGE_SKIPPED,
        IDX_MFA_ENROLL_CONDITIONAL_SKIPPED,
        IDX_MFA_STOPPED,
        // keep 4 additional bit in case other mfa flag should be added (ex: already have factor)
        IDX_RESERVED_6,
        IDX_RESERVED_7,
        IDX_WEB_AUTHN_REGISTER_ONGOING,
        IDX_WEB_AUTHN_LOGIN_ONGOING,
        // keep 2 additional bit in case other mfa flag should be added  (ex: already have credential)
        IDX_RESERVED_8,
        IDX_RESERVED_9;
    }
}
