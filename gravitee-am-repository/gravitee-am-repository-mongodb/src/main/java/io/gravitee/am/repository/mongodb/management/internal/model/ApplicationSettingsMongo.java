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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.repository.mongodb.management.internal.model.risk.RiskAssessmentSettingsMongo;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class ApplicationSettingsMongo {

    private ApplicationOAuthSettingsMongo oauth;
    private ApplicationSAMLSettingsMongo saml;
    private AccountSettingsMongo account;
    private LoginSettingsMongo login;
    private PostLoginActionMongo postLoginAction;
    private ApplicationAdvancedSettingsMongo advanced;
    private PasswordSettingsMongo passwordSettings;
    private MFASettingsMongo mfa;
    private CookieSettingsMongo cookieSettings;
    private RiskAssessmentSettingsMongo riskAssessment;
    private SecretSettingsMongo secretExpirationSettings;
}
