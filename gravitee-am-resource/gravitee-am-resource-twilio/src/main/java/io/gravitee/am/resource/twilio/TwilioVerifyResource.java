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
package io.gravitee.am.resource.twilio;

import io.gravitee.am.resource.api.Resource;
import io.gravitee.am.resource.api.ResourceConfiguration;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.am.resource.twilio.provider.TwilioVerifyResourceProvider;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class TwilioVerifyResource implements Resource {

    @Override
    public Class<? extends ResourceConfiguration> configuration() {
        return TwilioVerifyResourceConfiguration.class;
    }

    @Override
    public Class<? extends ResourceProvider> resourceProvider() {
        return TwilioVerifyResourceProvider.class;
    }
}
