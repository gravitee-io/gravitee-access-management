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
package io.gravitee.am.management.handlers.management.api.sort;

import jakarta.ws.rs.ext.ParamConverter;
import jakarta.ws.rs.ext.Provider;

@Provider
public class SortParamConverter implements ParamConverter<SortParam> {

    @Override
    public SortParam fromString(String input) {
        if (input == null || input.isBlank()) {
            return SortParam.empty();
        }
        String[] parts = input.split("\\.");
        String asc = (parts.length > 1) ? parts[1] : "asc";
        return new SortParam(parts[0], "asc".equals(asc));
    }

    @Override
    public String toString(SortParam sortParam) {
        if(sortParam.isEmpty()) {
            return null;
        } else {
            return sortParam.getSortBy() + "." + (sortParam.isAscending() ? "asc" : "desc");
        }
    }
}
