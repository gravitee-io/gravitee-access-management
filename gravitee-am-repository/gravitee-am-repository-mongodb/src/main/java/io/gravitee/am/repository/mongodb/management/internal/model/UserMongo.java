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

import io.gravitee.am.model.UserIdentity;
import io.gravitee.am.model.factor.EnrolledFactor;
import io.gravitee.am.repository.mongodb.common.model.Auditable;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.AddressMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.AttributeMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.CertificateMongo;
import io.gravitee.am.repository.mongodb.management.internal.model.scim.ManagerMongo;
import lombok.Getter;
import lombok.Setter;
import org.bson.Document;
import org.bson.codecs.pojo.annotations.BsonId;

import java.util.Date;
import java.util.List;
import java.util.Objects;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@Getter
@Setter
public class UserMongo extends Auditable {
    // TODO [DP] class to remove
    @BsonId
    private String id;
    private String externalId;
    private String username;
    private String email;
    private String displayName;
    private String nickName;
    private String firstName;
    private String lastName;
    private String title;
    private String type;
    private String preferredLanguage;
    private boolean accountNonExpired = true;
    private Date accountLockedAt;
    private Date accountLockedUntil;
    private boolean accountNonLocked = true;
    private boolean credentialsNonExpired = true;
    private boolean enabled = true;
    private boolean internal;
    private boolean preRegistration;
    private boolean registrationCompleted;
    private Boolean newsletter;
    private String registrationUserUri;
    private String registrationAccessToken;
    private String referenceType;
    private String referenceId;
    private String source;
    private String client;
    private long loginsCount;
    private Date loggedAt;
    private Date lastLoginWithCredentials;
    private Date mfaEnrollmentSkippedAt;
    private Date lastPasswordReset;
    private Date lastLogoutAt;
    private Date lastUsernameReset;
    private List<AttributeMongo> emails;
    private List<AttributeMongo> phoneNumbers;
    private List<AttributeMongo> ims;
    private List<AttributeMongo> photos;
    private List<String> entitlements;
    private List<AddressMongo> addresses;
    private List<CertificateMongo> x509Certificates;
    private List<EnrolledFactor> factors;
    private List<String> roles;
    private List<String> dynamicRoles;
    private List<String> dynamicGroups;
    private List<UserIdentity> identities;
    private String lastIdentityUsed;
    private Boolean forceResetPassword;
    private Boolean serviceAccount;
    private String employeeNumber;
    private String costCenter;
    private String organization;
    private String division;
    private String department;
    private ManagerMongo manager;
    /**
     * Map codec support is planned for version 3.7 jira.mongodb.org issue: JAVA-2695
     */
    private Document additionalInformation;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserMongo userMongo = (UserMongo) o;

        return Objects.equals(id, userMongo.id);
    }

    @Override
    public int hashCode() {
        return id != null ? id.hashCode() : 0;
    }

}
