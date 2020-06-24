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

import io.gravitee.am.common.exception.oauth2.OAuth2Exception;

/**
 * If the permission request contains not found/invalid resource_id or resource_scopes, it must fail with a 400 error
 * and the corresponding error_code & description.
 *
 * https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-federated-authz-2.0.html#rfc.section.4.3
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 */
public class InvalidPermissionRequestException extends OAuth2Exception {

    private String errorCode;

    private InvalidPermissionRequestException() {
        super();
    }

    private InvalidPermissionRequestException(String message) {
        super(message);
    }

    private InvalidPermissionRequestException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    @Override
    public String getOAuth2ErrorCode() {
        return errorCode;
    }

    public static final InvalidPermissionRequestException INVALID_RESOURCE_OWNER = new InvalidPermissionRequestException(
            "invalid_resource_id", "Requested resources permissions must all belong to a same resource owner"
    );

    public static final InvalidPermissionRequestException INVALID_RESOURCE_ID = new InvalidPermissionRequestException(
            "invalid_resource_id", "At least one of the provided resource identifiers was not found"
    );

    public static final InvalidPermissionRequestException INVALID_SCOPE_RESOURCE = new InvalidPermissionRequestException(
            "invalid_scope", "At least one of the scopes included in the request was not registered previously by this resource server for the referenced resource."
    );
}
