/*
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

function createUUID() {
    var dt = new Date().getTime();
    return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function (c) {
        var r = (dt + Math.random() * 16) % 16 | 0;
        dt = Math.floor(dt / 16);
        return (c === 'x' ? r : (r & 0x3 | 0x8)).toString(16);
    });
}

// Set 'default' hrid on the default organization.
organization = db.getCollection('organizations').findOne({"_id": "DEFAULT"});

if (!organization.hrids || organization.hrids.length === 0) {
    organization.hrids = ['default'];
    db.getCollection('organizations').replaceOne({"_id": "DEFAULT"}, organization);
}

// Set 'default' hrid on the default environment.
environment = db.getCollection('environments').findOne({"_id": "DEFAULT"});

if (!environment.hrids || environment.hrids.length === 0) {
    environment.hrids = ['default'];
    db.getCollection('environments').replaceOne({"_id": "DEFAULT"}, environment);
}

// Update ORGANIZATION_USER role adding the ENVIRONMENT['LIST'] permission.
organizationUserRole = db.getCollection("roles")
    .findOne({"name": "ORGANIZATION_USER", "referenceType": "ORGANIZATION", "referenceId": "DEFAULT"});

if (organizationUserRole != null) {
    if (!organizationUserRole.permissionAcls['ENVIRONMENT']) {
        organizationUserRole.permissionAcls['ENVIRONMENT'] = ['LIST']
    } else if (!organizationUserRole.permissionAcls['ENVIRONMENT'].includes('LIST')) {
        organizationUserRole.permissionAcls['ENVIRONMENT'].push('LIST');
    }
    db.getCollection("roles").replaceOne({'_id': organizationUserRole._id}, organizationUserRole);
}

// Create ENVIRONMENT_USER role if not already exists.
environmentUserRole = db.getCollection("roles")
    .findOne({"name": "ENVIRONMENT_USER", "referenceType": "ORGANIZATION", "referenceId": "DEFAULT"});

if (environmentUserRole == null) {
    let environmentUserRoleId = createUUID();
    db.getCollection("roles").insert({
        _id: environmentUserRoleId,
        assignableType: "ENVIRONMENT",
        createdAt: ISODate(),
        defaultRole: true,
        name: "ENVIRONMENT_USER",
        permissionAcls: {
            DOMAIN: ["LIST"],
            ENVIRONMENT: ["READ"],
        },
        referenceId: "DEFAULT",
        referenceType: "ORGANIZATION",
        system: false,
        updatedAt: ISODate(),
    });

    // Assign ENVIRONMENT_USER role to all existing users.
    db.getCollection('users')
        .find({"referenceType": "ORGANIZATION", "referenceId": "DEFAULT"})
        .forEach(function (user) {
            db.getCollection("memberships").insert({
                "_id": createUUID(),
                "createdAt": ISODate(),
                "memberId": user._id,
                "memberType": "USER",
                "referenceId": "DEFAULT",
                "referenceType": "ENVIRONMENT",
                "role": environmentUserRoleId,
                "updatedAt": ISODate()
            });
        });
}

// Create ENVIRONMENT_OWNER role if not already exists.
environmentOwnerRole = db.getCollection("roles")
    .findOne({"name": "ENVIRONMENT_OWNER", "referenceType": "ORGANIZATION", "referenceId": "DEFAULT"});

if (environmentOwnerRole == null) {
    db.getCollection("roles").insert({
        _id: createUUID(),
        assignableType: "ENVIRONMENT",
        createdAt: ISODate(),
        defaultRole: true,
        name: "ENVIRONMENT_OWNER",
        permissionAcls: {
            DOMAIN_MEMBER: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_SCIM: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            APPLICATION_IDENTITY_PROVIDER: [
                "UPDATE",
                "LIST",
                "READ",
                "DELETE",
                "CREATE",
            ],
            APPLICATION_SETTINGS: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            APPLICATION: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            ENVIRONMENT: ["READ"],
            APPLICATION_EMAIL_TEMPLATE: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_REPORTER: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            APPLICATION_FORM: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_EXTENSION_GRANT: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_FORM: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_SCOPE: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_SETTINGS: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_ROLE: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_IDENTITY_PROVIDER: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_ANALYTICS: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_UMA_SCOPE: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_EXTENSION_POINT: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            APPLICATION_CERTIFICATE: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_GROUP: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_USER: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            APPLICATION_OPENID: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_AUDIT: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_FACTOR: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            APPLICATION_MEMBER: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_OPENID: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_CERTIFICATE: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_UMA: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            APPLICATION_FACTOR: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            DOMAIN_EMAIL_TEMPLATE: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
            APPLICATION_RESOURCE: ["UPDATE", "LIST", "READ", "DELETE", "CREATE"],
        },
        referenceId: "DEFAULT",
        referenceType: "ORGANIZATION",
        system: false,
        updatedAt: ISODate(),
    });
}

