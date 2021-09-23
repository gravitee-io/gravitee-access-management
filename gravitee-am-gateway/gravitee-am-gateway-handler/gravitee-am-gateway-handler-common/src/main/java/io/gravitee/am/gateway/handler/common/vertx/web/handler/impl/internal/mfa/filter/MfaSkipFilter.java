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

import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.utils.MfaUtils;
import io.gravitee.am.model.oidc.Client;
import io.vertx.reactivex.ext.web.Session;

import java.util.function.Supplier;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MfaSkipFilter implements Supplier<Boolean> {

    private final Client client;
    private final Session session;

    public MfaSkipFilter(Client client, Session session) {
        this.client = client;
        this.session = session;
    }

    @Override
    public Boolean get() {
        String mfaStepUpRule = MfaUtils.getMfaStepUpRule(client);
        String adaptiveMfaStepUpRule = MfaUtils.getAdaptiveMfaStepUpRule(client);
        return isNullOrEmpty(mfaStepUpRule) && isNullOrEmpty(adaptiveMfaStepUpRule) && MfaUtils.isMfaSkipped(session);
    }
}
