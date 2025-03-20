#!/bin/bash
#
# Copyright (C) 2015 The Gravitee team (http://gravitee.io)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#         http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#


# Configuration
SOURCE_DB_HOST="source_host"
SOURCE_DB_PORT="5432"
SOURCE_DB_NAME="source_db"
SOURCE_DB_USER="source_user"
SOURCE_DB_PASSWORD="source_password"

DEST_DB_HOST="destination_host"
DEST_DB_PORT="5432"
DEST_DB_NAME="destination_db"
DEST_DB_USER="destination_user"
DEST_DB_PASSWORD="destination_password"

# List of tables to migrate (can also read from a file, see below for alternative)
TABLES=("uma_resource_set" "uma_resource_scopes" "webauthn_credentials" "groups" "group_members" "group_roles" "devices" "password_histories" "users" "user_entitlements" "user_roles" "user_addresses" "user_attributes" "dynamic_user_roles" "dynamic_user_groups" "user_activities" "user_identities" "uma_access_policies" "uma_permission_ticket")

# Export passwords to avoid being prompted
export PGPASSWORD=$SOURCE_DB_PASSWORD

# Create a backup of the schema and data for each table
for TABLE in "${TABLES[@]}"; do
  echo "Starting backup of table: $TABLE from the source database..."
  pg_dump -h $SOURCE_DB_HOST -p $SOURCE_DB_PORT -U $SOURCE_DB_USER -F c -t $TABLE --inserts --on-conflict-do-nothing -b -v -f /tmp/${TABLE}_backup.dump $SOURCE_DB_NAME

  # Check if the backup was successful
  if [[ $? -ne 0 ]]; then
    echo "Error during backup of table $TABLE. Exiting..."
    exit 1
  fi
  echo "Backup of table $TABLE completed successfully."
done

# Create the destination database if it doesn't exist
echo "Checking if destination database exists..."
PGPASSWORD=$DEST_DB_PASSWORD psql -h $DEST_DB_HOST -p $DEST_DB_PORT -U $DEST_DB_USER -d postgres -c "SELECT 1 FROM pg_database WHERE datname = '$DEST_DB_NAME'" | grep -q 1

if [[ $? -ne 0 ]]; then
  echo "Destination database doesn't exist. Creating destination database..."
  PGPASSWORD=$DEST_DB_PASSWORD psql -h $DEST_DB_HOST -p $DEST_DB_PORT -U $DEST_DB_USER -d postgres -c "CREATE DATABASE $DEST_DB_NAME"
else
  echo "Destination database already exists."
fi

# Restore the backup for each table to the destination database
for TABLE in "${TABLES[@]}"; do
  echo "Restoring table: $TABLE to the destination database..."
  export PGPASSWORD=$DEST_DB_PASSWORD
  pg_restore -h $DEST_DB_HOST -p $DEST_DB_PORT -U $DEST_DB_USER -d $DEST_DB_NAME -v /tmp/${TABLE}_backup.dump

  # Check if the restore was successful
  if [[ $? -ne 0 ]]; then
    echo "Error during restore of table $TABLE. Exiting..."
    exit 1
  fi
  echo "Table $TABLE restored successfully."
done

# Clean up
for TABLE in "${TABLES[@]}"; do
  rm /tmp/${TABLE}_backup.dump
  echo "Backup file for table $TABLE removed."
done

echo "Database migration for specified tables completed successfully."
