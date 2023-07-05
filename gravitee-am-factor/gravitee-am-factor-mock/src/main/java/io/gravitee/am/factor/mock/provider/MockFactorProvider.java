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
package io.gravitee.am.factor.mock.provider;

import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.factor.mock.MockFactorConfiguration;
import io.gravitee.am.gateway.handler.manager.resource.ResourceManager;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.resource.api.ResourceProvider;
import io.gravitee.am.resource.api.mfa.MFAChallenge;
import io.gravitee.am.resource.api.mfa.MFAResourceProvider;
import io.reactivex.rxjava3.core.Completable;
import org.springframework.beans.factory.annotation.Autowired;


/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MockFactorProvider implements FactorProvider {

    @Autowired
    private MockFactorConfiguration configuration;

    @Override
    public Completable verify(FactorContext context) {
        final String code = context.getData(FactorContext.KEY_CODE, String.class);
        ResourceManager component = context.getComponent(ResourceManager.class);
        ResourceProvider provider = component.getResourceProvider(configuration.getGraviteeResource());
        return ((MFAResourceProvider) provider).verify(new MFAChallenge(null, code, null));
    }

    @Override
    public boolean checkSecurityFactor(EnrolledFactor securityFactor) {
        return true;
    }

    @Override
    public boolean needChallengeSending() {
        return true;
    }

    @Override
    public Completable sendChallenge(FactorContext context) {
        ResourceManager component = context.getComponent(ResourceManager.class);
        var provider = component.getResourceProvider(configuration.getGraviteeResource());
        return ((MFAResourceProvider) provider).send(null);
    }
}
