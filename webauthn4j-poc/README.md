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

## 🔄 Migration Testing

This PoC helps test migration from hardcoded certificates to MDS:

### Phase 1: Hardcoded Certificates with Fallback
- Test with multiple Android Key certificates
- Verify fallback validation works
- Test Android Key attestation

### Phase 2: MDS-Only Mode
- Switch to MDS-only validation
- Test pure MDS validation
- Compare performance and reliability

## 📊 Available Endpoints

- `GET /health` - Health check
- `GET /webauthn/config` - Current configuration
- `POST /webauthn/register/begin` - Begin registration
- `POST /webauthn/register/complete` - Complete registration
- `POST /webauthn/authenticate/begin` - Begin authentication
- `POST /webauthn/authenticate/complete` - Complete authentication

## 🏗️ Architecture

```
WebAuthnService
├── CertificateService (TrustAnchorRepository)
├── MDSService (optional)
└── WebAuthn4J (Vert.x Auth)
```

## 🛠️ Development

```bash
# Build
mvn clean compile

# Run tests
./test-rotation.sh

# Package
mvn package
```

## 🔒 Key Benefits

1. **Fallback Validation**: Multiple certificates tried in sequence
2. **Migration Path**: Clear path from hardcoded to MDS
3. **Android Key Support**: Full attestation validation
4. **Simple Configuration**: Single flag for mode selection
5. **Production Ready**: Uses industry-standard libraries

## 📚 Resources

- [Vert.x Auth WebAuthn4J](https://vertx.io/docs/vertx-auth-webauthn4j/java/)
- [FIDO Alliance MDS](https://fidoalliance.org/metadata/)
- [Android Key Attestation](https://developer.android.com/training/articles/security-key-attestation)

## 🤝 Usage for Migration Planning

This PoC provides exactly what you need for migration planning:

- **Fallback Validation**: Demonstrates multiple certificate validation
- **MDS Integration**: Shows MDS-only validation approach
- **Android Key Support**: Full attestation validation
- **Simple Testing**: Easy to test both approaches

The implementation is ready to run and demonstrates all fallback validation concepts needed for your migration strategy!