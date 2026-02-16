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
package io.gravitee.am.identityprovider.facebook.model;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Facebook User claims
 *
 * See <a href="https://developers.facebook.com/docs/graph-api/reference/user">User reference</a></a>
 * See <a href="https://developers.facebook.com/docs/permissions/reference>Permission reference</a>
 * @author Jeoffrey HAEYAERT (jeoffrey.haeyaert at graviteesource.com)
 * @author GraviteeSource Team
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public abstract class FacebookUser {

    public static final String ID = "id";

    // Default Public Profile fields (see https://developers.facebook.com/docs/facebook-login/permissions/?locale=en_US#reference-default)
    public static final String FIRST_NAME = "first_name";
    public static final String LAST_NAME = "last_name";
    public static final String MIDDLE_NAME = "middle_name";
    public static final String NAME = "name";
    public static final String NAME_FORMAT = "name_format";
    public static final String PICTURE = "picture";
    public static final String SHORT_NAME = "short_name";

    // Email field (see https://developers.facebook.com/docs/facebook-login/permissions/?locale=en_US#reference-email)
    public static final String EMAIL = "email";

    // Age range (see https://developers.facebook.com/docs/facebook-login/permissions/?locale=en_US#reference-user_age_range)
    public static final String AGE_RANGE = "age_range";

    // Birthday field (see https://developers.facebook.com/docs/facebook-login/permissions/?locale=en_US#reference-user_birthday)
    public static final String BIRTHDAY = "birthday";

    // Gender field (see https://developers.facebook.com/docs/facebook-login/permissions/?locale=en_US#reference-user_gender)
    public static final String GENDER = "gender";

    // User Link field (see https://developers.facebook.com/docs/facebook-login/permissions/?locale=en_US#reference-user_link)
    public static final String LINK = "link";

    // Location fields (see https://developers.facebook.com/docs/facebook-login/permissions/?locale=en_US#reference-user_location)
    public static final String LOCATION = "location";

    // Other fields, but there are many others that can be added if necessary (see https://developers.facebook.com/docs/graph-api/reference/user).
    public static final String INSTALLED = "installed";
    public static final String IS_GUEST_USER = "is_guest_user";
    public static final String LANGUAGES = "languages";
    public static final String QUOTES = "quotes";
    public static final String SHARED_LOGIN_UPGRADE_REQUIRED_BY = "shared_login_upgrade_required_by";
    public static final String SIGNIFICANT_OTHER = "significant_other";
    public static final String SPORTS = "sports";
    public static final String SUPPORTS_DONATE_BUTTON_IN_LIVE_VIDEO = "supports_donate_button_in_live_video";

    public static final String LOCATION_STREET_FIELD = "street";
    public static final String LOCATION_CITY_FIELD = "city";
    public static final String LOCATION_REGION_FIELD = "region";
    public static final String LOCATION_ZIP_FIELD = "zip";
    public static final String LOCATION_COUNTRY_FIELD = "country";

    /**
     * Specific field param used to retrieve nested fields of 'location' field.
     */
    public static final String LOCATION_ALL_FIELDS_PARAM = LOCATION + "{" + LOCATION + "{" +
            String.join(",", LOCATION_STREET_FIELD, LOCATION_CITY_FIELD, LOCATION_REGION_FIELD, LOCATION_ZIP_FIELD, LOCATION_COUNTRY_FIELD) +
        "}"+"}";

    /**
     * Contains all useful fields we can retrieve and map to standard OIDC claims.
     */
    public static final List<String> BASE_FIELD_LIST = Arrays.asList(ID, FIRST_NAME, LAST_NAME, MIDDLE_NAME, NAME, NAME_FORMAT, PICTURE, SHORT_NAME,
            EMAIL, AGE_RANGE, BIRTHDAY, GENDER, LINK, LOCATION);

    /**
     * Contains all others fields we can retrieve and store as additional information.
     */
    public static final List<String> OTHER_FIELDS_LIST = Arrays.asList(INSTALLED, IS_GUEST_USER, LANGUAGES,
            QUOTES, SHARED_LOGIN_UPGRADE_REQUIRED_BY, SIGNIFICANT_OTHER, SPORTS, SUPPORTS_DONATE_BUTTON_IN_LIVE_VIDEO);

    /**
     * Contains all fields we have to retrieve from Facebook api.
     */
    public static final List<String> ALL_FIELDS_LIST = Stream.of(BASE_FIELD_LIST, OTHER_FIELDS_LIST).flatMap(Collection::stream).collect(Collectors.toList());

    /**
     * Contains all field names coma-separated.
     */
    public static final String ALL_FIELDS_PARAM = String.join(",", ALL_FIELDS_LIST).replace(LOCATION, LOCATION_ALL_FIELDS_PARAM);
}
