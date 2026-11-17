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
package io.gravitee.am.service.exception;

/**
 * The message names the Management API field; {@link #describe(String)} renders it with the caller's field name, such as the DCR member.
 */
public class InvalidApplicationAuthorizationDetailTypesException extends InvalidClientMetadataException {

    private static final String FIELD = "authorizationDetailTypes";

    public enum Reason {
        TYPE_REQUIRED,
        DUPLICATE_TYPE,
        UNKNOWN_TYPE
    }

    private final Reason reason;
    private final String type;

    public InvalidApplicationAuthorizationDetailTypesException(Reason reason, String type) {
        super(describe(FIELD, reason, type));
        this.reason = reason;
        this.type = type;
    }

    public String describe(String field) {
        return describe(field, reason, type);
    }

    private static String describe(String field, Reason reason, String type) {
        return field + ": " + switch (reason) {
            case TYPE_REQUIRED -> "type is required";
            case DUPLICATE_TYPE -> "type '" + type + "' appears more than once";
            case UNKNOWN_TYPE -> "type '" + type + "' is not an authorization detail type of the domain";
        };
    }
}
