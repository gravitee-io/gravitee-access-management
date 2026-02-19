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
package io.gravitee.am.repository.mongodb.management.internal.model;

import io.gravitee.am.model.TrustedIssuer;

import java.util.Map;

/**
 * MongoDB representation of TrustedIssuer.
 *
 * @author GraviteeSource Team
 */
public class TrustedIssuerMongo {

    private String issuer;
    private String keyResolutionMethod;
    private String jwksUri;
    private String certificate;
    private Map<String, String> scopeMappings;

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getKeyResolutionMethod() {
        return keyResolutionMethod;
    }

    public void setKeyResolutionMethod(String keyResolutionMethod) {
        this.keyResolutionMethod = keyResolutionMethod;
    }

    public String getJwksUri() {
        return jwksUri;
    }

    public void setJwksUri(String jwksUri) {
        this.jwksUri = jwksUri;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }

    public Map<String, String> getScopeMappings() {
        return scopeMappings;
    }

    public void setScopeMappings(Map<String, String> scopeMappings) {
        this.scopeMappings = scopeMappings;
    }

    public TrustedIssuer convert() {
        TrustedIssuer trustedIssuer = new TrustedIssuer();
        trustedIssuer.setIssuer(getIssuer());
        trustedIssuer.setKeyResolutionMethod(getKeyResolutionMethod());
        trustedIssuer.setJwksUri(getJwksUri());
        trustedIssuer.setCertificate(getCertificate());
        trustedIssuer.setScopeMappings(getScopeMappings());
        return trustedIssuer;
    }

    public static TrustedIssuerMongo convert(TrustedIssuer trustedIssuer) {
        if (trustedIssuer == null) {
            return null;
        }
        TrustedIssuerMongo mongo = new TrustedIssuerMongo();
        mongo.setIssuer(trustedIssuer.getIssuer());
        mongo.setKeyResolutionMethod(trustedIssuer.getKeyResolutionMethod());
        mongo.setJwksUri(trustedIssuer.getJwksUri());
        mongo.setCertificate(trustedIssuer.getCertificate());
        mongo.setScopeMappings(trustedIssuer.getScopeMappings());
        return mongo;
    }
}
