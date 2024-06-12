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
package io.gravitee.am.gateway.handler.account;

import io.gravitee.am.gateway.handler.account.spring.AccountConfiguration;
import io.gravitee.am.gateway.handler.api.Protocol;

/**
 * @author Donald Courtney (donald.courtney at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AccountProtocol extends Protocol<AccountConfiguration, AccountProvider> {

    @Override
    public Class<AccountProvider> provider() {
        return AccountProvider.class;
    }

    @Override
    public Class<AccountConfiguration> configuration() {
        return AccountConfiguration.class;
    }
}
