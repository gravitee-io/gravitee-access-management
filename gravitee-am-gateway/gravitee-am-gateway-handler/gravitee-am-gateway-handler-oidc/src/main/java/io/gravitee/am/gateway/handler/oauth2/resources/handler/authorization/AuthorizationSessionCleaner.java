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
package io.gravitee.am.gateway.handler.oauth2.resources.handler.authorization;

import io.gravitee.am.common.utils.ConstantKeys;
import io.vertx.rxjava3.ext.web.RoutingContext;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Drops the per-run session state of an authorization request once it has reached its terminal
 * step, whichever terminal step that is.
 *
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class AuthorizationSessionCleaner {

    public static void clean(RoutingContext context) {
        context.session().remove(ConstantKeys.TRANSACTION_ID_KEY);
        context.session().remove(ConstantKeys.AUTH_FLOW_CONTEXT_VERSION_KEY);
        context.session().remove(ConstantKeys.USER_CONSENT_COMPLETED_KEY);
        context.session().remove(ConstantKeys.WEBAUTHN_CREDENTIAL_ID_CONTEXT_KEY);
        context.session().remove(ConstantKeys.WEBAUTHN_CREDENTIAL_INTERNAL_ID_CONTEXT_KEY);
        context.session().remove(ConstantKeys.PASSWORDLESS_AUTH_ACTION_KEY);
        context.session().remove(ConstantKeys.MFA_FACTOR_ID_CONTEXT_KEY);
        context.session().remove(ConstantKeys.PASSWORDLESS_CHALLENGE_KEY);
        context.session().remove(ConstantKeys.PASSWORDLESS_CHALLENGE_USERNAME_KEY);
        context.session().remove(ConstantKeys.MFA_ENROLLMENT_COMPLETED_KEY);
        context.session().remove(ConstantKeys.MFA_CHALLENGE_COMPLETED_KEY);
        context.session().remove(ConstantKeys.USER_LOGIN_COMPLETED_KEY);
        context.session().remove(ConstantKeys.MFA_ENROLL_CONDITIONAL_SKIPPED_KEY);
    }
}
