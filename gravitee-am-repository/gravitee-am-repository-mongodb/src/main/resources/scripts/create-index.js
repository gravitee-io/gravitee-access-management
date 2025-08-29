const listOfCollections = db.getCollectionNames();

// "applications" collection
if (listOfCollections.includes("applications")) {
    console.log("Update applications indexes");
    db.applications.dropIndexes();
    db.applications.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.applications.createIndex({"domain": 1}, {"name": "d1"}, "majority");
    db.applications.createIndex({"domain": 1, "name": 1}, {"name": "d1n1"}, "majority");
    db.applications.createIndex({"domain": 1, "settings.oauth.clientId": 1}, {"name": "d1soc1"}, "majority");
    db.applications.createIndex({"domain": 1, "settings.oauth.grantTypes": 1}, {"name": "d1sog1"}, "majority");
    db.applications.createIndex({"identities": 1}, {"name": "i1"}, "majority");
    db.applications.createIndex({"certificate": 1}, {"name": "c1"}, "majority");
    db.applications.createIndex({"updatedAt": -1}, {"name": "u_1"}, "majority");
}

// "access_tokens" collection
if (listOfCollections.includes("access_tokens")) {
    console.log("Update access_tokens indexes");
    db.access_tokens.dropIndexes();
    db.access_tokens.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.access_tokens.createIndex({"token": 1}, {"name": "t1"}, "majority");
    db.access_tokens.createIndex({"client": 1}, {"name": "c1"}, "majority");
    db.access_tokens.createIndex({"authorization_code": 1}, {"name": "ac1"}, "majority");
    db.access_tokens.createIndex({"subject": 1}, {"name": "s1"}, "majority");
    db.access_tokens.createIndex({"domain": 1, "client": 1, "subject": 1}, {"name": "d1c1s1"}, "majority");
    db.access_tokens.createIndex({"expire_at": 1}, {"name": "e1", "expireAfterSeconds": 0}, "majority");
}

// "authorization_codes" collection
if (listOfCollections.includes("authorization_codes")) {
    console.log("Update authorization_codes indexes");
    db.authorization_codes.dropIndexes();
    db.authorization_codes.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.authorization_codes.createIndex({"code": 1}, {"name": "c1"}, "majority");
    db.authorization_codes.createIndex({"transactionId": 1}, {"name": "t1"}, "majority");
    db.authorization_codes.createIndex({"expire_at": 1}, {"name": "e1", "expireAfterSeconds": 0}, "majority");
}

// "certificates" collection
if (listOfCollections.includes("certificates")) {
    console.log("Update certificates indexes");
    db.certificates.dropIndexes();
    db.certificates.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.certificates.createIndex({"domain": 1}, {"name": "d1"}, "majority");
}

// "emails" collection
if (listOfCollections.includes("emails")) {
    console.log("Update emails indexes");
    db.emails.dropIndexes();
    db.emails.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.emails.createIndex({"referenceType": 1, "referenceId": 1}, {"name": "ri1rt1"}, "majority");
    db.emails.createIndex({"referenceType": 1, "referenceId": 1, "template": 1}, {"name": "ri1rt1t1"}, "majority");
    db.emails.createIndex({"referenceType": 1, "referenceId": 1, "client": 1, "template": 1}, {"name": "ri1rc1t1"}, "majority");
}

// "entrypoints" collection
if (listOfCollections.includes("entrypoints")) {
    console.log("Update entrypoints indexes");
    db.entrypoints.dropIndexes();
    db.entrypoints.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.entrypoints.createIndex({"organizationId": 1}, {"name": "o1"}, "majority");
}

// "environments" collection
if (listOfCollections.includes("environments")) {
    console.log("Update environments indexes");
    db.environments.dropIndexes();
    db.environments.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.environments.createIndex({"organizationId": 1}, {"name": "o1"}, "majority");
}

// "events" collection
if (listOfCollections.includes("events")) {
    console.log("Update events indexes");
    db.events.dropIndexes();
    db.events.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.events.createIndex({"updatedAt": 1}, {"name": "u1"}, "majority");
}

// "extension_grants" collection
if (listOfCollections.includes("extension_grants")) {
    console.log("Update extension_grants indexes");
    db.extension_grants.dropIndexes();
    db.extension_grants.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.extension_grants.createIndex({"domain": 1}, {"name": "d1"}, "majority");
    db.extension_grants.createIndex({"domain": 1, "name": 1}, {"name": "d1n1"}, "majority");
}

// "factors" collection
if (listOfCollections.includes("factors")) {
    console.log("Update factors indexes");
    db.factors.dropIndexes();
    db.factors.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.factors.createIndex({"domain": 1}, {"name": "d1"}, "majority");
    db.factors.createIndex({"domain": 1, "factorType": 1}, {"name": "d1f1"}, "majority");
}

// "forms" collection
if (listOfCollections.includes("forms")) {
    console.log("Update forms indexes");
    db.forms.dropIndexes();
    db.forms.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.forms.createIndex({"referenceType": 1, "referenceId": 1}, {"name": "rt1ri1"}, "majority");
    db.forms.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "template": 1
    }, {"name": "rt1ri1t1"}, "majority");
    db.forms.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "client": 1,
        "template": 1
    }, {"name": "rt1ri1c1t1"}, "majority");
}

// "groups" collection
if (listOfCollections.includes("groups")) {
    console.log("Update groups indexes");
    db.groups.dropIndexes();
    db.groups.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.groups.createIndex({"referenceType": 1, "referenceId": 1}, {"name": "rt1ri1"}, "majority");
    db.groups.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "name": 1
    }, {"name": "rt1ri1n1"}, "majority");
}

// "identities" collection
if (listOfCollections.includes("identities")) {
    console.log("Update identities indexes");
    db.identities.dropIndexes();
    db.identities.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.identities.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "rt1ri1"}, "majority");
}

// "login_attempts" collection
if (listOfCollections.includes("login_attempts")) {
    console.log("Update login_attempts indexes");
    db.login_attempts.dropIndexes();
    db.login_attempts.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.login_attempts.createIndex({
        "domain": 1,
        "client": 1,
        "username": 1
    }, {"name": "d1c1u1"}, "majority");
    db.login_attempts.createIndex({"expireAt": 1}, {
        "name": "e1",
        "expireAfterSeconds": 0
    }, "majority");
}

// "memberships" collection
if (listOfCollections.includes("memberships")) {
    console.log("Update memberships indexes");
    db.memberships.dropIndexes();
    db.memberships.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.memberships.createIndex({
        "referenceId": 1,
        "referenceType": 1
    }, {"name": "ri1rt1"}, "majority");
    db.memberships.createIndex({
        "referenceId": 1,
        "memberId": 1
    }, {"name": "ri1mi1"}, "majority");
    db.memberships.createIndex({
        "memberId": 1,
        "memberType": 1
    }, {"name": "mi1mt1"}, "majority");
}

// "flows" collection
if (listOfCollections.includes("flows")) {
    console.log("Update flows indexes");
    db.flows.dropIndexes();
    db.flows.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.flows.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "rt1ri1"}, "majority");
    db.flows.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "application": 1
    }, {"name": "rt1ri1a1"}, "majority");
    db.flows.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "_id": 1
    }, {"name": "rt1ri1id1"}, "majority");
}

// "refresh_tokens" collection
if (listOfCollections.includes("refresh_tokens")) {
    console.log("Update refresh_tokens indexes");
    db.refresh_tokens.dropIndexes();
    db.refresh_tokens.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.refresh_tokens.createIndex({"token": 1}, {"name": "t1"}, "majority");
    db.refresh_tokens.createIndex({"subject": 1}, {"name": "s1"}, "majority");
    db.refresh_tokens.createIndex({
        "domain": 1,
        "client": 1,
        "subject": 1
    }, {"name": "d1c1s1"}, "majority");
    db.refresh_tokens.createIndex({"expire_at": 1}, {
        "name": "e1",
        "expireAfterSeconds": 0
    }, "majority");
}
// "reporters" collection
if (listOfCollections.includes("reporters")) {
    console.log("Update reporters indexes");
    db.reporters.dropIndexes();
    db.reporters.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.reporters.createIndex({"domain": 1}, {"name": "d1"}, "majority");
}

// "roles" collection
if (listOfCollections.includes("roles")) {
    console.log("Update roles indexes");
    db.roles.dropIndexes();
    db.roles.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.roles.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "rt1ri1"}, "majority");
    db.roles.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "name": 1,
        "scope": 1
    }, {"name": "rt1ri1n1s1"}, "majority");
}

// "scopes" collection
if (listOfCollections.includes("scopes")) {
    console.log("Update scopes indexes");
    db.scopes.dropIndexes();
    db.scopes.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.scopes.createIndex({"domain": 1}, {"name": "d1"}, "majority");
    db.scopes.createIndex({
        "domain": 1,
        "key": 1
    }, {"name": "d1k1"}, "majority");
}

// "scope_approvals" collection
if (listOfCollections.includes("scope_approvals")) {
    console.log("Update scope_approvals indexes");
    db.scope_approvals.dropIndexes();
    db.scope_approvals.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.scope_approvals.createIndex({"transactionId": 1}, {"name": "t1"}, "majority");
    db.scope_approvals.createIndex({
        "domain": 1,
        "userId": 1
    }, {"name": "d1u1"}, "majority");
    db.scope_approvals.createIndex({
        "domain": 1,
        "clientId": 1,
        "userId": 1
    }, {"name": "d1c1u1"}, "majority");
    db.scope_approvals.createIndex({
        "domain": 1,
        "clientId": 1,
        "userId": 1,
        "scope": 1
    }, {"name": "d1c1u1s1"}, "majority");
    db.scope_approvals.createIndex({"expiresAt": 1}, {
        "name": "e1",
        "expireAfterSeconds": 0
    }, "majority");
}

// "tags" collection
if (listOfCollections.includes("tags")) {
    console.log("Update tags indexes");
    db.tags.dropIndexes();
    db.tags.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.tags.createIndex({"organizationId": 1}, {"name": "o1"}, "majority");
}

// "users" collection
if (listOfCollections.includes("users")) {
    console.log("Update users indexes");
    db.users.dropIndexes();
    db.users.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "email": 1
    }, {"name": "rt1ri1e1"}, "majority");
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "additionalInformation.email": 1
    }, {"name": "rt1ri1ae1"}, "majority");
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "username": 1
    }, {"name": "rt1ri1u1"}, "majority");
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "displayName": 1
    }, {"name": "rt1ri1d1"}, "majority");
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "firstName": 1
    }, {"name": "rt1ri1f1"}, "majority");
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "lastName": 1
    }, {"name": "rt1ri1l1"}, "majority");
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "externalId": 1
    }, {"name": "rt1ri1ext1"}, "majority");
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "username": 1,
        "source": 1
    }, {"name": "rt1ri1u1s1_unique", unique: true}, "majority");
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "externalId": 1,
        "source": 1
    }, {"name": "rt1ri1ext1s1"}, "majority");
}

// "organization_users" collection
if (listOfCollections.includes("organization_users")) {
    console.log("Update organization_users indexes");
    db.organization_users.dropIndexes();
    db.organization_users.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "email": 1
    }, {"name": "rt1ri1e1"}, "majority");
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "additionalInformation.email": 1
    }, {"name": "rt1ri1ae1"}, "majority");
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "username": 1
    }, {"name": "rt1ri1u1"}, "majority");
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "displayName": 1
    }, {"name": "rt1ri1d1"}, "majority");
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "firstName": 1
    }, {"name": "rt1ri1f1"}, "majority");
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "lastName": 1
    }, {"name": "rt1ri1l1"}, "majority");
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "externalId": 1
    }, {"name": "rt1ri1ext1"}, "majority");
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "username": 1,
        "source": 1
    }, {"name": "rt1ri1u1s1"}, "majority");
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "externalId": 1,
        "source": 1
    }, {"name": "rt1ri1ext1s1"}, "majority");
}

// "uma_access_policies" collection
if (listOfCollections.includes("uma_access_policies")) {
    console.log("Update uma_access_policies indexes");
    db.uma_access_policies.dropIndexes();
    db.uma_access_policies.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.uma_access_policies.createIndex({"domain": 1}, {"name": "d1"}, "majority");
    db.uma_access_policies.createIndex({"resource": 1}, {"name": "r1"}, "majority");
    db.uma_access_policies.createIndex({
        "domain": 1,
        "resource": 1
    }, {"name": "d1r1"}, "majority");
    db.uma_access_policies.createIndex({"updatedAt": -1}, {"name": "u_1"}, "majority");
}

// "uma_resource_set" collection
if (listOfCollections.includes("uma_resource_set")) {
    console.log("Update uma_resource_set indexes");
    db.uma_resource_set.dropIndexes();
    db.uma_resource_set.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.uma_resource_set.createIndex({"domain": 1}, {"name": "d1"}, "majority");
    db.uma_resource_set.createIndex({
        "domain": 1,
        "clientId": 1
    }, {"name": "d1c1"}, "majority");
    db.uma_resource_set.createIndex({
        "domain": 1,
        "clientId": 1,
        "userId": 1
    }, {"name": "d1c1u1"}, "majority");
}

// "uma_permission_ticket" collection
if (listOfCollections.includes("uma_permission_ticket")) {
    console.log("Update uma_permission_ticket indexes");
    db.uma_permission_ticket.dropIndexes();
    db.uma_permission_ticket.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.uma_permission_ticket.createIndex({"expireAt": 1}, {
        "name": "e1",
        "expireAfterSeconds": 0
    }, "majority");
}

// "webauthn_credentials" collection
if (listOfCollections.includes("webauthn_credentials")) {
    console.log("Update webauthn_credentials indexes");
    db.webauthn_credentials.dropIndexes();
    db.webauthn_credentials.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.webauthn_credentials.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "userId": 1
    }, {"name": "rt1ri1uid1"}, "majority");
    db.webauthn_credentials.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "username": 1,
        "createdAt": -1
    }, {"name": "rt1ri1un1c_1"}, "majority");
    db.webauthn_credentials.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "credentialId": 1
    }, {"name": "rt1ri1cid1"}, "majority");
    db.webauthn_credentials.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "aaguid": 1
    }, {"name": "rt1ri1a1"}, "majority");
}

// "auth_flow_ctx" collection
if (listOfCollections.includes("auth_flow_ctx")) {
    console.log("Update auth_flow_ctx indexes");
    db.auth_flow_ctx.dropIndexes();
    db.auth_flow_ctx.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.auth_flow_ctx.createIndex({
        "transactionId": 1,
        "version": -1
    }, {"name": "t1v_1"}, "majority");
    db.auth_flow_ctx.createIndex({"expire_at": 1}, {
        "name": "e1",
        "expireAfterSeconds": 0
    }, "majority");
}

// "domains" collection
if (listOfCollections.includes("domains")) {
    console.log("Update domains indexes");
    db.domains.dropIndexes();
    db.domains.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.domains.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "ri1rt1"}, "majority");
    db.domains.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "hrid": 1
    }, {"name": "ri1rt1h1"}, "majority");
}

// "bot_detections" collection
if (listOfCollections.includes("bot_detections")) {
    console.log("Update bot_detections indexes");
    db.bot_detections.dropIndexes();
    db.bot_detections.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.bot_detections.createIndex({
        "referenceId": 1,
        "referenceType": 1
    }, {"name": "ri1rt1"}, "majority");
}

// "authentication_device_notifiers" collection
if (listOfCollections.includes("authentication_device_notifiers")) {
    console.log("Update authentication_device_notifiers indexes");
    db.authentication_device_notifiers.dropIndexes();
    db.authentication_device_notifiers.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.authentication_device_notifiers.createIndex({
        "referenceId": 1,
        "referenceType": 1
    }, {"name": "ri1rt1"}, "majority");
}

// "device_identifiers" collection
if (listOfCollections.includes("device_identifiers")) {
    console.log("Update device_identifiers indexes");
    db.device_identifiers.dropIndexes();
    db.device_identifiers.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.device_identifiers.createIndex({
        "referenceId": 1,
        "referenceType": 1
    }, {"name": "ri1rt1"}, "majority");
}

// "devices" collection
if (listOfCollections.includes("devices")) {
    console.log("Update devices indexes");
    db.devices.dropIndexes();
    db.devices.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.devices.createIndex({
        "referenceId": 1,
        "referenceType": 1
    }, {"name": "ri1rt1"}, "majority");
    db.devices.createIndex({"expires_at": 1}, {
        "name": "e1",
        "expireAfterSeconds": 0
    }, "majority");
}

// "i18n_dictionaries" collection
if (listOfCollections.includes("i18n_dictionaries")) {
    console.log("Update i18n_dictionaries indexes");
    db.i18n_dictionaries.dropIndexes();
    db.i18n_dictionaries.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.i18n_dictionaries.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "rt1ri1"}, "majority");
    db.i18n_dictionaries.createIndex({
        "referenceId": 1,
        "referenceType": 1,
        "name": 1
    }, {"name": "ri1rt1n1"}, "majority");
}

// "node_monitoring" collection
if (listOfCollections.includes("node_monitoring")) {
    console.log("Update node_monitoring indexes");
    db.node_monitoring.dropIndexes();
    db.node_monitoring.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.node_monitoring.createIndex({"updatedAt": 1}, {"name": "u1"}, "majority");
    db.node_monitoring.createIndex({"nodeId": 1, "type": 1}, {"name": "n1t1"}, "majority");
}

// "notification_acknowledgements" collection
if (listOfCollections.includes("notification_acknowledgements")) {
    console.log("Update notification_acknowledgements indexes");
    db.notification_acknowledgements.dropIndexes();
    db.notification_acknowledgements.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.notification_acknowledgements.createIndex({
        "resourceId": 1,
        "type": 1,
        "audienceId": 1
    }, {"name": "ri1rt1a1"}, "majority");
}

// "password_histories" collection
if (listOfCollections.includes("password_histories")) {
    console.log("Update password_histories indexes");
    db.password_histories.dropIndexes();
    db.password_histories.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.password_histories.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "rt1ri1"}, "majority");
    db.password_histories.createIndex({
        "referenceId": 1,
        "referenceType": 1,
        "userId": 1
    }, {"name": "ri1rt1u1"}, "majority");
}

// "rate_limit" collection
if (listOfCollections.includes("rate_limit")) {
    console.log("Update rate_limit indexes");
    db.rate_limit.dropIndexes();
    db.rate_limit.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.rate_limit.createIndex({
        "userId": 1,
        "client": 1,
        "factorId": 1
    }, {"name": "u1c1f1"}, "majority");
}


// "verify_attempt" collection
if (listOfCollections.includes("verify_attempt")) {
    console.log("Update verify_attempt indexes");
    db.verify_attempt.dropIndexes();
    db.verify_attempt.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.verify_attempt.createIndex({"userId": 1, "client": 1, "factorId": 1}, {"name": "u1c1f1"}, "majority");
}

// "service_resources" collection
if (listOfCollections.includes("service_resources")) {
    console.log("Update service_resources indexes");
    db.service_resources.dropIndexes();
    db.service_resources.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.service_resources.createIndex({"referenceId": 1, "referenceType": 1}, {"name": "ri1rt1"}, "majority");
}

// "themes" collection
if (listOfCollections.includes("themes")) {
    console.log("Update themes indexes");
    db.themes.dropIndexes();
    db.themes.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.themes.createIndex({"referenceType": 1, "referenceId": 1}, {"name": "rt1ri1"}, "majority");
}

// "user_notifications" collection
if (listOfCollections.includes("user_notifications")) {
    console.log("Update user_notifications indexes");
    db.user_notifications.dropIndexes();
    db.user_notifications.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.user_notifications.createIndex({"audienceId": 1, "type": 1, "status": 1}, {"name": "a1t1s1"}, "majority");
}

// "user_activities" collection
if (listOfCollections.includes("user_activities")) {
    console.log("Update user_activities indexes");
    db.user_activities.dropIndexes();
    db.user_activities.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.user_activities.createIndex({"referenceType": 1, "referenceId": 1}, {"name": "rt1ri1"}, "majority");
    db.user_activities.createIndex({"createdAt": 1}, {"name": "c1"}, "majority");
    db.user_activities.createIndex({"expireAt": 1}, {"name": "e1", "expireAfterSeconds": 0}, "majority");
}

// "system_tasks" collection
if (listOfCollections.includes("system_tasks")) {
    console.log("Update system_tasks indexes");
    db.system_tasks.dropIndexes();
    db.system_tasks.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
}

// "organizations" collection
if (listOfCollections.includes("organizations")) {
    console.log("Update organizations indexes");
    db.organizations.dropIndexes();
    db.organizations.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
}

// "installation" collection
if (listOfCollections.includes("installation")) {
    console.log("Update installation indexes");
    db.installation.dropIndexes();
    db.installation.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
}

// "alert_triggers" collection
if (listOfCollections.includes("alert_triggers")) {
    console.log("Update alert_triggers indexes");
    db.alert_triggers.dropIndexes();
    db.alert_triggers.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
}

// "alert_notifiers" collection
if (listOfCollections.includes("alert_notifiers")) {
    console.log("Update alert_notifiers indexes");
    db.alert_notifiers.dropIndexes();
    db.alert_notifiers.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
}

// "request_objects" collection
if (listOfCollections.includes("request_objects")) {
    console.log("Update request_objects indexes");
    db.request_objects.dropIndexes();
    db.request_objects.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.request_objects.createIndex({"expire_at": 1}, {"name": "e1", "expireAfterSeconds": 0}, "majority");
}

// "pushed_authorization_requests" collection
if (listOfCollections.includes("pushed_authorization_requests")) {
    console.log("Update pushed_authorization_requests indexes");
    db.pushed_authorization_requests.dropIndexes();
    db.pushed_authorization_requests.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.pushed_authorization_requests.createIndex({"expire_at": 1}, {"name": "e1", "expireAfterSeconds": 0}, "majority");
}

// "ciba_auth_requests" collection
if (listOfCollections.includes("ciba_auth_requests")) {
    console.log("Update ciba_auth_requests indexes");
    db.ciba_auth_requests.dropIndexes();
    db.ciba_auth_requests.createIndex({"_id": 1}, {"name": "_id1"}, "majority");
    db.ciba_auth_requests.createIndex({"expire_at": 1}, {"name": "e1", "expireAfterSeconds": 0}, "majority");
}
