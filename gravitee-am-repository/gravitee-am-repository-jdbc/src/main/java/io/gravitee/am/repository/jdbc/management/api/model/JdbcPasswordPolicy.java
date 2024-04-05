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
package io.gravitee.am.repository.jdbc.management.api.model;

import lombok.Getter;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;
import java.util.List;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
@Table("password_policies")
public class JdbcPasswordPolicy {
    @Id
    private String id;
    @Column("reference_id")
    private String referenceId;
    @Column("reference_type")
    private String referenceType;
    private String name;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;
    @Column("min_length")
    private Integer minLength;
    @Column("max_length")
    private Integer maxLength;
    @Column("incl_numbers")
    private Boolean includeNumbers;
    @Column("incl_special_chars")
    private Boolean includeSpecialCharacters;
    @Column("letters_mixed_case")
    private Boolean lettersInMixedCase;
    @Column("max_consecutive_letters")
    private Integer maxConsecutiveLetters;
    @Column("exclude_pwd_in_dict")
    private Boolean excludePasswordsInDictionary;
    @Column("exclude_user_info_in_pwd")
    private Boolean excludeUserProfileInfoInPassword;
    @Column("expiry_duration")
    private Integer expiryDuration;
    @Column("password_history_enabled")
    private Boolean passwordHistoryEnabled;
    @Column("old_passwords")
    private Short oldPasswords;
    @Column("default_policy")
    private Boolean defaultPolicy;
}
