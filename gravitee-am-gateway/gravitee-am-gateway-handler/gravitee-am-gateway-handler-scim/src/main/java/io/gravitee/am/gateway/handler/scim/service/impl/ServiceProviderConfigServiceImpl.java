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
package io.gravitee.am.gateway.handler.scim.service.impl;

import io.gravitee.am.gateway.handler.scim.model.AuthenticationScheme;
import io.gravitee.am.gateway.handler.scim.model.ComplexType;
import io.gravitee.am.gateway.handler.scim.model.ServiceProviderConfiguration;
import io.gravitee.am.gateway.handler.scim.service.ServiceProviderConfigService;
import io.reactivex.Single;

import java.util.Collections;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ServiceProviderConfigServiceImpl implements ServiceProviderConfigService {

    @Override
    public Single<ServiceProviderConfiguration> get() {
        ServiceProviderConfiguration serviceProviderConfiguration = new ServiceProviderConfiguration();
        serviceProviderConfiguration.setPatch(new ComplexType(false));
        serviceProviderConfiguration.setBulk(new ComplexType(false));
        serviceProviderConfiguration.setFilter(new ComplexType(false));
        serviceProviderConfiguration.setChangePassword(new ComplexType(false));
        serviceProviderConfiguration.setSort(new ComplexType(false));
        serviceProviderConfiguration.setEtag(new ComplexType(false));
        serviceProviderConfiguration.setAuthenticationSchemes(Collections.singletonList(AuthenticationScheme.OAUTH_BEARER_TOKEN));

        return Single.just(serviceProviderConfiguration);
    }
}
