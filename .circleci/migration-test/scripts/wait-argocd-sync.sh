#!/bin/bash
# ---------------------------------------------------------------------------
# Script: wait-argocd-sync.sh
# Description: Polls the ArgoCD API to check the sync and health status of an application.
#              This is used in the pipeline to ensure a deployment has finished (Synced & Healthy)
#              before running tests.
#
# Usage: ./wait-argocd-sync.sh [max-wait-minutes]
#
# Environment Variables:
#   - MIGRATION_ARGOCD_SERVER: URL of the ArgoCD server (e.g., argocd.example.com)
#   - MIGRATION_ARGOCD_APP_NAME: Name of the application in ArgoCD
#   - MIGRATION_ARGOCD_TOKEN: Bearer token for authentication
# ---------------------------------------------------------------------------

set -e

ARGOCD_SERVER=${MIGRATION_ARGOCD_SERVER:-"argocd.example.com"}
APP_NAME=${MIGRATION_ARGOCD_APP_NAME}
TOKEN=${MIGRATION_ARGOCD_TOKEN}
MAX_WAIT_MINUTES=${1:-10}

# --- Validation ---
if [ -z "$APP_NAME" ] || [ -z "$TOKEN" ]; then
    echo "Error: MIGRATION_ARGOCD_APP_NAME and MIGRATION_ARGOCD_TOKEN must be set"
    echo "Usage: $0 [max-wait-minutes]"
    exit 1
fi

echo "Detailed Status Check:"
echo "----------------------"
echo "Target App:    $APP_NAME"
echo "ArgoCD Server: $ARGOCD_SERVER"
echo "Timeout:       $MAX_WAIT_MINUTES minutes"
echo "BS:            $(date)"

START_TIME=$(date +%s)
END_TIME=$((START_TIME + MAX_WAIT_MINUTES * 60))

# --- Polling Loop ---
while [ $(date +%s) -lt $END_TIME ]; do
    # Fetch application status from ArgoCD API
    RESPONSE=$(curl -s -k -H "Authorization: Bearer $TOKEN" "https://$ARGOCD_SERVER/api/v1/applications/$APP_NAME")
    
    # Check for curl connectivity errors
    if [ $? -ne 0 ]; then
        echo "⚠️ Error querying ArgoCD API. Retrying in 10s..."
        sleep 10
        continue
    fi
    
    # Parse status using jq (preferred) or grep/cut fallback
    if command -v jq &> /dev/null; then
        SYNC_STATUS=$(echo "$RESPONSE" | jq -r '.status.sync.status')
        HEALTH_STATUS=$(echo "$RESPONSE" | jq -r '.status.health.status')
    else
        # Fallback if jq missing (less reliable, but works for simple JSON)
        SYNC_STATUS=$(echo "$RESPONSE" | grep -o '"sync":{"status":"[^"]*"' | cut -d'"' -f4)
        HEALTH_STATUS=$(echo "$RESPONSE" | grep -o '"health":{"status":"[^"]*"' | cut -d'"' -f4)
    fi
    
    echo "[$(date +%H:%M:%S)] Status -> Sync: $SYNC_STATUS | Health: $HEALTH_STATUS"
    
    # Success Condition: Synced AND Healthy
    if [ "$SYNC_STATUS" == "Synced" ] && [ "$HEALTH_STATUS" == "Healthy" ]; then
        echo "✅ Application '$APP_NAME' is fully synced and healthy!"
        DURATION=$(($(date +%s) - $START_TIME))
        echo "Deployment took ${DURATION} seconds."
        exit 0
    fi
    
    # Failure Condition: Degraded
    if [ "$HEALTH_STATUS" == "Degraded" ]; then
        echo "⚠️ Application is Degraded. It might recover, or deployment failed."
        # We don't exit immediately as it might be temporary during rollout, 
        # but in a stricter pipeline you might choose to fail here.
    fi

    sleep 10
done

echo "❌ Timeout waiting for ArgoCD sync after $MAX_WAIT_MINUTES minutes"
exit 1
