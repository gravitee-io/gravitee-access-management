# -----------------------------------------------------------------------------
#                              CUSTOM FUNCTION
# -----------------------------------------------------------------------------
define prepare
	mkdir -p .working/$(1)
	cp gravitee-am-$(1)/gravitee-am-$(1)-standalone/gravitee-am-$(1)-standalone-distribution/gravitee-am-$(1)-standalone-distribution-zip/target/gravitee-am-$(1)-standalone-$(GIO_AM_VERSION).zip .working/$(1)
	cp docker/$(1)/Dockerfile-dev .working/$(1)/Dockerfile
	unzip -uj .working/$(1)/gravitee-am-$(1)-standalone-$(GIO_AM_VERSION).zip '*/plugins/*' -d .working/$(1)/plugins
	unzip -uj .working/$(1)/gravitee-am-$(1)-standalone-$(GIO_AM_VERSION).zip '*/config/*' -d .working/$(1)/config
	sed -i.bkp 's/<appender-ref ref=\"async-file\" \/>/<appender-ref ref=\"async-console\" \/>/' .working/$(1)/config/logback.xml
	sed -i.bkp 's/<appender-ref ref=\"FILE\" \/>/<appender-ref ref=\"STDOUT\" \/>/' .working/$(1)/config/logback.xml
endef

define addDefaultIssuer
	echo "" >> .working/$(1)/config/gravitee.yml
	echo "" >> .working/$(1)/config/gravitee.yml
	echo "#Openid settings, override default issuer" >> .working/$(1)/config/gravitee.yml
	echo "oidc:" >> .working/$(1)/config/gravitee.yml
	echo "  iss:$(2)" >> .working/$(1)/config/gravitee.yml
	unzip .working/$(1)/gravitee-am-$(1)-standalone-$(GIO_AM_VERSION).zip -d .working/$(1)
	cp .working/$(1)/config/gravitee.yml .working/$(1)/gravitee-am-$(1)-standalone-$(GIO_AM_VERSION)/config/gravitee.yml
	cd .working/$(1) && zip -u gravitee-am-$(1)-standalone-$(GIO_AM_VERSION).zip ./gravitee-am-$(1)-standalone-$(GIO_AM_VERSION)/config/gravitee.yml
	rm -rf .working/$(1)/gravitee-am-$(1)-standalone-$(GIO_AM_VERSION)
endef

define addNetworkToCompose
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
	@echo "$(shell mvn -o org.apache.maven.plugins:maven-help-plugin:2.1.1:evaluate -Dexpression=project.version 2> /dev/null | grep -v '\[')" > .working/.version

install: clean ## Compile, test, package then Set up working folder, you can skip test by doing : make OPTIONS=-DskipTests install
ifeq ($(GIO_AM_VERSION),)
	@echo "no version found, retrieving current maven version"
	@make version
	@make install
else
	@echo "Current build version is : $(GIO_AM_VERSION)"
	@mvn clean install -pl '!gravitee-am-ui' $(OPTIONS)
	@$(foreach project,gateway management-api, $(call prepare,$(project));)
	@$(call addDefaultIssuer,gateway, "http://gateway:8092/dcr/oidc");
endif

build: # Build docker images (require install to be run before)
	cd .working/gateway && docker build --build-arg GRAVITEEAM_VERSION=$(GIO_AM_VERSION) -t $(GIO_AM_GATEWAY_IMAGE):$(GIO_AM_VERSION) .
	cd .working/management-api && docker build --build-arg GRAVITEEAM_VERSION=$(GIO_AM_VERSION) -t $(GIO_AM_MANAGEMENT_API_IMAGE):$(GIO_AM_VERSION) .

env: # Set up .env file for gravitee docker-compose
	@mkdir -p .working/compose
	@echo "GIO_AM_VERSION=$(GIO_AM_VERSION)" > .working/compose/.env
	@echo "NGINX_PORT=80" >> .working/compose/.env

network: # Create and add an external network to gravitee access management docker-compose
	@cp docker/compose/docker-compose-dev.yml .working/compose/docker-compose.yml
	@$(call addNetworkToCompose, ".working/compose/docker-compose.yml",${GIO_AM_NETWORK});
	@docker network inspect $(GIO_AM_NETWORK) &>/dev/null || docker network create --driver bridge $(GIO_AM_NETWORK)

run: build env network start ## Create .env and network then start gravitee access management

start: ## Start gravitee Access Management containers
	@cd .working/compose; docker-compose up -d gateway management

stop: ## Stop gravitee Access Management running containers
	@cd .working/compose; docker-compose stop

status: ## See Access Management containers status
	@cd .working/compose; docker-compose ps

connectMongo: ## Connect to mongo repository on gravitee-am database
	@docker exec -ti gio_am_mongodb mongo gravitee-am

reset: stop deleteData start ## Stop containers, delete mongodb data and restart container

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

startAll: start oidctest-start ## Stop all running containers

stopAll: stop oidctest-stop ## Stop all running containers

deleteData: # remove mongodb data
	@rm -rf .working/compose/data/am-mongodb

deleteContainer: # delete image
	@$(shell docker-compose -f .working/compose/docker-compose.yml down &>/dev/null || true)
	@$(shell docker-compose -f .working/oidctest/docker/docker-compose.yml down &>/dev/null || true)

deleteImage: # delete image
	@docker rmi -f $(GIO_AM_GATEWAY_IMAGE):$(GIO_AM_VERSION)
	@docker rmi -f $(GIO_AM_MANAGEMENT_API_IMAGE):$(GIO_AM_VERSION)

deleteNetwork: # delete network
	@$(shell docker network rm $(GIO_AM_NETWORK) &>/dev/null || true)

deleteOidcTest: # delete Oidc Test
	@cd $(OIDC_TEST_FOLDER)/docker ; docker-compose down
	@rm -rf $(OIDC_TEST_FOLDER)

prune: deleteData deleteContainer deleteImage deleteNetwork ## /!\ Erase all (repositories folder & volumes, containers, images & data)
	@rm -rf .working

.DEFAULT_GOAL := help
.PHONY: all test clean build version
