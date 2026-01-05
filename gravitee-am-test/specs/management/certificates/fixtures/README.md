# test.jks generation
```
keytool -genkeypair -alias test -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 36500 -keystore test.jks -storetype JKS -storepass changeit -keypass changeit -dname "CN=localhost, OU=Dev, O=Test, L=Warsaw, C=PL"
```
# test.p12 generation
```
keytool -genkeypair -alias test -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 36500 -storetype PKCS12 -keystore test.p12 -storepass changeit -keypass changeit -dname "CN=localhost, OU=Dev, O=Test, L=Warsaw, C=PL"
```