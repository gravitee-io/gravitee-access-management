# Build Docker image

```
mvn package com.google.cloud.tools:jib-maven-plugin:dockerBuild  -Dimage=<image name>
```

# Run Image

The image is configured to bind on 0.0.0.0 and listen the port 8080.

# Request

Authenticate user using password (in clear text)

```
curl -vv -X POST http://localhost:8080/login -d '{"username":"user01", "password":"Test1234567!" }' -H'Content-Type: application/json'

{
  "username" : "user01",
  "preferred_username" : "alice",
  "given_name" : "Wonder",
  "first_name" : "Alice",
  "email" : "user01@acme.fr"
}
```

Get profile by username without password

```
curl -vv -X POST http://localhost:8080/username -d '{"username":"user01"}' -H'Content-Type: application/json'

{
  "username" : "user01",
  "preferred_username" : "alice",
  "given_name" : "Wonder",
  "first_name" : "Alice",
  "email" : "user01@acme.fr"
}
```
