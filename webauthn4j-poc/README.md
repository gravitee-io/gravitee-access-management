# WebAuthn4J PoC - Fallback Certificate Validation

A Proof of Concept demonstrating WebAuthn4J integration using **Vert.x Auth WebAuthn4J** (v5.0.4) with fallback certificate validation for Android Key attestation.

## 🎯 Purpose

This PoC demonstrates how to handle Android Key attestation validation using two approaches:
- **Hardcoded certificates** with fallback validation sequence
- **MDS-only mode** using FIDO Alliance Metadata Service

## 🚀 Quick Start

### Prerequisites
- Java 21+
- Maven 3.6+

### Running the PoC

```bash
# Default mode (hardcoded certificates with dynamic trust anchor)
./run.sh --port=8080

# MDS-only mode
./run.sh --mds-only --port=8080

# Help
./run.sh --help
```

### Verify Installation

```bash
# Check server health
curl http://localhost:8080/health

# Check configuration
curl http://localhost:8080/webauthn/config
```

## 🔧 Implementation

### Fallback Certificate Validation

The PoC uses multiple Android Key certificates in a fallback sequence:

```java
// Hardcoded certificates mode (default) - uses fallback validation
WebAuthnService service = new WebAuthnService(vertx, false);

// MDS-only mode - uses Metadata Service validation
WebAuthnService service = new WebAuthnService(vertx, true);
```

### Key Features

- **Fallback Validation**: Multiple certificates tried in sequence
- **5 Android Key Certificates**: Pre-loaded for comprehensive testing
- **MDS Integration**: Optional Metadata Service validation
- **Simple Configuration**: Single `--mds-only` flag

## 📱 Testing Fallback Validation

### 1. Test Default Mode (Hardcoded Certificates with Fallback)

```bash
# Start server
./run.sh --port=8080

# Check configuration
curl http://localhost:8080/webauthn/config
```

### 2. Test MDS-Only Mode

```bash
# Start in MDS-only mode
./run.sh --mds-only --port=8080

# Check configuration
curl http://localhost:8080/webauthn/config
```

### 3. Test Registration/Authentication

```bash
# Begin registration
curl -X POST http://localhost:8080/webauthn/register/begin \
  -H "Content-Type: application/json" \
  -d '{"username": "testuser"}'

# Complete registration (with Android Key attestation)
curl -X POST http://localhost:8080/webauthn/register/complete \
  -H "Content-Type: application/json" \
  -d '{
    "username": "testuser",
    "credential": {
      "id": "credential-id",
      "rawId": "raw-credential-id",
      "response": {
        "attestationObject": "attestation-object",
        "clientDataJSON": "client-data-json"
      },
      "type": "public-key"
    }
  }'
```

## 📚 Resources

- [WebAuthn4J](https://webauthn4j.github.io/webauthn4j/en/#webauthn4j)
- [FIDO Alliance MDS](https://fidoalliance.org/metadata/)
- [Android Key Attestation](https://developer.android.com/training/articles/security-key-attestation)
