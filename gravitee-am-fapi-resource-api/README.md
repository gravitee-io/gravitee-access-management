# Build Docker image

```
mvn package com.google.cloud.tools:jib-maven-plugin:dockerBuild  -Dimage=<image name>
```

# Run Image

The image is configured to bind on 0.0.0.0 and listen the port 9443.
There are two volumes to store respectively the truststore elements and keystore elements.

* /var/fapi/keystore
* /var/fapi/truststore

```
docker run <image name> -certificateHeader <argValue> -trustStorePath ...
```
