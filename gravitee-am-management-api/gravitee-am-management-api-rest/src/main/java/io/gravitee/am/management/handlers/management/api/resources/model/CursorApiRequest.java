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
package io.gravitee.am.management.handlers.management.api.resources.model;

import java.util.Base64;

public record CursorApiRequest(String id, String lastSortValue) {
    private final static String SEPARATOR = "##";

    public String encode(){
        Base64.Encoder encoder = Base64.getEncoder();
        String value = "%s%s%s".formatted(id, SEPARATOR, lastSortValue);
        return encoder.encodeToString(value.getBytes());
    }

    public static CursorApiRequest decode(String value){
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Query parameter 'cursor' is required");
        }
        String decoded;
        try {
            decoded = new String(Base64.getDecoder().decode(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Query parameter 'cursor' is not valid", e);
        }
        String[] split = decoded.split(SEPARATOR);
        if (split.length != 2) {
            throw new IllegalArgumentException("Query parameter 'cursor' is not valid");
        }
        return new CursorApiRequest(split[0], split[1]);
    }
}
