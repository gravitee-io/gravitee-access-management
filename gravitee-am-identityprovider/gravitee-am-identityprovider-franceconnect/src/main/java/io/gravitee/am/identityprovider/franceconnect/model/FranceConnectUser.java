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
package io.gravitee.am.identityprovider.franceconnect.model;

/**
 * France Connect User claims
 *
 * See <a href="https://partenaires.franceconnect.gouv.fr/fcp/fournisseur-service>Fournisseur de Service Particulier</a>
 *
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public abstract class FranceConnectUser {

    public static final String BIRTH_DATE = "birth_date";
    public static final String GENDER = "gender";
    public static final String BIRTH_PLACE = "birth_place";
    public static final String BIRTH_COUNTRY = "birth_country";
}
