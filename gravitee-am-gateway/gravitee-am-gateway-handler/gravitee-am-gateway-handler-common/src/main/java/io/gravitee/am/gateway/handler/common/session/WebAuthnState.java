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

import static io.gravitee.am.gateway.handler.common.session.SessionState.Flags.IDX_WEB_AUTHN_LOGIN_ONGOING;
import static io.gravitee.am.gateway.handler.common.session.SessionState.Flags.IDX_WEB_AUTHN_REGISTER_ONGOING;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class WebAuthnState {
    private final BitSet state;

    private WebAuthnState(BitSet state) {
        this.state = state;
    }

    static WebAuthnState load(BitSet state) {
        return new WebAuthnState(state);
    }

    public boolean isRegistrationOngoing() {
        return this.state.get(IDX_WEB_AUTHN_REGISTER_ONGOING.ordinal());
    }

    public void registrationOngoing() {
        this.state.flip(IDX_WEB_AUTHN_REGISTER_ONGOING.ordinal());
    }

    public boolean isLoginOngoing() {
        return this.state.get(IDX_WEB_AUTHN_LOGIN_ONGOING.ordinal());
    }

    public void loginOngoing() {
        this.state.flip(IDX_WEB_AUTHN_LOGIN_ONGOING.ordinal());
    }

    public void reset() {
        this.state.clear(IDX_WEB_AUTHN_REGISTER_ONGOING.ordinal());
        this.state.clear(IDX_WEB_AUTHN_LOGIN_ONGOING.ordinal());
    }
}
