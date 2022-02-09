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
import { loginPageElements } from "../fixtures/elemets/login-page-elements";
import { ADMIN_USER } from "../fixtures/users";

const {
  loginFormTitle,
  loginFormSubTitle,
  loginFormLogo,
  userName,
  password,
  signInButton,
  dashboardLogo,
} = loginPageElements;

describe("login feature", () => {
  before(() => {
    cy.visit(Cypress.env("baseUrl"));
    cy.setCookies();
  });

  it("should load login page", () => {
    cy.url().should("contain", "login");
  });

  it(`should have login page elements`, () => {
    cy.get(loginFormTitle).should("be.visible").and("contain", "Sign In");
    cy.get(loginFormSubTitle).should("be.visible");
    cy.get(loginFormLogo).should("be.visible");
  });

  it(`should sucessful login`, () => {
    cy.get(userName).type(ADMIN_USER.username);
    cy.get(password).type(ADMIN_USER.password);
    cy.get(signInButton).click();
  });

  it(`should verify the dashboard`, () => {
    cy.get(dashboardLogo).should("be.visible");
  });
});
