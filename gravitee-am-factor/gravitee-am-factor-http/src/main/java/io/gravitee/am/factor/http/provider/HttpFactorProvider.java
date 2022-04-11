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
import io.gravitee.am.factor.utils.SharedSecret;
import io.gravitee.am.gateway.handler.context.EvaluableRequest;
import io.gravitee.am.gateway.handler.manager.resource.ResourceManager;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.factor.EnrolledFactorSecurity;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.exceptions.TechnicalException;
import io.gravitee.am.resource.api.mfa.MFAChallenge;
import io.gravitee.am.resource.api.mfa.MFALink;
import io.gravitee.am.resource.api.mfa.MFAResourceProvider;
import io.reactivex.Completable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import static io.gravitee.am.factor.api.FactorContext.KEY_ENROLLED_FACTOR;
import static io.gravitee.am.resource.api.mfa.MFAType.HTTP;

public class HttpFactorProvider implements FactorProvider {

    private static final Logger logger = LoggerFactory.getLogger(HttpFactorProvider.class);
    @Autowired
    private HttpFactorConfiguration configuration;

    @Override
    public Completable verify(FactorContext context) {
        final var code = context.getData(FactorContext.KEY_CODE, String.class);
        final var enrolledFactor = context.getData(FactorContext.KEY_ENROLLED_FACTOR, EnrolledFactor.class);
        var component = context.getComponent(ResourceManager.class);
        var provider = component.getResourceProvider(configuration.getGraviteeResource());
        if (provider instanceof MFAResourceProvider) {
            var mfaProvider = (MFAResourceProvider) provider;
            var challenge = new MFAChallenge(enrolledFactor.getChannel().getTarget(), code);//TODO get challenge from resource
            return mfaProvider.verify(challenge);
        } else {
            return Completable.error(new TechnicalException("Resource referenced can't be used for MultiFactor Authentication with type HTTP"));
        }
    }

    @Override
    public Single<Enrollment> enroll(String account) {
        return Single.fromCallable(() -> new Enrollment(SharedSecret.generate()));
    }

    @Override
    public boolean checkSecurityFactor(EnrolledFactor factor) {
        boolean valid = true;
        if (factor != null) {
            EnrolledFactorSecurity securityFactor = factor.getSecurity();
            if (securityFactor == null || securityFactor.getValue() == null) {
                logger.warn("No shared secret in form - did you forget to include shared secret value ?");
                valid = false;
            }
        }
        return valid;
    }

    @Override
    public Completable sendChallenge(FactorContext context) {
        //TODO need access to current user, applicatoin and request (params, headers, body)
        User user = context.getUser();
        Client client = context.getClient();
        EvaluableRequest request = context.getData(FactorContext.KEY_REQUEST, EvaluableRequest.class);
        final var enrolledFactor = context.getData(KEY_ENROLLED_FACTOR, EnrolledFactor.class);
        var component = context.getComponent(ResourceManager.class);
        var provider = component.getResourceProvider(configuration.getGraviteeResource());


        //TODO This prob isn't what I need; look at EmailProvider.sendEmail etc, probably need a send method on the Http resource that
        //will accept  request/user/app details
        if (provider instanceof MFAResourceProvider) {
            var mfaProvider = (MFAResourceProvider)provider;
            var link = new MFALink(HTTP, "Address comes from resource");
            return mfaProvider.send(link);
        } else {
            return Completable.error(
                    new TechnicalException("Resource referenced can't be used for MultiFactor Authentication  with type HTTP"));
        }
    }
}
