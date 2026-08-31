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
package io.gravitee.am.gateway.handler.common.utils;

import io.gravitee.am.common.exception.authentication.AuthenticationException;
import io.gravitee.am.common.exception.oauth2.OAuth2Exception;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.service.exception.AbstractManagementException;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Optional;

/**
 * Decides whether a failure's message may be shown to the end user.
 *
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserFacingFailureMessage {

    /** The message to show for {@code failure}, or empty when it should stay in the log. */
    public static Optional<String> from(Throwable failure) {
        return switch (failure) {
            case null -> Optional.empty();
            case PolicyChainException policyFailure -> isDeliberate(policyFailure)
                    ? Optional.ofNullable(policyFailure.getMessage())
                    : Optional.empty();
            case AbstractManagementException managementFailure -> Optional.ofNullable(managementFailure.getMessage());
            case AuthenticationException authenticationFailure -> Optional.ofNullable(authenticationFailure.getMessage());
            case OAuth2Exception oauth2Failure -> Optional.ofNullable(oauth2Failure.getMessage());
            default -> Optional.empty();
        };
    }

    /** Whether a policy failure is safe to expose to the end user. */
    private static boolean isDeliberate(PolicyChainException failure) {
        return failure.key() != null && !ConstantKeys.POLICY_CHAIN_ERROR_KEY_INTERNAL_ERROR.equals(failure.key());
    }
}
