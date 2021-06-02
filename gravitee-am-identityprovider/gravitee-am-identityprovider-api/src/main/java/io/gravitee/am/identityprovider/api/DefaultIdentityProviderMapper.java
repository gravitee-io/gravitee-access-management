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
package io.gravitee.am.identityprovider.api;

import io.gravitee.el.TemplateEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.Map;

import static io.gravitee.am.identityprovider.api.AuthenticationContext.CONTEXT_KEY_PROFILE;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class DefaultIdentityProviderMapper implements IdentityProviderMapper {
    public static final Logger LOGGER = LoggerFactory.getLogger(DefaultIdentityProviderMapper.class);

    private Map<String, String> mappers;

    public Map<String, String> getMappers() {
        return this.mappers;
    }

    public void setMappers(Map<String, String> mappers) {
        this.mappers = mappers;
    }

    public Map<String, Object> apply(AuthenticationContext context, Map<String, Object> userInfo) {
        if (this.mappers == null || this.mappers.isEmpty()) {
            return userInfo;
        }

        Map<String, Object> additionalInformation = new HashMap<>();

        TemplateEngine templateEngine = context.getTemplateEngine();
        if (templateEngine != null) {
            templateEngine.getTemplateContext().setVariable(CONTEXT_KEY_PROFILE, userInfo);
        }

        this.mappers.forEach((userClaim, attribute) -> {
            // if attribute uses the EL syntax, evaluate the expression
            String sanitizedAttr = StringUtils.isEmpty(attribute) ? attribute : attribute.trim();
            if (sanitizedAttr.startsWith("{") && sanitizedAttr.endsWith("}") && templateEngine != null) {
                try {
                    additionalInformation.put(userClaim, templateEngine.getValue(sanitizedAttr, String.class));
                } catch (Exception e) {
                    LOGGER.warn("User mapper can't evaluate the expression [{}] as String", userClaim);
                }
            } else {
                // attribute is a 'simple' key used to get value from the userInfo
                if (userInfo.containsKey(attribute)) {
                    additionalInformation.put(userClaim, userInfo.get(attribute));
                }
            }
        });
        return additionalInformation;
    }
}
