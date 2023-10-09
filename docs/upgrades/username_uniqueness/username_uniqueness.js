/*
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


// DryRun: true to avoid applying renaming and only see if duplicates exist
//         false to rename duplicates
const dryRun = true;


/*  ======================
 *  ==== Helper functions:
 *  ======================
 */


/*
Search duplicates and sort them by loginsCount (higests come first) & loggedAt date (earliest comes first)
if never logged in, take the first profile created
*/
function searchDuplicates() {
    return db.users.aggregate([
        {
            "$group":{
                "_id":{
                    "source":"$source",
                    "username":"$username"
                },
                "count":{
                    "$sum":1
                }
            }
        },
        {
            "$match":{
                "count":{
                    "$gt":1
                }
            }
        },
        {
            "$lookup":{
                "from":"users",
                "localField":"_id.source",
                "foreignField":"source",
                "as":"result"
            }
        },
        {
            "$unwind":"$result"
        },
        {
            "$redact":{
                "$cond":[
                    {
                        "$eq":[
                            "$_id.username",
                            "$result.username"
                        ]
                    },
                    "$$KEEP",
                    "$$PRUNE"
                ]
            }
        },
        {
            "$project":{
                "result._id":1,
                "result.username":1,
                "result.externalId":1,
                "result.loggedAt":1,
                "result.createdAt":1,
                "result.loginsCount":1,
                "result.referenceId":1,
                "result.referenceType":1
            }
        },
        {
            "$sort": {
                "result.loginsCount":-1,
                "result.loggedAt":-1,
                "result.createdAt":1,
            }
        }
    ]).toArray()
};


/**
 * the source represent the IDP name on which we have to look into to get the idp collection
 */
function getIdpCollectionName(source) {
    var configStr = db.getCollection('identities').find({'_id': source}).next().configuration;
    var configObj = JSON.parse(configStr);
    return configObj['usersCollection'];
}


/*  =================
 *  ==== Processing :
 *  =================
 */


// create a report object to generate final summary
const report = {
    dryRun: dryRun
};

// count duplicates;
const duplicatesUsers = searchDuplicates();
console.log("[INFO] Found Duplicates: " + duplicatesUsers.length);
report['duplicates'] = duplicatesUsers.length;

// group duplicates by username/source
const duplicatesUsersGroupByUsernameAndSource = duplicatesUsers.reduce((acc, user) => {
    const userRef = user._id.username + '/' + user._id.source ;
    if (!acc[userRef]) {
        acc[userRef] = [];
    }
    acc[userRef].push(user)

    return acc;
}, {});

console.log("[INFO] Process duplicates...");
const usersCollection = db.getCollection("users");
Object.keys(duplicatesUsersGroupByUsernameAndSource).forEach(entry => {
    console.log("[INFO] Entry: " + entry);
    const reportEntry={};
    report[entry] = reportEntry;

    const dupUsers = duplicatesUsersGroupByUsernameAndSource[entry];

    const referenceUser = dupUsers.shift();
    const idpId = getIdpCollectionName(referenceUser._id.source);
    console.log("[INFO] Keep: user_id=" + referenceUser.result._id + " | externalId="+referenceUser.result.externalId);

    reportEntry['referenceUser'] = {
        username: referenceUser.result.username,
        createdAt: referenceUser.result.createdAt,
        loggedAt:referenceUser.result.loggedAt,
        loginsCount:referenceUser.result.loginsCount,
        userId: referenceUser.result._id,
        externalId: referenceUser.result.externalId,
        idpId: idpId,
        idpName: referenceUser._id.source,
        domainId: referenceUser.result.referenceId
    }
    reportEntry['renamedUsers'] = [];

    try {

        if (db.getCollectionNames().includes(idpId)) {
            const idpCollection = db.getCollection(idpId);

            // rename remaining users
            for (i = 0; i < dupUsers.length; ++i) {
                const entryToUpdate = dupUsers[i];
                const updatedName = entryToUpdate._id.username + "_" + i + "_TO_RENAME_OR_DELETE";

                const renamedEntityDesc = {
                    username: entryToUpdate.result.username,
                    createdAt: entryToUpdate.result.createdAt,
                    loggedAt: entryToUpdate.result.loggedAt,
                    loginsCount: entryToUpdate.result.loginsCount,
                    userId: entryToUpdate.result._id,
                    externalId: entryToUpdate.result.externalId,
                    idpId: idpId,
                    idpName: entryToUpdate._id.source,
                    domainId: entryToUpdate.result.referenceId,
                    renameTo: updatedName
                };

                reportEntry['renamedUsers'].push(renamedEntityDesc);
                console.log("[INFO] Renaming... " + JSON.stringify(renamedEntityDesc));

                if (dryRun) {
                    console.log("[INFO] Skip user update (DRY RUN) mode");
                } else {
                    idpCollection.updateOne({"_id": entryToUpdate.result.externalId}, {"$set": {"username": updatedName}});
                    usersCollection.updateOne({"_id": entryToUpdate.result._id}, {"$set": {"username": updatedName}});
                }
            }
        } else {
            console.error("[ERROR] Unable to rename remaining users with username [" + referenceUser._id.username + "] IDP collection is missing");
            reportEntry['error'] = "Idp collection [" + idpId +"] not found";
        }

    } catch(error) {
        console.error("[ERROR] Unable to rename remaining users with username [" + referenceUser._id.username + "] due to : " + error);
        reportEntry['error'] = error;
    }
});

console.log("[INFO] End of migration, generating summary...", '', '' );
console.log(JSON.stringify(report));