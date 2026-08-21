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

import io.gravitee.am.model.oidc.TrustDomainTokenExchangeSettings;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

/**
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class TrustDomainTokenExchangeMongo {

    private String issuer;
    private Map<String, String> scopeMappings;
    private Boolean userBindingEnabled;
    private List<UserBindingCriterionMongo> userBindingCriteria;

    public TrustDomainTokenExchangeSettings convert() {
        return TrustDomainTokenExchangeSettings.builder()
                .issuer(getIssuer())
                .scopeMappings(getScopeMappings())
                .userBindingEnabled(Boolean.TRUE.equals(getUserBindingEnabled()))
                .userBindingCriteria(UserBindingCriterionMongo.toModelList(getUserBindingCriteria()))
                .build();
    }

    public static TrustDomainTokenExchangeMongo convert(TrustDomainTokenExchangeSettings settings) {
        if (settings == null) {
            return null;
        }
        TrustDomainTokenExchangeMongo mongo = new TrustDomainTokenExchangeMongo();
        mongo.setIssuer(settings.getIssuer());
        mongo.setScopeMappings(settings.getScopeMappings());
        mongo.setUserBindingEnabled(settings.isUserBindingEnabled());
        mongo.setUserBindingCriteria(UserBindingCriterionMongo.fromModelList(settings.getUserBindingCriteria()));
        return mongo;
    }
}
