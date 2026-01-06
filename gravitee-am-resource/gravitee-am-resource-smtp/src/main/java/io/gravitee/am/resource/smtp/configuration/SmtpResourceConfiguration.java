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

import io.gravitee.am.resource.api.ResourceConfiguration;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class SmtpResourceConfiguration implements ResourceConfiguration {

    private OAuthSmtpResourceConfiguration oauth2Configuration;

    public static final String AUTH_TYPE_BASIC = "basic";
    public static final String AUTH_TYPE_OAUTH2 = "oauth2";

    private String host;
    private int port;
    private String from;
    private String protocol;
    private boolean authentication;
    private String authenticationType = AUTH_TYPE_BASIC;
    private String username;
    private String password;
    private boolean startTls;
    private String sslTrust;
    private String sslProtocols;

    public boolean isOauth2Authentication() {
        return authentication && AUTH_TYPE_OAUTH2.equals(getEffectiveAuthenticationType());
    }


    public boolean isBasicAuthentication() {
        return authentication && AUTH_TYPE_BASIC.equals(getEffectiveAuthenticationType());
    }

    public String getEffectiveAuthenticationType() {
        // Default to "basic" for backward compatibility with old configs
        return authenticationType != null ? authenticationType : AUTH_TYPE_BASIC;
    }
}
