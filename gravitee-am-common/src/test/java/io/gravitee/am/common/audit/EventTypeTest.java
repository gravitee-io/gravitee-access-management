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
package io.gravitee.am.common.audit;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author GraviteeSource Team
 */
class EventTypeTest {

    @Test
    void offersEventTypesFromEveryGroup() {
        assertThat(EventType.types()).contains(
                "USER_LOGIN",
                "USERNAME_UPDATED",
                "DEVICE_DELETED",
                "DOMAIN_CREATED",
                "APPLICATION_CREATED",
                "CLIENT_AUTHENTICATION",
                "TOKEN_CREATED",
                "ACCOUNT_ACCESS_TOKEN_CREATED",
                "PASSWORD_HISTORY_CREATED",
                "REPORTER_CREATED",
                "MFA_CHALLENGE",
                "TRUST_DOMAIN_CREATED",
                "RESOURCE_CREATED",
                "BOT_DETECTION_CREATED",
                "DEVICE_IDENTIFIER_CREATED",
                "ALERT_NOTIFIER_CREATED",
                "PROTECTED_RESOURCE_CLIENT_SECRET_RENEWED",
                "PERMISSION_EVALUATED");
    }

    @Test
    void offersValuesNotConstantNames() {
        assertThat(EventType.types())
                .contains("MFA_VERIFY_LIMIT_EXCEED")
                .doesNotContain("MFA_VERIFICATION_LIMIT_EXCEED");
    }

    @Test
    void isSorted() {
        assertThat(List.copyOf(EventType.types())).isSorted();
    }
}
