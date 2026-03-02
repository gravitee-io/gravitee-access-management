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
package io.gravitee.am.repository.mongodb.oauth2.internal.model;

import io.gravitee.am.repository.oauth2.api.TokenRepository;
import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

import java.util.Date;
import java.util.List;

@Getter
@Setter
public class TokenMongo {
    public static final String FIELD_JTI = "jti";
    public static final String FIELD_TYPE = "type";
    public static final String FIELD_EXPIRE_AT = "expire_at";
    public static final String FIELD_SUBJECT = "subject";
    public static final String FIELD_PARENT_SUBJECT_JTI = "parent_subject_jti";
    public static final String FIELD_PARENT_ACTOR_JTI = "parent_actor_jti";
    public static final String FIELD_PARENT_JTIS = "parent_jtis";
    public static final String FIELD_AUTHORIZATION_CODE = "authorization_code";

    @BsonId
    private String id;

    @BsonProperty(FIELD_TYPE)
    private TokenRepository.TokenType type;

    @BsonProperty(FIELD_JTI)
    private String jti;

    @BsonProperty("domain")
    private String domainId;

    @BsonProperty("client")
    private String clientId;

    @BsonProperty(FIELD_SUBJECT)
    private String subject;

    @BsonProperty(FIELD_AUTHORIZATION_CODE)
    private String authorizationCode;

    @BsonProperty("refresh_token_jti")
    private String refreshTokenJti;

    @BsonProperty("created_at")
    private Date createdAt;

    @BsonProperty(FIELD_EXPIRE_AT)
    private Date expireAt;

    @BsonProperty(FIELD_PARENT_SUBJECT_JTI)
    private String parentSubjectJti;

    @BsonProperty(FIELD_PARENT_ACTOR_JTI)
    private String parentActorJti;

    @BsonProperty(FIELD_PARENT_JTIS)
    private List<String> parentJtis;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TokenMongo that = (TokenMongo) o;

        return id.equals(that.id);
    }

    @Override
    public int hashCode() {
        return id.hashCode();
    }
}
