# Gravitee.io Access Management (AM)

## Project Overview

Gravitee.io Access Management (AM) is a flexible and lightweight Identity and Access Management (IAM) solution. It acts as a bridge between your applications and identity providers to centralize authentication, authorization, and user management.

**Key Features:**
*   **Protocols:** OAuth 2.0, OpenID Connect, SAML 2.0, SCIM 2.0, CAS.
*   **Security:** Multi-Factor Authentication (MFA), Passwordless (WebAuthn/FIDO2), Biometrics.
*   **Integration:** Marketplace for Identity Providers (LDAP, Database, Azure AD, Social providers, etc.).
*   **Management:** centralized Analytics Dashboard and Management Console.

**Architecture:**
The project is a multi-module Maven project (Java) with an Angular-based frontend.
*   **Backend:** Java (Spring framework ecosystem).
*   **Frontend:** Angular (located in `gravitee-am-ui/`).
*   **Storage:** Supports MongoDB and various RDBMS (PostgreSQL, MySQL, MariaDB, SQLServer) via R2DBC.

## Key Directories

*   `gravitee-am-gateway/`: The runtime engine handling access management requests.
*   `gravitee-am-management-api/`: The REST API used to configure the platform.
*   `gravitee-am-ui/`: The web-based Management Console (Angular).
*   `gravitee-am-identityprovider/`: Plugins for external Identity Providers.
*   `gravitee-am-policy/`: Plugins for applying policies during authentication/authorization flows.
*   `gravitee-am-repository/`: Database repository interfaces and implementations.
*   `docker/`: Docker Compose files and scripts for local deployment.

## Building and Running

### Prerequisites
*   Java (JDK 21 required)
*   Maven
*   Docker & Docker Compose

### Build Command
To build the entire project and run tests:
```bash
mvn clean install
```

### Running Locally (Docker)
The easiest way to run the full stack is via Docker.

**Quickstart:**
```bash
curl -L http://bit.ly/graviteeio-am | bash
```

**Using the repository scripts:**
Refer to `docker/README.adoc` for detailed instructions on launching specific environments or databases with SSL.

## Coding Standards & Best Practices

When making changes to this codebase, strict adherence to the following practices is required:

### 1. Code Style & Structure
*   **Adhere to Project Conventions:** Rigorously adhere to existing project conventions. Analyze surrounding code, tests, and configuration before writing new code.
*   **Mimic Existing Style:** Match the formatting, naming conventions, and architectural patterns of the existing code.
*   **Integrate Idiomatically:** Ensure changes integrate naturally with local context (imports, functions/classes). Use idiomatic Java (Spring) and TypeScript (Angular) patterns.
*   **Verify Library Usage:** Do not assume a library is available. Check `pom.xml` or `package.json` and established usage patterns before introducing new dependencies or classes.

### 2. Quality Assurance
*   **Create Tests:** Every new feature or bug fix must be accompanied by unit tests. Treat tests as permanent artifacts.
*   **Verify Build:** Always run `mvn clean install` to ensure changes pass build, linting, and tests.
*   **High-Value Comments:** Add comments sparingly. Focus on *why* complex logic exists, not *what* the code is doing.

### 3. Safety & Operations
*   **Ensure Safe Operations:** Be cautious with file system modifications.
*   **Secure Code:** Never introduce code that exposes secrets or sensitive information.

### 4. Version Control
*   **Branching:** Use the pattern `issue/#<issue-id>-short-description` (e.g., `issue/#123-fix-login-bug`).
*   **Commit Messages:** Write clear, concise messages focused on the "why". Format: `fix(auth): handle null pointer in login flow`.

## Contributing
1.  Fork the repository.
2.  Create a feature branch.
3.  Commit changes with descriptive messages.
4.  Submit a Pull Request to the `master` branch.
