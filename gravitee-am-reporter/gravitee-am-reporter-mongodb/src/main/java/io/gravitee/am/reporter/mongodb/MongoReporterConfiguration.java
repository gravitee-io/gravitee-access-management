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
package io.gravitee.am.reporter.mongodb;

import io.gravitee.am.reporter.api.ReporterConfiguration;

/**
 * @author Titouan COMPIEGNE (titouan.compiegne at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoReporterConfiguration implements ReporterConfiguration {

    private String uri;
    private String host;
    private int port;
    private boolean enableCredentials;
    private String databaseCredentials;
    private String usernameCredentials;
    private String passwordCredentials;
    private String database;
    private String reportableCollection;
    private Integer bulkActions = 1000;
    private Long flushInterval = 5l;

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public boolean isEnableCredentials() {
        return enableCredentials;
    }

    public void setEnableCredentials(boolean enableCredentials) {
        this.enableCredentials = enableCredentials;
    }

    public String getDatabaseCredentials() {
        return databaseCredentials;
    }

    public void setDatabaseCredentials(String databaseCredentials) {
        this.databaseCredentials = databaseCredentials;
    }

    public String getUsernameCredentials() {
        return usernameCredentials;
    }

    public void setUsernameCredentials(String usernameCredentials) {
        this.usernameCredentials = usernameCredentials;
    }

    public String getPasswordCredentials() {
        return passwordCredentials;
    }

    public void setPasswordCredentials(String passwordCredentials) {
        this.passwordCredentials = passwordCredentials;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getReportableCollection() {
        return reportableCollection;
    }

    public void setReportableCollection(String reportableCollection) {
        this.reportableCollection = reportableCollection;
    }

    public Integer getBulkActions() {
        return bulkActions;
    }

    public void setBulkActions(Integer bulkActions) {
        this.bulkActions = bulkActions;
    }

    public Long getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(Long flushInterval) {
        this.flushInterval = flushInterval;
    }
}
