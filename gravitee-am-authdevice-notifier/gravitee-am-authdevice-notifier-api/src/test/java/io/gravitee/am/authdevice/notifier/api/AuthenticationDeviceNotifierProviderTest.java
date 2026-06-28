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
package io.gravitee.am.authdevice.notifier.api;

import io.gravitee.am.authdevice.notifier.api.model.*;
import io.reactivex.rxjava3.core.Single;
import org.junit.jupiter.api.Test;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuthenticationDeviceNotifierProviderTest {
    @Test void default_capabilities_are_empty() {
        AuthenticationDeviceNotifierProvider p = new AuthenticationDeviceNotifierProvider() {
            public Single<ADNotificationResponse> notify(ADNotificationRequest r) { return null; }
            public Single<Optional<ADUserResponse>> extractUserResponse(ADCallbackContext c) { return null; }
        };
        assertTrue(p.capabilities().isEmpty());
    }
}
