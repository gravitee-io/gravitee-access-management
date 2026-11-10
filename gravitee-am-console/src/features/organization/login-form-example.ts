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
/** The minimal HTML a custom login page needs, as the Angular console documents it. */
export const LOGIN_FORM_EXAMPLE = `<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head>
    <title>Login custom</title>
</head>
<body>
  <form th:action="\${action}" method="post">
    <input type="text" name="username" required/>
    <input type="password" name="password" required/>
    <input type="hidden" th:name="\${_csrf.parameterName}" th:value="\${_csrf.token}"/>
    <input type="hidden" th:name="client_id" th:value="\${param.client_id}"/>
    <p th:if="\${error}" class="error">
      <span>Wrong user or password</span>
    </p>
    <br>
    <!-- Optional. used for user consent to use and store ip for audit logs and user activity-->
    <label>
        <input type="checkbox" th:checked="\${uc_geoip}" id="uc_geoip" name="uc_geoip">
        <span id="#label-consent-ip">I consent to the collection and use of IP-location information for audit log purposes
            <span th:if="\${userActivityEnabled}" th:text="\${'and to improve security for a duration of ' + user_activity_retention_time}"></span>
        </span>
    </label>
    <br>
    <!-- Optional. used for user consent to use and store user-agent for audit logs and user activity -->
    <label>
        <input type="checkbox" th:checked="\${uc_ua}" id="uc_ua" name="uc_ua">
        <span id="#label-consent-ua">I consent to the collection and use of User-Agent information for audit log purposes
            <span th:if="\${userActivityEnabled}" th:text="\${'and to improve security for a duration of ' + user_activity_retention_time}"></span>
        </span>
    </label>
    <br>
    <button type="submit">login</button>
    <a th:if="\${allowForgotPassword}" th:href="\${forgotPasswordAction}">Forgot Password ?</a>
    <a th:if="\${allowRegister}" th:href="\${registerAction}">Register</a>
    <a th:if="\${allowPasswordless}" th:href="\${passwordlessAction}">Sign in with fingerprint, device or security key</a>
  </form>
</body>
</html>`;
