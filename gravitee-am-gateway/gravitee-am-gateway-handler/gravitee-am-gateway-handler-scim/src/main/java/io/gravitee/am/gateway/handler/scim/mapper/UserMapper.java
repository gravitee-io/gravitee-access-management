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
package io.gravitee.am.gateway.handler.scim.mapper;

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.oidc.idtoken.Claims;
import io.gravitee.am.common.utils.ConstantKeys;
import io.gravitee.am.gateway.handler.scim.model.*;
import io.gravitee.am.identityprovider.api.DefaultUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Optional.ofNullable;
import static java.util.stream.Collectors.toList;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserMapper {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserMapper.class);

    public static User convert(io.gravitee.am.model.User user, String baseUrl, boolean listing) {
        Map<String, Object> additionalInformation = user.getAdditionalInformation() != null ? user.getAdditionalInformation() : Collections.emptyMap();

        User scimUser = new GraviteeUser();
        scimUser.setSchemas(User.SCHEMAS);
        scimUser.setId(user.getId());
        scimUser.setExternalId(user.getExternalId());
        scimUser.setUserName(user.getUsername());

        Name name = new Name();
        name.setGivenName(user.getFirstName());
        name.setFamilyName(user.getLastName());
        name.setMiddleName(get(additionalInformation, StandardClaims.MIDDLE_NAME));
        scimUser.setName(name.isNull() ? null : name);
        scimUser.setDisplayName(user.getDisplayName());
        scimUser.setNickName(user.getNickName());

        scimUser.setProfileUrl(get(additionalInformation, StandardClaims.PROFILE));
        scimUser.setTitle(user.getTitle());
        scimUser.setUserType(user.getType());
        scimUser.setPreferredLanguage(user.getPreferredLanguage());
        scimUser.setLocale(get(additionalInformation, StandardClaims.LOCALE));
        scimUser.setTimezone(get(additionalInformation, StandardClaims.ZONEINFO));
        scimUser.setActive(user.isEnabled());
        scimUser.setEmails(toScimAttributes(user.getEmails()));
        // set primary email
        if (user.getEmail() != null) {
            Attribute attribute = new Attribute();
            attribute.setValue(user.getEmail());
            attribute.setPrimary(true);
            if (scimUser.getEmails() != null) {
                Optional<Attribute> optional = scimUser.getEmails().stream().filter(attribute1 -> attribute1.getValue().equals(attribute.getValue())).findFirst();
                if (optional.isEmpty()) {
                    scimUser.setEmails(Collections.singletonList(attribute));
                }
            } else {
                scimUser.setEmails(Collections.singletonList(attribute));
            }
        }
        scimUser.setPhoneNumbers(toScimAttributes(user.getPhoneNumbers()));
        scimUser.setIms(toScimAttributes(user.getIms()));
        scimUser.setPhotos(toScimAttributes(user.getPhotos()));
        scimUser.setAddresses(toScimAddresses(user.getAddresses()));
        scimUser.setEntitlements(user.getEntitlements());
        scimUser.setRoles(getRoles(user));
        scimUser.setX509Certificates(toScimCertificates(user.getX509Certificates()));

        // Meta
        Meta meta = new Meta();
        if (user.getCreatedAt() != null) {
            meta.setCreated(user.getCreatedAt().toInstant().toString());
        }
        if (user.getUpdatedAt() != null) {
            meta.setLastModified(user.getUpdatedAt().toInstant().toString());
        }
        meta.setResourceType(User.RESOURCE_TYPE);
        meta.setLocation(baseUrl + (listing ? "/" + scimUser.getId() : ""));
        scimUser.setMeta(meta);

        // Gravitee User extension schemas
        // To determine if the current user resource is a Gravitee User Resource
        // we remove every OpenID standard claims from the additionalInformation map
        // if we have remaining claims, the user can be set up as a Gravitee User Resource
        Map<String, Object> customClaims =
                additionalInformation
                        .entrySet()
                        .stream()
                        .filter(a -> !StandardClaims.claims().contains(a.getKey()) && !restrictedClaims().contains(a.getKey()))
                        .filter(a -> a.getValue() != null)
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (!customClaims.isEmpty()) {
            scimUser.setSchemas(GraviteeUser.SCHEMAS);
            ((GraviteeUser) scimUser).setAdditionalInformation(customClaims);
        }

        return scimUser;
    }

    private static List<String> getRoles(io.gravitee.am.model.User user) {
        return Stream.of(
                ofNullable(user.getRoles()).orElse(List.of()),
                ofNullable(user.getDynamicRoles()).orElse(List.of())
        ).flatMap(List::stream).sorted().distinct().collect(toList());
    }

    public static io.gravitee.am.model.User convert(User scimUser) {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        Map<String, Object> additionalInformation = new HashMap<>();
        if (scimUser.getExternalId() != null) {
            user.setExternalId(scimUser.getExternalId());
            additionalInformation.put(StandardClaims.SUB, scimUser.getExternalId());
        }
        user.setUsername(scimUser.getUserName());
        if (scimUser.getName() != null) {
            Name name = scimUser.getName();
            if (name.getGivenName() != null) {
                user.setFirstName(name.getGivenName());
                additionalInformation.put(StandardClaims.GIVEN_NAME, name.getGivenName());
            }
            if (name.getFamilyName() != null) {
                user.setLastName(name.getFamilyName());
                additionalInformation.put(StandardClaims.FAMILY_NAME, name.getFamilyName());
            }
            if (name.getMiddleName() != null) {
                additionalInformation.put(StandardClaims.MIDDLE_NAME, name.getMiddleName());
            }
        }
        user.setDisplayName(scimUser.getDisplayName());
        user.setNickName(scimUser.getNickName());
        if (scimUser.getProfileUrl() != null) {
            additionalInformation.put(StandardClaims.PROFILE, scimUser.getProfileUrl());
        }
        user.setTitle(scimUser.getTitle());
        user.setType(scimUser.getUserType());
        user.setPreferredLanguage(scimUser.getPreferredLanguage());
        if (scimUser.getLocale() != null) {
            additionalInformation.put(StandardClaims.LOCALE, scimUser.getLocale());
        }
        if (scimUser.getTimezone() != null) {
            additionalInformation.put(StandardClaims.ZONEINFO, scimUser.getTimezone());
        }
        user.setEnabled(scimUser.isActive());
        user.setPassword(scimUser.getPassword());
        if (scimUser.getEmails() != null && !scimUser.getEmails().isEmpty()) {
            List<Attribute> emails = scimUser.getEmails();
            user.setEmail(emails.stream().filter(attribute -> Boolean.TRUE.equals(attribute.isPrimary())).findFirst().orElse(emails.get(0)).getValue());
            user.setEmails(toModelAttributes(emails));
        }
        user.setPhoneNumbers(toModelAttributes(scimUser.getPhoneNumbers()));
        user.setIms(toModelAttributes(scimUser.getIms()));
        if (scimUser.getPhotos() != null && !scimUser.getPhotos().isEmpty()) {
            List<Attribute> photos = scimUser.getPhotos();
            additionalInformation.put(StandardClaims.PICTURE, photos.stream().filter(attribute -> Boolean.TRUE.equals(attribute.isPrimary())).findFirst().orElse(photos.get(0)).getValue());
            user.setPhotos(toModelAttributes(photos));
        }
        user.setAddresses(toModelAddresses(scimUser.getAddresses()));
        user.setEntitlements(scimUser.getEntitlements());
        user.setRoles(scimUser.getRoles());
        user.setX509Certificates(toModelCertificates(scimUser.getX509Certificates()));

        // set additional information
        if (scimUser instanceof GraviteeUser) {
            GraviteeUser graviteeUser = (GraviteeUser) scimUser;
            if (graviteeUser.getAdditionalInformation() != null) {
                additionalInformation.putAll(graviteeUser.getAdditionalInformation());
            }
        }
        user.setAdditionalInformation(additionalInformation);
        return user;
    }

    public static <T> T get(Map<String, Object> additionalInformation, String key) {
        if (!additionalInformation.containsKey(key)) {
            return null;
        }
        try {
            return (T) additionalInformation.get(key);
        } catch (ClassCastException e) {
            LOGGER.debug("An error occurs while retrieving {} information from user", key, e);
            return null;
        }
    }

    public static io.gravitee.am.identityprovider.api.User convert(io.gravitee.am.model.User user) {
        DefaultUser idpUser = new DefaultUser(user.getUsername());
        idpUser.setId(user.getExternalId());
        idpUser.setCredentials(user.getPassword());

        Map<String, Object> additionalInformation = new HashMap<>();
        if (user.getFirstName() != null) {
            idpUser.setFirstName(user.getFirstName());
            additionalInformation.put(StandardClaims.GIVEN_NAME, user.getFirstName());
        }
        if (user.getLastName() != null) {
            idpUser.setLastName(user.getLastName());
            additionalInformation.put(StandardClaims.FAMILY_NAME, user.getLastName());
        }
        if (user.getEmail() != null) {
            idpUser.setEmail(user.getEmail());
            additionalInformation.put(StandardClaims.EMAIL, user.getEmail());
        }
        if (user.getAdditionalInformation() != null) {
            user.getAdditionalInformation().forEach((k, v) -> additionalInformation.putIfAbsent(k, v));
        }
        idpUser.setAdditionalInformation(additionalInformation);
        return idpUser;
    }

    public static List<io.gravitee.am.model.scim.Attribute> toModelAttributes(List<Attribute> scimAttributes) {
        if (scimAttributes == null) {
            return null;
        }
        return scimAttributes
                .stream()
                .map(scimAttribute -> {
                    io.gravitee.am.model.scim.Attribute modelAttribute = new io.gravitee.am.model.scim.Attribute();
                    modelAttribute.setPrimary(scimAttribute.isPrimary());
                    modelAttribute.setValue(scimAttribute.getValue());
                    modelAttribute.setType(scimAttribute.getType());
                    return modelAttribute;
                }).collect(toList());
    }

    public static List<Attribute> toScimAttributes(List<io.gravitee.am.model.scim.Attribute> modelAttributes) {
        if (modelAttributes == null) {
            return null;
        }
        return modelAttributes
                .stream()
                .map(modelAttribute -> {
                    Attribute scimAttribute = new Attribute();
                    scimAttribute.setPrimary(modelAttribute.isPrimary());
                    scimAttribute.setValue(modelAttribute.getValue());
                    scimAttribute.setType(modelAttribute.getType());
                    return scimAttribute;
                }).collect(toList());
    }

    public static List<io.gravitee.am.model.scim.Address> toModelAddresses(List<Address> scimAddresses) {
        if (scimAddresses == null) {
            return null;
        }
        return scimAddresses
                .stream()
                .map(scimAddress -> {
                    io.gravitee.am.model.scim.Address modelAddress = new io.gravitee.am.model.scim.Address();
                    modelAddress.setType(scimAddress.getType());
                    modelAddress.setFormatted(scimAddress.getFormatted());
                    modelAddress.setStreetAddress(scimAddress.getStreetAddress());
                    modelAddress.setCountry(scimAddress.getCountry());
                    modelAddress.setLocality(scimAddress.getLocality());
                    modelAddress.setPostalCode(scimAddress.getPostalCode());
                    modelAddress.setRegion(scimAddress.getRegion());
                    modelAddress.setPrimary(scimAddress.isPrimary());
                    return modelAddress;
                }).collect(toList());
    }

    public static List<Address> toScimAddresses(List<io.gravitee.am.model.scim.Address> modelAddresses) {
        if (modelAddresses == null) {
            return null;
        }
        return modelAddresses
                .stream()
                .map(modelAddress -> {
                    Address scimAddress = new Address();
                    scimAddress.setType(modelAddress.getType());
                    scimAddress.setFormatted(modelAddress.getFormatted());
                    scimAddress.setStreetAddress(modelAddress.getStreetAddress());
                    scimAddress.setCountry(modelAddress.getCountry());
                    scimAddress.setLocality(modelAddress.getLocality());
                    scimAddress.setPostalCode(modelAddress.getPostalCode());
                    scimAddress.setRegion(modelAddress.getRegion());
                    scimAddress.setPrimary(modelAddress.isPrimary());
                    return scimAddress;
                }).collect(toList());
    }

    public static List<io.gravitee.am.model.scim.Certificate> toModelCertificates(List<Certificate> scimCertificates) {
        if (scimCertificates == null) {
            return null;
        }
        return scimCertificates
                .stream()
                .map(scimCertificate -> {
                    io.gravitee.am.model.scim.Certificate modelCertificate = new io.gravitee.am.model.scim.Certificate();
                    modelCertificate.setValue(scimCertificate.getValue());
                    return modelCertificate;
                }).collect(toList());
    }

    public static List<Certificate> toScimCertificates(List<io.gravitee.am.model.scim.Certificate> modelCertificates) {
        if (modelCertificates == null) {
            return null;
        }
        return modelCertificates
                .stream()
                .map(modelCertificate -> {
                    Certificate scimCertificate = new Certificate();
                    scimCertificate.setValue(modelCertificate.getValue());
                    return scimCertificate;
                }).collect(toList());
    }

    private static List<String> restrictedClaims() {
        return List.of(Claims.auth_time,
                ConstantKeys.OIDC_PROVIDER_ID_ACCESS_TOKEN_KEY,
                ConstantKeys.OIDC_PROVIDER_ID_TOKEN_KEY);
    }
}
