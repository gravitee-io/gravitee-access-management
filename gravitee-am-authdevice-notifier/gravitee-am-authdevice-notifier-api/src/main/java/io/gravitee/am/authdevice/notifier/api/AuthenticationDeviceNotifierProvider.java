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

import io.gravitee.am.authdevice.notifier.api.model.ADCallbackContext;
import io.gravitee.am.authdevice.notifier.api.model.ADNotificationRequest;
import io.gravitee.am.authdevice.notifier.api.model.ADNotificationResponse;
import io.gravitee.am.authdevice.notifier.api.model.ADUserResponse;
import io.gravitee.common.component.Lifecycle;
import io.gravitee.common.service.Service;
import io.reactivex.rxjava3.core.Single;

import java.util.Optional;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface AuthenticationDeviceNotifierProvider extends Service<AuthenticationDeviceNotifierProvider> {

    @Override
    default Lifecycle.State lifecycleState() {
        return Lifecycle.State.INITIALIZED;
    }

    @Override
    default AuthenticationDeviceNotifierProvider start() throws Exception {
        return this;
    }

    @Override
    default AuthenticationDeviceNotifierProvider stop() throws Exception {
        return this;
    }

    Single<ADNotificationResponse> notify(ADNotificationRequest request);

    Single<Optional<ADUserResponse>> extractUserResponse(ADCallbackContext callbackContext);

}
