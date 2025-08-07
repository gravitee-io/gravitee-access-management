/**
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
package io.gravitee.am.extensiongrant.jwtbearer.provider;

import com.nimbusds.jwt.JWTClaimsSet;
import io.gravitee.am.extensiongrant.api.exceptions.InvalidGrantException;
import io.gravitee.am.extensiongrant.jwtbearer.JWTBearerExtensionGrantConfiguration;
import io.gravitee.am.identityprovider.api.User;
import io.gravitee.am.repository.oauth2.model.request.TokenRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static io.gravitee.am.extensiongrant.jwtbearer.provider.JWTBearerExtensionGrantProvider.SSH_PUB_KEY;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
@ExtendWith(MockitoExtension.class)
public class JWTBearerExtensionGrantProviderTest {

    private static final String ECDSA_SHA2_NISTP_256 = "ecdsa-sha2-nistp256 AAAAE2VjZHNhLXNoYTItbmlzdHAyNTYAAAAIbmlzdHAyNTYAAABBBGzYZe0xJ2NQWJKasGGCeo3SbgR9PaKn0q0FuMU3sMlDx5+cabj0NR0quVKfSQ7+Vl9cCur+e62AmGozkF1WfQA=";
    private static final String EC256_JWT_TOKEN = "eyJhbGciOiJFUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.6jk6B4IKg_lj9Fl8JBaJxOMBAvl_LKldjYb83XX4Kv23WFSr0MSQrsEJPnSX2AH8GqSBPFJWIyk1-BoDV3LEaw";
    private static final String ECDSA_SHA2_NISTP_256_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIBdjCCARygAwIBAgIUPLTN/ieRSGNo12H0Q2K3QmxSc8MwCgYIKoZIzj0EAwIw
            OzELMAkGA1UEBhMCUEwxETAPBgNVBAoMCFRlc3QgT3JnMRkwFwYDVQQDDBB0ZXN0
            LmV4YW1wbGUuY29tMB4XDTI1MDgxNDE0MjMxM1oXDTI2MDgxNDE0MjMxM1owOzEL
            MAkGA1UEBhMCUEwxETAPBgNVBAoMCFRlc3QgT3JnMRkwFwYDVQQDDBB0ZXN0LmV4
            YW1wbGUuY29tMFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEbNhl7TEnY1BYkpqw
            YYJ6jdJuBH09oqfSrQW4xTewyUPHn5xpuPQ1HSq5Up9JDv5WX1wK6v57rYCYajOQ
            XVZ9ADAKBggqhkjOPQQDAgNIADBFAiAzBbdTuvX0nHp6Ukxt44XTNIglcYAZXCeb
            Y664upf92wIhAMaA1uw4iXpbUKnwfnETdV7Mytyn4zCYubvmgvU42Jat
            -----END CERTIFICATE-----""";

    private static final String ECDSA_SHA2_NISTP_384 = "ecdsa-sha2-nistp384 AAAAE2VjZHNhLXNoYTItbmlzdHAzODQAAAAIbmlzdHAzODQAAABhBINCj5wEp09/BXKuOlox4nS1uLeg8U/Krk8kFRfWXskHi4yhIUm4ZjFt/94A2V3IgY6SGR9slAj5Hhvf56rRxEpfIb3/p04BnQcNt27R+Dc6qT9vL+zsI+PqFA77v0x5sQ==";
    private static final String EC384_JWT_TOKEN = "eyJhbGciOiJFUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.cXvPL5RhKiwHnA7NrJzLw6qrf0Q2Dbsz7E50l5LeK4Vw_WfLRY56CZJVC-pWg-PVNAs--rY7ZGkFzm7FeX1FtsURHvv1sX7YiColigKqKsdh57xbAyeu-v_v7_YC9mFb";
    private static final String ECDSA_SHA2_NISTP_284_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIBtDCCATmgAwIBAgIUWzvWN3mOOQKZvI+iRZ5QjwFgv64wCgYIKoZIzj0EAwIw
            OzELMAkGA1UEBhMCUEwxETAPBgNVBAoMCFRlc3QgT3JnMRkwFwYDVQQDDBB0ZXN0
            LmV4YW1wbGUuY29tMB4XDTI1MDgxNDE0MjUwNloXDTI2MDgxNDE0MjUwNlowOzEL
            MAkGA1UEBhMCUEwxETAPBgNVBAoMCFRlc3QgT3JnMRkwFwYDVQQDDBB0ZXN0LmV4
            YW1wbGUuY29tMHYwEAYHKoZIzj0CAQYFK4EEACIDYgAEg0KPnASnT38Fcq46WjHi
            dLW4t6DxT8quTyQVF9ZeyQeLjKEhSbhmMW3/3gDZXciBjpIZH2yUCPkeG9/nqtHE
            Sl8hvf+nTgGdBw23btH4NzqpP28v7Owj4+oUDvu/THmxMAoGCCqGSM49BAMCA2kA
            MGYCMQCfkF9uWK1k+5dABOZDPyKhfbPbmz2BF5+FvfEcBK88/4b9v2nX/lZufdHl
            gReoHW8CMQDHudIZC3bmdvMtX/8wSVS7sIDODKwCGZcPLE8fqFNOPZ7vQf8ZO6u6
            m3riNcEUzGw=
            -----END CERTIFICATE-----""";

    private static final String ECDSA_SHA2_NISTP_512 = "ecdsa-sha2-nistp521 AAAAE2VjZHNhLXNoYTItbmlzdHA1MjEAAAAIbmlzdHA1MjEAAACFBADHCPnbM+9MOhSm+7q+YtPdkLbdOkX3u2UdA9geOXyM9XrKyYzg/qzejkSxxkQ59TnlEsmi/RLav2dicjkwSA2oCgGOgQyJrGBvAYvD3yyqmITMZgG7yzAhx792tMgqFUUT0W3oZIKGQePPShTcE/t0xc5Rerjnf+ZxkKgYdMlvyy0Uhg==";
    private static final String EC512_JWT_TOKEN = "eyJhbGciOiJFUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.Aehinq2iABRCUEYw9YyTcy55ODZTZOCApc0UrGDtI7wX6M0oUHN8KDU3Q1RyaJrZEn1QD3SXTixKngew_74luPsjAAdSFTpjfYiXHRaB_TTBwO4Yl1_48O1hPHzmbAVnJTaJYQVM4-WMhP2q0kUh2FeQsSaNzW-HggBYejtTxCWi78aK";
    private static final String ECDSA_SHA2_NISTP_512_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIB/jCCAV+gAwIBAgIUBldHttLvEUmDxxsbgiybjbgBFWswCgYIKoZIzj0EAwIw
            OzELMAkGA1UEBhMCUEwxETAPBgNVBAoMCFRlc3QgT3JnMRkwFwYDVQQDDBB0ZXN0
            LmV4YW1wbGUuY29tMB4XDTI1MDgxNDE0MjYwNVoXDTI2MDgxNDE0MjYwNVowOzEL
            MAkGA1UEBhMCUEwxETAPBgNVBAoMCFRlc3QgT3JnMRkwFwYDVQQDDBB0ZXN0LmV4
            YW1wbGUuY29tMIGbMBAGByqGSM49AgEGBSuBBAAjA4GGAAQAxwj52zPvTDoUpvu6
            vmLT3ZC23TpF97tlHQPYHjl8jPV6ysmM4P6s3o5EscZEOfU55RLJov0S2r9nYnI5
            MEgNqAoBjoEMiaxgbwGLw98sqpiEzGYBu8swIce/drTIKhVFE9Ft6GSChkHjz0oU
            3BP7dMXOUXq453/mcZCoGHTJb8stFIYwCgYIKoZIzj0EAwIDgYwAMIGIAkIBuAp4
            SDVbjcgze/l6oUo2nwsK6V6bweAd+nHD+cIi8xz9By5L7wYyMxOEsmf+k5cdQ/ev
            D9c9iiaGZGMS4YAWcVoCQgHojsn80P2xcqbfoF/ck/UmyDnAjB7jdzA4fOLi43Cw
            HcQ0u0G5Oe+JmjQAkpjk624zXYEiyNXtmbms7kqBvJDUnA==
            -----END CERTIFICATE-----""";

    private static final String RSA_SHA256_KEY = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCNtfrlFPBe3gBPrdwE3Xxc+Vbr7loonuXgucSLyHrTrdaT/eJsjWuw5jDLkuyyAEvcDH9g4WIN4DbKD7sm65jJ+cy4d4mA7UhH7aYJKgpOPhvCFTDKh73lFO7A1zRPQ72Tqis6YfiswdRYGQ6XWTyHq2ZvMblfaSp8V9sg08DHLTekqa64XjAKaIys06kXXAbYh5spOtixpn5uNL+fBMjSeN1EUHVuJy5O3VOzJvV6DuSBgkPs0LYiA4Q8nFOdWA7zU5+uvW/p13qbucLwiWx0d95JhZNb7dxAC3bF2feyuGx+k7OtS7KdJMM4vQYMPvraIrOyO8v7hLmOjtO8e1Sj";
    private static final String RS256_JWT_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.TxKiF7lTiMkOJi0FOC6cj_SOQBSpqckd6xYbk3xElIHLg2DEwRNHc_cRus86BI4fZ2_lXzLj-0GSpi2ohQj4a9aEC42F4AMVmOQ34GxA0Sbndz_JlSqXgtPV5oZyNS4X66vd9oXXnfb7C2vSuiKr_DFpw4hA2CCn_vDXMmNGvxDEsrFQecz9B86Npw5JYN23nCorocnkFTI464ioqHrfdIR1Tu2N1EP0JEb7gO8B2GHxtW_SNrqFa5E8hqe6iYCYrNX42nIDTdEMYPJYAt1D_lkiIUACLZ9EPNWlDQ1IGDm5AGNU2uAd2YYDD8Qa5ek7LXNoI9xYZ1tI9oWCZ0C70w";
    private static final String RSA_SHA256_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIDAjCCAeqgAwIBAgIUCuddJvslQLtIxwCqTbZJespccN0wDQYJKoZIhvcNAQEL
            BQAwOzELMAkGA1UEBhMCUEwxETAPBgNVBAoMCFRlc3QgT3JnMRkwFwYDVQQDDBB0
            ZXN0LmV4YW1wbGUuY29tMB4XDTI1MDgxNDE0MTc0NVoXDTI2MDgxNDE0MTc0NVow
            OzELMAkGA1UEBhMCUEwxETAPBgNVBAoMCFRlc3QgT3JnMRkwFwYDVQQDDBB0ZXN0
            LmV4YW1wbGUuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjbX6
            5RTwXt4AT63cBN18XPlW6+5aKJ7l4LnEi8h6063Wk/3ibI1rsOYwy5LssgBL3Ax/
            YOFiDeA2yg+7JuuYyfnMuHeJgO1IR+2mCSoKTj4bwhUwyoe95RTuwNc0T0O9k6or
            OmH4rMHUWBkOl1k8h6tmbzG5X2kqfFfbINPAxy03pKmuuF4wCmiMrNOpF1wG2Ieb
            KTrYsaZ+bjS/nwTI0njdRFB1bicuTt1Tsyb1eg7kgYJD7NC2IgOEPJxTnVgO81Of
            rr1v6dd6m7nC8IlsdHfeSYWTW+3cQAt2xdn3srhsfpOzrUuynSTDOL0GDD762iKz
            sjvL+4S5jo7TvHtUowIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQB83gIGctZrrew9
            FkOHGXHTYxT1fcBXooBftDurjTOdZtZ1wv5qWIXY/yxAa81P3tzlh7Tz0JZmGuQj
            pYOXveqjMNUUz69ISapLxu3B2yjok9nndcVfyHpKvd7RDY9GvvRpEd8SL1ZS3pYq
            v/OEWSWuA886GH3JK81MZSbQlD5HU8exfaM2zsnVMa+YRE+6KJhERSf9G+BPIey2
            /KviXmRq7xwtBYUAHbRObZ7VEW7r3JxlDkQh6TRTUMROVOOU0DttKG+MoeYTEnLy
            MiuAZp8goJu7zyrDgNuKnFTyH94GJmCnca3u5OAZP+edfztwj++f6F3OdXpJFuKR
            bLbWQiO5
            -----END CERTIFICATE-----""";

    private static final String RSA_SHA384_KEY = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCnSu5S09n5ALJ7noxim0OTTHbVNP0Fhy+9ml5I8qan+X3/kfzUOPBPAXsGJm4fwVgdAnL/wHGj4bmMErithatln1O9YpQ9C+lLcdO6jRF6bVGti7HXDRkaNelpW3Y+FqsE8OiDnavljAv1/wkW+Hag24I77Z3XkrEg/3rmg7QjFFshGqWt2wBaM/W06PFHo+tAGn7K7sbVHETotrxpeq0wEJxqs+i1tCegERzY/xyhcPNOn7PCNPWBr5CrOs+dLfN1gFbusDcpBa7Iq15IfnbR03x25YhQK5f6Wash326zSWJF9ahxU8vTIsTXdpDfRJE3IPeNh+f2tj6mBbXYcA4N";
    private static final String RS384_JWT_TOKEN = "eyJhbGciOiJSUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.C6LLP1_M0gWlX9zpPuXSaiYrSzn_NJszzCGFMLSmdigCw0x6LwrabdnjRTc-4RCRjJN48qeuMOjKClhp0mbqVGkYXs9b2CGtJgpUfrdwTCl9fScKAMr3IbamLDlnjpu9YaiUgkouhTfcnR724p6R9EPnNBYoNtp8TfT-Hh7rg52-la7rxhYHXz5NiHzWA-snvZbyX_IdgoPHsP2pc2gkpLJdGHP2DxescqZdM5SaH78IYV9gd0YBf6DZ8S78iWeMEmtPCpSv9S_cCxIOPS3UPnwrf-6ke5yWeDP1PXMosuhbafFSaMGmauhDj81NY-ZqnACC0Bx-YUJyXm2Ftzi92g";
    private static final String RSA_SHA384_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIDAjCCAeqgAwIBAgIUV4RAl9wCvP2gnJsEO2bENV7HsyAwDQYJKoZIhvcNAQEL
            BQAwOzELMAkGA1UEBhMCUEwxETAPBgNVBAoMCFRlc3QgT3JnMRkwFwYDVQQDDBB0
            ZXN0LmV4YW1wbGUuY29tMB4XDTI1MDgxNDE0MjAwMloXDTI2MDgxNDE0MjAwMlow
            OzELMAkGA1UEBhMCUEwxETAPBgNVBAoMCFRlc3QgT3JnMRkwFwYDVQQDDBB0ZXN0
            LmV4YW1wbGUuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAp0ru
            UtPZ+QCye56MYptDk0x21TT9BYcvvZpeSPKmp/l9/5H81DjwTwF7BiZuH8FYHQJy
            /8Bxo+G5jBK4rYWrZZ9TvWKUPQvpS3HTuo0Rem1RrYux1w0ZGjXpaVt2PharBPDo
            g52r5YwL9f8JFvh2oNuCO+2d15KxIP965oO0IxRbIRqlrdsAWjP1tOjxR6PrQBp+
            yu7G1RxE6La8aXqtMBCcarPotbQnoBEc2P8coXDzTp+zwjT1ga+QqzrPnS3zdYBW
            7rA3KQWuyKteSH520dN8duWIUCuX+lmrId9us0liRfWocVPL0yLE13aQ30SRNyD3
            jYfn9rY+pgW12HAODQIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQBHNeLgMlfgvD7J
            tqEekRA7YZh4opRhqWELhbSIEqQS904JxJkphOpra/XTPbJkJCIgZmVFqZGY29DV
            VhgMsE5TeMLrWnWYeg3RNPsmm1KmEcUT6tvQ5x/uqZvJBpGDZ1uWOF/E11DjKdZs
            w+WVDisUaVp9u2diHqIS3jDRrqZJ+NEjA4TtKNaAq2Sl+iXTGUHq4e4brEZKWCHF
            +v0/dJB0Nj1b7fkc9feyvrpsXeLW2JoVZ04M41mBbVVxA03XsNLC0PLo5W3kODoq
            H5AvsJkKG4BtWMfC6Qjw2nNblwenECtR+GXVb9AOE1keadISjWtWrR8rSBN+m8d+
            66Mvc9Ia
            -----END CERTIFICATE-----""";

    private static final String RSA_SHA512_KEY = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABAQCrcZp1FOXOupGybDeN0qPLos/NNUlA5wvrpd77saC/ztlqwaJ7iQ4f8gIHe4cWqKf0vAZGhV/p712MHjNHLB9emUGbNmbM6qswfDkAaZF5FjOQholt1XbT3lz9SyzfuFq4rIZMQzlLvMntBXydfZRXblBzoFRNwcpNjYlvGhC52RX0TiWtChTib/EbzUR9BKObvHSn4KdDr40gjwliu5P31Qi3eXDysEywAsPX6x2zzVuLknU74e/4VlaGTGeWVc9zugrD1hQ7aWU17pNTOmok4uKYuH90uA+cjTMnJyyMczvFGyyAP3haIHINZmhIuXn1VWJddvwbJx8PpP2QXaWF";
    private static final String RS512_JWT_TOKEN = "eyJhbGciOiJSUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.X5ZRZpqitHwFllfLizSKuKgQMmao0xtxaFYebMMQjIar9DOjmDjKk05F5YqvSbCwaX4U7TnsQH_HIuUrgqHRrCZj4otlXhfeMK0bnZSGs_JyT1NM8mBzw9X1CymAWo-UJAhxrNIa4nZgYNe9ge5-15u3FyvGXZAE6fgMDmcbPYT5uS1f0f84YGL7u3Ou4cOmQKqVU1WRXSB3ZKabMCLz-BQZwDh8E-_KY9-bJ-uxILEISaSIXatzqRQHdW1vEVZkXBEfxCc-U0up3iRumovDwgJgUtOnifmBJKcwyC4bo3Y6qNde3PqO5gWbPMoyZ8gpgKOrrsvEinCWxdNCm9XksQ";
    private static final String RSA_SHA512_CERT = """
            -----BEGIN CERTIFICATE-----
            MIIDAjCCAeqgAwIBAgIUZVXH9dWleEk4ChEDJdJsLC2bHQYwDQYJKoZIhvcNAQEL
            BQAwOzELMAkGA1UEBhMCUEwxETAPBgNVBAoMCFRlc3QgT3JnMRkwFwYDVQQDDBB0
            ZXN0LmV4YW1wbGUuY29tMB4XDTI1MDgxNDE0MjExNloXDTI2MDgxNDE0MjExNlow
            OzELMAkGA1UEBhMCUEwxETAPBgNVBAoMCFRlc3QgT3JnMRkwFwYDVQQDDBB0ZXN0
            LmV4YW1wbGUuY29tMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAq3Ga
            dRTlzrqRsmw3jdKjy6LPzTVJQOcL66Xe+7Ggv87ZasGie4kOH/ICB3uHFqin9LwG
            RoVf6e9djB4zRywfXplBmzZmzOqrMHw5AGmReRYzkIaJbdV2095c/Uss37hauKyG
            TEM5S7zJ7QV8nX2UV25Qc6BUTcHKTY2JbxoQudkV9E4lrQoU4m/xG81EfQSjm7x0
            p+CnQ6+NII8JYruT99UIt3lw8rBMsALD1+sds81bi5J1O+Hv+FZWhkxnllXPc7oK
            w9YUO2llNe6TUzpqJOLimLh/dLgPnI0zJycsjHM7xRssgD94WiByDWZoSLl59VVi
            XXb8GycfD6T9kF2lhQIDAQABMA0GCSqGSIb3DQEBCwUAA4IBAQBe4ufrlabvnvep
            bjQL4NfxTQ+WGBz0BddevaS6ySVcmlCv3iXu4jUHfBeiSU2oQ0SpBEMZc/GOzY3f
            0J+33iNHkhgoNxOnGgGXaDs/tm0iXaHyCprKFkl9T/uIvOjFPV6TDx7HyBob2waS
            6Us20d/toAxHlyDGYnJdXx0bebNxOe/rleGcOzS1dItHSWmsgGJ6aH2fDV3c2zjD
            4Qbe+NhpLBj5padQ1QB2dN6+kDKL79tzstBpOdIwLQlPCa7qwEh0j3Rfj1df8Xu/
            KfQPbsMcI4tMq4N9OVAPPtDur6qDangul2Q4fi3YfATS9CMhH/Y2mlIegqKCuOiT
            i+HAdqsp
            -----END CERTIFICATE-----""";

    private static final String HS256_JWT_TOKEN = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.JzETlj7KYTjxzULZBp7g_Q0cX7YTZWrte1gybI5kcm0";
    private static final String HMAC_256_SECRET = "BfC_B_jsbmj81-d0P9680nsOQWixjv5VsBjlj6BUzGg";

    private static final String HS384_JWT_TOKEN = "eyJhbGciOiJIUzM4NCIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.-mVPDvxNEhtlUxRxuLz0W3Tbtu2ASVse-b4gMnxTLKohXBGJ4YHziDRNlUugh84U";
    private static final String HMAC_384_SECRET = "UGedK9KfvbbFUKoLXgXP6u7sp4jkITWnZB5y4lbbU_NDr3Xs0lK07NHuKajmmVP7";

    private static final String HS512_JWT_TOKEN = "eyJhbGciOiJIUzUxMiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiYWRtaW4iOnRydWV9.jYDh79n0yn1eH1IshmiltPGZum4jcT1e348Fa2whoncS1ndPwjPNtQp-4SL3VC0KMG-V7rtkesDoSBwdlyzM0A";
    private static final String HMAC_512_SECRET = "xwtuBRqdYcvBgQ-5tlCqY8bP0Brfuz7nWpfUbDjWZTs6ke26K9Zo05bofnM1-2NsN6xlIxAb7v5DKqdDKTxarw";


    @InjectMocks
    private JWTBearerExtensionGrantProvider jwtBearerExtensionGrantProvider = new JWTBearerExtensionGrantProvider();

    @Mock
    private JWTBearerExtensionGrantConfiguration jwtBearerTokenGranterConfiguration;

    @Test
    public void testParseKey_RSA() {
        final String key = "ssh-rsa AAAAE2VjZHNhLXNoYTItbmlzdHAyNTY=";
        final String key2 = "ssh-rsa AAAAE2VjZHNhLXNoYTItbmlzdHAyNTY= test@test.com";
        final String key3 = "ssh";
        final String key4 = "ssh-rsa";
        assertTrue(SSH_PUB_KEY.matcher(key).matches());
        assertTrue(SSH_PUB_KEY.matcher(key2).matches());
        assertFalse(SSH_PUB_KEY.matcher(key3).matches());
        assertFalse(SSH_PUB_KEY.matcher(key4).matches());
    }

    @Test
    public void testParseKey_ECDSA() {
        final String key = "ecdsa AAAAE2VjZHNhLXNoYTItbmlzdHAyNTY=";
        final String key2 = "ecdsa-sha2-xyz AAAAE2VjZHNhLXNoYTItbmlzdHAyNTY=";
        final String key3 = "ecdsa-sha2-xyz AAAAE2VjZHNhLXNoYTItbmlzdHAyNTY= test@test.com";
        final String key4 = "ecdsa";
        final String key5 = "ecdsa-sha2";
        assertTrue(SSH_PUB_KEY.matcher(key).matches());
        assertTrue(SSH_PUB_KEY.matcher(key2).matches());
        assertTrue(SSH_PUB_KEY.matcher(key3).matches());
        assertFalse(SSH_PUB_KEY.matcher(key4).matches());
        assertFalse(SSH_PUB_KEY.matcher(key5).matches());
    }

    @Test
    public void testCreateUser_withClaimsMapper() {
        List<Map<String, String>> claimsMapper = new ArrayList<>();
        Map<String, String> claimMapper1 = new HashMap<>();
        claimMapper1.put("assertion_claim", "username");
        claimMapper1.put("token_claim", "username");

        Map<String, String> claimMapper2 = new HashMap<>();
        claimMapper2.put("assertion_claim", "email");
        claimMapper2.put("token_claim", "email");

        claimsMapper.add(claimMapper1);
        claimsMapper.add(claimMapper2);

        when(jwtBearerTokenGranterConfiguration.getClaimsMapper()).thenReturn(claimsMapper);

        JWTClaimsSet claimsSet = new JWTClaimsSet.Builder()
                .claim("username", "test_username")
                .claim("email", "test_email")
                .claim("gis", "default-idp-1234:1234")
                .subject("test_subject")
                .build();
        User user = jwtBearerExtensionGrantProvider.createUser(claimsSet);

        assertEquals(4, user.getAdditionalInformation().size());
        assertEquals("test_username", user.getAdditionalInformation().get("username"));
        assertEquals("test_email", user.getAdditionalInformation().get("email"));
        assertEquals("default-idp-1234:1234", user.getAdditionalInformation().get("gis"));
    }

    /**
     * Test RSA with SHA-256 hashing algorithm (RS256)
     * Standard RSA-PKCS1-v1_5 signature with SHA-256
     */
    @Test
    public void must_grant_with_rsa256() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(RSA_SHA256_KEY);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", RS256_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test RSA with SHA-384 hashing algorithm (RS384)
     * RSA-PKCS1-v1_5 signature with SHA-384
     */
    @Test
    public void must_grant_with_rsa384() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(RSA_SHA384_KEY);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", RS384_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test RSA with SHA-512 hashing algorithm (RS512)
     * RSA-PKCS1-v1_5 signature with SHA-512
     */
    @Test
    public void must_grant_with_rsa512() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(RSA_SHA512_KEY);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", RS512_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }


    @Test
    public void must_grant_with_ecdsa_sha2_nistp256() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_256);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC256_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    @Test
    public void must_grant_with_ecdsa_sha2_nistp384() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_384);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC384_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    @Test
    public void must_grant_with_ecdsa_sha2_nistp512() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_512);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC512_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    @Test
    public void must_fail_due_to_wrong_publickey_with_wrong_assertion() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_512);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC256_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidGrantException.class);
    }

    @Test
    public void must_fail_due_to_rsa_algorithm_mismatch() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(RSA_SHA256_KEY);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", RS384_JWT_TOKEN)); // Using RS384 token with RS256 key

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidGrantException.class);
    }

    @Test
    public void must_fail_due_to_wrong_public_key_prefix() {
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn("wrong prefix");

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0In0.test"));

        assertThrows(InvalidGrantException.class, () -> jwtBearerExtensionGrantProvider.grant(tokenRequest).blockingGet());
    }

    @Test
    public void must_fail_due_to_null_assertion() {
        final TokenRequest tokenRequest = new TokenRequest();

        assertThrows(InvalidGrantException.class, () -> jwtBearerExtensionGrantProvider.grant(tokenRequest).blockingGet());
    }

    /**
     * Test with RSA-256 using SSH public key format
     */
    @Test
    public void must_grant_with_new_processor_rsa256_ssh_key() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(RSA_SHA256_KEY);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", RS256_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with RSA-384 using SSH public key format
     */
    @Test
    public void must_grant_with_new_processor_rsa384_ssh_key() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(RSA_SHA384_KEY);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", RS384_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with RSA-512 using SSH public key format
     */
    @Test
    public void must_grant_with_new_processor_rsa512_ssh_key() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(RSA_SHA512_KEY);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", RS512_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with RSA-256 using the X.509 certificate format
     */
    @Test
    public void must_grant_with_new_processor_rsa256_certificate() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(RSA_SHA256_CERT);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", RS256_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with RSA-384 using the X.509 certificate format
     */
    @Test
    public void must_grant_with_new_processor_rsa384_certificate() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(RSA_SHA384_CERT);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", RS384_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with RSA-512 using the X.509 certificate format
     */
    @Test
    public void must_grant_with_new_processor_rsa512_certificate() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(RSA_SHA512_CERT);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", RS512_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with ECDSA-256 using SSH public key format
     */
    @Test
    public void must_grant_with_new_processor_ecdsa256_ssh_key() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_256);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC256_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with ECDSA-384 using SSH public key format
     */
    @Test
    public void must_grant_with_new_processor_ecdsa384_ssh_key() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_384);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC384_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with ECDSA-512 using SSH public key format
     */
    @Test
    public void must_grant_with_new_processor_ecdsa512_ssh_key() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_512);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC512_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with ECDSA-256 using the X.509 certificate format
     */
    @Test
    public void must_grant_with_new_processor_ecdsa256_certificate() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_256_CERT);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC256_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with ECDSA-384 using the X.509 certificate format
     */
    @Test
    public void must_grant_with_new_processor_ecdsa384_certificate() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_284_CERT);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC384_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with ECDSA-512 using the X.509 certificate format
     */
    @Test
    public void must_grant_with_new_processor_ecdsa512_certificate() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_512_CERT);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC512_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with HMAC-256
     */
    @Test
    public void must_grant_with_new_processor_hmac256() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(HMAC_256_SECRET);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", HS256_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with HMAC-384
     */
    @Test
    public void must_grant_with_new_processor_hmac384() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(HMAC_384_SECRET);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", HS384_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test with HMAC-512
     */
    @Test
    public void must_grant_with_new_processor_hmac512() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(HMAC_512_SECRET);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", HS512_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertComplete()
                .assertNoErrors()
                .assertValue(Objects::nonNull);
    }

    /**
     * Test fails when an RSA key configured for RS256, but JWT uses RS384
     */
    @Test
    public void must_fail_with_new_processor_rsa_algorithm_mismatch_256_384() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(RSA_SHA256_KEY);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", RS384_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidGrantException.class);
    }

    /**
     * Test fails when an RSA key configured for RS384 but JWT uses RS512
     */
    @Test
    public void must_fail_with_new_processor_rsa_algorithm_mismatch_384_512() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(RSA_SHA384_KEY);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", RS512_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidGrantException.class);
    }

    /**
     * Test fails when ECDSA key configured for ES256, but JWT uses ES384
     */
    @Test
    public void must_fail_with_new_processor_ecdsa_algorithm_mismatch_256_384() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_256);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC384_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidGrantException.class);
    }

    /**
     * Test fails when ECDSA key configured for ES384, but JWT uses ES512
     */
    @Test
    public void must_fail_with_new_processor_ecdsa_algorithm_mismatch_384_512() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(ECDSA_SHA2_NISTP_384);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", EC512_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidGrantException.class);
    }

    /**
     * Test fails when HMAC configured for HS256 but JWT uses HS384
     */
    @Test
    public void must_fail_with_new_processor_hmac_algorithm_mismatch_256_384() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(HMAC_256_SECRET);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", HS384_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidGrantException.class);
    }

    /**
     * Test fails when HMAC configured for HS384 but JWT uses HS512
     */
    @Test
    public void must_fail_with_new_processor_hmac_algorithm_mismatch_384_512() throws Exception {
        when(jwtBearerTokenGranterConfiguration.getPublicKeyResolver()).thenReturn(JWTBearerExtensionGrantConfiguration.KeyResolver.GIVEN_KEY);
        when(jwtBearerTokenGranterConfiguration.getPublicKey()).thenReturn(HMAC_384_SECRET);

        final TokenRequest tokenRequest = new TokenRequest();
        tokenRequest.setRequestParameters(Map.of("assertion", HS512_JWT_TOKEN));

        var testObserver = jwtBearerExtensionGrantProvider.grant(tokenRequest).test();
        testObserver.await(10, TimeUnit.SECONDS);
        testObserver.assertError(InvalidGrantException.class);
    }
}
