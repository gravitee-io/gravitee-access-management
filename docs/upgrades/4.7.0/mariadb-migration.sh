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
SOURCE_DB_PORT="3306"
SOURCE_DB_NAME="source_db"
SOURCE_DB_USER="source_user"
SOURCE_DB_PASSWORD="source_password"

DEST_DB_HOST="destination_host"
DEST_DB_PORT="3306"
DEST_DB_NAME="destination_db"
DEST_DB_USER="destination_user"
DEST_DB_PASSWORD="destination_password"

# List of tables to migrate (can also read from a file, see below for alternative)
TABLES=("uma_resource_set" "uma_resource_scopes" "webauthn_credentials" "groups" "group_members" "group_roles" "devices" "password_histories" "users" "user_entitlements" "user_roles" "user_addresses" "user_attributes" "dynamic_user_roles" "dynamic_user_groups" "user_activities" "user_identities" "uma_access_policies" "uma_permission_ticket")

# Export passwords to avoid being prompted
export MYSQL_PWD=$SOURCE_DB_PASSWORD

# Create the destination database if it doesn't exist
echo "Checking if destination database exists..."
mariadb -h $DEST_DB_HOST -P $DEST_DB_PORT -u $DEST_DB_USER -e "CREATE DATABASE IF NOT EXISTS $DEST_DB_NAME;"

# Migrate data for each table
for TABLE in "${TABLES[@]}"; do
  echo "Starting backup of table: $TABLE from the source database..."

  # Export schema and data for the table from the source database using mysqldump
  mariadb-dump -h $SOURCE_DB_HOST -P $SOURCE_DB_PORT -u $SOURCE_DB_USER --no-tablespaces --single-transaction --quick --lock-tables=false $SOURCE_DB_NAME $TABLE > /tmp/${TABLE}_backup.sql

  # Check if the export was successful
  if [[ $? -ne 0 ]]; then
    echo "Error during export of table $TABLE. Exiting..."
    exit 1
  fi
  echo "Backup of table $TABLE completed successfully."

  # Import schema and data into the destination database
  echo "Restoring table: $TABLE into the destination database..."
  mariadb -h $DEST_DB_HOST -P $DEST_DB_PORT -u $DEST_DB_USER $DEST_DB_NAME < /tmp/${TABLE}_backup.sql

  # Check if the import was successful
  if [[ $? -ne 0 ]]; then
    echo "Error during import of table $TABLE. Exiting..."
    exit 1
  fi
  echo "Table $TABLE restored successfully."

  # Clean up the backup file
  rm /tmp/${TABLE}_backup.sql
  echo "Backup file for table $TABLE removed."
done

echo "MariaDB table migration completed successfully."
