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
package io.gravitee.am.gateway.handler.ciba.service.request;

import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import io.gravitee.am.common.exception.jwt.ExpiredJWTException;
import io.gravitee.am.common.exception.oauth2.ExpiredLoginHintTokenException;
import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.gateway.handler.common.jwt.SubjectManager;
import io.gravitee.am.gateway.handler.common.user.UserService;
import io.gravitee.am.gateway.handler.oauth2.service.request.AbstractRequestResolver;
import io.gravitee.am.gateway.handler.oidc.service.jwk.JWKService;
import io.gravitee.am.gateway.handler.oidc.service.jws.JWSService;
import io.gravitee.am.model.Domain;
import io.gravitee.am.model.oidc.Client;
import io.gravitee.am.repository.management.api.search.FilterCriteria;
import io.reactivex.rxjava3.core.Single;
import net.minidev.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;

import java.text.ParseException;
import java.time.Instant;
import java.util.Date;

import static io.gravitee.am.jwt.DefaultJWTParser.evaluateExp;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class CibaAuthenticationRequestResolver extends AbstractRequestResolver<CibaAuthenticationRequest> {
    private static final Logger LOGGER = LoggerFactory.getLogger(CibaAuthenticationRequestResolver.class);

    private final Domain domain;
    private final JWSService jwsService;
    private final JWKService jwkService;
    private final UserService userService;
    private final SubjectManager subjectManager;

    public CibaAuthenticationRequestResolver(Domain domain, JWSService jwsService, JWKService jwkService, UserService userService, SubjectManager subjectManager) {
        this.domain = domain;
        this.jwsService = jwsService;
        this.jwkService = jwkService;
        this.userService = userService;
        this.subjectManager = subjectManager;
    }

    public Single<CibaAuthenticationRequest> resolve(CibaAuthenticationRequest authRequest, Client client) {
        return resolveAuthorizedScopes(authRequest, client, null).flatMap(req -> {
            if (!StringUtils.isEmpty(req.getIdTokenHint())) {
                return parseJwt(req.getIdTokenHint()).flatMap(jwt -> {
                    if (jwt instanceof SignedJWT) {
                        return validateIdTokenHint(authRequest, (SignedJWT)jwt);
                    } else {
                        return Single.error(new InvalidRequestException("id_token_hint must be signed"));
                    }
                });
            } else if (!StringUtils.isEmpty(req.getLoginHintToken())) {
                return parseJwt(req.getLoginHintToken()).flatMap(jwt -> {
                    // Specification doesn't specify if this token have to be signed
                    if (jwt instanceof SignedJWT) {
                        return verifyLoginHintTokenSignature((SignedJWT)jwt, client)
                                .flatMap(verifiedJwt -> validateLoginHintToken(authRequest, verifiedJwt));
                    } else {
                        return validateLoginHintToken(authRequest, jwt);
                    }
                });
            } else {
                // login_hint is provided (look for username or email)
                final FilterCriteria criteria = new FilterCriteria();

                criteria.setQuoteFilterValue(true);
                criteria.setFilterName(authRequest.getLoginHint().contains("@") ? "email" : "username");
                criteria.setFilterValue(authRequest.getLoginHint());
                criteria.setOperator("eq");

                return userService.findByDomainAndCriteria(domain.getId(), criteria).map(users -> {
                    if (users.size() != 1) {
                        LOGGER.warn("login_hint match multiple users or no one");
                        throw new InvalidRequestException("Invalid hint");
                    }
                    authRequest.setUser(users.get(0));
                    authRequest.setSubject(users.get(0).getId());
                    return authRequest;
                });

            }
        });
    }

    private Single<JWT> parseJwt(String hint) {
        return Single.fromCallable(() -> {
            try {
                return JWTParser.parse(hint);
            } catch (ParseException e) {
                throw new InvalidRequestException("hint is not a valid JWT");
            }
        });
    }

    private Single<CibaAuthenticationRequest> validateIdTokenHint(CibaAuthenticationRequest authRequest, SignedJWT signedJwt) {
        // id_token_hint is the id_token generate by the OP for the client, so we use the JWKS provided by the domain.
        return jwkService.getKeys()
                .flatMapMaybe(jwks -> jwkService.getKey(jwks, signedJwt.getHeader().getKeyID()))
                .switchIfEmpty(Single.error(() -> new InvalidRequestException("JWK not found for id_token_hint")))
                .filter(jwk -> jwsService.isValidSignature(signedJwt, jwk))
                .switchIfEmpty(Single.error(() -> new InvalidRequestException("Invalid signature fo id_token_hint")))
                .flatMap(jwk -> {
                    try {
                        final Date expirationTime = signedJwt.getJWTClaimsSet().getExpirationTime();
                        if (expirationTime != null) {
                            evaluateExp(expirationTime.toInstant().getEpochSecond(), Instant.now(), 0);
                        }
                        return subjectManager.findUserBySub(new io.gravitee.am.common.jwt.JWT(signedJwt.getJWTClaimsSet().getClaims())).map(user -> {
                            authRequest.setUser(user);
                            authRequest.setSubject(user.getId());
                            return authRequest;
                        }).toSingle();
                    } catch (ExpiredJWTException e) {
                        return Single.error(new InvalidRequestException("id_token_hint expired"));
                    }
                });
    }

    private Single<JWT> verifyLoginHintTokenSignature(SignedJWT signedJwt, Client client) {
        // As the login_hint_token is provided by the client, we use the Client JWKS to validate the signature.
        // contrary to the id_token_hint that is the id_token generated by the OP for the client.
        return jwkService.getKeys(client)
                .flatMap(jwks -> jwkService.getKey(jwks, signedJwt.getHeader().getKeyID()))
                .switchIfEmpty(Single.error(() -> new InvalidRequestException("JWK not found for login_token_hint")))
                .filter(jwk -> jwsService.isValidSignature(signedJwt, jwk))
                .switchIfEmpty(Single.error(() -> new InvalidRequestException("Invalid signature fo login_token_hint")))
                .flatMap(__ -> Single.just(signedJwt));
    }

    private Single<CibaAuthenticationRequest> validateLoginHintToken(CibaAuthenticationRequest authRequest, JWT jwt) {
        try {
            final Date expirationTime = jwt.getJWTClaimsSet().getExpirationTime();
            if (expirationTime != null) {
                evaluateExp(expirationTime.toInstant().getEpochSecond(), Instant.now(), 0);
            }

            final JSONObject subIdObject = new JSONObject(jwt.getJWTClaimsSet().getJSONObjectClaim("sub_id"));
            /*
                sub_id is an object specifying the field identifying the user (through format entry)
                Supported format : email and username
                {
                  "sub_id": {
                    "format": "email",
                    "email": "user@acme.fr"
                  }
                }
             */
            final FilterCriteria criteria = new FilterCriteria();
            criteria.setQuoteFilterValue(false);
            final String field = subIdObject.getAsString("format");
            if (!"email".equals(field) && !"username".equals(field)) {
                return Single.error(new InvalidRequestException("Invalid hint, only email and username are supported"));
            }
            criteria.setFilterName(field);
            criteria.setFilterValue(subIdObject.getAsString(field));

            return userService.findByDomainAndCriteria(domain.getId(), criteria).flatMap(users -> {
                if (users.size() != 1) {
                    LOGGER.warn("login_hint_token match multiple users or no one");
                    return Single.error(new InvalidRequestException("Invalid hint"));
                }
                authRequest.setUser(users.get(0));
                authRequest.setSubject(users.get(0).getId());
                return Single.just(authRequest);
            });

        } catch (ExpiredJWTException e) {
            return Single.error(new ExpiredLoginHintTokenException("login_token_hint expired"));
        } catch (ParseException e) {
            // should never happen
            LOGGER.warn("login_hint_token can't be read", e);
            return Single.error(new ExpiredLoginHintTokenException("invalid login_token_hint"));
        }
    }
}
