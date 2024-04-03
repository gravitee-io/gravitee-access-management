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

import io.gravitee.am.repository.mongodb.common.model.Auditable;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class PasswordPolicyMongo extends Auditable {
    private String id;
    private String referenceId;
    private String referenceType;
    private String name;
    private Integer minLength;
    private Integer maxLength;
    private Boolean includeNumbers;
    private Boolean includeSpecialCharacters;
    private Boolean lettersInMixedCase;
    private Integer maxConsecutiveLetters;
    private Boolean excludePasswordsInDictionary;
    private Boolean excludeUserProfileInfoInPassword;
    private Integer expiryDuration;
    private boolean passwordHistoryEnabled;
    private Short oldPasswords;
    private Boolean defaultPolicy;
}
