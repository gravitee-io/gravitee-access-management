/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.am.factor.http.provider;

import io.gravitee.am.factor.api.Enrollment;
import io.gravitee.am.factor.api.FactorContext;
import io.gravitee.am.factor.api.FactorProvider;
import io.gravitee.am.factor.http.HttpFactorConfiguration;
import io.gravitee.am.gateway.handler.manager.resource.ResourceManager;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.resource.api.mfa.MFAChallenge;
import io.gravitee.am.resource.api.mfa.MFAResourceProvider;
import io.reactivex.Completable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

public class HttpFactorProvider implements FactorProvider {

    private static final Logger logger = LoggerFactory.getLogger(HttpFactorProvider.class);
    @Autowired
    private HttpFactorConfiguration configuration;

    @Override
    public Completable verify(FactorContext context) {
        var component = context.getComponent(ResourceManager.class);
        var provider = component.getResourceProvider(configuration.getGraviteeResource());
        if (provider instanceof MFAResourceProvider) {
            var mfaProvider = (MFAResourceProvider) provider;
            var challenge = new MFAChallenge("From resource again?", "123456");//TODO will pass the FactorContext
            return mfaProvider.verify(challenge);
        } else {
            return Completable.error(new TechnicalException("Resource referenced can't be used for MultiFactor Authentication with type HTTP"));
        }
    }

    @Override
    public Single<Enrollment> enroll(String account) {
        return Single.fromCallable(Enrollment::new);
    }

    @Override
    public boolean checkSecurityFactor(EnrolledFactor factor) {
        return true;
    }

    @Override
    public boolean needChallengeSending() {
        return false;
    }

    @Override
    public Completable sendChallenge(FactorContext context) {
        return null;
    }
}
