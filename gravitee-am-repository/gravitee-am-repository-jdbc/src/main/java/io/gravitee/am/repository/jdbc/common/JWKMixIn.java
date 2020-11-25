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
package io.gravitee.am.repository.jdbc.common;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.gravitee.am.model.jose.ECKey;
import io.gravitee.am.model.jose.OCTKey;
import io.gravitee.am.model.jose.OKPKey;
import io.gravitee.am.model.jose.RSAKey;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "kty")
@JsonSubTypes({
        // OCTKey has a lower case value.
        @JsonSubTypes.Type(value = OCTKey.class, name = "oct"),
        @JsonSubTypes.Type(value = OKPKey.class, name = "OKP"),
        @JsonSubTypes.Type(value = RSAKey.class, name = "RSA"),
        @JsonSubTypes.Type(value = ECKey.class, name = "EC")
})
/**
 * This Mixin allows to manage the JWK class hierarchy in order to Serialize/deserialize JWK
 * entry into the Application.settings.oauth configuration
 */
public class JWKMixIn {
}
