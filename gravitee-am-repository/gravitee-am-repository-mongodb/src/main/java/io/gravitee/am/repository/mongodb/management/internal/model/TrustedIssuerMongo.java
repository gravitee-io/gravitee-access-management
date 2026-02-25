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

import io.gravitee.am.model.KeyResolutionMethod;
import io.gravitee.am.model.TrustedIssuer;
import io.gravitee.am.repository.common.EnumParsingUtils;
import lombok.Getter;
import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * MongoDB representation of TrustedIssuer.
 *
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class TrustedIssuerMongo {

    private static final Logger log = LoggerFactory.getLogger(TrustedIssuerMongo.class);

    private String issuer;
    private String keyResolutionMethod;
    private String jwksUri;
    private String certificate;
    private Map<String, String> scopeMappings;
    private Boolean userBindingEnabled;
    private List<UserBindingCriterionMongo> userBindingCriteria;

    public TrustedIssuer convert() {
        KeyResolutionMethod method = EnumParsingUtils.safeValueOf(
                KeyResolutionMethod.class, getKeyResolutionMethod(), getIssuer(), "keyResolutionMethod", log);
        if (EnumParsingUtils.isUnknown(getKeyResolutionMethod(), method)) {
            EnumParsingUtils.logDiscard(getIssuer(), log, "incompatible keyResolutionMethod");
            return null;
        }
        TrustedIssuer ti = new TrustedIssuer();
        ti.setIssuer(getIssuer());
        ti.setKeyResolutionMethod(method);
        ti.setJwksUri(getJwksUri());
        ti.setCertificate(getCertificate());
        ti.setScopeMappings(getScopeMappings());
        ti.setUserBindingEnabled(Boolean.TRUE.equals(getUserBindingEnabled()));
        ti.setUserBindingCriteria(UserBindingCriterionMongo.toModelList(getUserBindingCriteria()));
        return ti;
    }

    public static TrustedIssuerMongo convert(TrustedIssuer ti) {
        if (ti == null) {
            return null;
        }
        TrustedIssuerMongo mongo = new TrustedIssuerMongo();
        mongo.setIssuer(ti.getIssuer());
        mongo.setKeyResolutionMethod(ti.getKeyResolutionMethod() != null ? ti.getKeyResolutionMethod().name() : null);
        mongo.setJwksUri(ti.getJwksUri());
        mongo.setCertificate(ti.getCertificate());
        mongo.setScopeMappings(ti.getScopeMappings());
        mongo.setUserBindingEnabled(ti.isUserBindingEnabled());
        mongo.setUserBindingCriteria(UserBindingCriterionMongo.fromModelList(ti.getUserBindingCriteria()));
        return mongo;
    }
}
