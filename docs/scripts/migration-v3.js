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

// Migrate sharding tags to default organization. Need to attach them to directly to the default organization.
db.getCollection("tags").updateMany({}, {"$set": {"organizationId": "DEFAULT"}});

// Migrate admin identities to default organization.
db.getCollection("identities").updateMany({"domain": "admin"}, {
    "$unset": {"domain": ""},
    "$set": {"referenceId": "DEFAULT", "referenceType": "ORGANIZATION", "roleMapper": {}}
});

// Migrate system roles. System roles are now global and attached to the platform.
db.getCollection("roles").updateMany({"domain": "admin", "system": true}, {
    "$unset": {"domain": ""},
    "$set": {"referenceId": "PLATFORM", "referenceType": "PLATFORM"}
});

// Custom admin roles are useless in v2. Remove them.
db.getCollection("roles").remove({"domain": "admin", "system": false});

// Migrate admin groups to default organization.
db.getCollection("groups").updateMany({"domain": "admin"}, {
    "$unset": {"domain": ""},
    "$set": {"referenceId": "DEFAULT", "referenceType": "ORGANIZATION", "roles": []}
});

// Migrate admin forms to default organization.
db.getCollection("forms").updateMany({"domain": "admin"}, {
    "$unset": {"domain": ""},
    "$set": {"referenceId": "DEFAULT", "referenceType": "ORGANIZATION"}
});

// Migrate user consent forms content
db.getCollection("forms")
    .find({"template": "oauth2_user_consent"})
    .forEach(function (form) {
        var content = form.content.replace("@{authorize}","@{consent}");
        db.getCollection("forms").update({_id: form._id}, { "$set": { "content" : content } });
    });

// Admin reporters can be deleted in favor of internal reporter used for organization audits.
db.getCollection('reporters').remove({"domain": "admin"});

// Migrate admin users to default organization.
db.getCollection("users").updateMany({"domain": "admin"}, {
    "$unset": {"domain": ""},
    "$set": {"referenceId": "DEFAULT", "referenceType": "ORGANIZATION", "roles": []}
});

// Migrate admin domain to organization.
var adminDomain = db.getCollection("domains").findOne({"_id": "admin"});

if (adminDomain != null) {
    var organization = {
        "_id": "DEFAULT",
        "createdAt": ISODate(),
        "description": "Default organization",
        "domainRestrictions": [],
        "identities": adminDomain.identities,
        "name": "Default organization",
        "updatedAt": ISODate()
    };

    db.getCollection("organizations").update({"_id": "DEFAULT"}, organization, {"upsert": true});
}

// Migrate all other domains to default environment and remove useless loginForm field
db.getCollection("domains").updateMany({}, {
    "$unset": {"loginForm": "", "master": "", "identities": ""},
    "$set": {"referenceId": "DEFAULT", "referenceType": "ENVIRONMENT"}
});

// Moving to referenceType / referenceId
db.getCollection("identities").updateMany({}, {"$rename": {"domain": "referenceId"}});
db.getCollection("users").updateMany({}, {"$rename": {"domain": "referenceId"}});
db.getCollection("groups").updateMany({}, {"$rename": {"domain": "referenceId"}});
db.getCollection("roles").updateMany({}, {"$rename": {"domain": "referenceId"}});
db.getCollection("forms").updateMany({}, {"$rename": {"domain": "referenceId"}});

db.getCollection("identities").updateMany({"referenceType": {"$exists": false}}, {"$set": {"referenceType": "DOMAIN"}});
db.getCollection("users").updateMany({"referenceType": {"$exists": false}}, {"$set": {"referenceType": "DOMAIN"}});
db.getCollection("groups").updateMany({"referenceType": {"$exists": false}}, {"$set": {"referenceType": "DOMAIN"}});
db.getCollection("forms").updateMany({"referenceType": {"$exists": false}}, {"$set": {"referenceType": "DOMAIN"}});

// Migrate domain role permissions to oauthScopes.
db.getCollection("roles")
    .find({"referenceType": {"$exists": false}, "system": false})
    .forEach(function (role) {
        db.getCollection("roles").update({_id: role._id}, {
            "$set": {
                "referenceType": "DOMAIN",
                "oauthScopes": role.permissions,
                "permissionAcls": {}
            }
        });
    });

// Migrate admin audits to internal audits collection.
db.getCollection("reporter_audits_admin").renameCollection("reporter_audits");

var collectionNames = db.getCollectionNames();

// Migrate all audits, audit actors and audit targets.
for (index = 0; index < collectionNames.length; index++) {
    var collectionName = collectionNames[index];
    if (collectionName.startsWith("reporter_audits")) {
        // Audit
        db.getCollection(collectionName).updateMany({"domain": "admin"}, {
            "$unset": {"domain": ""},
            "$set": {"referenceId": "DEFAULT", "referenceType": "ORGANIZATION"}
        });
        db.getCollection(collectionName).updateMany({"domain": "system"}, {
            "$unset": {"domain": ""},
            "$set": {"referenceId": "PLATFORM", "referenceType": "PLATFORM"}
        });
        db.getCollection(collectionName).updateMany({"domain": {"$exists": true}}, {
            "$rename": {"domain": "referenceId"},
            "$set": {"referenceType": "DOMAIN"}
        });

        // Audit.actor
        db.getCollection(collectionName).updateMany({"actor.domain": "admin"}, {
            "$unset": {"actor.domain": ""},
            "$set": {"actor.referenceId": "DEFAULT", "actor.referenceType": "ORGANIZATION"}
        });
        db.getCollection(collectionName).updateMany({"actor.domain": "system"}, {
            "$unset": {"actor.domain": ""},
            "$set": {"actor.referenceId": "PLATFORM", "actor.referenceType": "PLATFORM"}
        });
        db.getCollection(collectionName).updateMany({"actor.domain": {"$exists": true}}, {
            "$rename": {"actor.domain": "actor.referenceId"},
            "$set": {"actor.referenceType": "DOMAIN"}
        });

        // Audit.actor admin
        db.getCollection(collectionName).updateMany({
                "actor.referenceType": {"$exists": false},
                "actor.referenceId": {"$exists": false},
                "actor.type": "USER",
                "actor.id": "admin"
            },
            {"$set": {"actor.referenceId": "DEFAULT", "actor.referenceType": "ORGANIZATION"}});

        // Audit.target
        db.getCollection(collectionName).updateMany({"target.domain": "admin"}, {
            "$unset": {"target.domain": ""},
            "$set": {"target.referenceId": "DEFAULT", "target.referenceType": "ORGANIZATION"}
        });
        db.getCollection(collectionName).updateMany({"target.domain": "system"}, {
            "$unset": {"target.domain": ""},
            "$set": {"target.referenceId": "PLATFORM", "target.referenceType": "PLATFORM"}
        });
        db.getCollection(collectionName).updateMany({"target.domain": {"$exists": true}}, {
            "$rename": {"target.domain": "target.referenceId"},
            "$set": {"target.referenceType": "DOMAIN"}
        });
    }
}

// Delete admin domain replaced by default organization.
db.getCollection("domains").remove({"_id": "admin"});
