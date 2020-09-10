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
package io.gravitee.am.model;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum Template {

    LOGIN("login", "/login"),
    REGISTRATION("registration", "/register"),
    REGISTRATION_CONFIRMATION("registration_confirmation", "/confirmRegistration"),
    FORGOT_PASSWORD("forgot_password", "/forgotPassword"),
    RESET_PASSWORD("reset_password", "/resetPassword"),
    OAUTH2_USER_CONSENT("oauth2_user_consent", "/oauth/confirm_access"),
    MFA_ENROLL("mfa_enroll", "/mfa/enroll"),
    MFA_CHALLENGE("mfa_challenge", "/mfa/challenge"),
    BLOCKED_ACCOUNT("blocked_account", "/resetPassword"),
    COMPLETE_PROFILE("complete_profile", "/completeProfile"),
    WEBAUTHN_REGISTER("webauthn_register", "/webauthn/register"),
    WEBAUTHN_LOGIN("webauthn_login", "/webauthn/login"),
    ERROR("error", "/error");

    private final String template;
    private final String redirectUri;

    Template(String template, String redirectUri) {
        this.template = template;
        this.redirectUri = redirectUri;
    }

    public String template() {
        return template;
    }

    public String redirectUri() {
        return redirectUri;
    }

    public static Template parse(String toParse) {
        if(toParse==null || toParse.trim().isEmpty()) {
            throw new IllegalArgumentException("template must not be null");
        }
        List<Template> matchingTemplate = Arrays.stream(Template.values())
                .filter(template -> template.template().equals(toParse))
                .collect(Collectors.toList());

        if(matchingTemplate.size()==1) {
            return matchingTemplate.get(0);
        }

        throw new IllegalArgumentException("No template is matching");
    }
}
