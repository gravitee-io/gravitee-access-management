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
package io.gravitee.am.factor.api;

import io.gravitee.am.common.plugin.AmPluginProvider;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public interface FactorProvider extends AmPluginProvider {

    Completable verify(FactorContext context);

    default Single<Enrollment> enroll(String account) {
        return Single.just(new Enrollment());
    }

    default Single<Enrollment> enroll(FactorContext factorContext) {
        return enroll(factorContext.getUser().getUsername());
    }

    boolean checkSecurityFactor(EnrolledFactor securityFactor);

    boolean needChallengeSending();

    Completable sendChallenge(FactorContext context);

    default boolean useVariableFactorSecurity() {
        return false;
    }

    default boolean useVariableFactorSecurity(FactorContext factorContext) {
        return useVariableFactorSecurity();
    }

    default Single<EnrolledFactor> changeVariableFactorSecurity(EnrolledFactor factor) {
        return Single.just(factor);
    }

    default Maybe<String> generateQrCode(User user, EnrolledFactor enrolledFactor) {
        return Maybe.empty();
    }
}
