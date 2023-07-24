# Gravitee.io Access Management Helm Chart

**Chart supported versions: 2.10.x and higher**

**Versioning change**

After version 1.0.56, version of the Helm Chart will be aligned with the product version.
eg:
- If you want to use AM version 3.x.y you have to use the Chart version 3.x.y
- If you want to use AM version 3.x.z you have to use the Chart version 3.x.z

## Components

This chart will deploy the following:

- Gravitee UI
- Gravitee API
- Gravitee Access Gateway
- MongoDB replica-set (optional dependency)

## Installing

* Add the Gravitee.io helm charts repo
  ```
  $ helm repo add graviteeio https://helm.gravitee.io
  ```
* Install it
  ```
  $ helm install --name graviteeio-am graviteeio/am

**Note:** If you're using Helm 3, the name parameter is no more valid. Please check https://helm.sh/docs/faq/#release-names-are-now-scoped-to-the-namespace

## Create a chart archive

To package this chart directory into a chart archive, run:

```
$ helm package .
```

## Installing the Chart

To install the chart from the Helm repository with the release name `graviteeio-am`:

```bash
$ helm install --name graviteeio-am graviteeio/am
```

To install the chart using the chart archive, run:

```
$ helm install am-1.0.0.tgz
```

## Configuration

The following tables list the configurable parameters of the Gravitee chart and their default values.

You can rely on kubernetes _ConfigMaps_ and _Secrets_ to initialize Gravitee settings since AM 3.15.0.
To use this feature, you have to create the ServiceAccount that allows AM to connect to the Kubernetes API (the helm chart should do it by default) and then you simply have to define your application settings like this:

* for a Secret : `kubernetes://<namespace>/secrets/<my-secret-name>/<my-secret-key>`
* for a ConfigMap : `kubernetes://<namespace>/configmaps/<my-configmap-name>/<my-configmap-key>`


Here is an example for the mongodb uri initialized from the `mongo` secret deployed in the `default` namespace:

```yaml
mongo:
  uri: kubernetes://default/secrets/mongo/mongouri
```

If you need to access a secret, you have to create a role within your namespace.

If you are deploying in another namespace and you need to access a secret there, you have to create a separate role in that namespace. The two roles can have the same name, but they are completely separate objects - each role only gives access to the namespace it is created in.

For more information about roles, see [Role and ClusterRole](https://kubernetes.io/docs/reference/access-authn-authz/rbac/#role-and-clusterrole) in the [Kubernetes documentation](https://kubernetes.io/docs/).

### Shared configuration

To configure common features such as:

- chaos testing (see
  [chaoskube](https://github.com/kubernetes/charts/tree/master/stable/chaoskube)
  chart)
- configuration database (see
  [mongodb-replicaset](https://github.com/kubernetes/charts/tree/master/stable/mongodb-replicaset)

| Parameter              | Description        | Default |
| ---------------------- | ------------------ | ------- |
| `chaos.enabled`        | Enable Chaos test  | false   |


### Mongo

#### MongoDB connections

There are three ways to configure MongoDB connections.

The most simple is to provide the [MongoDB URI](https://docs.mongodb.com/manual/reference/connection-string/).

| Parameter             | Description                                | Default                    |
| --------------------- | ------------------------------------------ | -------------------------- |
| `mongo.uri`           | Mongo URI                                  | `null`                     |

If no `mongo.uri` is provided, you can provide a `mongo.servers` raw definition in combination with `mongo.dbname`, plus
eventual authentication configuration:

```yaml
mongo:
  servers: |
    - host: mongo1
      port: 27017
    - host: mongo2
      port: 27017
  dbname: gravitee
  auth:
    enabled: false
    username: 
    password:
``` 

If neither `mongo.uri` or `mongo.servers` are provided, you have to define the following configuration options:

| Parameter             | Description                                | Default                    |
| --------------------- | ------------------------------------------ | -------------------------- |
| `mongo.rsEnabled`     | Whether Mongo replicaset is enabled or not | `true`                     |
| `mongo.rs`            | Mongo replicaset name                      | `rs0`                      |
| `mongo.dbhost`        | Mongo host address                         | `mongo-mongodb-replicaset` |
| `mongo.dbport`        | Mongo host port                            | `27017`                    |
| `mongo.dbname`        | Mongo DB name                              | `gravitee`                 |
| `mongo.auth.enabled`  | Enable Mongo DB authentication             | `false`                    |
| `mongo.auth.username` | Mongo DB username                          | `null`                     |
| `mongo.auth.password` | Mongo DB password                          | `null`                     |

#### Other keys

| Parameter               | Description                      | Default |
| ----------------------- | -------------------------------- | ------- |
| `mongo.sslEnabled`      | Enable SSL connection to MongoDB | `false` |
| `mongo.socketKeepAlive` | Enable keep alive for socket     | `false` |

### Mongo ReplicaSet

| Parameter                    | Description                           | Default |
| ---------------------------- | ------------------------------------- | ------- |
| `mongodb-replicaset.enabled` | Enable deployment of Mongo replicaset | `false` |

See [MongoDB replicaset](https://artifacthub.io/packages/helm/bitnami/mongodb) for detailed documentation on helm chart.

** Please be aware that the mongodb-replicaset installed by Gravitee is NOT recommended in production and it is just for testing purpose and running AM locally.

### Proxy configuration for HTTP clients

To define the proxy settings for HTTP clients used by the Management API and the Gateway, the `httpClient` section needs to be defined into the values.yaml. This section will be apply on both Gateway and Management API configuration files.

```yaml
httpClient:
  timeout: 10000 # in milliseconds
  proxy:
    enabled: false
    exclude-hosts: # list of hosts to exclude from proxy (wildcard hosts are supported)
      - '*.internal.com'
      - internal.mycompany.com
    type: HTTP #HTTP, SOCK4, SOCK5
    http:
      host: localhost
      port: 3128
      username: user
      password: secret
    https:
      host: localhost
      port: 3128
      username: user
      password: secret
```

### Gravitee.io Configuration

| Key | Type | Default | Description |
|-----|------|---------|-------------|
| alerts.enabled |bool |`+true+` |
| alerts.endpoints |string[] |`+- http://localhost:8072/+` |
| alerts.security.enabled |bool |`+false+` |
| alerts.security.username |string |`+"admin"+` |
| alerts.security.password |string |`+"password"+` |
| alerts.options.sendEventsOnHttp |bool |`+true+` |
| alerts.options.useSystemProxy |bool |`+false+` |
| alerts.options.connectTimeout |int |`+2000+` |
| alerts.options.idleTimeout |int |`+120000+` |
| alerts.options.keepAlive |bool |`+true+` |
| alerts.options.pipelining |bool |`+true+` |
| alerts.options.tryCompression |bool |`+true+` |
| alerts.options.maxPoolSize |int |`+50+` |
| alerts.options.bulkEventsSize |int |`+100+` |
| alerts.options.bulkEventsWait |int |`+100+` |
| alerts.options.ssl.trustall |boolean  |`+false+` |
| alerts.options.ssl.keystore.type |enum(jks, pkcs12, pem) |`+null+` |
| alerts.options.ssl.keystore.path |string |`+null+` |
| alerts.options.ssl.keystore.password |string |`+null+` |
| alerts.options.ssl.keystore.certs |array<string> |`+null+` |
| alerts.options.ssl.keystore.keys |array<string> |`+null+` |
| alerts.options.ssl.truststore.type |enum(jks, pkcs12, pem) |`+null+` |
| alerts.options.ssl.truststore.path |string |`+null+` |
| alerts.options.ssl.truststore.password |string |`+null+` |
| alerts.engines.<cluster-name>.endpoints |array<string> |`+- http://localhost:8072/+` |
| alerts.engines.<cluster-name>.security.username |string |`+"null"+` |
| alerts.engines.<cluster-name>.security.password |string |`+"null"+` |
| alerts.engines.<cluster-name>.ssl.trustall |boolean |`+false+` |
| alerts.engines.<cluster-name>.ssl.keystore.type |enum(jks, pkcs12, pem) |`+null+` |
| alerts.engines.<cluster-name>.ssl.keystore.path |string |`+null+` |
| alerts.engines.<cluster-name>.ssl.keystore.password |string |`+null+` |
| alerts.engines.<cluster-name>.ssl.keystore.certs |array<string> |`+null+ |`
| alerts.engines.<cluster-name>.ssl.keystore.keys |array<string> |`+null+` |
| alerts.engines.<cluster-name>.ssl.truststore.type |enum(jks, pkcs12, pem) |`+null+` |
| alerts.engines.<cluster-name>.ssl.truststore.path |string |`+null+` |
| alerts.engines.<cluster-name>.ssl.truststore.password |string |`+null+` |
| api.autoscaling.enabled | bool | `true` |  |
| api.autoscaling.maxReplicas | int | `3` |  |
| api.autoscaling.minReplicas | int | `1` |  |
| api.autoscaling.targetAverageUtilization | int | `50` |  |
| api.autoscaling.targetMemoryAverageUtilization | int | `80` |  |
| api.enabled | bool | `true` |  |
| api.http.services.core.http.authentication.password | string | `"adminadmin"` |  |
| api.http.services.core.http.host | string | `"localhost"` |  |
| api.http.services.core.http.port | int | `18093` |  |
| api.http.services.core.ingress.enabled | bool | `false` |  |
| api.http.services.core.service.enabled | bool | `false` |  |
| api.image.pullPolicy | string | `"Always"` |  |
| api.image.repository | string | `"graviteeio/am-management-api"` |  |
| api.ingress.annotations."ingress.kubernetes.io/configuration-snippet" | string | `"etag on;\nproxy_pass_header ETag;\nproxy_set_header if-match \"\";\n"` |  |
| api.ingress.annotations."kubernetes.io/ingress.class" | string | `"nginx"` |  |
| api.ingress.enabled | bool | `true` |  |
| api.ingress.hosts[0].host | string | `"am.example.com"` |  |
| api.ingress.hosts[0].paths[0] | string | `"/management"` |  |
| api.ingress.hosts[0].paths[1] | string | `"/admin"` |  |
| api.ingress.path | string | `"/management"` |  |
| api.ingress.tls[0].hosts[0] | string | `"am.example.com"` |  |
| api.ingress.tls[0].secretName | string | `"api-custom-cert"` |  |
| api.jwt.secret | string | `"s3cR3t4grAv1t3310AMS1g1ingDftK3y"` |  |
| api.logging.debug | bool | `false` |  |
| api.logging.file.enabled | bool | `true` |  |
| api.logging.file.encoderPattern | string | `"%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n%n"` |  |
| api.logging.file.rollingPolicy | string | `"\u003crollingPolicy class=\"ch.qos.logback.core.rolling.TimeBasedRollingPolicy\"\u003e\n    \u003c!-- daily rollover --\u003e\n    \u003cfileNamePattern\u003e${gravitee.management.log.dir}/gravitee_%d{yyyy-MM-dd}.log\u003c/fileNamePattern\u003e\n    \u003c!-- keep 30 days' worth of history --\u003e\n    \u003cmaxHistory\u003e30\u003c/maxHistory\u003e\n\u003c/rollingPolicy\u003e\n"` |  |
| api.logging.graviteeLevel | string | `"DEBUG"` |  |
| api.logging.jettyLevel | string | `"INFO"` |  |
| api.logging.stdout.encoderPattern | string | `"%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n"` |  |
| api.logging.stdout.json | bool | `false` |  |
| api.name | string | `"management-api"` |  |
| api.reloadOnConfigChange | bool | `true` |  |
| api.replicaCount | int | `1` |  |
| api.resources.limits.cpu | string | `"500m"` |  |
| api.resources.limits.memory | string | `"1024Mi"` |  |
| api.resources.requests.cpu | string | `"200m"` |  |
| api.resources.requests.memory | string | `"512Mi"` |  |
| api.restartPolicy | string | `"OnFailure"` |  |
| api.service.externalPort | int | `83` |  |
| api.service.internalPort | int | `8093` |  |
| api.service.internalPortName | string | `http` |  |
| api.service.type | string | `"ClusterIP"` |  |
| api.ssl.clientAuth | bool | `false` |  |
| api.ssl.enabled | bool | `false` |  |
| api.updateStrategy.rollingUpdate.maxUnavailable | int | `1` |  |
| api.updateStrategy.type | string | `"RollingUpdate"` |  |
| chaos.enabled | bool | `false` |  |
| gateway.autoscaling.enabled | bool | `true` |  |
| gateway.autoscaling.maxReplicas | int | `3` |  |
| gateway.autoscaling.minReplicas | int | `1` |  |
| gateway.autoscaling.targetAverageUtilization | int | `50` |  |
| gateway.autoscaling.targetMemoryAverageUtilization | int | `80` |  |
| gateway.enabled | bool | `true` |  |
| gateway.image.pullPolicy | string | `"Always"` |  |
| gateway.image.repository | string | `"graviteeio/am-gateway"` |  |
| gateway.http.cookie.secure |bool |`false` |  |
| gateway.http.cookie.sameSite |string |`"Lax"` |  |
| gateway.http.cookie.session.name |string |`"GRAVITEE_IO_AM_SESSION"` |  |
| gateway.http.cookie.session.timeout |int |`1800000` |  |
| gateway.ingress.annotations."kubernetes.io/app-root" | string | `"/auth"` |  |
| gateway.ingress.annotations."kubernetes.io/ingress.class" | string | `"nginx"` |  |
| gateway.ingress.annotations."kubernetes.io/rewrite-target" | string | `"/auth"` |  |
| gateway.ingress.annotations."nginx.ingress.kubernetes.io/enable-rewrite-log" | string | `"true"` |  |
| gateway.ingress.annotations."nginx.ingress.kubernetes.io/ssl-redirect" | string | `"false"` |  |
| gateway.ingress.enabled | bool | `true` |  |
| gateway.ingress.hosts[0] | string | `"am.example.com"` |  |
| gateway.ingress.path | string | `"/auth"` |  |
| gateway.ingress.tls[0].hosts[0] | string | `"am.example.com"` |  |
| gateway.ingress.tls[0].secretName | string | `"api-custom-cert"` |  |
| gateway.jwt.secret | string | `"s3cR3t4grAv1t3310AMS1g1ingDftK3y"` |  |
| gateway.logging.debug | bool | `false` |  |
| gateway.logging.file.enabled | bool | `true` |  |
| gateway.logging.file.encoderPattern | string | `"%d{HH:mm:ss.SSS} [%thread] [%X{api}] %-5level %logger{36} - %msg%n"` |  |
| gateway.logging.file.rollingPolicy | string | `"\u003crollingPolicy class=\"ch.qos.logback.core.rolling.TimeBasedRollingPolicy\"\u003e\n    \u003c!-- daily rollover --\u003e\n    \u003cfileNamePattern\u003e${gravitee.home}/logs/gravitee_%d{yyyy-MM-dd}.log\u003c/fileNamePattern\u003e\n    \u003c!-- keep 30 days' worth of history --\u003e\n    \u003cmaxHistory\u003e30\u003c/maxHistory\u003e\n\u003c/rollingPolicy\u003e\n"` |  |
| gateway.logging.graviteeLevel | string | `"DEBUG"` |  |
| gateway.logging.jettyLevel | string | `"WARN"` |  |
| gateway.logging.stdout.encoderPattern | string | `"%d{HH:mm:ss.SSS} [%thread] [%X{api}] %-5level %logger{36} - %msg%n"` |  |
| gateway.logging.stdout.json | bool | `false` |  |
| gateway.name | string | `"gateway"` |  |
| gateway.reloadOnConfigChange | bool | `true` |  |
| gateway.replicaCount | int | `1` |  |
| gateway.resources.limits.cpu | string | `"500m"` |  |
| gateway.resources.limits.memory | string | `"512Mi"` |  |
| gateway.resources.requests.cpu | string | `"200m"` |  |
| gateway.resources.requests.memory | string | `"256Mi"` |  |
| gateway.service.externalPort | int | `82` |  |
| gateway.service.internalPort | int | `8092` |  |
| gateway.service.internalPortName | string | `http` |  |
| gateway.service.type | string | `"ClusterIP"` |  |
| gateway.ssl.clientAuth | bool | `false` |  |
| gateway.ssl.enabled | bool | `false` |  |
| gateway.type | string | `"Deployment"` |  |
| mongo.auth.enabled | bool | `false` |  |
| mongo.auth.password | string | `nil` |  |
| mongo.auth.source | string | `"admin"` |  |
| mongo.auth.username | string | `nil` |  |
| mongo.connectTimeoutMS | int | `30000` |  |
| mongo.dbhost | string | `"mongo-mongodb-replicaset"` |  |
| mongo.dbname | string | `"gravitee"` |  |
| mongo.dbport | int | `27017` |  |
| mongo.rs | string | `"rs0"` |  |
| mongo.rsEnabled | bool | `true` |  |
| mongo.socketKeepAlive | bool | `false` |  |
| mongo.sslEnabled | bool | `false` |  |
| mongodb-replicaset.auth.adminPassword | string | `"password"` |  |
| mongodb-replicaset.auth.adminUser | string | `"username"` |  |
| mongodb-replicaset.auth.enabled | bool | `false` |  |
| mongodb-replicaset.auth.key | string | `"keycontent"` |  |
| mongodb-replicaset.auth.metricsPassword | string | `"password"` |  |
| mongodb-replicaset.auth.metricsUser | string | `"metrics"` |  |
| mongodb-replicaset.configmap | object | `{}` |  |
| mongodb-replicaset.enabled | bool | `false` |  |
| mongodb-replicaset.image.repository | string | `"mongo"` |  |
| mongodb-replicaset.image.tag | float | `3.6` |  |
| mongodb-replicaset.persistentVolume.accessModes[0] | string | `"ReadWriteOnce"` |  |
| mongodb-replicaset.persistentVolume.enabled | bool | `true` |  |
| mongodb-replicaset.persistentVolume.size | string | `"1Gi"` |  |
| mongodb-replicaset.replicaSetName | string | `"rs0"` |  |
| mongodb-replicaset.replicas | int | `3` |  |
| mongodb-replicaset.resources.limits.cpu | string | `"500m"` |  |
| mongodb-replicaset.resources.limits.memory | string | `"512Mi"` |  |
| mongodb-replicaset.resources.requests.cpu | string | `"100m"` |  |
| mongodb-replicaset.resources.requests.memory | string | `"256Mi"` |  |
| smtp.enabled | bool | `true` |  |
| smtp.from | string | `"info@example.com"` |  |
| smtp.host | string | `"smtp.example.com"` |  |
| smtp.password | string | `"example.com"` |  |
| smtp.port | int | `25` |  |
| smtp.properties.auth | bool | `true` |  |
| smtp.properties.starttlsEnable | bool | `false` |  |
| smtp.subject | string | `"[gravitee] %s"` |  |
| smtp.username | string | `"info@example.com"` |  |
| ui.autoscaling.enabled | bool | `true` |  |
| ui.autoscaling.maxReplicas | int | `3` |  |
| ui.autoscaling.minReplicas | int | `1` |  |
| ui.autoscaling.targetAverageUtilization | int | `50` |  |
| ui.autoscaling.targetMemoryAverageUtilization | int | `80` |  |
| ui.enabled | bool | `true` |  |
| ui.image.pullPolicy | string | `"Always"` |  |
| ui.image.repository | string | `"graviteeio/am-management-ui"` |  |
| ui.ingress.annotations."ingress.kubernetes.io/configuration-snippet" | string | `"etag on;\nproxy_pass_header ETag;\n"` |  |
| ui.ingress.annotations."kubernetes.io/app-root" | string | `"/"` |  |
| ui.ingress.annotations."kubernetes.io/ingress.class" | string | `"nginx"` |  |
| ui.ingress.annotations."kubernetes.io/rewrite-target" | string | `"/"` |  |
| ui.ingress.enabled | bool | `true` |  |
| ui.ingress.hosts[0] | string | `"am.example.com"` |  |
| ui.ingress.path | string | `"/"` |  |
| ui.ingress.tls[0].hosts[0] | string | `"am.example.com"` |  |
| ui.ingress.tls[0].secretName | string | `"api-custom-cert"` |  |
| ui.name | string | `"management-ui"` |  |
| ui.replicaCount | int | `1` |  |
| ui.resources.limits.cpu | string | `"100m"` |  |
| ui.resources.limits.memory | string | `"128Mi"` |  |
| ui.resources.requests.cpu | string | `"50m"` |  |
| ui.resources.requests.memory | string | `"64Mi"` |  |
| ui.service.externalPort | int | `8002` |  |
| ui.service.internalPort | int | `80` |  |
| ui.service.internalPortName | string | `http` |  |
| ui.service.name | string | `"nginx"` |  |
| ui.service.type | string | `"ClusterIP"` |  |
| userManagement.activity.enabled | boolean | `false` | |
| userManagement.activity.anon.algorithm | enum(`SHA256`, `SHA512`, `NONE`)| `SHA256` | | 
| userManagement.activity.anon.salt | string | `null` | |
| userManagement.activity.retention.time | int | `3` | | 
| userManagement.activity.retention.unit | `enum(java.time.temporal.ChronoUnit)` | `MONTHS` |  |
| userManagement.activity.geolocation.variation.latitude | double | `0.07` | | 
| userManagement.activity.geolocation.variation.longitude: | double | `0.07`  | |

### Gravitee.io Alert trigger & settings

When alerts are enabled, you may want to define your own settings the alert triggers and for the risk_assessment settings. 
To do so, you wan define triggers and settings under the alerts section of the values.yaml.

```yaml

alerts:
  enabled: true
  endpoints:
    - http://localhost:8072/
  security:
    enabled: true
    username: admin
    password: adminadmin
  triggers:
    risk_assessment:
      # You need the Risk Assessment Service plugin for these alerts
      geoVelocity:
        name: Geo velocity alert
        description: A geo velocity risk-based alert has been triggered
        assessments: LOW # Default is LOW
        severity: WARNING
      ipReputation:
        name: IP reputation alert
        description: An IP reputation risk-based alert has been triggered
        assessments: LOW # Default is LOW
        severity: WARNING
      unknownDevices:
        name: Unknown Device alert
        description: An unknown device risk-based alert has been triggered
        assessments: HIGH # Default is HIGH
        severity: WARNING
    too_many_login_failures:
      name: "Too many login failures detected"
      description: "More than {threshold}% of logins are in failure over the last {window} second(s)"
      # the threshold rate in % to reach before notify. Default 10% of login failures.
      threshold: 10
      # the minimum sample size. Default 1000 login attempts.
      sampleSize: 1000
      # window time in seconds. Default 600s (10 minutes).
      window: 600
      # severity of the alert (INFO, WARNING, CRITICAL). Default WARNING.
      severity: WARNING
  settings:
    risk_assessment:
      settings:
        enabled: true # default is false
        devices:
          enabled: true # default is true
          thresholds:
            HIGH: 1 # Arbitrary value
        ipReputation:
          enabled: true # default is true
          thresholds:
            #Default is only LOW, but you can add more thresholds
            #percentage
            LOW: 1
            #MEDIUM: 30
            #HIGH: 70
        geoVelocity:
          enabled: true # default is true
          thresholds:
            # meter per second, default is 0.2777778 (1km/h)
            LOW: 0.2777778
            #MEDIUM: 6.9444445 # (25km/h)
            #HIGH: 69.444445 # (250km/h)
```

## OpenShift

The Gravitee.io Access Management Helm Chart supports OpenShift > 3.10
This chart is only supporting Ingress standard objects and not the specific OpenShift Routes, reason why OpenShift is supported started from 3.10.

There are two major considerations to have in mind when deploying Gravitee.io Access Management within OpenShift:
1_ Use full host domain instead of paths for all the components (ingress paths are not well supported by OpenShift)
2_ Override the security context to let OpenShift to define automatically the user-id and the group-id to run the containers.

Also, for Openshift to automatically create Routes from Ingress, you must define the ingressClassName to "none".

Here is a standard values.yaml used to deploy Gravitee.io APIM into OpenShift:

```yaml
api:
  ingress:
    ingressClassName: none
    path: /management
    hosts:
      - api-graviteeio.apps.openshift-test.l8e4.p1.openshiftapps.com
    annotations:
      route.openshift.io/termination: edge
  securityContext: null
  deployment:
    securityContext:
      runAsUser: null
      runAsGroup: null
      runAsNonRoot: true
      allowPrivilegeEscalation: false
      capabilities:
        drop: ["ALL"]
      seccompProfile:
        type: RuntimeDefault

gateway:
  ingress:
    ingressClassName: none
    path: /
    hosts:
      - gw-graviteeio.apps.openshift-test.l8e4.p1.openshiftapps.com
    annotations:
      route.openshift.io/termination: edge
  securityContext: null
  deployment:
    securityContext:
      runAsUser: null
      runAsGroup: null
      runAsNonRoot: true
      allowPrivilegeEscalation: false
      capabilities:
        drop: ["ALL"]
      seccompProfile:
        type: RuntimeDefault

ui:
  ingress:
    ingressClassName: none
    path: /
    hosts:
      - console-graviteeio.apps.openshift-test.l8e4.p1.openshiftapps.com
    annotations:
      route.openshift.io/termination: edge
  securityContext: null
  deployment:
    securityContext:
      runAsUser: null
      runAsGroup: null
      runAsNonRoot: true
      allowPrivilegeEscalation: false
      capabilities:
        drop: ["ALL"]
      seccompProfile:
        type: RuntimeDefault
```

By setting the value to `null` for `runAsUser` and `runAsGroup` it forces OpenShift to define the correct values for you while deploying the Helm Chart.

## Run unit tests

Install `unittest` helm plugin

```shell
helm plugin install https://github.com/quintush/helm-unittest
```

Inside `am` directory, run:

```shell
helm unittest -3 -f 'tests/**/*.yaml' .
```

The consecutive starts in the previous command matches number of subdirectories between `tests` folder and `yaml` files and runs the tests in the sub folders.
Run the following command for test files located in the root of the `tests` folder.

```shell
helm unittest -3 -f 'tests/*.yaml' .
```
