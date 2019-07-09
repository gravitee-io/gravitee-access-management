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
package io.gravitee.am.gateway.handler.scim.service.impl;

import io.gravitee.am.common.oidc.StandardClaims;
import io.gravitee.am.common.utils.RandomString;
import io.gravitee.am.gateway.handler.common.auth.idp.IdentityProviderManager;
import io.gravitee.am.gateway.handler.scim.exception.SCIMException;
import io.gravitee.am.gateway.handler.scim.exception.UniquenessException;
import io.gravitee.am.gateway.handler.scim.model.*;
import io.gravitee.am.gateway.handler.scim.service.GroupService;
import io.gravitee.am.gateway.handler.scim.service.UserService;
import io.gravitee.am.identityprovider.api.DefaultUser;
import io.gravitee.am.model.Domain;
import io.gravitee.am.repository.management.api.UserRepository;
import io.gravitee.am.service.exception.*;
import io.reactivex.Completable;
import io.reactivex.Maybe;
import io.reactivex.Observable;
import io.reactivex.Single;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.*;
import java.util.stream.Collectors;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserServiceImpl implements UserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(UserServiceImpl.class);
    private static final String DEFAULT_IDP_PREFIX = "default-idp-";

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private GroupService groupService;

    @Autowired
    private Domain domain;

    @Autowired
    private IdentityProviderManager identityProviderManager;

    @Override
    public Single<ListResponse<User>> list(int page, int size, String baseUrl) {
        LOGGER.debug("Find users by domain: {}", domain.getId());
        return userRepository.findByDomain(domain.getId(), page, size)
                .flatMap(userPage -> {
                    // A negative value SHALL be interpreted as "0".
                    // A value of "0" indicates that no resource results are to be returned except for "totalResults".
                    if (size <= 0) {
                        return Single.just(new ListResponse<User>(null, userPage.getCurrentPage() + 1, userPage.getTotalCount(), 0));
                    } else {
                        // SCIM use 1-based index (increment current page)
                        return Observable.fromIterable(userPage.getData())
                                .map(user1 -> convert(user1, baseUrl, true))
                                // set groups
                                .flatMapSingle(user1 -> setGroups(user1))
                                .toList()
                                .map(users -> new ListResponse<>(users, userPage.getCurrentPage() + 1, userPage.getTotalCount(), users.size()));
                    }
                })
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find users by domain {}", domain, ex);
                    return Single.error(new TechnicalManagementException(String.format("An error occurs while trying to find users by domain %s", domain), ex));
                });
    }

    @Override
    public Maybe<User> get(String userId, String baseUrl) {
        LOGGER.debug("Find user by id : {}", userId);
        return userRepository.findById(userId)
                .map(user1 -> convert(user1, baseUrl, false))
                .flatMap(scimUser -> setGroups(scimUser).toMaybe())
                .onErrorResumeNext(ex -> {
                    LOGGER.error("An error occurs while trying to find a user using its ID", userId, ex);
                    return Maybe.error(new TechnicalManagementException(
                            String.format("An error occurs while trying to find a user using its ID: %s", userId), ex));
                });
    }

    @Override
    public Single<User> create(User user, String baseUrl) {
        LOGGER.debug("Create a new user {} for domain {}", user.getUserName(), domain.getName());

        // set user idp source
        final String source = user.getSource() == null ? DEFAULT_IDP_PREFIX + domain.getId() : user.getSource();

        // check if user is unique
        return userRepository.findByDomainAndUsernameAndSource(domain.getId(), user.getUserName(), source)
                .isEmpty()
                .map(isEmpty -> {
                    if (!isEmpty) {
                        throw new UniquenessException("User with username [" + user.getUserName()+ "] already exists");
                    }
                    return true;
                })
                .flatMapMaybe(irrelevant -> identityProviderManager.getUserProvider(source))
                .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(source)))
                .flatMapSingle(userProvider -> {
                    io.gravitee.am.model.User userModel = convert(user);
                    // set technical ID
                    userModel.setId(RandomString.generate());
                    userModel.setDomain(domain.getId());
                    userModel.setSource(source);
                    userModel.setInternal(true);
                    userModel.setCreatedAt(new Date());
                    userModel.setUpdatedAt(userModel.getCreatedAt());
                    userModel.setEnabled(userModel.getPassword() != null);

                    // store user in its identity provider
                    return userProvider.create(convert(userModel))
                            .flatMap(idpUser -> {
                                // AM 'users' collection is not made for authentication (but only management stuff)
                                // clear password
                                userModel.setPassword(null);
                                // set external id
                                userModel.setExternalId(idpUser.getId());
                                return userRepository.create(userModel);
                            })
                            .onErrorResumeNext(ex -> {
                                if (ex instanceof UserAlreadyExistsException) {
                                    return Single.error(new UniquenessException("User with username [" + user.getUserName()+ "] already exists"));
                                }
                                return Single.error(ex);
                            });
                })
                .map(user1 -> convert(user1, baseUrl, true))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof SCIMException || ex instanceof UserProviderNotFoundException) {
                        return Single.error(ex);
                    } else {
                        LOGGER.error("An error occurs while trying to create a user", ex);
                        return Single.error(new TechnicalManagementException("An error occurs while trying to create a user", ex));
                    }
                });
    }

    @Override
    public Single<User> update(String userId, User user, String baseUrl) {
        LOGGER.debug("Update a user {} for domain {}", user.getUserName(), domain.getName());
        return userRepository.findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapSingle(existingUser -> {
                    io.gravitee.am.model.User userToUpdate = convert(user);
                    // set immutable attribute
                    userToUpdate.setId(existingUser.getId());
                    userToUpdate.setExternalId(existingUser.getExternalId());
                    userToUpdate.setUsername(existingUser.getUsername());
                    userToUpdate.setDomain(existingUser.getDomain());
                    userToUpdate.setSource(existingUser.getSource());
                    userToUpdate.setCreatedAt(existingUser.getCreatedAt());
                    userToUpdate.setUpdatedAt(new Date());

                    return identityProviderManager.getUserProvider(userToUpdate.getSource())
                            .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(userToUpdate.getSource())))
                            .flatMapSingle(userProvider -> {
                                // no idp user check if we need to create it
                                if (userToUpdate.getExternalId() == null) {
                                    // if user set a password we can create the idp user
                                    if (userToUpdate.getPassword() != null) {
                                        return userProvider.create(convert(userToUpdate));
                                    } else {
                                        return Single.error(new UserInvalidException("No need to router idp user"));
                                    }
                                } else {
                                    return userProvider.update(userToUpdate.getExternalId(), convert(userToUpdate));
                                }
                            })
                            .flatMap(idpUser -> {
                                // AM 'users' collection is not made for authentication (but only management stuff)
                                // clear password
                                userToUpdate.setPassword(null);
                                // set external id
                                userToUpdate.setExternalId(idpUser.getId());
                                return userRepository.update(userToUpdate);
                            })
                            .onErrorResumeNext(ex -> {
                                if (ex instanceof UserNotFoundException || ex instanceof UserInvalidException) {
                                    // idp user does not exist, only update AM user
                                    // clear password
                                    userToUpdate.setPassword(null);
                                    return userRepository.update(userToUpdate);
                                }
                                return Single.error(ex);
                            });
                })
                .map(user1 -> convert(user1, baseUrl, false))
                // set groups
                .flatMap(user1 -> setGroups(user1))
                .onErrorResumeNext(ex -> {
                    if (ex instanceof AbstractManagementException || ex instanceof SCIMException) {
                        return Single.error(ex);
                    } else {
                        LOGGER.error("An error occurs while trying to update a user", ex);
                        return Single.error(new TechnicalManagementException("An error occurs while trying to update a user", ex));
                    }
                });
    }

    @Override
    public Completable delete(String userId) {
        LOGGER.debug("Delete user {}", userId);
        return userRepository.findById(userId)
                .switchIfEmpty(Maybe.error(new UserNotFoundException(userId)))
                .flatMapCompletable(user -> identityProviderManager.getUserProvider(user.getSource())
                        .switchIfEmpty(Maybe.error(new UserProviderNotFoundException(user.getSource())))
                        .flatMapCompletable(userProvider -> userProvider.delete(user.getExternalId()))
                        .andThen(userRepository.delete(userId))
                        .onErrorResumeNext(ex -> {
                            if (ex instanceof UserNotFoundException) {
                                // idp user does not exist, only remove AM user
                                return userRepository.delete(userId);
                            }
                            return Completable.error(ex);
                        })
                        .onErrorResumeNext(ex -> {
                            if (ex instanceof AbstractManagementException) {
                                return Completable.error(ex);
                            } else {
                                LOGGER.error("An error occurs while trying to delete user: {}", userId, ex);
                                return Completable.error(new TechnicalManagementException(
                                        String.format("An error occurs while trying to delete user: %s", userId), ex));
                            }
                        }));
    }


    private Single<User> setGroups(User scimUser) {
        // fetch groups
        return groupService.findByMember(scimUser.getId())
                .map(groups -> {
                    if (!groups.isEmpty()) {
                        List<Member> scimGroups = groups
                                .stream()
                                .map(group -> {
                                    Member member = new Member();
                                    member.setValue(group.getId());
                                    member.setDisplay(group.getDisplayName());
                                    return member;
                                }).collect(Collectors.toList());
                        scimUser.setGroups(scimGroups);
                        return scimUser;
                    } else {
                        return scimUser;
                    }
                });
    }

    private User convert(io.gravitee.am.model.User user, String baseUrl, boolean listing) {
        Map<String, Object> additionalInformation = user.getAdditionalInformation() != null ? user.getAdditionalInformation() : Collections.emptyMap();

        User scimUser = new User();
        scimUser.setSchemas(User.SCHEMAS);
        scimUser.setId(user.getId());
        scimUser.setExternalId(get(additionalInformation, StandardClaims.SUB, String.class));
        scimUser.setUserName(user.getUsername());

        Name name = new Name();
        name.setGivenName(user.getFirstName());
        name.setFamilyName(user.getLastName());
        name.setMiddleName(get(additionalInformation, StandardClaims.MIDDLE_NAME, String.class));
        scimUser.setName(name);
        scimUser.setDisplayName(user.getDisplayName());
        scimUser.setNickName(user.getNickName());

        scimUser.setProfileUrl(get(additionalInformation, StandardClaims.PROFILE, String.class));
        scimUser.setTitle(user.getTitle());
        scimUser.setUserType(user.getType());
        scimUser.setPreferredLanguage(user.getPreferredLanguage());
        scimUser.setLocale(get(additionalInformation, StandardClaims.LOCALE, String.class));
        scimUser.setTimezone(get(additionalInformation, StandardClaims.ZONEINFO, String.class));
        scimUser.setActive(user.isEnabled());
        scimUser.setEmails(toScimAttributes(user.getEmails()));
        // set primary email
        if (user.getEmail() != null) {
            Attribute attribute = new Attribute();
            attribute.setValue(user.getEmail());
            attribute.setPrimary(true);
            if (scimUser.getEmails() != null) {
                Optional<Attribute> optional = scimUser.getEmails().stream().filter(attribute1 -> attribute1.getValue().equals(attribute.getValue())).findFirst();
                if (!optional.isPresent()) {
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
        // TODO
        // scimUser.setRoles(user.getRoles());
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
        meta.setLocation(baseUrl + (listing ?  "/" + scimUser.getId() : ""));
        scimUser.setMeta(meta);
        return scimUser;
    }

    private io.gravitee.am.model.User convert(User scimUser) {
        io.gravitee.am.model.User user = new io.gravitee.am.model.User();
        Map<String, Object> additionalInformation = new HashMap();
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
        // password
        // if not password set disable account
        if (user.getPassword() == null && (scimUser.getPassword() == null || scimUser.getPassword().isEmpty())) {
            user.setEnabled(false);
        }
        if (user.getPassword() == null && (scimUser.getPassword() != null && !scimUser.getPassword().isEmpty())) {
            user.setPassword(scimUser.getPassword());
            user.setEnabled(true);
        }
        // we do not allow to set null password
        if (user.getPassword() != null && (scimUser.getPassword() == null || scimUser.getPassword().isEmpty())) {
            user.setEnabled(user.isEnabled());
            user.setPassword(user.getPassword());
        }
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
        // TODO
        // user.setRoles(scimUser.getRoles());
        user.setX509Certificates(toModelCertificates(scimUser.getX509Certificates()));

        // set additional information
        user.setAdditionalInformation(additionalInformation);
        return user;
    }

    private <T> T get(Map<String, Object> additionalInformation, String key, Class<T> valueType) {
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

    private io.gravitee.am.identityprovider.api.User convert(io.gravitee.am.model.User user) {
        DefaultUser idpUser = new DefaultUser(user.getUsername());
        idpUser.setId(user.getExternalId());
        idpUser.setCredentials(user.getPassword());

        Map<String, Object> additionalInformation = new HashMap<>();
        if (user.getFirstName() != null) {
            additionalInformation.put(StandardClaims.GIVEN_NAME, user.getFirstName());
        }
        if (user.getLastName() != null) {
            additionalInformation.put(StandardClaims.FAMILY_NAME, user.getLastName());
        }
        if (user.getEmail() != null) {
            additionalInformation.put(StandardClaims.EMAIL, user.getEmail());
        }
        if (user.getAdditionalInformation() != null) {
            user.getAdditionalInformation().forEach((k, v) -> additionalInformation.putIfAbsent(k, v));
        }
        idpUser.setAdditionalInformation(additionalInformation);
        return idpUser;
    }

    private List<io.gravitee.am.model.scim.Attribute> toModelAttributes(List<Attribute> scimAttributes) {
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
                }).collect(Collectors.toList());
    }

    private List<Attribute> toScimAttributes(List<io.gravitee.am.model.scim.Attribute> modelAttributes) {
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
                }).collect(Collectors.toList());
    }

    private List<io.gravitee.am.model.scim.Address> toModelAddresses(List<Address> scimAddresses) {
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
                }).collect(Collectors.toList());
    }

    private List<Address> toScimAddresses(List<io.gravitee.am.model.scim.Address> modelAddresses) {
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
                }).collect(Collectors.toList());
    }

    private List<io.gravitee.am.model.scim.Certificate> toModelCertificates(List<Certificate> scimCertificates) {
        if (scimCertificates == null) {
            return null;
        }
        return scimCertificates
                .stream()
                .map(scimCertificate -> {
                    io.gravitee.am.model.scim.Certificate modelCertificate = new io.gravitee.am.model.scim.Certificate();
                    modelCertificate.setValue(scimCertificate.getValue());
                    return modelCertificate;
                }).collect(Collectors.toList());
    }

    private List<Certificate> toScimCertificates(List<io.gravitee.am.model.scim.Certificate> modelCertificates) {
        if (modelCertificates == null) {
            return null;
        }
        return modelCertificates
                .stream()
                .map(modelCertificate -> {
                    Certificate scimCertificate = new Certificate();
                    scimCertificate.setValue(modelCertificate.getValue());
                    return scimCertificate;
                }).collect(Collectors.toList());
    }
}
