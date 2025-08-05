# Overview

This directory contains a docker compose file to start a MongoDB instance with TLS enabled and required.

If the certificates are present, you can just start Mongo

```
docker compose -f docker-compose-mongo-tls.yml up
```

If certificates are missing, please execute in the config directoty the commands described in "Create the certificates" section.

# Create the certificates
First, we need to create an SSL certificate, signed by itself, that will be used as root certificate:

```
openssl req -newkey rsa:4096 -keyform PEM -keyout ca.key -x509 -days 3650 -subj "/emailAddress=contact@graviteesource.com/CN=mongo.gravitee.io/OU=GraviteeSource/O=GraviteeSource/L=Lille/ST=France/C=FR" -passout pass:ca-secret -outform PEM -out ca.pem
```

It will create a file named ca.pem containing the certificate and a file named ca.key containing the private key.

Then, we need to create a certificate that we will use on the MongoDB side that needs to be signed by the root certificate:

```
# First, create a private key
openssl genrsa -out mongo.key 4096

# Then, create a certificate request
openssl req -new -key mongo.key -out mongo.csr -sha256 -subj "/emailAddress=contact@graviteesource.com/CN=localhost/OU=Mongo/O=GraviteeSource/L=Lille/ST=France/C=FR"

# Finally, sign the certificate request with the root certificate
openssl x509 -req -in mongo.csr -CA ca.pem -CAkey ca.key -set_serial 100 -extensions server -days 3650 -outform PEM -out mongo.cer -sha256 -passin pass:ca-secret

# Create .pem file
cat mongo.key mongo.cer > mongo.pem

# Create a TrustStore with the root certificate (need to be used in AM configuration to connect to Mongo)
keytool -import -file ca.pem -storetype PKCS12 -keystore ca-truststore.p12 -storepass truststore-secret -noprompt -alias mongo-ca
```

# Configure AM

In the AM gravitee.yaml (for MAPI & GW), in the repository layer configure sslEnabled and the truststore.

```
repositories:
  management:
    type: mongodb
    mongodb:
      dbname: ${ds.mongodb.dbname}
      host: ${ds.mongodb.host}
      port: ${ds.mongodb.port}
      sslEnabled: true
      truststore:
        path: ${path-to}/mongo/config/ca-truststore.p12                    
        type: pkcs12               
        password: truststore-secret
```

It is also possible to use a URI

```
mongosh "mongodb://localhost:27017/?tls=true&tlsCAFile=./tls/ca.pem"
```

