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

package io.gravitee.am.service.authentication.crypto.password.jmh;

import io.gravitee.am.service.authentication.crypto.password.NoOpPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.PBKDF2PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.PasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.SHAPasswordEncoder;
import io.gravitee.am.service.authentication.crypto.password.bcrypt.BCryptPasswordEncoder;
import lombok.Getter;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Threads;

import java.util.UUID;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@State(Scope.Benchmark)
@Threads(4)
@Fork(value = 2, warmups = 2)
public class EncoderBenchmark {
    private Provider noOpProvider = new NoOpProvider();
    private Provider sha256Provider = new Sha256Provider();
    private Provider sha512Provider = new Sha512Provider();
    private Provider bcrypt8Provider = new Bcrypt8Provider();
    private Provider bcrypt10Provider = new Bcrypt10Provider();
    private Provider pbkdf2_sha256_300k_Provider = new PBKDF2_SHA256_300000_Provider();
    private Provider pbkdf2_sha256_600k_Provider = new PBKDF2_SHA256_600000_Provider();
    private Provider pbkdf2_sha512_105k_Provider = new PBKDF2_SHA256_300000_Provider();
    private Provider pbkdf2_sha512_210k_Provider = new PBKDF2_SHA256_600000_Provider();
/*
    @Benchmark
    public void benchmarkNoOp() {
        process(noOpProvider);
    }

    @Benchmark
    public void benchmarkSHA256() {
        process(sha256Provider);
    }
*/
    @Benchmark
    public void benchmarkSHA512() {
        process(sha512Provider);
    }
/*
    @Benchmark
    public void benchmarkBCrypt_8() {
        process(bcrypt8Provider);
    }

    @Benchmark
    public void benchmarkBCrypt_10() {
        process(bcrypt10Provider);
    }

    @Benchmark
    public void benchmarkPBKDF2_sha256_300k() {
        process(pbkdf2_sha256_300k_Provider);
    }

    @Benchmark
    public void benchmarkPBKDF2_sha256_600k() {
        process(pbkdf2_sha256_600k_Provider);
    }

    @Benchmark
    public void benchmarkPBKDF2_sha512_105k() {
        process(pbkdf2_sha512_105k_Provider);
    }

    @Benchmark
    public void benchmarkPBKDF2_sha512_210k() {
        process(pbkdf2_sha512_210k_Provider);
    }*/

    private void process(Provider provider) {
        var encoder = provider.getEncoder();
        var rawPassword = provider.getRawPassword();
        var encodedPassword = provider.getEncodedPassword();
        if (!encoder.matches(rawPassword, encodedPassword)) {
            throw new RuntimeException();
        }
    }

    private interface Provider {
        PasswordEncoder getEncoder();
        String getRawPassword();
        String getEncodedPassword();
    }

    @Getter
    private class NoOpProvider implements Provider {
        private PasswordEncoder encoder = NoOpPasswordEncoder.getInstance();
        private String rawPassword = UUID.randomUUID().toString();
        private String encodedPassword = encoder.encode(rawPassword);
    }

    @Getter
    private class Sha512Provider implements Provider {
        private PasswordEncoder encoder = new SHAPasswordEncoder(512);
        private String rawPassword = UUID.randomUUID().toString();
        private String encodedPassword = encoder.encode(rawPassword);
    }

    @Getter
    private class Sha256Provider implements Provider {
        private PasswordEncoder encoder = new SHAPasswordEncoder(256);
        private String rawPassword = UUID.randomUUID().toString();
        private String encodedPassword = encoder.encode(rawPassword);
    }

    @Getter
    private class Bcrypt10Provider implements Provider {
        private PasswordEncoder encoder = new BCryptPasswordEncoder(10);
        private String rawPassword = UUID.randomUUID().toString();
        private String encodedPassword = encoder.encode(rawPassword);
    }

    @Getter
    private class Bcrypt8Provider implements Provider {
        private PasswordEncoder encoder = new BCryptPasswordEncoder(8);
        private String rawPassword = UUID.randomUUID().toString();
        private String encodedPassword = encoder.encode(rawPassword);
    }

    @Getter
    private class PBKDF2_SHA256_600000_Provider implements Provider {
        private PasswordEncoder encoder = new PBKDF2PasswordEncoder(16, 600_000, "PBKDF2WithHmacSHA256");
        private String rawPassword = UUID.randomUUID().toString();
        private String encodedPassword = encoder.encode(rawPassword);
    }

    @Getter
    private class PBKDF2_SHA256_300000_Provider implements Provider {
        private PasswordEncoder encoder = new PBKDF2PasswordEncoder(16, 300_000, "PBKDF2WithHmacSHA256");
        private String rawPassword = UUID.randomUUID().toString();
        private String encodedPassword = encoder.encode(rawPassword);
    }

    @Getter
    private class PBKDF2_SHA512_105000_Provider implements Provider {
        private PasswordEncoder encoder = new PBKDF2PasswordEncoder(16, 105_000, "PBKDF2WithHmacSHA512");
        private String rawPassword = UUID.randomUUID().toString();
        private String encodedPassword = encoder.encode(rawPassword);
    }

    @Getter
    private class PBKDF2_SHA512_210000_Provider implements Provider {
        private PasswordEncoder encoder = new PBKDF2PasswordEncoder(16, 210_000, "PBKDF2WithHmacSHA512");
        private String rawPassword = UUID.randomUUID().toString();
        private String encodedPassword = encoder.encode(rawPassword);
    }

}
