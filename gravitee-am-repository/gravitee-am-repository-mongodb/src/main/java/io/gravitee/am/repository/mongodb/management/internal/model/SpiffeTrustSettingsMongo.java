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

import io.gravitee.am.model.oidc.SpiffeTrustSettings;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * MongoDB representation of the SPIFFE block of a trusted domain.
 */
@Getter
@Setter
public class SpiffeTrustSettingsMongo {

    private String spiffeTrustDomain;
    private List<String> allowedAlgorithms;

    public SpiffeTrustSettings convert() {
        return SpiffeTrustSettings.builder()
                .spiffeTrustDomain(getSpiffeTrustDomain())
                .allowedAlgorithms(getAllowedAlgorithms())
                .build();
    }

    public static SpiffeTrustSettingsMongo convert(SpiffeTrustSettings settings) {
        if (settings == null) {
            return null;
        }
        SpiffeTrustSettingsMongo mongo = new SpiffeTrustSettingsMongo();
        mongo.setSpiffeTrustDomain(settings.getSpiffeTrustDomain());
        mongo.setAllowedAlgorithms(settings.getAllowedAlgorithms());
        return mongo;
    }
}
