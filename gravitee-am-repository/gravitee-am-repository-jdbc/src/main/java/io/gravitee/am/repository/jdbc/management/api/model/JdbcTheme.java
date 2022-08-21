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

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@Table("themes")
public class JdbcTheme {
    @Id
    private String id;
    @Column("reference_id")
    private String referenceId;
    @Column("reference_type")
    private String referenceType;
    @Column("logo_url")
    private String logoUrl;
    @Column("logo_width")
    private int logoWidth;
    @Column("favicon_url")
    private String faviconUrl;
    @Column("primary_button_color")
    private String primaryButtonColorHex;
    @Column("secondary_button_color")
    private String secondaryButtonColorHex;
    @Column("primary_text_color")
    private String primaryTextColorHex;
    @Column("secondary_text_color")
    private String secondaryTextColorHex;
    private String css;
    @Column("created_at")
    private LocalDateTime createdAt;
    @Column("updated_at")
    private LocalDateTime updatedAt;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getReferenceId() {
        return referenceId;
    }

    public void setReferenceId(String referenceId) {
        this.referenceId = referenceId;
    }

    public String getReferenceType() {
        return referenceType;
    }

    public void setReferenceType(String referenceType) {
        this.referenceType = referenceType;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public int getLogoWidth() {
        return logoWidth;
    }

    public void setLogoWidth(int logoWidth) {
        this.logoWidth = logoWidth;
    }

    public String getFaviconUrl() {
        return faviconUrl;
    }

    public void setFaviconUrl(String faviconUrl) {
        this.faviconUrl = faviconUrl;
    }

    public String getPrimaryButtonColorHex() {
        return primaryButtonColorHex;
    }

    public void setPrimaryButtonColorHex(String primaryButtonColorHex) {
        this.primaryButtonColorHex = primaryButtonColorHex;
    }

    public String getSecondaryButtonColorHex() {
        return secondaryButtonColorHex;
    }

    public void setSecondaryButtonColorHex(String secondaryButtonColorHex) {
        this.secondaryButtonColorHex = secondaryButtonColorHex;
    }

    public String getPrimaryTextColorHex() {
        return primaryTextColorHex;
    }

    public void setPrimaryTextColorHex(String primaryTextColorHex) {
        this.primaryTextColorHex = primaryTextColorHex;
    }

    public String getSecondaryTextColorHex() {
        return secondaryTextColorHex;
    }

    public void setSecondaryTextColorHex(String secondaryTextColorHex) {
        this.secondaryTextColorHex = secondaryTextColorHex;
    }

    public String getCss() {
        return css;
    }

    public void setCss(String css) {
        this.css = css;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
