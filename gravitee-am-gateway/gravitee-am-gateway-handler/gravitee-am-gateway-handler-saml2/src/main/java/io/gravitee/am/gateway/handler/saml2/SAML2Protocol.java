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
package io.gravitee.am.gateway.handler.saml2;

import io.gravitee.am.gateway.handler.api.Protocol;
import io.gravitee.am.gateway.handler.api.ProtocolConfiguration;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.saml2.spring.SAMLConfiguration;
import io.gravitee.plugin.core.api.Plugin;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SAML2Protocol extends Protocol<SAMLConfiguration, SAML2Provider> {

    @Override
    public Class<SAMLConfiguration> configuration() {
        return SAMLConfiguration.class;
    }

    @Override
    public Class<SAML2Provider> provider() {
        return SAML2Provider.class;
    }
}
