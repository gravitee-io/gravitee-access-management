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
package io.gravitee.am.gateway.handler.jwt.impl;

import io.gravitee.am.common.jwt.JWT;
import io.gravitee.am.gateway.handler.jwt.JwtParser;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class JJwtParser implements JwtParser {

    private static final Logger logger = LoggerFactory.getLogger(JJwtParser.class);

    private io.jsonwebtoken.JwtParser jwtParser;

    public JJwtParser(io.jsonwebtoken.JwtParser jwtParser) {
        this.jwtParser = jwtParser;
    }

    @Override
    public JWT parse(String payload) {
        try {
            return new JWT(jwtParser.parseClaimsJws(payload).getBody());
        } catch (ExpiredJwtException ex) {
            logger.debug("The following JWT token : {} is expired", payload);
            throw new io.gravitee.am.common.jwt.exception.ExpiredJwtException("Token is expired", ex);
        } catch (MalformedJwtException ex) {
            logger.debug("The following JWT token : {} is malformed", payload);
            throw new io.gravitee.am.common.jwt.exception.MalformedJwtException("Token is malformed", ex);
        } catch (io.jsonwebtoken.security.SignatureException | UnsupportedJwtException ex) {
            logger.debug("Verifying JWT token signature : {} has failed", payload);
            throw new io.gravitee.am.common.jwt.exception.SignatureException("Token's signature is invalid", ex);
        } catch (Exception ex) {
            logger.error("An error occurs while parsing JWT token : {}", payload, ex);
            throw ex;
        }
    }


}
