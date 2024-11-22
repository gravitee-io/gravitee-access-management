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
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SessionManager {

    public SessionState getSessionState(RoutingContext routingContext) {
        return Optional.ofNullable(routingContext.session()).map(SessionState::new).orElse(new SessionState());
    }

    public void cleanSessionAfterAuth(RoutingContext context) {
        cleanSessionOnMfaChallenge(context);
        if (context.session() != null) {
            getSessionState(context).getUserAuthState().finalized();

            context.session().remove(ConstantKeys.TRANSACTION_ID_KEY);
            context.session().remove(ConstantKeys.USER_CONSENT_COMPLETED_KEY);
            context.session().remove(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY);
            context.session().remove(ConstantKeys.WEBAUTHN_CREDENTIAL_INTERNAL_ID_CONTEXT_KEY);
            context.session().remove(ConstantKeys.PASSWORDLESS_AUTH_ACTION_KEY);
            context.session().remove(ConstantKeys.MFA_FACTOR_ID_CONTEXT_KEY);
            context.session().remove(ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY);
            context.session().remove(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY);
            context.session().remove(ConstantKeys.USER_LOGIN_COMPLETED_KEY);
            context.session().remove(ConstantKeys.MFA_ENROLL_CONDITIONAL_SKIPPED_KEY);

            context.session().remove(ConstantKeys.SESSION_KEY_AUTH_FLOW_STATE);
        }
    }

    public void cleanSessionOnMfaChallenge(RoutingContext context) {
        if (context.session() != null) {
            getSessionState(context).getMfaState().reset();

            context.session().remove(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY);
            context.session().remove(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY);

            context.session().remove(ConstantKeys.ENROLLED_FACTOR_ID_KEY);
            context.session().remove(ConstantKeys.ENROLLED_FACTOR_SECURITY_VALUE_KEY);
            context.session().remove(ConstantKeys.ENROLLED_FACTOR_PHONE_NUMBER);
            context.session().remove(ConstantKeys.ENROLLED_FACTOR_EXTENSION_PHONE_NUMBER);
            context.session().remove(ConstantKeys.ENROLLED_FACTOR_EMAIL_ADDRESS);
        }
    }
}
