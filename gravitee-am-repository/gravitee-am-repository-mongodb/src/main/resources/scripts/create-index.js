
db.clients.createIndex( { "domain" : 1 } );
db.clients.createIndex( { "domain" : 1, "clientId": 1 } );
db.clients.createIndex( { "identities" : 1 });
db.clients.createIndex( { "certificate" : 1 });
db.clients.createIndex( { "authorizedGrantTypes" : 1 });
db.scopes.createIndex( { "domain" : 1 } );
db.scopes.createIndex( { "domain" : 1, "key" : 1 } );
db.scope_aprovals.createIndex( { "domain" : 1, "clientId": 1, "userId": 1 } );
db.roles.createIndex( { "domain" : 1 } );
db.users.createIndex( { "domain" : 1 } );
db.users.createIndex( { "username": 1, "domain" : 1 } );

db.clients.reIndex();
db.scopes.reIndex();
db.scope_aprovals.reIndex();
db.roles.reIndex();
db.users.reIndex();