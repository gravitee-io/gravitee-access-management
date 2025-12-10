in $GRAVITEE_AM_HOME directory, after building the project:

`
npm --prefix docker/local-stack run stack:init:ciba
npm --prefix docker/local-stack run stack:init:copy-am
npm --prefix docker/local-stack run stack:init:build
`
It builds CIBA test service Docker image.
Then, it copies result of mvn install to build-ctx directory so the docker-compose can build AM images

to run locally with Mongo:
`npm --prefix docker/local-stack run stack:dev:setup:mongo:4`

to run locally with Postgres:
`
npm --prefix docker/local-stack run stack:init:plugins:psql
npm --prefix docker/local-stack run stack:dev:setup:psql:17
`

Tests Mongo:
`
npm --prefix gravitee-am-test run ci:management:parallel
npm --prefix gravitee-am-test run ci:gateway
`

Tests PSQL:
`
REPOSITORY_TYPE=jdbc npm --prefix gravitee-am-test run ci:management:parallel
REPOSITORY_TYPE=jdbc npm --prefix gravitee-am-test run ci:gateway
`