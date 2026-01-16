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
package io.gravitee.am.extensiongrant.tokenexchange;

import io.gravitee.am.extensiongrant.api.ExtensionGrant;
import io.gravitee.am.extensiongrant.tokenexchange.provider.TokenExchangeExtensionGrantProvider;

/**
 * RFC 8693 OAuth 2.0 Token Exchange Extension Grant
 *
 * This extension grant enables the exchange of one security token for another,
 * supporting impersonation and delegation scenarios.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693">RFC 8693 - OAuth 2.0 Token Exchange</a>
 * @author GraviteeSource Team
 */
public class TokenExchangeExtensionGrant extends ExtensionGrant<TokenExchangeExtensionGrantConfiguration, TokenExchangeExtensionGrantProvider> {

    @Override
    public Class<TokenExchangeExtensionGrantConfiguration> configuration() {
        return TokenExchangeExtensionGrantConfiguration.class;
    }

    @Override
    public Class<TokenExchangeExtensionGrantProvider> provider() {
        return TokenExchangeExtensionGrantProvider.class;
    }
}
