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
db.getCollection("tags").updateMany({}, { "$set": { "organizationId": "DEFAULT" }});

// Migrate admin identities to default organization.
db.getCollection("identities").updateMany({ "domain": "admin" }, { "$set": { "referenceId": "DEFAULT", "referenceType" : "ORGANIZATION" }});
db.getCollection("identities").updateMany({ "referenceId": "admin", "referenceType": "DOMAIN" }, { "$set": { "referenceId": "DEFAULT", "referenceType" : "ORGANIZATION" }});

// Migrate system roles. System roles are now global and attached to the platform.
db.getCollection("roles").update({ "domain": "admin", "system": true }, { "$unset": { "domain": "" }, "$set": { "referenceId": "PLATFORM", "referenceType" : "PLATFORM" }});
db.getCollection("roles").update({ "referenceId": "admin", "referenceType": "DOMAIN", "system": true }, { "$unset": { "domain": "" }, "$set": { "referenceId": "PLATFORM", "referenceType" : "PLATFORM" }});

// Migrate custom admin roles to default organization.
db.getCollection("roles").update({ "domain": "admin", "system": false }, { "$set": { "referenceId": "DEFAULT", "referenceType" : "ORGANIZATION" }});
db.getCollection("roles").update({ "referenceId": "admin", "referenceType": "DOMAIN", "system": false }, { "$set": { "referenceId": "DEFAULT", "referenceType" : "ORGANIZATION" }});
// Migrate admin groups to default organization.
db.getCollection("groups").update({ "domain": "admin" }, { "$set": { "referenceId": "DEFAULT", "referenceType" : "ORGANIZATION" }});
db.getCollection("groups").update({ "referenceId": "admin", "referenceType": "DOMAIN" }, { "$set": { "referenceId": "DEFAULT", "referenceType" : "ORGANIZATION" }});

// Migrate admin forms to default organization.
db.getCollection("forms").update({ "domain": "admin" }, { "$set": { "referenceId": "DEFAULT", "referenceType" : "ORGANIZATION" }});
db.getCollection("forms").update({ "referenceId": "admin", "referenceType": "DOMAIN" }, { "$set": { "referenceId": "DEFAULT", "referenceType" : "ORGANIZATION" }});

// Admin reporters can be deleted in favor of internal reporter used for organization audits.
db.getCollection('reporters').remove({ "domain": "admin" });

// Migrate admin users to default organization.
db.getCollection("users").update({ "domain": "admin" }, { "$set": { "referenceId": "DEFAULT", "referenceType" : "ORGANIZATION" }});
db.getCollection("users").update({ "referenceId": "admin", "referenceType": "DOMAIN" }, { "$set": { "referenceId": "DEFAULT", "referenceType" : "ORGANIZATION" }});

// Migrate admin domain to organization.
var adminDomain = db.getCollection("domains").findOne({ "_id" : "admin"});

if(adminDomain != null) {
    var organization = {
        "_id" : "DEFAULT",
        "createdAt" : ISODate(),
        "description" : "Default organization",
        "domainRestrictions" : [],
        "identities" : adminDomain.identities,
        "name" : "Default organization",
        "updatedAt" : ISODate()
    };

    db.getCollection("organizations").update({ "_id": "DEFAULT"}, organization, { "upsert": true });
}

// Moving to referenceType / referenceId
db.getCollection("identities").updateMany({}, { "$rename": { "domain": "referenceId" }});
db.getCollection("users").updateMany({}, { "$rename": { "domain": "referenceId" }});
db.getCollection("groups").updateMany({}, { "$rename": { "domain": "referenceId" }});
db.getCollection("roles").updateMany({}, { "$rename": { "domain": "referenceId" }});
db.getCollection("forms").updateMany({}, { "$rename": { "domain": "referenceId" }});
db.getCollection("reporters").updateMany({}, { "$rename": { "domain": "referenceId" }});

db.getCollection("identities").updateMany({ "referenceType": { "$exists": false }}, { "$set": { "referenceType" : "DOMAIN" }});
db.getCollection("users").updateMany({ "referenceType": { "$exists": false }}, { "$set": { "referenceType" : "DOMAIN" }});
db.getCollection("groups").updateMany({ "referenceType": { "$exists": false }}, { "$set": { "referenceType" : "DOMAIN" }});
db.getCollection("roles").updateMany({ "referenceType": { "$exists": false }, "system": false}, { "$set": { "referenceType" : "DOMAIN" }});
db.getCollection("forms").updateMany({ "referenceType": { "$exists": false }}, { "$set": { "referenceType" : "DOMAIN" }});
db.getCollection("reporters").updateMany({ "referenceType": { "$exists": false }}, { "$set": { "referenceType" : "DOMAIN" }});

var collectionNames = db.getCollectionNames();

for (index = 0; index < collectionNames.length; index++) {
    var collectionName = collectionNames[index];
    if(collectionName !== "reporter_audits_admin" && collectionName.startsWith("reporter_audits")) {
        db.getCollection(collectionName).updateMany({}, { "$rename": { "domain": "referenceId" }, "$set": { "referenceType" : "DOMAIN" }});
    }
}

// Migrate admin audits to internal audits collection.
db.getCollection("reporter_audits_admin").renameCollection("reporter_audits");