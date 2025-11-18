# -----------------------------------------------------------------------------
#                              CUSTOM FUNCTION
# -----------------------------------------------------------------------------
define prepare
	echo "preparing working $(1) directory version $(2) \n"
	mkdir -p .working/$(1)
	cp gravitee-am-$(1)/gravitee-am-$(1)-standalone/gravitee-am-$(1)-standalone-distribution/gravitee-am-$(1)-standalone-distribution-zip/target/gravitee-am-$(1)-standalone-$(2).zip .working/$(1)
	cp docker/$(1)/Dockerfile-dev .working/$(1)/Dockerfile
	unzip -uj .working/$(1)/gravitee-am-$(1)-standalone-$(2).zip '*/plugins/*' -d .working/$(1)/plugins
	unzip -uj .working/$(1)/gravitee-am-$(1)-standalone-$(2).zip '*/config/*' -d .working/$(1)/config
	unzip -uj .working/$(1)/gravitee-am-$(1)-standalone-$(2).zip '*/templates/*' -d .working/$(1)/templates
	sed -i.bkp 's/<appender-ref ref=\"async-file\" \/>/<appender-ref ref=\"async-console\" \/>/' .working/$(1)/config/logback.xml
	sed -i.bkp 's/<appender-ref ref=\"FILE\" \/>/<appender-ref ref=\"STDOUT\" \/>/' .working/$(1)/config/logback.xml
	echo "$(1) working directory preparation is done.\n"
endef

define addDefaultIssuer
	echo "adding default issuer $(2) to $(1) version $(3) \n"
	echo "" >> .working/$(1)/config/gravitee.yml
	echo "" >> .working/$(1)/config/gravitee.yml
	echo "#Openid settings, override default issuer" >> .working/$(1)/config/gravitee.yml
	echo "oidc:" >> .working/$(1)/config/gravitee.yml
	echo "  iss:$(2)" >> .working/$(1)/config/gravitee.yml
	unzip .working/$(1)/gravitee-am-$(1)-standalone-$(3).zip -d .working/$(1)
	cp .working/$(1)/config/gravitee.yml .working/$(1)/gravitee-am-$(1)-standalone-$(3)/config/gravitee.yml
	cd .working/$(1) && zip -u gravitee-am-$(1)-standalone-$(3).zip ./gravitee-am-$(1)-standalone-$(3)/config/gravitee.yml
	rm -rf .working/$(1)/gravitee-am-$(1)-standalone-$(3)
endef

define addNetworkToCompose
	echo "adding network $(1) to docker compose file"
	echo "" >> $(1)
	echo "" >> $(1)
	echo "networks:" >> $(1)
	echo "  default:" >> $(1)
	echo "    external:" >> $(1)
	echo "      name: $(2)" >> $(1)
endef

define generateCertificate
	cd .working/certificate \
	&& keytool -genkeypair \
		-alias mytestkey \
		-keyalg RSA \
		-dname "CN=Web Server,OU=Unit,O=Organization,L=City,S=State,C=US" \
		-keypass changeme \
		-keystore server.jks \
		-storepass letmein \
	&& keytool -genkeypair \
        -alias my3072key \
        -keyalg RSA \
        -keysize 3072 \
        -sigalg SHA384withRSA \
        -dname "CN=Web Server,OU=Unit,O=Organization,L=City,S=State,C=US" \
        -keypass changeme \
        -keystore server3072.jks \
        -storepass letmein
	&& keytool -genkeypair \
        -alias my4096key \
        -keyalg RSA \
        -keysize 4096 \
        -sigalg SHA512withRSA \
        -dname "CN=Web Server,OU=Unit,O=Organization,L=City,S=State,C=US" \
        -keypass changeme \
        -keystore server4096.jks \
        -storepass letmein
endef

define clone
	printf "Cloning \033[32m%s\033[0m branch \033[33m%s\033[0m into folder \033[35m%s\033[0m\n" $(1) $(3) $(2)
	$(shell if cd $(2); then git checkout . ; else printf "git repository was not existing yet" ; fi &>/dev/null)
	git clone -b $(3) $(1) $(2) 2> /dev/null || (cd $(2) ; git pull origin $(3))
endef
# -----------------------------------------------------------------------------
#                                ENV VARIABLE
# -----------------------------------------------------------------------------

# Access Management release retrieved asking maven to retrieve it
GIO_AM_VERSION:=$(shell cat .working/.version 2>/dev/null)
GIO_AM_NETWORK:=gio_am_network
GIO_AM_GATEWAY_IMAGE:=graviteeio/am-gateway
GIO_AM_MANAGEMENT_API_IMAGE:=graviteeio/am-management-api
GIO_AM_MANAGEMENT_UI_IMAGE:=graviteeio/am-management-ui

GIO_AM_GATEWAY_PLUGINS:=gravitee-am-gateway/gravitee-am-gateway-standalone/gravitee-am-gateway-standalone-distribution/src/main/resources/plugins/
GIO_AM_MANAGEMENT_API_PLUGINS:=gravitee-am-management-api/gravitee-am-management-api-standalone/gravitee-am-management-api-standalone-distribution/src/main/resources/plugins/
OIDC_TEST_SOURCE:="https://github.com/openid-certification/oidctest.git"
OIDC_TEST_BRANCH:="stable-release-1.1.x"
OIDC_TEST_FOLDER:=".working/oidctest"

# Retrieve current file name, must be done before doing "include .env" ...
makefile := $(MAKEFILE_LIST)
# Projects list (extracted from .env file, looking for every XXX_REPOSITORY variables)

# -----------------------------------------------------------------------------
#                                 Main targets
# -----------------------------------------------------------------------------

help: ## Print this message
	@grep -E '^[a-zA-Z_-]+:.*?## .*$$' $(makefile) | awk 'BEGIN {FS = ":.*?## "}; {printf "\033[36m%-20s\033[0m %s\n", $$1, $$2}'

gravitee: ## Stop and delete containers if exists, then install and run new ones
ifneq ($(wildcard .working/compose),)
	@echo "Stopping gravitee and cleaning containers and images"
	@make stop
	@make prune
else
	@echo "No previous gravitee .working/compose dir, no docker content to delete..."
	@echo "Deleting working dir"
	rm -rf .working
endif
	@make version
	@make install
	@make run

certificate: ## Generate certificate that can be used
	@mkdir -p .working/certificate
	@$(call generateCertificate);

clean: # remove .working directory
	@rm -rf .working/gateway
	@rm -rf .working/management-api
	@mvn clean -pl '!gravitee-am-ui'

version: # Get version and save it into a file
	@mkdir -p .working
	@rm -f .working/.version
	@echo "$(shell mvn org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version 2> /dev/null | grep '^[0-9]\+\.[0-9]\+\.[0-9]\+.*')" > .working/.version

install: clean ## Compile, test, package then Set up working folder, you can skip test by doing : make OPTIONS=-DskipTests install
ifeq ($(GIO_AM_VERSION),)
	@echo "Current build version is : $(GIO_AM_VERSION)"
	@echo "no version found, retrieving current maven version"
	@make version
	@make install
else
	@echo "Current build version is : $(GIO_AM_VERSION)"
	@mvn clean install -pl '!gravitee-am-ui' $(OPTIONS)
	@$(foreach project,gateway management-api, $(call prepare,$(project),$(GIO_AM_VERSION)))
	@$(call addDefaultIssuer,gateway, "http://gateway:8092/dcr/oidc",$(GIO_AM_VERSION))
endif

build: # Build docker images (require install to be run before)
	cd .working/gateway && docker build --build-arg GRAVITEEAM_VERSION=$(GIO_AM_VERSION) -t $(GIO_AM_GATEWAY_IMAGE):$(GIO_AM_VERSION) .
	cd .working/management-api && docker build --build-arg GRAVITEEAM_VERSION=$(GIO_AM_VERSION) -t $(GIO_AM_MANAGEMENT_API_IMAGE):$(GIO_AM_VERSION) .
	cd gravitee-am-ciba-delegated-service && mvn compile com.google.cloud.tools:jib-maven-plugin:3.5.0:dockerBuild -Dimage=local/ciba-delegated-service:$(GIO_AM_VERSION)

env: # Set up .env file for gravitee docker-compose
	@mkdir -p .working/compose
	@echo "GIO_AM_VERSION=$(GIO_AM_VERSION)" > .working/compose/.env
	@echo "NGINX_PORT=80" >> .working/compose/.env

network: # Create and add an external network to gravitee access management docker-compose
	@cp docker/compose/docker-compose-dev.yml .working/compose/docker-compose.yml
	@$(call addNetworkToCompose, ".working/compose/docker-compose.yml",${GIO_AM_NETWORK});
	@docker network inspect $(GIO_AM_NETWORK) &>/dev/null || docker network create --driver bridge $(GIO_AM_NETWORK)

run: build env network ## Create .env and network then start gravitee access management
	@cd .working/compose; docker-compose up -d gateway management ciba
	@echo "To start and stop, use \"make stop; make start\" command"

start: ## Start gravitee Access Management containers
ifneq ($(wildcard .working/compose),)
	@cd .working/compose; docker-compose start mongodb gateway management ciba
else
	@echo "Please use \"make run\" for the first time."
endif

startUi: ## Run UI (yarn serve).
	@cd gravitee-am-ui; yarn serve

startMongo: ## Start gravitee Access Management mongo container only
ifneq ($(wildcard .working/compose),)
	@cd .working/compose; docker-compose start mongodb
else
	@echo "Please use \"make run\" for the first time."
endif

stop: ## Stop gravitee Access Management running containers
ifneq ($(wildcard .working/compose),)
	@cd .working/compose; docker-compose stop || true
endif

status: ## See Access Management containers status
ifneq ($(wildcard .working/compose),)
	@cd .working/compose; docker-compose ps
endif

connectMongo: ## Connect to mongo repository on gravitee-am database
	@docker exec -ti gio_am_mongodb mongo gravitee-am

reset: stop deleteData start ## Stop containers, delete mongodb data and restart container

postman: ## Run postman non regression test (require newman npm module)
	@newman run postman/collections/graviteeio-am-oauth2-collection-app-version.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-oauth2-par-version.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-openid-core-collection-app-version.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-openid-core-request-object-collection.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-openid-dcr-collection-app-version.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-openid-jarm-collection.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-openid-fapi-collection.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-scim-collection-app-version.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-scope-management-collection.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-client-management-collection-app-version.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-flows-collection-app-version.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-login-collection-app-version.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-logout-collection-app-version.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-user-management-collection-app-version.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-api-management-collection.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-uma2-app-version-collection.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-vhost-collection.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-environment-collection.json -e postman/environment/dev.json --ignore-redirects --insecure --bail
	@newman run postman/collections/graviteeio-am-self-account-management-collection-app-version.json -e postman/environment/dev.json --ignore-redirects --insecure --bail

oidctest-run: oidctest-install oidctest-start ## Run openid-certification tools, using same docker network

oidctest-install: # Clone openid-certification tools and set same docker network
	@echo "Require to update /etc/hosts with \"127.0.0.1 op-test op rp-test gateway\"!"
	@$(call clone,${OIDC_TEST_SOURCE},${OIDC_TEST_FOLDER},${OIDC_TEST_BRANCH});
	@$(call addNetworkToCompose, ".working/oidctest/docker/docker-compose.yml", ${GIO_AM_NETWORK});

oidctest-start: ## Start openid-certificatoin test containers
	@cd $(OIDC_TEST_FOLDER)/docker ; docker-compose up -d

oidctest-stop: ## Stop openid-certificatoin test containers
	@cd $(OIDC_TEST_FOLDER)/docker ; docker-compose stop

oidctest-status: ## See OIDC containers status
	@cd $(OIDC_TEST_FOLDER)/docker ; docker-compose ps

startAll: start oidctest-start ## Start all running containers

stopAll: stop oidctest-stop ## Stop all running containers

deleteData: # remove mongodb data
	@rm -rf .working/compose/data/am-mongodb

deleteContainer: # delete image
ifneq ($(wildcard .working/compose),)
	@$(shell docker-compose -f .working/compose/docker-compose.yml down &>/dev/null || true)
	@$(shell docker-compose -f .working/oidctest/docker/docker-compose.yml down &>/dev/null || true)
endif

deleteImage: # delete image
ifneq ($(GIO_AM_VERSION),)
	@docker rmi -f $(GIO_AM_GATEWAY_IMAGE):$(GIO_AM_VERSION) || true
	@docker rmi -f $(GIO_AM_MANAGEMENT_API_IMAGE):$(GIO_AM_VERSION) || true
endif

deleteNetwork: # delete network
	@$(shell docker network rm $(GIO_AM_NETWORK) &>/dev/null || true)

deleteOidcTest: # delete Oidc Test
	@cd $(OIDC_TEST_FOLDER)/docker ; docker-compose down
	@rm -rf $(OIDC_TEST_FOLDER)

prune: deleteData deleteContainer deleteImage deleteNetwork ## /!\ Erase all (repositories folder & volumes, containers, images & data)
	@rm -rf .working

plugins: # Copy plugins to Gateway and Management API
	@make version
	@make commonPluginsManagement
	@make commonPluginsGateway
	@make pluginsGateway
	@make pluginsManagement

pluginsJdbc: # Copy plugins to Gateway and Management API
	@make version
	@make commonPluginsManagement
	@make commonPluginsGateway
	@make pluginsJdbcGateway
	@make pluginsJdbcManagement

commonPluginsGateway: # Copy plugins to Gateway
	@mkdir -p $(GIO_AM_GATEWAY_PLUGINS)
	@rm -fr $(GIO_AM_GATEWAY_PLUGINS)/.work
	@rm -fr $(GIO_AM_GATEWAY_PLUGINS)/*.zip
	@cp -fr gravitee-am-gateway/gravitee-am-gateway-standalone/gravitee-am-gateway-standalone-distribution/target/distribution/plugins/*.zip $(GIO_AM_GATEWAY_PLUGINS)


commonPluginsManagement: # Copy plugins to Management API
	@mkdir -p $(GIO_AM_MANAGEMENT_API_PLUGINS)
	@rm -fr $(GIO_AM_MANAGEMENT_API_PLUGINS)/.work
	@rm -fr $(GIO_AM_MANAGEMENT_API_PLUGINS)/*.zip
	@cp -fr gravitee-am-management-api/gravitee-am-management-api-standalone/gravitee-am-management-api-standalone-distribution/target/distribution/plugins/*.zip $(GIO_AM_MANAGEMENT_API_PLUGINS)

pluginsGateway: # Copy plugins to Gateway
	@cp -fr gravitee-am-repository/gravitee-am-repository-mongodb/target/gravitee-am-repository-mongodb-$(GIO_AM_VERSION).zip $(GIO_AM_GATEWAY_PLUGINS)
	@cp -fr gravitee-am-reporter/gravitee-am-reporter-mongodb/target/gravitee-am-reporter-mongodb-$(GIO_AM_VERSION).zip $(GIO_AM_GATEWAY_PLUGINS)

pluginsManagement: # Copy plugins to Management API
	@cp -fr gravitee-am-repository/gravitee-am-repository-mongodb/target/gravitee-am-repository-mongodb-$(GIO_AM_VERSION).zip $(GIO_AM_MANAGEMENT_API_PLUGINS)
	@cp -fr gravitee-am-reporter/gravitee-am-reporter-mongodb/target/gravitee-am-reporter-mongodb-$(GIO_AM_VERSION).zip $(GIO_AM_MANAGEMENT_API_PLUGINS)

pluginsJdbcGateway: # Copy plugins to Gateway with JDBC Repository
	@cp -fr gravitee-am-repository/gravitee-am-repository-jdbc/target/gravitee-am-repository-jdbc-$(GIO_AM_VERSION).zip $(GIO_AM_GATEWAY_PLUGINS)
	@cp -fr gravitee-am-reporter/gravitee-am-reporter-jdbc/target/gravitee-am-reporter-jdbc-$(GIO_AM_VERSION).zip $(GIO_AM_GATEWAY_PLUGINS)

pluginsJdbcManagement: # Copy plugins to Management API with JDBC Repository
	@cp -fr gravitee-am-repository/gravitee-am-repository-jdbc/target/gravitee-am-repository-jdbc-$(GIO_AM_VERSION).zip $(GIO_AM_MANAGEMENT_API_PLUGINS)
	@cp -fr gravitee-am-reporter/gravitee-am-reporter-jdbc/target/gravitee-am-reporter-jdbc-$(GIO_AM_VERSION).zip $(GIO_AM_MANAGEMENT_API_PLUGINS)

startPostgres: ## Start PostgresSQL 9.6 container
	@docker-compose -f docker/compose/docker-compose-relational-databases.yml up --no-recreate --detach pgsql

startPostgres13: ## Start PostgresSQL 13.1 container
	@docker-compose -f docker/compose/docker-compose-relational-databases.yml up --no-recreate --detach pgsql_13

startMySQL: ## Start MySQL 5.7 container
	@docker-compose -f docker/compose/docker-compose-relational-databases.yml up --no-recreate --detach mysql

startMySQL8: ## Start MySQL 8.0 container
	@docker-compose -f docker/compose/docker-compose-relational-databases.yml up --no-recreate --detach mysql_8

startMariaDB: ## Start MariaDB 10.2 container
	@docker-compose -f docker/compose/docker-compose-relational-databases.yml up --no-recreate --detach mariadb

startMariaDB_10_5: ## Start MariaDB 10.5 container
	@docker-compose -f docker/compose/docker-compose-relational-databases.yml up --no-recreate --detach mariadb_10_5

startSQLServer: ## Start SQLServer 2017 container
	@docker-compose -f docker/compose/docker-compose-relational-databases.yml up --no-recreate --detach sqlserver

startSQLServer_2019: ## Start SQLServer 2019 container
	@docker-compose -f docker/compose/docker-compose-relational-databases.yml up --no-recreate --detach sqlserver_2019

stopDatabase:
	@docker-compose -f docker/compose/docker-compose-relational-databases.yml stop


.DEFAULT_GOAL := help
.PHONY: all test clean build version postman nvm npm
