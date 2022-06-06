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

import io.gravitee.am.gateway.handler.common.vertx.web.handler.impl.internal.mfa.MfaFilterContext;

import java.util.function.Supplier;

import static com.google.common.base.Strings.isNullOrEmpty;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MfaSkipFilter extends MfaContextHolder implements Supplier<Boolean> {

    public MfaSkipFilter(MfaFilterContext context) {
        super(context);
    }

    @Override
    public Boolean get() {
        // We need to check whether the AMFA rule is false since we don't know
        final boolean mfaSkipped = context.isMfaSkipped();
        String mfaStepUpRule = context.getStepUpRule();
        return isNullOrEmpty(mfaStepUpRule) && mfaSkipped;
    }
}
