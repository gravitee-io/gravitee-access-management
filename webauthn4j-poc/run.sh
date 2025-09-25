#!/bin/bash

# WebAuthn4J PoC Runner Script
# This script builds and runs the WebAuthn4J PoC with certificate rotation and MDS support

set -e

echo "🚀 WebAuthn4J PoC - Certificate Rotation & MDS Integration"
echo "=========================================================="

# Function to check if command exists
command_exists() {
    command -v "$1" >/dev/null 2>&1
}

# Function to get Java version
get_java_version() {
    if command_exists java; then
        java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1
    else
        echo "0"
    fi
}

# Check Java version
echo "🔍 Checking Java version..."
JAVA_VERSION=$(get_java_version)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Java 17+ is required. Found Java $JAVA_VERSION."
    echo "   Please install Java 17 or higher (OpenJDK 21 is supported)."
    echo "   Current Java version:"
    java -version 2>&1 | head -n 1 || echo "   Java not found"
    exit 1
fi
echo "✅ Java version check passed (Java $JAVA_VERSION)"

# Check Maven
echo "🔍 Checking Maven..."
if ! command_exists mvn; then
    echo "❌ Maven is not installed. Please install Maven 3.6+."
    exit 1
fi
echo "✅ Maven found"

# Clean and build
echo "🔨 Building the project..."
echo "   This may take a few minutes on first run due to dependency download..."

if ! mvn clean package; then
    echo "❌ Build failed. Common solutions:"
    echo "   1. Check internet connection for dependency download"
    echo "   2. Try: mvn clean package -U (force update dependencies)"
    echo "   3. Check Maven settings: ~/.m2/settings.xml"
    echo "   4. Try: mvn dependency:resolve"
    exit 1
fi
echo "✅ Build completed"

# Parse command line arguments
MODE="default"
PORT=8080
ARGS=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --mds-only)
            MODE="mds-only"
            ARGS="$ARGS --mds-only"
            shift
            ;;
        --no-mds)
            MODE="no-mds"
            ARGS="$ARGS --no-mds"
            shift
            ;;
        --no-rotation)
            MODE="no-rotation"
            ARGS="$ARGS --no-rotation"
            shift
            ;;
        --no-hardcoded-certs)
            MODE="no-hardcoded-certs"
            ARGS="$ARGS --no-hardcoded-certs"
            shift
            ;;
        --port=*)
            PORT="${1#*=}"
            ARGS="$ARGS --port=$PORT"
            shift
            ;;
        --help)
            echo "Usage: $0 [OPTIONS]"
            echo ""
            echo "Options:"
            echo "  --mds-only              Use MDS for validation (default: hardcoded certs)"
            echo "  --port=PORT             Set server port (default: 8080)"
            echo "  --help                  Show this help message"
            echo ""
            echo "Examples:"
            echo "  $0                      # Default mode with hardcoded certs"
            echo "  $0 --mds-only           # MDS only mode"
            echo "  $0 --port=9090          # Run on port 9090"
            exit 0
            ;;
        *)
            # Legacy support for port as first argument
            if [[ $1 =~ ^[0-9]+$ ]]; then
                PORT=$1
                ARGS="$ARGS --port=$PORT"
            else
                echo "Unknown option: $1"
                echo "Use --help for usage information"
                exit 1
            fi
            shift
            ;;
    esac
done

# Check if dependencies were copied
if [ ! -d "target/dependency" ]; then
    echo "❌ Dependencies not found. Running dependency copy..."
    mvn dependency:copy-dependencies -DoutputDirectory=target/dependency
fi

# Create classpath
CLASSPATH="target/classes"
if [ -d "target/dependency" ]; then
    # Use proper path separator for OS
    if [[ "$OSTYPE" == "msys" || "$OSTYPE" == "cygwin" ]]; then
        # Windows
        CLASSPATH="$CLASSPATH;target/dependency/*"
    else
        # Unix/Linux/macOS
        CLASSPATH="$CLASSPATH:target/dependency/*"
    fi
fi

# Start the server
echo "🚀 Starting WebAuthn4J PoC server on port $PORT..."
echo ""
echo "📋 Available endpoints:"
echo "   Health Check: http://localhost:$PORT/health"
echo "   WebAuthn Config: http://localhost:$PORT/webauthn/config"
echo "   Certificate Management: http://localhost:$PORT/webauthn/certificates"
echo "   MDS Management: http://localhost:$PORT/webauthn/mds/config"
echo "   Registration: http://localhost:$PORT/webauthn/register/begin"
echo "   Authentication: http://localhost:$PORT/webauthn/authenticate/begin"
echo ""
echo "📱 Testing:"
echo "   1. Use Postman collection: postman/WebAuthn4J-PoC.postman_collection.json"
echo "   2. Run test script: ./scripts/test-android-key.sh"
echo "   3. Test with webauthn.io: https://webauthn.io/"
echo ""
echo "🛑 Press Ctrl+C to stop the server"
echo ""

# Start the server
echo "🔧 Starting server with Java..."
echo "   Mode: $MODE"
echo "   Port: $PORT"
echo "   Args: $ARGS"
echo ""
java -cp "$CLASSPATH" io.gravitee.am.webauthn4j.poc.Main $ARGS
