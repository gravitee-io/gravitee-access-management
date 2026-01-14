#!/bin/bash
# Usage: wait-argocd-sync.sh [max-wait-minutes]
# Environment variables:
# - MIGRATION_ARGOCD_SERVER: ArgoCD server URL
# - MIGRATION_ARGOCD_APP_NAME: Application name
# - MIGRATION_ARGOCD_TOKEN: API token

set -e

ARGOCD_SERVER=${MIGRATION_ARGOCD_SERVER:-"argocd.example.com"}
APP_NAME=${MIGRATION_ARGOCD_APP_NAME}
TOKEN=${MIGRATION_ARGOCD_TOKEN}
MAX_WAIT_MINUTES=${1:-10}

if [ -z "$APP_NAME" ] || [ -z "$TOKEN" ]; then
    echo "Error: MIGRATION_ARGOCD_APP_NAME and MIGRATION_ARGOCD_TOKEN must be set"
    exit 1
fi

echo "Waiting for ArgoCD application '$APP_NAME' to sync..."
echo "Server: $ARGOCD_SERVER"
echo "Timeout: $MAX_WAIT_MINUTES minutes"

START_TIME=$(date +%s)
END_TIME=$((START_TIME + MAX_WAIT_MINUTES * 60))

while [ $(date +%s) -lt $END_TIME ]; do
    RESPONSE=$(curl -s -k -H "Authorization: Bearer $TOKEN" "https://$ARGOCD_SERVER/api/v1/applications/$APP_NAME")
    
    # Check for curl errors
    if [ $? -ne 0 ]; then
        echo "Error querying ArgoCD API"
        sleep 10
        continue
    fi
    
    # Parse status using simple grep/sed to avoid jq dependency if possible, but jq is standard in CircleCI images
    # Assuming jq is available
    if command -v jq &> /dev/null; then
        SYNC_STATUS=$(echo "$RESPONSE" | jq -r '.status.sync.status')
        HEALTH_STATUS=$(echo "$RESPONSE" | jq -r '.status.health.status')
    else
        # Fallback if jq missing (less reliable)
        SYNC_STATUS=$(echo "$RESPONSE" | grep -o '"sync":{"status":"[^"]*"' | cut -d'"' -f4)
        HEALTH_STATUS=$(echo "$RESPONSE" | grep -o '"health":{"status":"[^"]*"' | cut -d'"' -f4)
    fi
    
    echo "[$(date +%H:%M:%S)] Sync: $SYNC_STATUS | Health: $HEALTH_STATUS"
    
    if [ "$SYNC_STATUS" == "Synced" ] && [ "$HEALTH_STATUS" == "Healthy" ]; then
        echo "✅ Application '$APP_NAME' is fully synced and healthy!"
        exit 0
    fi
    
    sleep 10
done

echo "❌ Timeout waiting for ArgoCD sync after $MAX_WAIT_MINUTES minutes"
exit 1
