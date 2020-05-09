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
package io.gravitee.am.common.exception.oauth2;

import io.gravitee.common.http.HttpStatusCode;

/**
 * UMA 2.0 require a specific error code value as described
 * <a href="https://docs.kantarainitiative.org/uma/wg/rec-oauth-uma-federated-authz-2.0.html#reg-api">here</a>
 *
 * @author Alexandre FARIA (contact at alexandrefaria.net)
 * @author GraviteeSource Team
 */
public class MethodNotSupportedException extends OAuth2Exception {

    public MethodNotSupportedException() {
        super();
    }

    public MethodNotSupportedException(String message) {
        super(message);
    }

    @Override
    public String getOAuth2ErrorCode() {
        return "unsupported_method_type";
    }

    @Override
    public int getHttpStatusCode() {
        return HttpStatusCode.METHOD_NOT_ALLOWED_405;
    }
}
