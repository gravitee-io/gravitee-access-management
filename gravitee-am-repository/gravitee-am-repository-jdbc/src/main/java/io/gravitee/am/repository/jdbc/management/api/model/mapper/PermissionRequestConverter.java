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
import io.gravitee.am.model.uma.PermissionRequest;
import io.gravitee.am.repository.jdbc.common.JSONMapper;

import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class PermissionRequestConverter extends DozerConverter<List, String> {

    public PermissionRequestConverter() {
        super(List.class, String.class);
    }

    @Override
    public String convertTo(List bean, String s) {
        return JSONMapper.toJson(bean);
    }

    @Override
    public List convertFrom(String s, List bean) {
        return JSONMapper.toCollectionOfBean(s, new TypeReference<List<PermissionRequest>>(){});
    }
}
