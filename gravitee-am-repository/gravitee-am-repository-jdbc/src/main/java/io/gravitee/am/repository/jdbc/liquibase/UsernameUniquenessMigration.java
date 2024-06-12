/**
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
package io.gravitee.am.repository.jdbc.liquibase;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.gravitee.am.model.User;
import liquibase.change.custom.CustomSqlChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.DatabaseException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;
import liquibase.statement.SqlStatement;
import liquibase.statement.core.UpdateStatement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
public class UsernameUniquenessMigration implements CustomSqlChange {
    public static final String TABLE_USERS = "users";
    public static final String TABLE_ORGANIZATION_USERS = "organization_users";
    public static final String USERNAME = "username";
    private final Logger logger = LoggerFactory.getLogger(UsernameUniquenessMigration.class);
    private ObjectMapper mapper = new ObjectMapper();

    @Override
    public SqlStatement[] generateStatements(Database database) throws CustomChangeException {
        logger.debug("Starting username migration...");
        // Statement accumulator to provide to Liquibase the statements to execute
        List<SqlStatement> statements = new ArrayList<>();

        try {
            JdbcConnection connection = (JdbcConnection) database.getConnection();

            Map<String, List<User>> duplicatesUsersGroupByUsernameAndSource = searchDuplicatesGroupedByUsernameAndSource(connection, TABLE_USERS);

            // boolean to keep in memory if the process must fail
            // in order to go through all the duplicates to see all potential errors.
            boolean needToFail = false;

            for (Map.Entry<String, List<User>> entries : duplicatesUsersGroupByUsernameAndSource.entrySet()) {
                List<User> duplicates = entries.getValue();
                User referenceUser = duplicates.get(0);

                PreparedStatement searchIdentityProviderStmt = connection.prepareStatement("select * from identities where id = ? ");
                searchIdentityProviderStmt.setString(1, referenceUser.getSource());
                ResultSet idpResult = searchIdentityProviderStmt.executeQuery();
                if (!idpResult.next()) {
                    logger.error("Username '{}' can't be processed due to unknown identity provider with id '{}'",
                            referenceUser.getUsername(),
                            referenceUser.getSource());
                    needToFail = true;
                } else {
                    String idpConfigStr = idpResult.getString("configuration");
                    Map<String, Object> idpConfig = mapper.readValue(idpConfigStr, HashMap.class);

                    String idpTable = (String)idpConfig.get("usersTable");
                    String usernameColumn = (String)idpConfig.get("usernameAttribute");
                    String idColumn = (String)idpConfig.get("identifierAttribute");
                    Boolean autoProvisioned = (Boolean)idpConfig.get("autoProvisioning");
                    if (!autoProvisioned) {
                        // we can't process automatically IDP table that are not "auto provisioned" as they may be defined in another DB
                        logger.error("Duplicate user detected in IdentityProvider different from the default one for username '{}' and idp '{}'",
                                referenceUser.getUsername(),
                                referenceUser.getSource());
                        needToFail = true;
                    }

                    // Duplicates found in default IDP, we can process them
                    statements.addAll(generateStatementToRenameDuplicatedUsers(database, duplicates, idpTable, usernameColumn, idColumn));

                    idpResult.close();
                }
            }

            Map<String, List<User>> duplicatesOrgUsersGroupByUsernameAndSource = searchDuplicatesGroupedByUsernameAndSource(connection, TABLE_ORGANIZATION_USERS);
            for (Map.Entry<String, List<User>> entries : duplicatesOrgUsersGroupByUsernameAndSource.entrySet()) {
                List<User> duplicates = entries.getValue();
                User referenceUser = duplicates.get(0);

                if (!referenceUser.getSource().equalsIgnoreCase("gravitee") && !referenceUser.getSource().equalsIgnoreCase("cockpit")) {
                    logger.error("Organization Username '{}' migration only manages gravitee & cockpit identity providers",
                            referenceUser.getUsername(),
                            referenceUser.getSource());
                    needToFail = true;
                } else {
                    statements.addAll(generateStatementToRenameDuplicatedOrgUsers(database, duplicates));
                }
            }

            if (needToFail) {
                throw new CustomChangeException("Some duplicates can't be processed automatically, liquibase will fail");
            }

            return statements.toArray(new SqlStatement[statements.size()]);
        } catch (JacksonException | SQLException | DatabaseException e) {
            logger.error("Unable to apply username migration changes", e);
            throw new CustomChangeException(e);
        }
    }

    private List<SqlStatement> generateStatementToRenameDuplicatedOrgUsers(Database database, List<User> duplicates) {
        List<SqlStatement> statementsToApply = new ArrayList<>();
        for (int i = 1; i < duplicates.size(); ++i) {
            final User duplicateToUpdate = duplicates.get(i);
            final String updatedUsername =  duplicateToUpdate.getUsername()+"_"+i+"_TO_RENAME_OR_DELETE";
            logger.info("Renaming organization username '{}' to '{}' into tables '" + TABLE_ORGANIZATION_USERS + "' (user_id: {})",
                    duplicateToUpdate.getUsername(),
                    updatedUsername,
                    duplicateToUpdate.getId());

            SqlStatement updateUsersTable = new UpdateStatement(database.getDefaultCatalogName(), database.getDefaultSchemaName(), TABLE_ORGANIZATION_USERS)
                    .addNewColumnValue(USERNAME, updatedUsername)
                    .setWhereClause(String.format("id='%s'", duplicateToUpdate.getId()));
            statementsToApply.add(updateUsersTable);
        }
        return statementsToApply;
    }

    private List<SqlStatement> generateStatementToRenameDuplicatedUsers(Database database, List<User> duplicates, String idpTable, String usernameColumn, String idColumn) {
        List<SqlStatement> statementsToApply = new ArrayList<>();

        for (int i = 1; i < duplicates.size(); ++i) {
            final User duplicateToUpdate = duplicates.get(i);
            final String updatedUsername =  duplicateToUpdate.getUsername()+"_"+i+"_TO_RENAME_OR_DELETE";
            logger.info("Renaming username '{}' to '{}' into tables 'users' and '{}' (user_id: {}, external_id: {})",
                    duplicateToUpdate.getUsername(),
                    updatedUsername,
                    idpTable,
                    duplicateToUpdate.getId(),
                    duplicateToUpdate.getExternalId());

            SqlStatement updateUsersTable = new UpdateStatement(database.getDefaultCatalogName(), database.getDefaultSchemaName(), TABLE_USERS)
                    .addNewColumnValue(USERNAME, updatedUsername)
                    .setWhereClause(String.format("id='%s'", duplicateToUpdate.getId()));
            statementsToApply.add(updateUsersTable);

            SqlStatement updateIdpTable = new UpdateStatement(database.getDefaultCatalogName(), database.getDefaultSchemaName(), idpTable)
                    .addNewColumnValue(usernameColumn, updatedUsername)
                    .setWhereClause(String.format("%s='%s'", idColumn, duplicateToUpdate.getExternalId()));
            statementsToApply.add(updateIdpTable);
        }
        return statementsToApply;
    }

    private Map<String, List<User>> searchDuplicatesGroupedByUsernameAndSource(JdbcConnection connection, String table) throws SQLException, DatabaseException {
        int userCounter = 0;
        PreparedStatement searchDuplicates = connection.prepareStatement("select u.*\n" +
                "from "+table+" u,\n" +
                "(select username, source\n" +
                "from (select username, source, count(username) as count\n" +
                "from "+table + "\n" +
                "group by source, username) as multiEntries\n" +
                "where multiEntries.count > 1) duplicateUser\n" +
                "where u.username = duplicateUser.username\n" +
                "and u.source = duplicateUser.source\n" +
                "order by u.username asc, u.logins_count desc, u.logged_at desc, u.created_at asc");

        ResultSet duplicateUsers = searchDuplicates.executeQuery();

        Map<String, List<User>> duplicatesUsersGroupByUsernameAndSource = new LinkedHashMap<>();
        while (duplicateUsers.next()) {
            User currentUser = new User();
            currentUser.setId(duplicateUsers.getString("id"));
            currentUser.setUsername(duplicateUsers.getString(USERNAME));
            currentUser.setExternalId(duplicateUsers.getString("external_id"));
            currentUser.setCreatedAt(duplicateUsers.getDate("created_at"));
            currentUser.setLoggedAt(duplicateUsers.getDate("logged_at"));
            currentUser.setLoginsCount(duplicateUsers.getLong("logins_count"));
            currentUser.setSource(duplicateUsers.getString("source"));

            String groupKey = currentUser.getUsername() + "/" + currentUser.getSource();
            if (!duplicatesUsersGroupByUsernameAndSource.containsKey(groupKey)) {
                duplicatesUsersGroupByUsernameAndSource.put(groupKey, new ArrayList<>());
            }
            duplicatesUsersGroupByUsernameAndSource.get(groupKey).add(currentUser);

            userCounter++;
        }

        duplicateUsers.close();
        searchDuplicates.close();

        logger.info("{} duplicate usernames found into {} table", userCounter, table);

        return duplicatesUsersGroupByUsernameAndSource;
    }

    @Override
    public String getConfirmationMessage() {
        return "Usernames processed successfully to avoid duplicates";
    }

    @Override
    public void setUp() {

    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {

    }

    @Override
    public ValidationErrors validate(Database database) {
        return null;
    }
}
