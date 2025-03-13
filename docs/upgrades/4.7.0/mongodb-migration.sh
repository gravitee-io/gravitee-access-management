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


# Source MongoDB Cluster details
SOURCE_URI="mongodb://user:pasword@source.mongodb.cluster:27017"
SOURCE_DB="source_database"

# Destination MongoDB Cluster details
DEST_URI="mongodb://user:pasword@destination.mongodb.cluster:27017"
DEST_DB="destination_database"

# Path for temporary dump
TEMP_DUMP_PATH="/tmp/mongo_dump"



# List of collections to migrate
COLLECTIONS=("uma_access_policies" "webauthn_credentials" "devices" "groups" "login_attempts" "password_histories" "uma_permission_ticket" "uma_resource_set" "user_activities" "users")

# Function to dump and restore a collection
migrate_collection() {
    local collection=$1

    echo "Migrating collection: $collection"

    # Dump the collection from the source cluster
    echo "Dumping $collection from source cluster..."
    mongodump --uri $SOURCE_URI --db $SOURCE_DB --collection $collection --out $TEMP_DUMP_PATH

    # Check if dump was successful
    if [ $? -ne 0 ]; then
        echo "Error during mongodump for $collection. Skipping."
        return 1
    fi

    # Restore the collection to the destination cluster
    echo "Restoring $collection to destination cluster..."
    mongorestore --uri $DEST_URI --db $DEST_DB --collection $collection $TEMP_DUMP_PATH/$SOURCE_DB/$collection.bson

    # Check if restore was successful
    if [ $? -ne 0 ]; then
        echo "Error during mongorestore for $collection. Skipping."
        return 1
    fi

    # Clean up dump
    rm -rf $TEMP_DUMP_PATH
    echo "$collection migrated successfully."
}

# Iterate through the list of collections and migrate them
for collection in "${COLLECTIONS[@]}"; do
    migrate_collection $collection
done

# End of script
echo "Migration completed!"
