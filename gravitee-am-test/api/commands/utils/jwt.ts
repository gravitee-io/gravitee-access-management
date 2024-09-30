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

// see https://github.com/panva/jose/blob/main/README.md
import * as jose from 'jose';
import crypto from 'crypto';
import jwt from 'jsonwebtoken';
import forge from 'node-forge';

export function decodeJwt(accessToken) {
  return jose.decodeJwt(accessToken);
}

const priv =
  '-----BEGIN PRIVATE KEY-----\nMIIEvgIBADANBgkqhkiG9w0BAQEFAASCBKgwggSkAgEAAoIBAQDDoS6ez/H/Cjlm\naG1cPYxuvNiOIdfbloEWivrmlNS53TYynUs8092jG8ooPIjd9wEgwM1l63nTjcEF\njtGR9n/Y0aTuBeIDrnTHoCl9D2Qolkjtg+I6JLZgj5jLmtgDMQP0wRsg3+qyQfIV\nLWIw2cVYO2PTkvqI4iQ4kxuBdTchJ0KUX8iup4fj+j6BJry2DSR7llVUaYSFFY2w\n4qg/aHZNy1ZLOIg2mfUjHf+Pjqem4A53SCklfcpwoRBK5Bvg1zdzv2Ia5ZNdLgfl\nuBbre7+DM7li5pDlKd0KrvXJxFFyqDzh0dR5YjUBUorfMIrD/J92zspnz7USR5Me\nfCxweO7XAgMBAAECggEAE9DrZoXc1v9DojZ4wWuwonT5oKgX8/PSXqS0VpAD3lHk\nkArz2JUDcea6OwoquQUwtY8iHy1TmVTWFQiK/PSRrbZKtRuecc4FoUT9fuAEvsG+\nYGTIESiv2qchDJrCP8yCwJOg+lsELItWiMOgmx8sCMx3R7KvMxdpINvx4NKtQ2vH\nvEicKaaMwbaojzbTlBNX84Kfg7JEHqHR2vQfdGSafKtOhoeUOWKDEaA2Gztf5ggI\nLEmztYIfR3bR68HNQiZBvDGHWUX22yO0Lis1rPBge4GuWewvZ3rdHydha1YITRGn\nsywFiPQdCusMJ1268Ld0Jla4HfzjTrfNR/r3e0s1uQKBgQDt2d45vgxBs3VDAXeF\nG0mYTOqaRHjjM6BOsTcsj3F/HzTjYYDVBOZk2kaH2FnaGNAIKrzVTKQHqVsuPkhu\nBAdrUX0eKO1j6G1c8hZiogE/cWztpcmb1u37WkDR9YgPdBf0r/X+kCNyjfZIQfTU\nTfkmHEvnIqUQiGJz07AKNZYivwKBgQDSjpHQkF1Tj4qy0APyrVW9FfL5ioo7v1N8\nF1Q88eLDYacojPg+mbwvKJKuhAizgmF2LVAnIBBHGpCpNFugItJ2cuwdPFN1dosh\nTmTM05vhJ/aUr6c7NuhP0wB209AkLmwA0kqPoZH6gCjpMKnu3H+m3aZKTWZJxtbp\nbZwzksNx6QKBgQCALpTzu3cxhTxhww2df2o3GOSqBNK9ZxoyrpDUg+2fm7rN+8MJ\nih18JqIyHae2wP6EGDwyG56evmd0UX6JQ8SX0o/CTD47RIyDtbfYqgRQI5mXrWws\nfzU765TZUiBka6VpadcBvL5NwzICGQGP1QG7xy4kv2gDs+yTDXdm2SAzUwKBgQC6\nPG5O6pIRqeIFHSaQzK4MbFCYVejExgritIPG0DBBnqtKm72rKTGhCKMNBy3nEQiB\njbzajmq3aZDQiMYOUx6StD2R8lzjhbG0CPHxla3HriBVDTS+lGdQy5IaiGkOx5yx\n8U8P0doblqYY8/kqlA+4mU5PZDx4Pw6yM0g4+bQzmQKBgCea/T7hn3OUtzQk378I\nB2SLml1xBBfH2pcWA/UE9VcdOIN86r+M2RNMojMA8s2DTgXhf/qnQ2vSvIaF+GY/\nQ/sAm5gSUysxrSzqFSic+a0AZSeWpc/E2FUsqaVijfpuLiFlVMgL80zX/y/bke4A\ngFMIuhXCqg/cSTIGCX2SAjMU\n-----END PRIVATE KEY-----\n';

export function generateSignedJwt(customPayload: any) {
  const signOptions = {
    algorithm: 'RS256',
    expiresIn: '10h',
    issuer: 'gravitee',
  };
  return jwt.sign(customPayload, priv, signOptions);
}

export function getPublicKey() {
  const publicKey = crypto.createPublicKey(priv);
  const publicKeyExport = publicKey.export({
    format: 'pem',
    type: 'spki',
  });
  const forgePublicKey = forge.pki.publicKeyFromPem(publicKeyExport);
  return forge.ssh.publicKeyToOpenSSH(forgePublicKey);
}
