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

import io.gravitee.am.common.exception.authentication.BadCredentialsException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.policy.PolicyChainException;
import io.gravitee.am.service.exception.EnrollmentChannelValidationException;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class UserFacingFailureMessageTest {

    private static final String SCRIPT_FAILURE = "Cannot invoke method somethingThatDoesNotExist() on null object";

    @Test
    void should_withhold_message_of_policy_failure_without_key() {
        var failure = policyFailure(null, SCRIPT_FAILURE);

        assertThat(UserFacingFailureMessage.from(failure)).isEmpty();
    }

    @Test
    void should_withhold_message_of_internal_policy_chain_failure() {
        var failure = policyFailure(ConstantKeys.POLICY_CHAIN_ERROR_KEY_INTERNAL_ERROR, SCRIPT_FAILURE);

        assertThat(UserFacingFailureMessage.from(failure)).isEmpty();
    }

    @Test
    void should_keep_message_of_deliberate_policy_failure() {
        var failure = policyFailure("CALLOUT_EXIT_ON_ERROR", "{\"errorTest\": \"Test Error\"}");

        assertThat(UserFacingFailureMessage.from(failure)).contains("{\"errorTest\": \"Test Error\"}");
    }

    @Test
    void should_keep_messages_am_writes_itself() {
        assertThat(UserFacingFailureMessage.from(new EnrollmentChannelValidationException("Invalid phone number")))
                .contains("Invalid phone number");
        assertThat(UserFacingFailureMessage.from(new BadCredentialsException("access_denied")))
                .contains("access_denied");
        assertThat(UserFacingFailureMessage.from(new InvalidRequestException("Missing parameter")))
                .contains("Missing parameter");
    }

    @Test
    void should_withhold_message_of_unrecognised_failure() {
        assertThat(UserFacingFailureMessage.from(new NullPointerException(SCRIPT_FAILURE))).isEmpty();
        assertThat(UserFacingFailureMessage.from(new IllegalStateException("jdbc://internal-host:5432 unreachable"))).isEmpty();
    }

    @Test
    void should_return_empty_for_no_failure() {
        assertThat(UserFacingFailureMessage.from(null)).isEmpty();
    }

    private static PolicyChainException policyFailure(String key, String message) {
        return new PolicyChainException(message, 500, key, Map.of(), "text/plain");
    }
}
