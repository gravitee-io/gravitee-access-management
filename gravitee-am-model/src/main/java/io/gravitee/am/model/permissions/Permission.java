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
package io.gravitee.am.model.permissions;

import io.gravitee.am.model.Acl;
import io.gravitee.am.model.ReferenceType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public enum Permission {

    ORGANIZATION(ReferenceType.PLATFORM, ReferenceType.ORGANIZATION),
    ORGANIZATION_SETTINGS(ReferenceType.ORGANIZATION),
    ORGANIZATION_IDENTITY_PROVIDER(ReferenceType.ORGANIZATION),
    ORGANIZATION_AUDIT(ReferenceType.ORGANIZATION),
    ORGANIZATION_REPORTER(ReferenceType.ORGANIZATION),
    ORGANIZATION_SCOPE(ReferenceType.ORGANIZATION),
    ORGANIZATION_USER(ReferenceType.ORGANIZATION),
    ORGANIZATION_GROUP(ReferenceType.ORGANIZATION),
    ORGANIZATION_ROLE(ReferenceType.ORGANIZATION),
    ORGANIZATION_TAG(ReferenceType.ORGANIZATION),
    ORGANIZATION_ENTRYPOINT(ReferenceType.ORGANIZATION),
    ORGANIZATION_FORM(ReferenceType.ORGANIZATION),
    ORGANIZATION_MEMBER(ReferenceType.ORGANIZATION),

    ENVIRONMENT(ReferenceType.PLATFORM, ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT),

    DATA_PLANE(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT),

    DOMAIN(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_SETTINGS(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_FORM(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_EMAIL_TEMPLATE(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_EXTENSION_POINT(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_IDENTITY_PROVIDER(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_AUDIT(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_CERTIFICATE(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_USER(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_USER_DEVICE(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_GROUP(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_ROLE(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_SCIM(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_SCOPE(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_EXTENSION_GRANT(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_OPENID(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_SAML(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_UMA(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_UMA_SCOPE(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_REPORTER(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_MEMBER(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_ANALYTICS(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_FACTOR(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_RESOURCE(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_FLOW(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_ALERT(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_ALERT_NOTIFIER(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_BOT_DETECTION(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_DEVICE_IDENTIFIER(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_AUTHDEVICE_NOTIFIER(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_I18N_DICTIONARY(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),
    DOMAIN_THEME(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN),

    APPLICATION(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    APPLICATION_SETTINGS(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    APPLICATION_IDENTITY_PROVIDER(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    APPLICATION_FORM(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    APPLICATION_EMAIL_TEMPLATE(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    APPLICATION_OPENID(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    APPLICATION_SAML(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    APPLICATION_CERTIFICATE(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    APPLICATION_MEMBER(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    APPLICATION_FACTOR(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    APPLICATION_RESOURCE(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    APPLICATION_ANALYTICS(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    APPLICATION_FLOW(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    PROTECTED_RESOURCE(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    LICENSE_NOTIFICATION(ReferenceType.ORGANIZATION, ReferenceType.ENVIRONMENT, ReferenceType.DOMAIN, ReferenceType.APPLICATION),
    INSTALLATION(ReferenceType.PLATFORM);


    final List<ReferenceType> relevantTypes;

    Permission(ReferenceType... relevantTypes) {
        this.relevantTypes = Arrays.asList(relevantTypes);
    }

    public boolean isRelevantWith(ReferenceType referenceType) {

        return this.relevantTypes.contains(referenceType);
    }

    public static Map<Permission, Set<Acl>> of(Permission permission, Acl... acls) {

        Map<Permission, Set<Acl>> permissions = new EnumMap<>(Permission.class);
        permissions.put(permission, Acl.of(acls));

        return permissions;
    }

    public static Map<Permission, Set<Acl>> allPermissionAcls(ReferenceType referenceType) {

        Map<Permission, Set<Acl>> allPermissionAcls = new EnumMap<>(Permission.class);

        Stream.of(Permission.values())
                .filter(permission -> permission.relevantTypes.contains(referenceType))
                .forEach(permission -> allPermissionAcls.put(permission, Acl.all()));

        return allPermissionAcls;
    }

    public static List<Permission> allPermissions(ReferenceType referenceType) {

        return Stream.of(Permission.values())
                .filter(permission -> permission.relevantTypes.contains(referenceType)).toList();
    }

    public static List<String> flatten(Map<Permission, Set<Acl>> permissions) {

        List<String> flattenedPermissions = new ArrayList<>();

        if (permissions != null) {
            permissions.forEach((key, value) -> value.forEach(acl -> flattenedPermissions.add(key.name().toLowerCase() + "_" + acl.name().toLowerCase())));
        }

        return flattenedPermissions;
    }

    public static Map<Permission, Set<Acl>> unflatten(List<String> flatPermissions) {

        Map<Permission, Set<Acl>> permissions = new EnumMap<>(Permission.class);

        if (flatPermissions != null) {
            flatPermissions.stream().map(String::toUpperCase)
                    .forEach(flatPermission -> {
                        int i = flatPermission.lastIndexOf('_');
                        Acl acl = Acl.valueOf(flatPermission.substring(i + 1));
                        Permission permission = Permission.valueOf(flatPermission.substring(0, i));


                        if (permissions.containsKey(permission)) {
                            permissions.get(permission).add(acl);
                        } else {
                            HashSet<Acl> acls = new HashSet<>();
                            acls.add(acl);
                            permissions.put(permission, acls);
                        }
                    });
        }

        return permissions;
    }
}
