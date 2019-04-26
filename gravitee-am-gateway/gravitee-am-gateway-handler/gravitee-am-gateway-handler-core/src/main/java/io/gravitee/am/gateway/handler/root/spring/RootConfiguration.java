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
package io.gravitee.am.gateway.handler.root.spring;

import io.gravitee.am.gateway.handler.api.ProtocolConfiguration;
import io.gravitee.am.gateway.handler.api.ProtocolProvider;
import io.gravitee.am.gateway.handler.common.jwt.JWTBuilder;
import io.gravitee.am.gateway.handler.common.jwt.JWTParser;
import io.gravitee.am.gateway.handler.common.jwt.impl.JJWTBuilder;
import io.gravitee.am.gateway.handler.common.jwt.impl.JJWTParser;
import io.gravitee.am.gateway.handler.root.RootProvider;
import io.gravitee.am.gateway.handler.root.service.user.UserService;
import io.gravitee.am.gateway.handler.root.service.user.impl.UserServiceImpl;
import io.jsonwebtoken.JwsHeader;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.security.Key;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Configuration
public class RootConfiguration implements ProtocolConfiguration {

    @Value("${jwt.kid:default-gravitee-AM-key}")
    private String kid;

    @Value("${jwt.secret:s3cR3t4grAv1t3310AMS1g1ingDftK3y}")
    private String signingKeySecret;

    @Value("${jwt.issuer:https://gravitee.am}")
    private String issuer;

    @Bean("managementUserService")
    public UserService userService() {
        return new UserServiceImpl();
    }

    @Bean
    public JWTParser jwtParser() {
        // jwt parser for token user registration
        JWTParser jwtParser = new JJWTParser(Jwts.parser().setSigningKey(key()));
        return jwtParser;
    }

    @Bean
    public JWTBuilder jwtBuilder() {
        // jwt builder for reset password
        JWTBuilder jwtBuilder = new JJWTBuilder(Jwts.builder().signWith(key()).setHeaderParam(JwsHeader.KEY_ID, kid).setIssuer(issuer));
        return jwtBuilder;
    }

    @Bean
    public Key key() {
        // HMAC key to sign/verify JWT used for email purpose
        Key key = Keys.hmacShaKeyFor(signingKeySecret.getBytes());
        return key;
    }

    @Bean
    public ProtocolProvider rootProvider() {
        return new RootProvider();
    }
}
