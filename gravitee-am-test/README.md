# Setup

in `$GRAVITEE_AM_HOME` directory, after building the project (`mvn install`) run:

```
npm --prefix docker/local-stack run stack:init:ciba
npm --prefix docker/local-stack run stack:init:copy-am
npm --prefix docker/local-stack run stack:init:build
```
It builds "CIBA test service" Docker image. \
It also copies AM bundles to the _build-ctx_ directory so that the AM Docker images can be built using the Docker Compose stack file.

# Starting the stack
## with Mongo
```
npm --prefix docker/local-stack run stack:dev:setup:mongo:4
```
## with Postgres
```
npm --prefix docker/local-stack run stack:init:plugins:psql
npm --prefix docker/local-stack run stack:dev:setup:psql:17
```

# Running tests

## Mongo
```
npm --prefix gravitee-am-test run ci:management:parallel
npm --prefix gravitee-am-test run ci:gateway
```

## JDBC
```
REPOSITORY_TYPE=jdbc npm --prefix gravitee-am-test run ci:management:parallel
REPOSITORY_TYPE=jdbc npm --prefix gravitee-am-test run ci:gateway
```