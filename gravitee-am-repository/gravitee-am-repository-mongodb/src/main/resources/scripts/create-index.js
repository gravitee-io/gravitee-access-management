const listOfCollections = db.getCollectionNames();

// "applications" collection
if (listOfCollections.includes("applications")) {
    console.log("Update applications indexes");
    db.applications.dropIndexes();
    db.applications.createIndex({"_id": 1}, {"name": "_id1"});
    db.applications.createIndex({"domain": 1}, {"name": "d1"});
    db.applications.createIndex({"domain": 1, "name": 1}, {"name": "d1n1"});
    db.applications.createIndex({"domain": 1, "settings.oauth.clientId": 1}, {"name": "d1soc1"});
    db.applications.createIndex({"domain": 1, "settings.oauth.grantTypes": 1}, {"name": "d1sog1"});
    db.applications.createIndex({"identities": 1}, {"name": "i1"});
    db.applications.createIndex({"certificate": 1}, {"name": "c1"});
    db.applications.createIndex({"updatedAt": -1}, {"name": "u_1"});
}

// "access_tokens" collection
if (listOfCollections.includes("access_tokens")) {
    console.log("Update access_tokens indexes");
    db.access_tokens.dropIndexes();
    db.access_tokens.createIndex({"_id": 1}, {"name": "_id1"});
    db.access_tokens.createIndex({"token": 1}, {"name": "t1"});
    db.access_tokens.createIndex({"client": 1}, {"name": "c1"});
    db.access_tokens.createIndex({"authorization_code": 1}, {"name": "ac1"});
    db.access_tokens.createIndex({"subject": 1}, {"name": "s1"});
    db.access_tokens.createIndex({"domain": 1, "client": 1, "subject": 1}, {"name": "d1c1s1"});
    db.access_tokens.createIndex({"expire_at": 1}, {"name": "e1", "expireAfterSeconds": 0});
}

// "authorization_codes" collection
if (listOfCollections.includes("authorization_codes")) {
    console.log("Update authorization_codes indexes");
    db.authorization_codes.dropIndexes();
    db.authorization_codes.createIndex({"_id": 1}, {"name": "_id1"});
    db.authorization_codes.createIndex({"code": 1}, {"name": "c1"});
    db.authorization_codes.createIndex({"transactionId": 1}, {"name": "t1"});
    db.authorization_codes.createIndex({"expire_at": 1}, {"name": "e1", "expireAfterSeconds": 0});
}

// "certificates" collection
if (listOfCollections.includes("certificates")) {
    console.log("Update certificates indexes");
    db.certificates.dropIndexes();
    db.certificates.createIndex({"_id": 1}, {"name": "_id1"});
    db.certificates.createIndex({"domain": 1}, {"name": "d1"});
}

// "emails" collection
if (listOfCollections.includes("emails")) {
    console.log("Update emails indexes");
    db.emails.dropIndexes();
    db.emails.createIndex({"_id": 1}, {"name": "_id1"});
    db.emails.createIndex({"referenceType": 1, "referenceId": 1}, {"name": "ri1rt1"});
    db.emails.createIndex({"referenceType": 1, "referenceId": 1, "template": 1}, {"name": "ri1rt1t1"});
    db.emails.createIndex({"referenceType": 1, "referenceId": 1, "client": 1, "template": 1}, {"name": "ri1rc1t1"});
}

// "entrypoints" collection
if (listOfCollections.includes("entrypoints")) {
    console.log("Update entrypoints indexes");
    db.entrypoints.dropIndexes();
    db.entrypoints.createIndex({"_id": 1}, {"name": "_id1"});
    db.entrypoints.createIndex({"organizationId": 1}, {"name": "o1"});
}

// "environments" collection
if (listOfCollections.includes("environments")) {
    console.log("Update environments indexes");
    db.environments.dropIndexes();
    db.environments.createIndex({"_id": 1}, {"name": "_id1"});
    db.environments.createIndex({"organizationId": 1}, {"name": "o1"});
}

// "events" collection
if (listOfCollections.includes("events")) {
    console.log("Update events indexes");
    db.events.dropIndexes();
    db.events.createIndex({"_id": 1}, {"name": "_id1"});
    db.events.createIndex({"updatedAt": 1}, {"name": "u1"});
}

// "extension_grants" collection
if (listOfCollections.includes("extension_grants")) {
    console.log("Update extension_grants indexes");
    db.extension_grants.dropIndexes();
    db.extension_grants.createIndex({"_id": 1}, {"name": "_id1"});
    db.extension_grants.createIndex({"domain": 1}, {"name": "d1"});
    db.extension_grants.createIndex({"domain": 1, "name": 1}, {"name": "d1n1"});
}

// "factors" collection
if (listOfCollections.includes("factors")) {
    console.log("Update factors indexes");
    db.factors.dropIndexes();
    db.factors.createIndex({"_id": 1}, {"name": "_id1"});
    db.factors.createIndex({"domain": 1}, {"name": "d1"});
    db.factors.createIndex({"domain": 1, "factorType": 1}, {"name": "d1f1"});
}

// "forms" collection
if (listOfCollections.includes("forms")) {
    console.log("Update forms indexes");
    db.forms.dropIndexes();
    db.forms.createIndex({"_id": 1}, {"name": "_id1"});
    db.forms.createIndex({"referenceType": 1, "referenceId": 1}, {"name": "rt1ri1"});
    db.forms.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "template": 1
    }, {"name": "rt1ri1t1"});
    db.forms.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "client": 1,
        "template": 1
    }, {"name": "rt1ri1c1t1"});
}

// "groups" collection
if (listOfCollections.includes("groups")) {
    console.log("Update groups indexes");
    db.groups.dropIndexes();
    db.groups.createIndex({"_id": 1}, {"name": "_id1"});
    db.groups.createIndex({"referenceType": 1, "referenceId": 1}, {"name": "rt1ri1"});
    db.groups.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "name": 1
    }, {"name": "rt1ri1n1"});
}

// "identities" collection
if (listOfCollections.includes("identities")) {
    console.log("Update identities indexes");
    db.identities.dropIndexes();
    db.identities.createIndex({"_id": 1}, {"name": "_id1"});
    db.identities.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "rt1ri1"});
}

// "login_attempts" collection
if (listOfCollections.includes("login_attempts")) {
    console.log("Update login_attempts indexes");
    db.login_attempts.dropIndexes();
    db.login_attempts.createIndex({"_id": 1}, {"name": "_id1"});
    db.login_attempts.createIndex({
        "domain": 1,
        "client": 1,
        "username": 1
    }, {"name": "d1c1u1"});
    db.login_attempts.createIndex({"expireAt": 1}, {
        "name": "e1",
        "expireAfterSeconds": 0
    });
}

// "memberships" collection
if (listOfCollections.includes("memberships")) {
    console.log("Update memberships indexes");
    db.memberships.dropIndexes();
    db.memberships.createIndex({"_id": 1}, {"name": "_id1"});
    db.memberships.createIndex({
        "referenceId": 1,
        "referenceType": 1
    }, {"name": "ri1rt1"});
    db.memberships.createIndex({
        "referenceId": 1,
        "memberId": 1
    }, {"name": "ri1mi1"});
    db.memberships.createIndex({
        "memberId": 1,
        "memberType": 1
    }, {"name": "mi1mt1"});
}

// "flows" collection
if (listOfCollections.includes("flows")) {
    console.log("Update flows indexes");
    db.flows.dropIndexes();
    db.flows.createIndex({"_id": 1}, {"name": "_id1"});
    db.flows.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "rt1ri1"});
    db.flows.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "application": 1
    }, {"name": "rt1ri1a1"});
    db.flows.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "_id": 1
    }, {"name": "rt1ri1id1"});
}

// "refresh_tokens" collection
if (listOfCollections.includes("refresh_tokens")) {
    console.log("Update refresh_tokens indexes");
    db.refresh_tokens.dropIndexes();
    db.refresh_tokens.createIndex({"_id": 1}, {"name": "_id1"});
    db.refresh_tokens.createIndex({"token": 1}, {"name": "t1"});
    db.refresh_tokens.createIndex({"subject": 1}, {"name": "s1"});
    db.refresh_tokens.createIndex({
        "domain": 1,
        "client": 1,
        "subject": 1
    }, {"name": "d1c1s1"});
    db.refresh_tokens.createIndex({"expire_at": 1}, {
        "name": "e1",
        "expireAfterSeconds": 0
    });
}
// "reporters" collection
if (listOfCollections.includes("reporters")) {
    console.log("Update reporters indexes");
    db.reporters.dropIndexes();
    db.reporters.createIndex({"_id": 1}, {"name": "_id1"});
    db.reporters.createIndex({"domain": 1}, {"name": "d1"});
}

// "roles" collection
if (listOfCollections.includes("roles")) {
    console.log("Update roles indexes");
    db.roles.dropIndexes();
    db.roles.createIndex({"_id": 1}, {"name": "_id1"});
    db.roles.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "rt1ri1"});
    db.roles.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "name": 1,
        "scope": 1
    }, {"name": "rt1ri1n1s1"});
}

// "scopes" collection
if (listOfCollections.includes("scopes")) {
    console.log("Update scopes indexes");
    db.scopes.dropIndexes();
    db.scopes.createIndex({"_id": 1}, {"name": "_id1"});
    db.scopes.createIndex({"domain": 1}, {"name": "d1"});
    db.scopes.createIndex({
        "domain": 1,
        "key": 1
    }, {"name": "d1k1"});
}

// "scope_approvals" collection
if (listOfCollections.includes("scope_approvals")) {
    console.log("Update scope_approvals indexes");
    db.scope_approvals.dropIndexes();
    db.scope_approvals.createIndex({"_id": 1}, {"name": "_id1"});
    db.scope_approvals.createIndex({"transactionId": 1}, {"name": "t1"});
    db.scope_approvals.createIndex({
        "domain": 1,
        "userId": 1
    }, {"name": "d1u1"});
    db.scope_approvals.createIndex({
        "domain": 1,
        "clientId": 1,
        "userId": 1
    }, {"name": "d1c1u1"});
    db.scope_approvals.createIndex({
        "domain": 1,
        "clientId": 1,
        "userId": 1,
        "scope": 1
    }, {"name": "d1c1u1s1"});
    db.scope_approvals.createIndex({"expiresAt": 1}, {
        "name": "e1",
        "expireAfterSeconds": 0
    });
}

// "tags" collection
if (listOfCollections.includes("tags")) {
    console.log("Update tags indexes");
    db.tags.dropIndexes();
    db.tags.createIndex({"_id": 1}, {"name": "_id1"});
    db.tags.createIndex({"organizationId": 1}, {"name": "o1"});
}

// "users" collection
if (listOfCollections.includes("users")) {
    console.log("Update users indexes");
    db.users.dropIndexes();
    db.users.createIndex({"_id": 1}, {"name": "_id1"});
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "rt1ri1"});
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "email": 1
    }, {"name": "rt1ri1e1"});
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "additionalInformation.email": 1
    }, {"name": "rt1ri1ae1"});
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "username": 1
    }, {"name": "rt1ri1u1"});
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "displayName": 1
    }, {"name": "rt1ri1d1"});
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "firstName": 1
    }, {"name": "rt1ri1f1"});
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "lastName": 1
    }, {"name": "rt1ri1l1"});
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "externalId": 1
    }, {"name": "rt1ri1ext1"});
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "username": 1,
        "source": 1
    }, {"name": "rt1ri1u1s1_unique"}, {unique: true});
    db.users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "externalId": 1,
        "source": 1
    }, {"name": "rt1ri1ext1s1"});
}

// "organization_users" collection
if (listOfCollections.includes("organization_users")) {
    console.log("Update organization_users indexes");
    db.organization_users.dropIndexes();
    db.organization_users.createIndex({"_id": 1}, {"name": "_id1"});
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "rt1ri1"});
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "email": 1
    }, {"name": "rt1ri1e1"});
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "additionalInformation.email": 1
    }, {"name": "rt1ri1ae1"});
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "username": 1
    }, {"name": "rt1ri1u1"});
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "displayName": 1
    }, {"name": "rt1ri1d1"});
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "firstName": 1
    }, {"name": "rt1ri1f1"});
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "lastName": 1
    }, {"name": "rt1ri1l1"});
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "externalId": 1
    }, {"name": "rt1ri1ext1"});
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "username": 1,
        "source": 1
    }, {"name": "rt1ri1u1s1"});
    db.organization_users.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "externalId": 1,
        "source": 1
    }, {"name": "rt1ri1ext1s1"});
}

// "uma_access_policies" collection
if (listOfCollections.includes("uma_access_policies")) {
    console.log("Update uma_access_policies indexes");
    db.uma_access_policies.dropIndexes();
    db.uma_access_policies.createIndex({"_id": 1}, {"name": "_id1"});
    db.uma_access_policies.createIndex({"domain": 1}, {"name": "d1"});
    db.uma_access_policies.createIndex({"resource": 1}, {"name": "r1"});
    db.uma_access_policies.createIndex({
        "domain": 1,
        "resource": 1
    }, {"name": "d1r1"});
    db.uma_access_policies.createIndex({"updatedAt": -1}, {"name": "u_1"});
}

// "uma_resource_set" collection
if (listOfCollections.includes("uma_resource_set")) {
    console.log("Update uma_resource_set indexes");
    db.uma_resource_set.dropIndexes();
    db.uma_resource_set.createIndex({"_id": 1}, {"name": "_id1"});
    db.uma_resource_set.createIndex({"domain": 1}, {"name": "d1"});
    db.uma_resource_set.createIndex({
        "domain": 1,
        "clientId": 1
    }, {"name": "d1c1"});
    db.uma_resource_set.createIndex({
        "domain": 1,
        "clientId": 1,
        "userId": 1
    }, {"name": "d1c1u1"});
}

// "uma_permission_ticket" collection
if (listOfCollections.includes("uma_permission_ticket")) {
    console.log("Update uma_permission_ticket indexes");
    db.uma_permission_ticket.dropIndexes();
    db.uma_permission_ticket.createIndex({"_id": 1}, {"name": "_id1"});
    db.uma_permission_ticket.createIndex({"expireAt": 1}, {
        "name": "e1",
        "expireAfterSeconds": 0
    });
}

// "webauthn_credentials" collection
if (listOfCollections.includes("webauthn_credentials")) {
    console.log("Update webauthn_credentials indexes");
    db.webauthn_credentials.dropIndexes();
    db.webauthn_credentials.createIndex({"_id": 1}, {"name": "_id1"});
    db.webauthn_credentials.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "rt1ri1"});
    db.webauthn_credentials.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "userId": 1
    }, {"name": "rt1ri1uid1"});
    db.webauthn_credentials.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "username": 1
    }, {"name": "rt1ri1un1"});
    db.webauthn_credentials.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "credentialId": 1
    }, {"name": "rt1ri1cid1"});
    db.webauthn_credentials.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "aaguid": 1
    }, {"name": "rt1ri1a1"});
}

// "auth_flow_ctx" collection
if (listOfCollections.includes("auth_flow_ctx")) {
    console.log("Update auth_flow_ctx indexes");
    db.auth_flow_ctx.dropIndexes();
    db.auth_flow_ctx.createIndex({"_id": 1}, {"name": "_id1"});
    db.auth_flow_ctx.createIndex({
        "transactionId": 1,
        "version": -1
    }, {"name": "t1v_1"});
    db.auth_flow_ctx.createIndex({"expire_at": 1}, {
        "name": "e1",
        "expireAfterSeconds": 0
    });
}

// "domains" collection
if (listOfCollections.includes("domains")) {
    console.log("Update domains indexes");
    db.domains.dropIndexes();
    db.domains.createIndex({"_id": 1}, {"name": "_id1"});
    db.domains.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "ri1rt1"});
    db.domains.createIndex({
        "referenceType": 1,
        "referenceId": 1,
        "hrid": 1
    }, {"name": "ri1rt1h1"});
}

// "bot_detections" collection
if (listOfCollections.includes("bot_detections")) {
    console.log("Update bot_detections indexes");
    db.bot_detections.dropIndexes();
    db.bot_detections.createIndex({"_id": 1}, {"name": "_id1"});
    db.bot_detections.createIndex({
        "referenceId": 1,
        "referenceType": 1
    }, {"name": "ri1rt1"});
}

// "authentication_device_notifiers" collection
if (listOfCollections.includes("authentication_device_notifiers")) {
    console.log("Update authentication_device_notifiers indexes");
    db.authentication_device_notifiers.dropIndexes();
    db.authentication_device_notifiers.createIndex({"_id": 1}, {"name": "_id1"});
    db.authentication_device_notifiers.createIndex({
        "referenceId": 1,
        "referenceType": 1
    }, {"name": "ri1rt1"});
}

// "device_identifiers" collection
if (listOfCollections.includes("device_identifiers")) {
    console.log("Update device_identifiers indexes");
    db.device_identifiers.dropIndexes();
    db.device_identifiers.createIndex({"_id": 1}, {"name": "_id1"});
    db.device_identifiers.createIndex({
        "referenceId": 1,
        "referenceType": 1
    }, {"name": "ri1rt1"});
}

// "devices" collection
if (listOfCollections.includes("devices")) {
    console.log("Update devices indexes");
    db.devices.dropIndexes();
    db.devices.createIndex({"_id": 1}, {"name": "_id1"});
    db.devices.createIndex({
        "referenceId": 1,
        "referenceType": 1
    }, {"name": "ri1rt1"});
    db.devices.createIndex({"expires_at": 1}, {
        "name": "e1",
        "expireAfterSeconds": 0
    });
}

// "i18n_dictionaries" collection
if (listOfCollections.includes("i18n_dictionaries")) {
    console.log("Update i18n_dictionaries indexes");
    db.i18n_dictionaries.dropIndexes();
    db.i18n_dictionaries.createIndex({"_id": 1}, {"name": "_id1"});
    db.i18n_dictionaries.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "rt1ri1"});
    db.i18n_dictionaries.createIndex({
        "referenceId": 1,
        "referenceType": 1,
        "name": 1
    }, {"name": "ri1rt1n1"});
}

// "node_monitoring" collection
if (listOfCollections.includes("node_monitoring")) {
    console.log("Update node_monitoring indexes");
    db.node_monitoring.dropIndexes();
    db.node_monitoring.createIndex({"_id": 1}, {"name": "_id1"});
    db.node_monitoring.createIndex({"updatedAt": 1}, {"name": "u1"});
}

// "notification_acknowledgements" collection
if (listOfCollections.includes("notification_acknowledgements")) {
    console.log("Update notification_acknowledgements indexes");
    db.notification_acknowledgements.dropIndexes();
    db.notification_acknowledgements.createIndex({"_id": 1}, {"name": "_id1"});
    db.notification_acknowledgements.createIndex({
        "resourceId": 1,
        "type": 1,
        "audienceId": 1
    }, {"name": "ri1rt1a1"});
}

// "password_histories" collection
if (listOfCollections.includes("password_histories")) {
    console.log("Update password_histories indexes");
    db.password_histories.dropIndexes();
    db.password_histories.createIndex({"_id": 1}, {"name": "_id1"});
    db.password_histories.createIndex({
        "referenceType": 1,
        "referenceId": 1
    }, {"name": "rt1ri1"});
    db.password_histories.createIndex({
        "referenceId": 1,
        "referenceType": 1,
        "userId": 1
    }, {"name": "ri1rt1u1"});
}

// "rate_limit" collection
if (listOfCollections.includes("rate_limit")) {
    console.log("Update rate_limit indexes");
    db.rate_limit.dropIndexes();
    db.rate_limit.createIndex({"_id": 1}, {"name": "_id1"});
    db.rate_limit.createIndex({
        "userId": 1,
        "client": 1,
        "factorId": 1
    }, {"name": "u1c1f1"});
}

// "verify_attempt" collection
if (listOfCollections.includes("verify_attempt")) {
    console.log("Update verify_attempt indexes");
    db.verify_attempt.dropIndexes();
    db.verify_attempt.createIndex({"_id": 1}, {"name": "_id1"});
    db.verify_attempt.createIndex({"userId": 1, "client": 1, "factorId": 1}, {"name": "u1c1f1"});
}

// "service_resources" collection
if (listOfCollections.includes("service_resources")) {
    console.log("Update service_resources indexes");
    db.service_resources.dropIndexes();
    db.service_resources.createIndex({"_id": 1}, {"name": "_id1"});
    db.service_resources.createIndex({"referenceId": 1, "referenceType": 1}, {"name": "ri1rt1"});
}

// "themes" collection
if (listOfCollections.includes("themes")) {
    console.log("Update themes indexes");
    db.themes.dropIndexes();
    db.themes.createIndex({"_id": 1}, {"name": "_id1"});
    db.themes.createIndex({"referenceType": 1, "referenceId": 1}, {"name": "rt1ri1"});
}

// "user_notifications" collection
if (listOfCollections.includes("user_notifications")) {
    console.log("Update user_notifications indexes");
    db.user_notifications.dropIndexes();
    db.user_notifications.createIndex({"_id": 1}, {"name": "_id1"});
    db.user_notifications.createIndex({"audienceId": 1, "type": 1, "status": 1}, {"name": "a1t1s1"});
}

// "user_activities" collection
if (listOfCollections.includes("user_activities")) {
    console.log("Update user_activities indexes");
    db.user_activities.dropIndexes();
    db.user_activities.createIndex({"_id": 1}, {"name": "_id1"});
    db.user_activities.createIndex({"referenceType": 1, "referenceId": 1}, {"name": "rt1ri1"});
    db.user_activities.createIndex({"createdAt": 1}, {"name": "c1"});
    db.user_activities.createIndex({"expireAt": 1}, {"name": "e1", "expireAfterSeconds": 0});
}

// "system_tasks" collection
if (listOfCollections.includes("system_tasks")) {
    console.log("Update system_tasks indexes");
    db.system_tasks.dropIndexes();
    db.system_tasks.createIndex({"_id": 1}, {"name": "_id1"});
}

// "organizations" collection
if (listOfCollections.includes("organizations")) {
    console.log("Update organizations indexes");
    db.organizations.dropIndexes();
    db.organizations.createIndex({"_id": 1}, {"name": "_id1"});
}

// "installation" collection
if (listOfCollections.includes("installation")) {
    console.log("Update installation indexes");
    db.installation.dropIndexes();
    db.installation.createIndex({"_id": 1}, {"name": "_id1"});
}

// "alert_triggers" collection
if (listOfCollections.includes("alert_triggers")) {
    console.log("Update alert_triggers indexes");
    db.alert_triggers.dropIndexes();
    db.alert_triggers.createIndex({"_id": 1}, {"name": "_id1"});
}

// "alert_notifiers" collection
if (listOfCollections.includes("alert_notifiers")) {
    console.log("Update alert_notifiers indexes");
    db.alert_notifiers.dropIndexes();
    db.alert_notifiers.createIndex({"_id": 1}, {"name": "_id1"});
}

// "request_objects" collection
if (listOfCollections.includes("request_objects")) {
    console.log("Update request_objects indexes");
    db.request_objects.dropIndexes();
    db.request_objects.createIndex({"_id": 1}, {"name": "_id1"});
    db.request_objects.createIndex({"expire_at": 1}, {"name": "e1", "expireAfterSeconds": 0});
}

// "pushed_authorization_requests" collection
if (listOfCollections.includes("pushed_authorization_requests")) {
    console.log("Update pushed_authorization_requests indexes");
    db.pushed_authorization_requests.dropIndexes();
    db.pushed_authorization_requests.createIndex({"_id": 1}, {"name": "_id1"});
    db.pushed_authorization_requests.createIndex({"expire_at": 1}, {"name": "e1", "expireAfterSeconds": 0});
}

// "ciba_auth_requests" collection
if (listOfCollections.includes("ciba_auth_requests")) {
    console.log("Update ciba_auth_requests indexes");
    db.ciba_auth_requests.dropIndexes();
    db.ciba_auth_requests.createIndex({"_id": 1}, {"name": "_id1"});
    db.ciba_auth_requests.createIndex({"expire_at": 1}, {"name": "e1", "expireAfterSeconds": 0});
}
