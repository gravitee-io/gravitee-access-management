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
package io.gravitee.am.gateway.handler.root.resources.endpoint.user.password;

import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.root.service.user.model.ForgotPasswordParameters;
import io.gravitee.am.model.account.AccountSettings;
import io.gravitee.am.model.account.FormField;
import io.gravitee.am.model.account.ForgotPasswordLookupField;
import io.vertx.rxjava3.ext.web.RoutingContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link ForgotPasswordParameters} from the HTTP request and account settings.
 *
 * @author GraviteeSource Team
 */
final class ForgotPasswordRequestMapper {

    private ForgotPasswordRequestMapper() {
    }

    static ForgotPasswordParameters toParameters(RoutingContext context, AccountSettings settings) {
        boolean customFormEnabled = settings != null && settings.isResetPasswordCustomForm();
        boolean confirmIdentityEnabled = settings != null && settings.isResetPasswordConfirmIdentity();
        Map<String, String> lookupValues = new LinkedHashMap<>();

        if (customFormEnabled) {
            List<FormField> fields = settings.getResetPasswordCustomFormFields();
            if (fields != null) {
                for (FormField field : fields) {
                    if (field == null || field.getKey() == null) {
                        continue;
                    }
                    String value = context.request().getParam(field.getKey());
                    if (value != null && !value.isBlank()) {
                        lookupValues.put(field.getKey(), value.trim());
                    }
                }
            }
        } else {
            String email = context.request().getParam(ConstantKeys.EMAIL_PARAM_KEY);
            if (email != null && !email.isBlank()) {
                lookupValues.put(ForgotPasswordLookupField.EMAIL, email.trim());
            }
        }

        return new ForgotPasswordParameters(lookupValues, customFormEnabled, confirmIdentityEnabled);
    }

    static String principalIdentifier(ForgotPasswordParameters parameters) {
        if (parameters.getEmail() != null) {
            return parameters.getEmail();
        }
        if (parameters.getUsername() != null) {
            return parameters.getUsername();
        }
        return parameters.getLookupValues().values().stream().findFirst().orElse(null);
    }
}
