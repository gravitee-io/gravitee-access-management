/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *         http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
# test.jks generation
```
keytool -genkeypair -alias test -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 36500 -keystore test.jks -storetype JKS -storepass changeit -keypass changeit -dname "CN=localhost, OU=Dev, O=Test, L=Warsaw, C=PL"
```
# test.p12 generation
```
keytool -genkeypair -alias test -keyalg RSA -keysize 2048 -sigalg SHA256withRSA -validity 36500 -storetype PKCS12 -keystore test.p12 -storepass changeit -keypass changeit -dname "CN=localhost, OU=Dev, O=Test, L=Warsaw, C=PL"
```