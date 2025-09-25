#!/bin/bash

# Simple build script for WebAuthn4J PoC
# This script handles the build process with better error reporting

set -e

echo "🔨 WebAuthn4J PoC - Build Script"
echo "================================"

# Check Java
echo "🔍 Checking Java..."
if ! command -v java &> /dev/null; then
    echo "❌ Java not found. Please install Java 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ Java 17+ required. Found Java $JAVA_VERSION"
    exit 1
fi
echo "✅ Java $JAVA_VERSION found"

# Check Maven
echo "🔍 Checking Maven..."
if ! command -v mvn &> /dev/null; then
    echo "❌ Maven not found. Please install Maven 3.6+"
    exit 1
fi
echo "✅ Maven found"

# Clean
echo "🧹 Cleaning previous build..."
mvn clean

# Compile
echo "🔨 Compiling..."
mvn compile

# Copy dependencies
echo "📦 Copying dependencies..."
mvn dependency:copy-dependencies -DoutputDirectory=target/dependency

# Package
echo "📦 Packaging..."
mvn package -DskipTests

echo "✅ Build completed successfully!"
echo ""
echo "To run the application:"
echo "  ./run.sh [port]"
echo ""
echo "Example:"
echo "  ./run.sh 8080"
