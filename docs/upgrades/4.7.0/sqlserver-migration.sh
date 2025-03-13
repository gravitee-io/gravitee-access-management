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
SOURCE_DB_SERVER="source_server"
SOURCE_DB_NAME="source_db"
SOURCE_DB_USER="source_user"
SOURCE_DB_PASSWORD="source_password"

DEST_DB_SERVER="destination_server"
DEST_DB_NAME="destination_db"
DEST_DB_USER="destination_user"
DEST_DB_PASSWORD="destination_password"

# SQLCMD and BCP paths
SQLCMD_PATH="/opt/mssql-tools/bin/sqlcmd"
BCP_PATH="/opt/mssql-tools/bin/bcp"
SCHEMA="$(dirname $0)/sqlserver.schema"


# List of tables to migrate (can also read from a file, see below for alternative)
TABLES=("uma_resource_set" "uma_resource_scopes" "webauthn_credentials" "groups" "group_members" "group_roles" "devices" "password_histories" "users" "user_entitlements" "user_roles" "user_addresses" "user_attributes" "dynamic_user_roles" "dynamic_user_groups" "user_activities" "login_attempts" "user_identities" "uma_access_policies" "uma_permission_ticket")

# Export passwords to avoid being prompted
export SQLCMDPASSWORD=$SOURCE_DB_PASSWORD
export BCP_PASSWORD=$SOURCE_DB_PASSWORD

# Create the destination database if it doesn't exist
echo "Checking if destination database exists..."
$SQLCMD_PATH -S $DEST_DB_SERVER -U $DEST_DB_USER -P $DEST_DB_PASSWORD -d master -Q "IF NOT EXISTS (SELECT * FROM sys.databases WHERE name = '$DEST_DB_NAME') CREATE DATABASE $DEST_DB_NAME"

# Create the table schema in the destination database (this assumes the schema is identical)
echo "Creating schema for tables in the destination database..."
$SQLCMD_PATH -S $DEST_DB_SERVER -U $DEST_DB_USER -P $DEST_DB_PASSWORD -d $DEST_DB_NAME -i $SCHEMA

  # Check if the schema creation was successful
if [[ $? -ne 0 ]]; then
  echo "Error during schema creation. Exiting..."
  exit 1
fi
echo "Schema creation completed successfully."

# Migrate data for each table
for TABLE in "${TABLES[@]}"; do
  echo "Starting backup of table: $TABLE from the source database..."

  # Export data from the source table using bcp
  $BCP_PATH "$SOURCE_DB_NAME.dbo.$TABLE" out /tmp/${TABLE}_data.dat -S $SOURCE_DB_SERVER -U $SOURCE_DB_USER -P $SOURCE_DB_PASSWORD -c -t"," -r"\n"

  # Check if the export was successful
  if [[ $? -ne 0 ]]; then
    echo "Error during export of table $TABLE. Exiting..."
    exit 1
  fi
  echo "Data export of table $TABLE completed successfully."


  # Import data into the destination table using bcp
  echo "Importing data into table: $TABLE in the destination database..."
  $BCP_PATH "$DEST_DB_NAME.dbo.$TABLE" in /tmp/${TABLE}_data.dat -S $DEST_DB_SERVER -U $DEST_DB_USER -P $DEST_DB_PASSWORD -c -t"," -r"\n"

  # Check if the import was successful
  if [[ $? -ne 0 ]]; then
    echo "Error during import of data into table $TABLE. Exiting..."
    exit 1
  fi
  echo "Data import into table $TABLE completed successfully."

  # Clean up the data file
  rm /tmp/${TABLE}_data.dat
  echo "Backup file for table $TABLE removed."
done

echo "SQL Server table migration completed successfully."
