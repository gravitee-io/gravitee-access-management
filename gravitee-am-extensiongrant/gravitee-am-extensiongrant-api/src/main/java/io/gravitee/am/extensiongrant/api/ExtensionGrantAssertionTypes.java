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
package io.gravitee.am.extensiongrant.api;

import com.nimbusds.jose.Header;
import com.nimbusds.jose.JOSEObject;
import com.nimbusds.jose.JOSEObjectType;
import io.gravitee.am.common.jwt.JwtType;
import io.gravitee.am.common.oauth2.Parameters;
import io.gravitee.am.repository.oauth2.model.request.TokenRequest;

import java.text.ParseException;
import java.util.Map;

public final class ExtensionGrantAssertionTypes {

    private static final JOSEObjectType ID_JAG_TYPE = new JOSEObjectType(JwtType.ID_JAG.getValue());

    private ExtensionGrantAssertionTypes() {
    }

    public static boolean isIdJag(TokenRequest tokenRequest) {
        Map<String, String> parameters = tokenRequest.getRequestParameters();
        String assertion = parameters == null ? null : parameters.get(Parameters.ASSERTION);
        if (assertion == null) {
            return false;
        }
        try {
            return ID_JAG_TYPE.equals(Header.parse(JOSEObject.split(assertion)[0]).getType());
        } catch (ParseException e) {
            return false;
        }
    }

    public static boolean isNotIdJag(TokenRequest tokenRequest) {
        return !isIdJag(tokenRequest);
    }
}
