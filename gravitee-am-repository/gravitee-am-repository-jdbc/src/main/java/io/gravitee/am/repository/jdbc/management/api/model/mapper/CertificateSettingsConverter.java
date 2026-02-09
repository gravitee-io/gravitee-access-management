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
package io.gravitee.am.repository.jdbc.management.api.model.mapper;

import com.github.dozermapper.core.DozerConverter;
import io.gravitee.am.model.CertificateSettings;
import io.gravitee.am.repository.jdbc.provider.common.JSONMapper;

/**
 * @author GraviteeSource Team
 */
public class CertificateSettingsConverter extends DozerConverter<CertificateSettings, String> {
    public CertificateSettingsConverter() {
        super(CertificateSettings.class, String.class);
    }

    @Override
    public String convertTo(CertificateSettings certificateSettings, String s) {
        return JSONMapper.toJson(certificateSettings);
    }

    @Override
    public CertificateSettings convertFrom(String s, CertificateSettings certificateSettings) {
        return JSONMapper.toBean(s, CertificateSettings.class);
    }
}
