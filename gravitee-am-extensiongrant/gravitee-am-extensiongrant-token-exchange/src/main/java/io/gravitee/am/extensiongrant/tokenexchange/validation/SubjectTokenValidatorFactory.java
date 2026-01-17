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
package io.gravitee.am.extensiongrant.tokenexchange.validation;

import io.gravitee.am.common.oauth2.TokenTypeURN;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Factory for creating and managing subject token validators.
 *
 * This factory provides the appropriate validator based on the token type URI,
 * supporting the token types defined in RFC 8693.
 *
 * @see <a href="https://datatracker.ietf.org/doc/html/rfc8693#section-3">RFC 8693 Section 3 - Token Type Identifiers</a>
 * @author GraviteeSource Team
 */
public class SubjectTokenValidatorFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(SubjectTokenValidatorFactory.class);

    private final Map<String, SubjectTokenValidator> validators;

    public SubjectTokenValidatorFactory() {
        this.validators = new HashMap<>();
        registerDefaultValidators();
    }

    /**
     * Register the default validators for standard token types.
     */
    private void registerDefaultValidators() {
        // JWT validator
        JwtSubjectTokenValidator jwtValidator = new JwtSubjectTokenValidator();
        validators.put(TokenTypeURN.JWT, jwtValidator);

        // ID Token validator (uses JWT validator with different token type)
        JwtSubjectTokenValidator idTokenValidator = new JwtSubjectTokenValidator(TokenTypeURN.ID_TOKEN);
        validators.put(TokenTypeURN.ID_TOKEN, idTokenValidator);

        // Access Token validator
        AccessTokenValidator accessTokenValidator = new AccessTokenValidator();
        validators.put(TokenTypeURN.ACCESS_TOKEN, accessTokenValidator);

        // Refresh Token validator
        RefreshTokenValidator refreshTokenValidator = new RefreshTokenValidator();
        validators.put(TokenTypeURN.REFRESH_TOKEN, refreshTokenValidator);

        // SAML validators
        validators.put(TokenTypeURN.SAML2, new Saml2SubjectTokenValidator());
        validators.put(TokenTypeURN.SAML1, new Saml1SubjectTokenValidator());

        LOGGER.info("Registered {} subject token validators: {}", validators.size(), validators.keySet());
    }

    /**
     * Get a validator for the specified token type.
     *
     * @param tokenType the token type URI
     * @return the appropriate validator
     * @throws InvalidGrantException if no validator is available for the token type
     */
    public SubjectTokenValidator getValidator(String tokenType) throws InvalidGrantException {
        SubjectTokenValidator validator = validators.get(tokenType);

        if (validator == null) {
            // Try to find a validator that supports this token type
            for (SubjectTokenValidator v : validators.values()) {
                if (v.supports(tokenType)) {
                    validator = v;
                    break;
                }
            }
        }

        if (validator == null) {
            throw new InvalidGrantException("Unsupported token type: " + tokenType);
        }

        return validator;
    }

    /**
     * Register a custom validator for a token type.
     *
     * @param tokenType the token type URI
     * @param validator the validator to register
     */
    public void registerValidator(String tokenType, SubjectTokenValidator validator) {
        validators.put(tokenType, validator);
        LOGGER.info("Registered custom validator for token type: {}", tokenType);
    }

    /**
     * Check if a validator is available for the specified token type.
     *
     * @param tokenType the token type URI
     * @return true if a validator is available
     */
    public boolean hasValidator(String tokenType) {
        if (validators.containsKey(tokenType)) {
            return true;
        }

        // Check if any validator supports this token type
        for (SubjectTokenValidator v : validators.values()) {
            if (v.supports(tokenType)) {
                return true;
            }
        }

        return false;
    }

    /**
     * Get all supported token types.
     *
     * @return a set of supported token type URIs
     */
    public java.util.Set<String> getSupportedTokenTypes() {
        return validators.keySet();
    }
}
