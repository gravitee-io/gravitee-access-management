// "applications" collection
db.applications.dropIndexes();
db.applications.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.applications.createIndex( { "domain" : 1 }, {"name": "d1"} );
db.applications.createIndex( { "domain" : 1, "name": 1 }, {"name": "d1n1"} );
db.applications.createIndex( { "domain" : 1, "settings.oauth.clientId": 1 }, {"name": "d1soc1"}  );
db.applications.createIndex( { "domain" : 1, "settings.oauth.grantTypes": 1 }, {"name": "d1sog1"}  );
db.applications.createIndex( { "identities": 1 }, {"name": "i1"}  );
db.applications.createIndex( { "certificate": 1 }, {"name": "c1"}  );
db.applications.createIndex( { "updatedAt" : -1 }, {"name": "u_1"}  );
db.applications.reIndex();

// "access_tokens" collection
db.access_tokens.dropIndexes();
db.access_tokens.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.access_tokens.createIndex( { "token" : 1 }, {"name": "t1"} );
db.access_tokens.createIndex( { "client" : 1 }, {"name": "c1"} );
db.access_tokens.createIndex( { "authorization_code" : 1 }, {"name": "ac1"} );
db.access_tokens.createIndex( { "subject" : 1 }, {"name": "s1"} );
db.access_tokens.createIndex( { "domain": 1, "client": 1, "subject": 1 }, {"name": "d1c1s1"} );
db.access_tokens.createIndex( { "expire_at" : 1 }, {"name": "e1", "expireAfterSeconds": 0});
db.access_tokens.reIndex();

// "authorization_codes" collection
db.authorization_codes.dropIndexes();
db.authorization_codes.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.authorization_codes.createIndex( { "code" : 1 }, {"name": "c1"} );
db.authorization_codes.createIndex( { "transactionId" : 1 }, {"name": "t1"} );
db.authorization_codes.createIndex( { "expire_at" : 1 }, {"name": "e1", "expireAfterSeconds": 0});
db.authorization_codes.reIndex();

// "certificates" collection
db.certificates.dropIndexes();
db.certificates.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.certificates.createIndex( { "domain" : 1 }, {"name": "d1"} );
db.certificates.reIndex();

// "emails" collection
db.emails.dropIndexes();
db.emails.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.emails.createIndex( { "referenceType" : 1, "referenceId": 1 }, {"name": "ri1rt1"});
db.emails.createIndex( { "referenceType" : 1, "referenceId": 1, "template": 1 }, {"name": "ri1rt1t1"} );
db.emails.createIndex( { "referenceType" : 1, "referenceId": 1, "client" : 1, "template": 1 }, {"name": "ri1rc1t1"} );
db.emails.reIndex();

// "entrypoints" collection
db.entrypoints.dropIndexes();
db.entrypoints.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.entrypoints.createIndex( { "organizationId": 1 }, {"name": "o1"} );
db.entrypoints.reIndex();

// "environments" collection
db.environments.dropIndexes();
db.environments.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.environments.createIndex( { "organizationId": 1 }, {"name": "o1"}  );
db.environments.reIndex();

// "events" collection
db.events.dropIndexes();
db.events.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.events.createIndex( { "updatedAt" : 1 }, {"name": "u1"} );
db.events.reIndex();

// "extension_grants" collection
db.extension_grants.dropIndexes();
db.extension_grants.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.extension_grants.createIndex( { "domain" : 1 }, {"name": "d1"} );
db.extension_grants.createIndex( { "domain" : 1, "name": 1 }, {"name": "d1n1"} );
db.extension_grants.reIndex();

// "factors" collection
db.factors.dropIndexes();
db.factors.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.factors.createIndex( { "domain" : 1 }, {"name": "d1"});
db.factors.createIndex( { "domain" : 1, "factorType": 1 }, {"name": "d1f1"} );
db.factors.reIndex();

// "forms" collection
db.forms.dropIndexes();
db.forms.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.forms.createIndex( { "referenceType" : 1, "referenceId": 1 }, {"name": "rt1ri1"} );
db.forms.createIndex( { "referenceType" : 1, "referenceId": 1, "template": 1 }, {"name": "rt1ri1t1"} );
db.forms.createIndex( { "referenceType" : 1, "referenceId": 1, "client" : 1, "template": 1 }, {"name": "rt1ri1c1t1"} );
db.forms.reIndex();

// "groups" collection
db.groups.dropIndexes();
db.groups.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.groups.createIndex( { "referenceType" : 1, "referenceId": 1 }, {"name": "rt1ri1"} );
db.groups.createIndex( { "referenceType" : 1, "referenceId": 1, "name": 1 }, {"name": "rt1ri1n1"} );
db.groups.reIndex();

// "identities" collection
db.identities.dropIndexes();
db.identities.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.identities.createIndex( { "referenceType" : 1, "referenceId": 1 }, {"name": "rt1ri1"} );
db.identities.reIndex();

// "login_attempts" collection
db.login_attempts.dropIndexes();
db.login_attempts.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.login_attempts.createIndex( { "domain" : 1, "client": 1, "username": 1 }, {"name": "d1c1u1"} );
db.login_attempts.createIndex( { "expireAt" : 1 }, {"name": "e1", "expireAfterSeconds": 0});
db.login_attempts.reIndex();

// "memberships" collection
db.memberships.dropIndexes();
db.memberships.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.memberships.createIndex( { "referenceId" : 1, "referenceType": 1 }, {"name": "ri1rt1"} );
db.memberships.createIndex( { "referenceId" : 1, "memberId": 1 }, {"name": "ri1mi1"} );
db.memberships.createIndex( { "memberId" : 1, "memberType": 1 }, {"name": "mi1mt1"} );
db.memberships.reIndex();

// "flows" collection
db.flows.dropIndexes();
db.flows.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.flows.createIndex( { "referenceType" : 1, "referenceId": 1 }, {"name": "rt1ri1"} );
db.flows.createIndex( { "referenceType" : 1, "referenceId": 1, "application": 1 }, {"name": "rt1ri1a1"} );
db.flows.createIndex( { "referenceType" : 1, "referenceId": 1, "_id": 1 }, {"name": "rt1ri1id1"} );
db.flows.reIndex();

// "refresh_tokens" collection
db.refresh_tokens.dropIndexes();
db.refresh_tokens.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.refresh_tokens.createIndex( { "token" : 1 }, {"name": "t1"} );
db.refresh_tokens.createIndex( { "subject" : 1 }, {"name": "s1"} );
db.refresh_tokens.createIndex( { "domain": 1, "client": 1, "subject": 1 }, {"name": "d1c1s1"} );
db.refresh_tokens.createIndex( { "expire_at" : 1 }, {"name": "e1", "expireAfterSeconds": 0});
db.refresh_tokens.reIndex();

// "reporters" collection
db.reporters.dropIndexes();
db.reporters.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.reporters.createIndex( { "domain" : 1 }, {"name": "d1"} );
db.reporters.reIndex();

// "roles" collection
db.roles.dropIndexes();
db.roles.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.roles.createIndex( { "referenceType" : 1, "referenceId": 1 }, {"name": "rt1ri1"} );
db.roles.createIndex( { "referenceType" : 1, "referenceId": 1, "name": 1, "scope": 1 }, {"name": "rt1ri1n1s1"} );
db.roles.reIndex();

// "scopes" collection
db.scopes.dropIndexes();
db.scopes.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.scopes.createIndex( { "domain" : 1 }, {"name": "d1"} );
db.scopes.createIndex( { "domain" : 1, "key": 1 }, {"name": "d1k1"} );
db.scopes.reIndex();

// "scope_approvals" collection
db.scope_approvals.dropIndexes();
db.scope_approvals.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.scope_approvals.createIndex( { "transactionId" : 1 }, {"name": "t1"} );
db.scope_approvals.createIndex( { "domain" : 1, "userId": 1 }, {"name": "d1u1"} );
db.scope_approvals.createIndex( { "domain" : 1, "clientId": 1, "userId": 1 }, {"name": "d1c1u1"} );
db.scope_approvals.createIndex( { "domain" : 1, "clientId": 1, "userId": 1, "scope": 1 }, {"name": "d1c1u1s1"} );
db.scope_approvals.createIndex( { "expiresAt" : 1 }, {"name": "e1", "expireAfterSeconds": 0});
db.scope_approvals.reIndex();

// "tags" collection
db.tags.dropIndexes();
db.tags.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.tags.createIndex( { "organizationId": 1 }, {"name": "o1"} );
db.tags.reIndex();

// "users" collection
db.users.dropIndexes();
db.users.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1 }, {"name": "rt1ri1"} );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "email": 1 }, {"name": "rt1ri1e1"} );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "additionalInformation.email": 1 }, {"name": "rt1ri1ae1"} );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "username": 1 }, {"name": "rt1ri1u1"} );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "displayName": 1 }, {"name": "rt1ri1d1"} );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "firstName": 1 }, {"name": "rt1ri1f1"} );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "lastName": 1 }, {"name": "rt1ri1l1"} );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "externalId": 1 }, {"name": "rt1ri1ext1"} );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "username": 1, "source": 1 }, {"name": "rt1ri1u1s1"} );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "externalId": 1, "source": 1 }, {"name": "rt1ri1ext1s1"} );
db.users.reIndex();

// "organization_users" collection
db.organization_users.dropIndexes();
db.organization_users.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.organization_users.createIndex( { "referenceType" : 1, "referenceId": 1 }, {"name": "rt1ri1"} );
db.organization_users.createIndex( { "referenceType" : 1, "referenceId": 1, "email": 1 }, {"name": "rt1ri1e1"} );
db.organization_users.createIndex( { "referenceType" : 1, "referenceId": 1, "additionalInformation.email": 1 }, {"name": "rt1ri1ae1"} );
db.organization_users.createIndex( { "referenceType" : 1, "referenceId": 1, "username": 1 }, {"name": "rt1ri1u1"} );
db.organization_users.createIndex( { "referenceType" : 1, "referenceId": 1, "displayName": 1 }, {"name": "rt1ri1d1"} );
db.organization_users.createIndex( { "referenceType" : 1, "referenceId": 1, "firstName": 1 }, {"name": "rt1ri1f1"} );
db.organization_users.createIndex( { "referenceType" : 1, "referenceId": 1, "lastName": 1 }, {"name": "rt1ri1l1"} );
db.organization_users.createIndex( { "referenceType" : 1, "referenceId": 1, "externalId": 1 }, {"name": "rt1ri1ext1"} );
db.organization_users.createIndex( { "referenceType" : 1, "referenceId": 1, "username": 1, "source": 1 }, {"name": "rt1ri1u1s1"} );
db.organization_users.createIndex( { "referenceType" : 1, "referenceId": 1, "externalId": 1, "source": 1 }, {"name": "rt1ri1ext1s1"} );
db.organization_users.reIndex();

// "uma_access_policies" collection
db.uma_access_policies.dropIndexes();
db.uma_access_policies.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.uma_access_policies.createIndex( { "domain" : 1 }, {"name": "d1"} );
db.uma_access_policies.createIndex( { "resource" : 1 }, {"name": "r1"}  );
db.uma_access_policies.createIndex( { "domain" : 1, "resource": 1 }, {"name": "d1r1"}  );
db.uma_access_policies.createIndex( { "updatedAt" : -1 }, {"name": "u_1"}  );
db.uma_access_policies.reIndex();

// "uma_resource_set" collection
db.uma_resource_set.dropIndexes();
db.uma_resource_set.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.uma_resource_set.createIndex( { "domain" : 1 }, {"name": "d1"} );
db.uma_resource_set.createIndex( { "domain" : 1, "clientId": 1 }, {"name": "d1c1"} );
db.uma_resource_set.createIndex( { "domain" : 1, "clientId": 1, "userId": 1 }, {"name": "d1c1u1"} );
db.uma_resource_set.reIndex();

// "uma_permission_ticket" collection
db.uma_permission_ticket.dropIndexes();
db.uma_permission_ticket.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.uma_permission_ticket.createIndex( { "expireAt" : 1 }, {"name": "e1", "expireAfterSeconds": 0});
db.uma_permission_ticket.reIndex();

// "webauthn_credentials" collection
db.webauthn_credentials.dropIndexes();
db.webauthn_credentials.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.webauthn_credentials.createIndex( { "referenceType" : 1, "referenceId": 1}, {"name": "rt1ri1"} );
db.webauthn_credentials.createIndex( { "referenceType" : 1, "referenceId": 1, "userId": 1 }, {"name": "rt1ri1uid1"} );
db.webauthn_credentials.createIndex( { "referenceType" : 1, "referenceId": 1, "username": 1 }, {"name": "rt1ri1un1"} );
db.webauthn_credentials.createIndex( { "referenceType" : 1, "referenceId": 1, "credentialId": 1 }, {"name": "rt1ri1cid1"} );
db.webauthn_credentials.createIndex( { "referenceType" : 1, "referenceId": 1, "aaguid": 1 }, {"name": "rt1ri1a1"} );
db.webauthn_credentials.reIndex();

// "auth_flow_ctx" collection
db.auth_flow_ctx.dropIndexes();
db.auth_flow_ctx.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.auth_flow_ctx.createIndex({ "transactionId" : 1, "version": -1 }, {"name": "t1v_1"});
db.auth_flow_ctx.createIndex({ "expire_at" : 1}, {"name": "e1", "expireAfterSeconds": 0});
db.auth_flow_ctx.reIndex();

// "domains" collection
db.domains.dropIndexes();
db.domains.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.domains.createIndex( { "referenceType" : 1, "referenceId": 1}, {"name": "ri1rt1"} );
db.domains.createIndex( { "referenceType" : 1, "referenceId": 1, "hrid": 1 }, {"name": "ri1rt1h1"}  );
db.domains.reIndex();

// "bot_detections" collection
db.bot_detections.dropIndexes();
db.bot_detections.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.bot_detections.createIndex( { "referenceId": 1, "referenceType" : 1 }, {"name": "ri1rt1"} );
db.bot_detections.reIndex();

// "authentication_device_notifiers" collection
db.authentication_device_notifiers.dropIndexes();
db.authentication_device_notifiers.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.authentication_device_notifiers.createIndex( { "referenceId": 1, "referenceType" : 1 }, {"name": "ri1rt1"} );
db.authentication_device_notifiers.reIndex();

// "device_identifiers" collection
db.device_identifiers.dropIndexes();
db.device_identifiers.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.device_identifiers.createIndex( { "referenceId": 1, "referenceType" : 1 }, {"name": "ri1rt1"} );
db.device_identifiers.reIndex();

// "devices" collection
db.devices.dropIndexes();
db.devices.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.devices.createIndex( { "referenceId": 1, "referenceType" : 1 }, {"name": "ri1rt1"} );
db.devices.createIndex({ "expires_at" : 1}, {"name": "e1", "expireAfterSeconds": 0});
db.devices.reIndex();

// "i18n_dictionaries" collection
db.i18n_dictionaries.dropIndexes();
db.i18n_dictionaries.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.i18n_dictionaries.createIndex( { "referenceType": 1, "referenceId" : 1 }, {"name": "rt1ri1"} );
db.i18n_dictionaries.createIndex( { "referenceId": 1, "referenceType" : 1, "name" : 1 }, {"name": "ri1rt1n1"} );
db.i18n_dictionaries.reIndex();

// "node_monitoring" collection
db.node_monitoring.dropIndexes();
db.node_monitoring.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.node_monitoring.createIndex( { "updatedAt": 1 }, {"name": "u1"} );
db.node_monitoring.reIndex();

// "notification_acknowledgements" collection
db.notification_acknowledgements.dropIndexes();
db.notification_acknowledgements.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.notification_acknowledgements.createIndex( { "resourceId": 1, "type": 1, "audienceId": 1 }, {"name": "ri1rt1a1"} );
db.notification_acknowledgements.reIndex();

// "password_histories" collection
db.password_histories.dropIndexes();
db.password_histories.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.password_histories.createIndex( { "referenceType": 1, "referenceId" : 1 }, {"name": "rt1ri1"} );
db.password_histories.createIndex( { "referenceId": 1, "referenceType" : 1, "userId": 1 }, {"name": "ri1rt1u1"} );
db.password_histories.reIndex();

// "rate_limit" collection
db.rate_limit.dropIndexes();
db.rate_limit.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.rate_limit.createIndex({ "userId" : 1, "client" : 1, "factorId" : 1}, {"name": "u1c1f1"});
db.rate_limit.reIndex();

// "verify_attempt" collection
db.verify_attempt.dropIndexes();
db.verify_attempt.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.verify_attempt.createIndex({ "userId" : 1, "client" : 1, "factorId" : 1}, {"name": "u1c1f1"});
db.verify_attempt.reIndex();

// "service_resources" collection
db.service_resources.dropIndexes();
db.service_resources.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.service_resources.createIndex( { "referenceId": 1, "referenceType" : 1 }, {"name": "ri1rt1"} );
db.service_resources.reIndex();

// "themes" collection
db.themes.dropIndexes();
db.themes.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.themes.createIndex( { "referenceType" : 1, "referenceId": 1 }, {"name": "rt1ri1"} );
db.themes.reIndex();

// "user_notifications" collection
db.user_notifications.dropIndexes();
db.user_notifications.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.user_notifications.createIndex( { "audienceId" : 1, "type": 1, "status": 1 }, {"name": "a1t1s1"} );
db.user_notifications.reIndex();

// "user_activities" collection
db.user_activities.dropIndexes();
db.user_activities.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.user_activities.createIndex( { "referenceType" : 1, "referenceId": 1 }, {"name": "rt1ri1"} );
db.user_activities.createIndex( { "createdAt": 1 }, {"name": "c1"} );
db.user_activities.createIndex( { "expireAt" : 1 }, {"name": "e1", "expireAfterSeconds": 0});
db.user_activities.reIndex();

// "system_tasks" collection
db.system_tasks.dropIndexes();
db.system_tasks.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.system_tasks.reIndex();

// "organizations" collection
db.organizations.dropIndexes();
db.organizations.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.organizations.reIndex();

// "installation" collection
db.installation.dropIndexes();
db.installation.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.installation.reIndex();

// "alert_triggers" collection
db.alert_triggers.dropIndexes();
db.alert_triggers.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.alert_triggers.reIndex();

// "alert_notifiers" collection
db.alert_notifiers.dropIndexes();
db.alert_notifiers.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.alert_notifiers.reIndex();

// "request_objects" collection
db.request_objects.dropIndexes();
db.request_objects.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.request_objects.createIndex( { "expire_at" : 1 }, {"name": "e1", "expireAfterSeconds": 0});
db.request_objects

// "pushed_authorization_requests" collection
db.pushed_authorization_requests.dropIndexes();
db.pushed_authorization_requests.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.pushed_authorization_requests.createIndex( { "expire_at" : 1 }, {"name": "e1", "expireAfterSeconds": 0});
db.pushed_authorization_requests.reIndex();

// "ciba_auth_requests" collection
db.ciba_auth_requests.dropIndexes();
db.ciba_auth_requests.createIndex( { "_id": 1 }, {"name": "_id1"} );
db.ciba_auth_requests.createIndex( { "expire_at" : 1 }, {"name": "e1", "expireAfterSeconds": 0});
db.ciba_auth_requests.reIndex();