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
import io.gravitee.am.extensiongrant.tokenexchange.TokenExchangeExtensionGrantConfiguration;
import io.reactivex.rxjava3.core.Single;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Validator for SAML 1.1 assertions (urn:ietf:params:oauth:token-type:saml1).
 */
public class Saml1SubjectTokenValidator extends AbstractSamlSubjectTokenValidator {

    private static final String NS = "urn:oasis:names:tc:SAML:1.0:assertion";

    @Override
    public Single<ValidatedToken> validate(String token, TokenExchangeExtensionGrantConfiguration configuration) throws InvalidGrantException {
        return Single.fromCallable(() -> doValidate(token, configuration));
    }

    private ValidatedToken doValidate(String token, TokenExchangeExtensionGrantConfiguration configuration) throws InvalidGrantException {
        Document document = parseAssertion(token);
        Element assertion = document.getDocumentElement();
        if (assertion == null || !"Assertion".equals(assertion.getLocalName()) || !NS.equals(assertion.getNamespaceURI())) {
            throw new InvalidGrantException("Unsupported SAML 1.1 assertion");
        }

        String issuer = assertion.getAttribute("Issuer");
        ensureIssuerPresent(issuer);
        validateIssuer(issuer, configuration);

        String subject = getSubject(assertion);
        ensureSubjectPresent(subject);

        Element conditions = getFirstChild(assertion, NS, "Conditions");
        validateTemporal(parseInstant(conditions != null ? conditions.getAttribute("NotBefore") : null),
                parseInstant(conditions != null ? conditions.getAttribute("NotOnOrAfter") : null));

        Set<String> audiences = extractAudiences(conditions);
        Map<String, Object> claims = baseClaims(subject, issuer);
        if (!audiences.isEmpty()) {
            claims.put("aud", audiences);
        }

        return ValidatedToken.builder()
                .subject(subject)
                .issuer(issuer)
                .claims(claims)
                .audience(audiences.stream().collect(Collectors.toList()))
                .tokenType(getSupportedTokenType())
                .build();
    }

    private String getSubject(Element assertion) {
        Element subjectEl = getFirstChild(assertion, NS, "Subject");
        Element nameId = subjectEl != null ? getFirstChild(subjectEl, NS, "NameIdentifier") : null;
        return nameId != null ? nameId.getTextContent() : null;
    }

    private Set<String> extractAudiences(Element conditions) {
        if (conditions == null) {
            return java.util.Collections.emptySet();
        }
        java.util.Set<String> audiences = new java.util.LinkedHashSet<>();
        for (Element restriction = getFirstChild(conditions, NS, "AudienceRestrictionCondition");
             restriction != null;
             restriction = getNextElementSibling(restriction)) {
            for (Element audience = getFirstChild(restriction, NS, "Audience");
                 audience != null;
                 audience = getNextElementSibling(audience)) {
                if (audience.getTextContent() != null && !audience.getTextContent().isEmpty()) {
                    audiences.add(audience.getTextContent());
                }
            }
        }
        return audiences;
    }

    private Element getNextElementSibling(Element element) {
        for (org.w3c.dom.Node node = element.getNextSibling(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element) {
                return (Element) node;
            }
        }
        return null;
    }

    @Override
    public String getSupportedTokenType() {
        return TokenTypeURN.SAML1;
    }
}
