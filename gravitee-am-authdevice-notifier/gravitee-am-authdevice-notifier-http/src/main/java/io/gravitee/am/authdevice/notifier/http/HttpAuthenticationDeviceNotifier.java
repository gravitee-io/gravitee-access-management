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
package io.gravitee.am.authdevice.notifier.http;

import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifier;
import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierConfiguration;
import io.gravitee.am.authdevice.notifier.api.AuthenticationDeviceNotifierProvider;
import io.gravitee.am.authdevice.notifier.http.provider.HttpAuthenticationDeviceNotifierProvider;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class HttpAuthenticationDeviceNotifier implements AuthenticationDeviceNotifier {

    @Override
    public Class<? extends AuthenticationDeviceNotifierConfiguration> configuration() {
        return HttpAuthenticationDeviceNotifierConfiguration.class;
    }

    @Override
    public Class<? extends AuthenticationDeviceNotifierProvider> notificationProvider() {
        return HttpAuthenticationDeviceNotifierProvider.class;
    }
}
