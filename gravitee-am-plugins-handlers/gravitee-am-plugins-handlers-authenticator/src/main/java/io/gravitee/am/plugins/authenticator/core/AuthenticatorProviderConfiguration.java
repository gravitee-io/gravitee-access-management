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

package io.gravitee.am.plugins.authenticator.core;

import io.gravitee.am.plugins.handlers.api.provider.ProviderConfiguration;
import lombok.Getter;
import org.springframework.context.ApplicationContext;

@Getter
public class AuthenticatorProviderConfiguration extends ProviderConfiguration {
    private final ApplicationContext applicationContext;

    public AuthenticatorProviderConfiguration(String authenticator, ApplicationContext applicationContext) {
        super(authenticator, null);
        this.applicationContext = applicationContext;
    }
}
