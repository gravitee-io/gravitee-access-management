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
package io.gravitee.am.model;

import io.gravitee.am.model.membership.MemberType;
import io.gravitee.am.model.membership.ReferenceType;

import java.util.Date;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class Membership {

    private String id;
    private String domain;
    private String memberId;
    private MemberType memberType;
    private String referenceId;
    private ReferenceType referenceType;
    private String role;
    private Date createdAt;
    private Date updatedAt;

    public Membership() {}

    public Membership(Membership other) {
        this.id = other.id;
        this.domain = other.domain;
        this.memberId = other.memberId;
        this.memberType = other.memberType;
        this.referenceId = other.referenceId;
        this.referenceType = other.referenceType;
        this.role = other.role;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public MemberType getMemberType() {
        return memberType;
    }

    public void setMemberType(MemberType memberType) {
        this.memberType = memberType;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public ReferenceType getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(ReferenceType referenceType) {
        this.referenceType = referenceType;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public String toString() {
        return "{\"_class\":\"Membership\", " +
                "\"id\":" + (id == null ? "null" : "\"" + id + "\"") + ", " +
                "\"domain\":" + (domain == null ? "null" : "\"" + domain + "\"") + ", " +
                "\"memberId\":" + (memberId == null ? "null" : "\"" + memberId + "\"") + ", " +
                "\"memberType\":" + (memberType == null ? "null" : memberType) + ", " +
                "\"referenceId\":" + (referenceId == null ? "null" : "\"" + referenceId + "\"") + ", " +
                "\"referenceType\":" + (referenceType == null ? "null" : referenceType) + ", " +
                "\"role\":" + (role == null ? "null" : "\"" + role + "\"") + ", " +
                "\"createdAt\":" + (createdAt == null ? "null" : createdAt) + ", " +
                "\"updatedAt\":" + (updatedAt == null ? "null" : updatedAt) +
                "}";
    }
}
