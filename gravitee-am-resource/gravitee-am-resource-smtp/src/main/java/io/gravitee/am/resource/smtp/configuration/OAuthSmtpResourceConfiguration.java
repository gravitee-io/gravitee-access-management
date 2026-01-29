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
package io.gravitee.am.resource.smtp.configuration;

import io.gravitee.secrets.api.annotation.Secret;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class OAuthSmtpResourceConfiguration {

    private String oauth2Username;
    private String tokenEndpoint;
    private String oauth2ClientId;
    @Secret
    private String oauth2ClientSecret;
    @Secret
    private String oauth2RefreshToken;
    private String oauth2Scope;
}
