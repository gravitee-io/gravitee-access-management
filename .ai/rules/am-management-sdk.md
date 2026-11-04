---
name: AM Management API Java SDK
layer: local
description: The SDK's main sources are generated, not written
dirs: [gravitee-am-management-api-sdk-java]
---

# AM Management API Java SDK

The module commits no main sources. The build generates them with openapi-generator from `docs/mapi/openapi.yaml`, after the antrun steps in `pom.xml` patch a copy of the spec and the generated files.

- To change the client, change the spec or the antrun steps in `pom.xml`. Never hand-edit generated code.
- The generated client uses Vert.x `Future`, not RxJava.
