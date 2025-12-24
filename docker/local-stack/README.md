# Bootstrap

in `$GRAVITEE_AM_HOME` directory, after building the project (`mvn install`), run:
## MongoDB
```
npm --prefix docker/local-stack run stack:dev:mongo
```
## Postgres
```
npm --prefix docker/local-stack run stack:dev:psql
```

# Stopping and cleaning up the stack
```
npm --prefix docker/local-stack run stack:down
```