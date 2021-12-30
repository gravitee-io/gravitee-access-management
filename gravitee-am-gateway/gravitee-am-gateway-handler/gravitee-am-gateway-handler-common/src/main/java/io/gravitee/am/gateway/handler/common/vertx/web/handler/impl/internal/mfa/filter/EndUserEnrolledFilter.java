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

package io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.filter;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.model.User;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.model.oidc.Client;
import io.vertx.reactivex.ext.web.Session;

import java.util.function.Supplier;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class EndUserEnrolledFilter implements Supplier<Boolean> {

    private final Session session;
    private final User user;
    private final Client client;

    public EndUserEnrolledFilter(Session session, io.gravitee.am.model.User user, Client client) {
        this.session = session;
        this.user = user;
        this.client = client;
    }

    @Override
    public Boolean get() {
        if (session.get(ConstantKeys.ENROLLED_FACTOR_ID_KEY) != null) {
            return true;
        }

        if (user.getFactors() == null || user.getFactors().isEmpty()) {
            return false;
        }

        return user.getFactors().stream()
                .map(EnrolledFactor::getFactorId)
                .anyMatch(factor -> client.getFactors().contains(factor));
    }
}
