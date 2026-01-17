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

import io.gravitee.am.common.exception.oauth2.InvalidRequestException;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.tokenexchange.TokenExchangeExtensionGrantConfiguration;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

abstract class AbstractSamlSubjectTokenValidator implements SubjectTokenValidator {

    protected Document parseAssertion(String token) throws InvalidGrantException {
        try {
            byte[] decoded = decodeToken(token);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new ByteArrayInputStream(decoded));
        } catch (InvalidGrantException e) {
            throw e;
        } catch (Exception ex) {
            throw new InvalidGrantException("Invalid SAML assertion: " + ex.getMessage(), ex);
        }
    }

    private byte[] decodeToken(String token) throws InvalidGrantException {
        try {
            return Base64.getDecoder().decode(token);
        } catch (IllegalArgumentException e) {
            try {
                return Base64.getUrlDecoder().decode(token);
            } catch (IllegalArgumentException ex) {
                throw new InvalidGrantException("SAML assertion is not valid Base64");
            }
        }
    }

    protected void validateIssuer(String issuer, TokenExchangeExtensionGrantConfiguration configuration)
            throws InvalidGrantException {
        if (configuration.getTrustedIssuers() != null && !configuration.getTrustedIssuers().isEmpty()) {
            if (issuer == null || !configuration.getTrustedIssuers().contains(issuer)) {
                throw new InvalidGrantException("Untrusted issuer: " + issuer);
            }
        }
    }

    protected void validateTemporal(Date notBefore, Date notOnOrAfter) throws InvalidGrantException {
        Date now = new Date();
        if (notBefore != null && notBefore.after(now)) {
            throw new InvalidGrantException("SAML assertion is not yet valid");
        }
        if (notOnOrAfter != null && !notOnOrAfter.after(now)) {
            throw new InvalidGrantException("SAML assertion has expired");
        }
    }

    protected Date parseInstant(String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        try {
            Instant instant = Instant.parse(value);
            return Date.from(instant);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    protected Map<String, Object> baseClaims(String subject, String issuer) {
        Map<String, Object> claims = new HashMap<>();
        if (subject != null) {
            claims.put("sub", subject);
        }
        if (issuer != null) {
            claims.put("iss", issuer);
        }
        return claims;
    }

    protected Element getFirstChild(Element parent, String namespace, String localName) {
        if (parent == null) {
            return null;
        }
        for (Element child = getFirstElementChild(parent); child != null; child = getNextElementSibling(child)) {
            if ((namespace == null || namespace.equals(child.getNamespaceURI())) &&
                    localName.equals(child.getLocalName())) {
                return child;
            }
        }
        return null;
    }

    private Element getFirstElementChild(Element parent) {
        for (org.w3c.dom.Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element) {
                return (Element) node;
            }
        }
        return null;
    }

    private Element getNextElementSibling(Element element) {
        for (org.w3c.dom.Node node = element.getNextSibling(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element) {
                return (Element) node;
            }
        }
        return null;
    }

    protected void ensureSubjectPresent(String subject) {
        if (subject == null || subject.isEmpty()) {
            throw new InvalidRequestException("SAML assertion missing Subject");
        }
    }

    protected void ensureIssuerPresent(String issuer) {
        if (issuer == null || issuer.isEmpty()) {
            throw new InvalidRequestException("SAML assertion missing Issuer");
        }
    }
}
