# Setup local docker-compose stack
[README.md](../docker/local-stack/README.md)

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