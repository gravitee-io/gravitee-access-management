# Gravitee AM MCP Server E2E

Minimal MCP Server for Gravitee AM E2E Testing.

## Overview

This is a minimal MCP (Model Context Protocol) server designed specifically for E2E testing of Gravitee AM's AuthZen endpoint authentication. It provides:

- Simple mock tools (`getTests`, `addTest`, `deleteTest`)
- PEP (Policy Enforcement Point) functionality with OAuth 2.0 token introspection
- AuthZen integration for authorization decisions
- Test metadata management for sequential E2E test execution
- Docker support for containerized testing

## Features

- **Test Metadata Management**: E2E tests can inject test metadata (testId + token) via admin API
- **Test Isolation**: Each test has a unique testId, and all responses include testId for verification
- **Sequential Test Support**: Multiple tests can use the same MCP server container sequentially
- **Conflict Prevention**: Can't inject new metadata if existing metadata exists (409 Conflict)

## Quick Start

### Prerequisites

- Node.js 18+
- TypeScript 5+
- Docker (for containerized deployment)

### Installation

```bash
npm install
```

### Development

```bash
npm run dev
```

### Build

```bash
npm run build
npm start
```

## Configuration

Copy `.env.example` to `.env` and configure:

```env
PORT=3001
AM_GATEWAY_URL=http://gateway:8092
DOMAIN_HRID=test-domain
AUTHZEN_URL=http://gateway:8092
LOG_LEVEL=info
```

## API Endpoints

### Health Check

```
GET /health
```

Returns server status and current testId if metadata is configured.

### Admin Endpoints

#### Inject Test Metadata

```
POST /admin/test-metadata
Content-Type: application/json

{
  "testId": "test-123",
  "token": "eyJhbGc...",
  "expiresIn": 3600
}
```

#### Clear Test Metadata

```
DELETE /admin/test-metadata
```

## Docker

### Build and Run

```bash
# Build the image
docker compose -f docker/docker-compose.yml build

# Start the server
docker compose -f docker/docker-compose.yml up -d

# Check logs
docker logs -f mcp-server-e2e

# Stop the server
docker compose -f docker/docker-compose.yml down
```

### Manual Testing

Quick test:
```bash
# Health check
curl http://localhost:3001/health

# Inject test metadata
curl -X POST http://localhost:3001/admin/test-metadata \
  -H "Content-Type: application/json" \
  -d '{
    "testId": "test-123",
    "token": "your-token",
    "clientId": "your-client-id",
    "clientSecret": "your-client-secret",
    "expiresIn": 3600,
    "domainHrid": "your-domain"
  }'
```

## Code Quality

- ✅ All code follows TypeScript strict mode
- ✅ Comprehensive unit tests
- ✅ Consistent error handling patterns
- ✅ Proper input validation

