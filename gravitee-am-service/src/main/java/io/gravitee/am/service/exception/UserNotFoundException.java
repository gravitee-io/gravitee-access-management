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
package io.gravitee.am.service.exception;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UserNotFoundException extends AbstractNotFoundException {

    private String id;
    private String domain;
    private String username;

    public UserNotFoundException() { }

    public UserNotFoundException(String id) {
        this.id = id;
        this.domain = null;
        this.username = null;
    }

    public UserNotFoundException(String domain, String username) {
        this.domain = domain;
        this.username = username;
        this.id = null;
    }

    @Override
    public String getMessage() {
        if (id != null) {
            return "User [" + id + "] can not be found.";
        } else if (username != null ){
            return "User [" + username + "] can not be found for domain[" + domain + "].";
        } else {
            return "No user found";
        }
    }
}
