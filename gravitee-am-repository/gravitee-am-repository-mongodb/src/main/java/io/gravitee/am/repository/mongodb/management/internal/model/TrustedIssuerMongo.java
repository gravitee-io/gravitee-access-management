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
import lombok.Getter;
import lombok.Setter;

import java.util.Map;

/**
 * MongoDB representation of TrustedIssuer.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class TrustedIssuerMongo {

    private String issuer;
    private String keyResolutionMethod;
    private String jwksUri;
    private String certificate;
    private Map<String, String> scopeMappings;
    private boolean userBindingEnabled;
    private Map<String, String> userBindingMappings;

    public TrustedIssuer convert() {
        TrustedIssuer trustedIssuer = new TrustedIssuer();
        trustedIssuer.setIssuer(issuer);
        trustedIssuer.setKeyResolutionMethod(keyResolutionMethod);
        trustedIssuer.setJwksUri(jwksUri);
        trustedIssuer.setCertificate(certificate);
        trustedIssuer.setScopeMappings(scopeMappings);
        trustedIssuer.setUserBindingEnabled(userBindingEnabled);
        trustedIssuer.setUserBindingMappings(userBindingMappings);
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
        mongo.setUserBindingEnabled(trustedIssuer.isUserBindingEnabled());
        mongo.setUserBindingMappings(trustedIssuer.getUserBindingMappings());
        return mongo;
    }
}
