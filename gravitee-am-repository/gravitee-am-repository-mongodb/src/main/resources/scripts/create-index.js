// "applications" collection
db.applications.dropIndexes();
db.applications.createIndex( { "domain" : 1 } );
db.applications.createIndex( { "domain" : 1, "name": 1 } );
db.applications.createIndex( { "domain" : 1, "settings.oauth.clientId": 1 } );
db.applications.createIndex( { "domain" : 1, "settings.oauth.grantTypes": 1 } );
db.applications.createIndex( { "identities": 1 } );
db.applications.createIndex( { "certificate": 1 } );
db.applications.createIndex( { "updatedAt" : -1 } );
db.applications.reIndex();

// "access_tokens" collection
db.access_tokens.dropIndexes();
db.access_tokens.createIndex( { "token" : 1 } );
db.access_tokens.createIndex( { "client" : 1 } );
db.access_tokens.createIndex( { "authorization_code" : 1 } );
db.access_tokens.createIndex( { "subject" : 1 } );
db.access_tokens.createIndex( { "domain": 1, "client": 1, "subject": 1 } );
db.access_tokens.reIndex();

// "authorization_codes" collection
db.authorization_codes.dropIndexes();
db.authorization_codes.createIndex( { "code" : 1 } );
db.authorization_codes.createIndex( { "transactionId" : 1 } );
db.authorization_codes.reIndex();

// "certificates" collection
db.certificates.dropIndexes();
db.certificates.createIndex( { "domain" : 1 } );
db.certificates.reIndex();

// "emails" collection
db.emails.dropIndexes();
db.emails.createIndex( { "referenceType" : 1, "referenceId": 1 } );
db.emails.createIndex( { "referenceType" : 1, "referenceId": 1, "template": 1 } );
db.emails.createIndex( { "referenceType" : 1, "referenceId": 1, "client" : 1, "template": 1 } );
db.emails.reIndex();

// "entrypoints" collection
db.entrypoints.dropIndexes();
db.entrypoints.createIndex( { "_id" : 1, "organizationId": 1 } );
db.entrypoints.reIndex();

// "environments" collection
db.environments.dropIndexes();
db.environments.createIndex( { "_id" : 1, "organizationId": 1 } );
db.environments.reIndex();

// "events" collection
db.events.dropIndexes();
db.events.createIndex( { "updatedAt" : 1 } );
db.events.reIndex();

// "extension_grants" collection
db.extension_grants.dropIndexes();
db.extension_grants.createIndex( { "domain" : 1 } );
db.extension_grants.createIndex( { "domain" : 1, "name": 1 } );
db.extension_grants.reIndex();

// "factors" collection
db.factors.dropIndexes();
db.factors.createIndex( { "domain" : 1 } );
db.factors.createIndex( { "domain" : 1, "factorType": 1 } );
db.factors.reIndex();

// "forms" collection
db.forms.dropIndexes();
db.forms.createIndex( { "referenceType" : 1, "referenceId": 1 } );
db.forms.createIndex( { "referenceType" : 1, "referenceId": 1, "template": 1 } );
db.forms.createIndex( { "referenceType" : 1, "referenceId": 1, "client" : 1, "template": 1 } );
db.forms.reIndex();

// "groups" collection
db.groups.dropIndexes();
db.groups.createIndex( { "referenceType" : 1, "referenceId": 1 } );
db.groups.createIndex( { "referenceType" : 1, "referenceId": 1, "name": 1 } );
db.groups.reIndex();

// "identities" collection
db.identities.dropIndexes();
db.identities.createIndex( { "referenceType" : 1, "referenceId": 1 } );
db.identities.reIndex();

// "login_attempts" collection
db.login_attempts.dropIndexes();
db.login_attempts.createIndex( { "domain" : 1, "client": 1, "username": 1 } );
db.login_attempts.reIndex();

// "memberships" collection
db.memberships.dropIndexes();
db.memberships.createIndex( { "referenceType" : 1, "referenceId": 1 } );
db.memberships.createIndex( { "referenceId" : 1, "memberId": 1 } );
db.memberships.reIndex();

// "flows" collection
db.flows.dropIndexes();
db.flows.createIndex( { "referenceType" : 1, "referenceId": 1 } );
db.flows.createIndex( { "referenceType" : 1, "referenceId": 1, "application": 1 } );
db.flows.createIndex( { "referenceType" : 1, "referenceId": 1, "_id": 1 } );
db.flows.reIndex();

// "refresh_tokens" collection
db.refresh_tokens.dropIndexes();
db.refresh_tokens.createIndex( { "token" : 1 } );
db.refresh_tokens.createIndex( { "subject" : 1 } );
db.refresh_tokens.createIndex( { "domain": 1, "client": 1, "subject": 1 } );
db.refresh_tokens.reIndex();

// "reporters" collection
db.reporters.dropIndexes();
db.reporters.createIndex( { "domain" : 1 } );
db.reporters.reIndex();

// "roles" collection
db.roles.dropIndexes();
db.roles.createIndex( { "referenceType" : 1, "referenceId": 1 } );
db.roles.createIndex( { "referenceType" : 1, "referenceId": 1, "name": 1, "scope": 1 } );
db.roles.reIndex();

// "scopes" collection
db.scopes.dropIndexes();
db.scopes.createIndex( { "domain" : 1 } );
db.scopes.createIndex( { "domain" : 1, "key": 1 } );
db.scopes.reIndex();

// "scope_approvals" collection
db.scope_approvals.dropIndexes();
db.scope_approvals.createIndex( { "transactionId" : 1 } );
db.scope_approvals.createIndex( { "domain" : 1, "userId": 1 } );
db.scope_approvals.createIndex( { "domain" : 1, "clientId": 1, "userId": 1 } );
db.scope_approvals.createIndex( { "domain" : 1, "clientId": 1, "userId": 1, "scope": 1 } );
db.scope_approvals.reIndex();

// "tags" collection
db.tags.dropIndexes();
db.tags.createIndex( { "_id" : 1, "organizationId": 1 } );
db.tags.reIndex();

// "users" collection
db.users.dropIndexes();
db.users.createIndex( { "referenceType" : 1, "referenceId": 1 } );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "email": 1 } );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "additionalInformation.email": 1 } );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "username": 1 } );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "displayName": 1 } );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "firstName": 1 } );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "lastName": 1 } );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "externalId": 1 } );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "username": 1, "source": 1 } );
db.users.createIndex( { "referenceType" : 1, "referenceId": 1, "externalId": 1, "source": 1 } );
db.users.reIndex();

// "uma_access_policies" collection
db.uma_access_policies.dropIndexes();
db.uma_access_policies.createIndex( { "domain" : 1 } );
db.uma_access_policies.createIndex( { "resource" : 1 } );
db.uma_access_policies.createIndex( { "domain" : 1, "resource": 1 } );
db.uma_access_policies.createIndex( { "updatedAt" : -1 } );
db.uma_access_policies.reIndex();

// "uma_resource_set" collection
db.uma_resource_set.dropIndexes();
db.uma_resource_set.createIndex( { "domain" : 1 } );
db.uma_resource_set.createIndex( { "domain" : 1, "clientId": 1 } );
db.uma_resource_set.reIndex();

// "webauthn_credentials" collection
db.webauthn_credentials.dropIndexes();
db.webauthn_credentials.createIndex( { "referenceType" : 1, "referenceId": 1, "userId": 1 } );
db.webauthn_credentials.createIndex( { "referenceType" : 1, "referenceId": 1, "username": 1 } );
db.webauthn_credentials.createIndex( { "referenceType" : 1, "referenceId": 1, "credentialId": 1 } );
db.webauthn_credentials.createIndex( { "referenceType" : 1, "referenceId": 1, "aaguid": 1 } );
db.webauthn_credentials.reIndex();

// "auth_flow_ctx" collection
db.auth_flow_ctx.dropIndexes();
db.auth_flow_ctx.createIndex({ "transactionId" : 1, "version": -1 });
db.auth_flow_ctx.reIndex();

// "domains" collection
db.domains.dropIndexes();
db.domains.createIndex( { "referenceType" : 1, "referenceId": 1, "hrid": 1 } );
db.domains.reIndex();
