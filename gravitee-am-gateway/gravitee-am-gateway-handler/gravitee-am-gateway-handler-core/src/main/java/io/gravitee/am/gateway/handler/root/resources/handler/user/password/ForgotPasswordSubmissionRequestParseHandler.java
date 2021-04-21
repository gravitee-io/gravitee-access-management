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
package io.gravitee.am.gateway.handler.root.resources.handler.user.password;

import io.gravitee.am.gateway.handler.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.root.resources.handler.user.UserBodyRequestParseHandler;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.account.FormField;
import io.gravitee.am.model.oidc.Client;
import io.vertx.reactivex.core.MultiMap;
import io.vertx.reactivex.ext.web.RoutingContext;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static io.gravitee.am.gateway.handler.common.utils.ConstantKeys.EMAIL_PARAM_KEY;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class ForgotPasswordSubmissionRequestParseHandler extends UserBodyRequestParseHandler {

    private final Domain domain;

    public ForgotPasswordSubmissionRequestParseHandler(Domain domain) {
        super(Arrays.asList(EMAIL_PARAM_KEY));
        this.domain = domain;
    }

    @Override
    protected Optional<String> lookForMissingParameters(RoutingContext context, MultiMap params, List<String> requiredParams) {
        Optional<String> missingParam = super.lookForMissingParameters(context, params, requiredParams);
        AccountSettings accountSettings = AccountSettings.getInstance(domain, (Client)  context.get(ConstantKeys.CLIENT_CONTEXT_KEY));
        if (missingParam.isPresent() && accountSettings.isResetPasswordCustomForm()) {
            final List<String> alternativeParams = accountSettings.getResetPasswordCustomFormFields().stream().map(FormField::getKey).collect(Collectors.toList());
            missingParam = super.lookForMissingParameters(context, params, alternativeParams);
        }
        return missingParam;
    }

}
