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
package io.gravitee.am.service.authentication.crypto.password;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class SHAMD5PasswordEncoder extends SHAPasswordEncoder {

    private final MD5PasswordEncoder md5PasswordEncoder = new MD5PasswordEncoder();
    private final SHAPasswordEncoder shaPasswordEncoder = new SHAPasswordEncoder();

    public SHAMD5PasswordEncoder() {
        md5PasswordEncoder.setSaltLength(0);
    }

    public SHAMD5PasswordEncoder(String algorithm) {
        this();
        shaPasswordEncoder.setAlgorithm(algorithm);
    }

    @Override
    public String encode(CharSequence rawPassword) {
        return shaPasswordEncoder.encode(md5PasswordEncoder.encode(rawPassword));
    }

    @Override
    public String encode(CharSequence rawPassword, byte[] salt) {
        return shaPasswordEncoder.encode(md5PasswordEncoder.encode(rawPassword), salt);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        final String rawPasswordEncoded = md5PasswordEncoder.encode(rawPassword);
        return shaPasswordEncoder.matches(rawPasswordEncoded, encodedPassword);
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword, byte[] salt) {
        final String rawPasswordEncoded = md5PasswordEncoder.encode(rawPassword);
        return shaPasswordEncoder.matches(rawPasswordEncoded, encodedPassword, salt);
    }

    @Override
    public void setEncodeSaltAsBase64(boolean encodeSaltAsBase64) {
        md5PasswordEncoder.setEncodeSaltAsBase64(encodeSaltAsBase64);
        shaPasswordEncoder.setEncodeSaltAsBase64(encodeSaltAsBase64);
    }

    @Override
    public void setSaltLength(int saltLength) {
        shaPasswordEncoder.setSaltLength(saltLength);
    }
}
