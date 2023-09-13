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
package io.gravitee.am.jwt;

import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.PlainJWT;
import io.gravitee.am.common.exception.jwt.MalformedJWTException;
import io.gravitee.am.common.jwt.JWT;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.ParseException;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class NoJWTBuilder implements JWTBuilder {

    private static final Logger logger = LoggerFactory.getLogger(NoJWTBuilder.class);

    @Override
    public String sign(JWT payload) {
        try {
            return new PlainJWT(JWTClaimsSet.parse(payload)).serialize();
        } catch (ParseException e) {
            logger.debug("Signing JWT token: {} has failed", payload);
            throw new MalformedJWTException("Failed to encode JWT token", e);
        }
    }
}
