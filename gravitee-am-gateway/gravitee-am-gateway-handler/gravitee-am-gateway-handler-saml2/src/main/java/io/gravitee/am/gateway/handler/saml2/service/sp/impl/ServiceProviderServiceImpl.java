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
package io.gravitee.am.gateway.handler.saml2.service.sp.impl;

import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.saml2.service.sp.ServiceProviderService;
import io.gravitee.am.identityprovider.api.Metadata;
import io.gravitee.am.service.exception.IdentityProviderMetadataNotFoundException;
import io.gravitee.am.service.exception.IdentityProviderNotFoundException;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ServiceProviderServiceImpl implements ServiceProviderService {

    private static final Logger logger = LoggerFactory.getLogger(ServiceProviderServiceImpl.class);

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Override
    public Single<Metadata> metadata(String providerId, String idpUrl) {
        return identityProviderManager.get(providerId)
                .switchIfEmpty(Single.error(new IdentityProviderNotFoundException(providerId)))
                .map(authenticationProvider -> {
                    Metadata metadata = authenticationProvider.metadata(idpUrl);
                    if (metadata == null) {
                        logger.debug("No metadata found for identity provider : {}", providerId);
                        throw new IdentityProviderMetadataNotFoundException(providerId);
                    }
                    return metadata;
                });
    }
}
