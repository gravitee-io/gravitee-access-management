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
package io.gravitee.am.identityprovider.api.oidc;

import com.nimbusds.jose.util.JSONObjectUtils;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.text.ParseException;
import java.util.Map;
import java.util.Optional;

@Slf4j
@UtilityClass
public final class OpenIDConnectConfigurationUtils {

    public static Optional<String> extractCertificateId(String configuration) {
        if (configuration == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> cfg = JSONObjectUtils.parse(configuration);
            String certId = JSONObjectUtils.getString(cfg, "clientAuthenticationCertificate");
            return Optional.ofNullable(certId);
        } catch (ParseException e) {
            log.warn("Problem at parsing certificate configuration, msg={}", e.getMessage());
            return Optional.empty();
        }
    }
}
