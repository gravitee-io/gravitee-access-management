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
import io.swagger.v3.oas.annotations.media.Schema;

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
    private String roleId;
    @Schema(type = "java.lang.Long")
    private Date createdAt;
    @Schema(type = "java.lang.Long")
    private Date updatedAt;
    private boolean fromRoleMapper = false;

    public Membership() {}

    public Membership(Membership other) {
        this.id = other.id;
        this.domain = other.domain;
        this.memberId = other.memberId;
        this.memberType = other.memberType;
        this.referenceId = other.referenceId;
        this.referenceType = other.referenceType;
        this.roleId = other.roleId;
        this.createdAt = other.createdAt;
        this.updatedAt = other.updatedAt;
        this.fromRoleMapper = other.fromRoleMapper;
    }

    public boolean isUserMember(String userId) {
        return this.isMember(MemberType.USER, userId);
    }

    public boolean isGroupMember(String groupId) {
        return this.isMember(MemberType.GROUP, groupId);
    }

    public boolean isMember(MemberType memberType, String memberId) {
        return this.memberType == memberType && this.memberId.equals(memberId);
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

    public String getRoleId() {
        return roleId;
    }

    public void setRoleId(String roleId) {
        this.roleId = roleId;
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

    public boolean isFromRoleMapper() {
        return fromRoleMapper;
    }

    public void setFromRoleMapper(boolean fromRoleMapper) {
        this.fromRoleMapper = fromRoleMapper;
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
                "\"role\":" + (roleId == null ? "null" : "\"" + roleId + "\"") + ", " +
                "\"createdAt\":" + (createdAt == null ? "null" : createdAt) + ", " +
                "\"updatedAt\":" + (updatedAt == null ? "null" : updatedAt) +
                "\"fromRoleMapper\":" + fromRoleMapper +
                "}";
    }
}
