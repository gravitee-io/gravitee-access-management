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

import com.fasterxml.jackson.core.type.TypeReference;
import com.github.dozermapper.core.DozerConverter;
import io.gravitee.am.model.application.ApplicationSecretSettings;
import io.gravitee.am.repository.jdbc.common.JSONMapper;

import java.util.List;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AppSecretSettingsConverter extends DozerConverter<List, String> {

    public AppSecretSettingsConverter() {
        super(List.class, String.class);
    }

    @Override
    public String convertTo(List bean, String s) {
        return JSONMapper.toJson(bean.stream().map(secretSettings -> new JSONMapper.JdbcApplicationSecretSettings((ApplicationSecretSettings) secretSettings)).collect(Collectors.toList()));
    }

    @Override
    public List convertFrom(String s, List bean) {
        final List<JSONMapper.JdbcApplicationSecretSettings> settings = JSONMapper.toCollectionOfBean(s, new TypeReference<List<JSONMapper.JdbcApplicationSecretSettings>>(){});
        return settings != null ? settings.stream().map(jdbcSettings -> new ApplicationSecretSettings(jdbcSettings.getId(), jdbcSettings.getAlgorithm(), jdbcSettings.getProperties())).collect(Collectors.toList()) : null;
    }
}
