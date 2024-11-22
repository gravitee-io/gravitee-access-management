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

    static final short IDX_ONGOING = 0;
    static final short IDX_SIGNED_IN = 1;
    static final short IDX_STRONG_AUTH = 2;
    static final short IDX_STRONG_AUTH_MFA = 3;
    static final short IDX_STRONG_AUTH_WEBAUTHN = 4;
    // keep 3 additional bit in case other auth methods should be used (Wallet?)
    static final short IDX_RESERVED_1 = 5;
    static final short IDX_RESERVED_2 = 6;
    static final short IDX_RESERVED_3 = 7;
    static final short IDX_MFA_STEP_ENROLLMENT_ONGOING = 8;
    static final short IDX_MFA_STEP_CHALLENGE_ONGOING = 9;
    // keep 2 additional bit in case other mfa flag should be added (ex: already have factor)
    static final short IDX_RESERVED_4 = 10;
    static final short IDX_RESERVED_5 = 11;
    static final short IDX_WEB_AUTHN_REGISTER_ONGOING = 12;
    static final short IDX_WEB_AUTHN_LOGIN_ONGOING = 13;
    // keep 2 additional bit in case other mfa flag should be added  (ex: already have credential)
    static final short IDX_RESERVED_6 = 14;
    static final short IDX_RESERVED_7 = 15;

    private final BitSet state;

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

    public long getState() {
        return state.toLongArray()[0];
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
}
