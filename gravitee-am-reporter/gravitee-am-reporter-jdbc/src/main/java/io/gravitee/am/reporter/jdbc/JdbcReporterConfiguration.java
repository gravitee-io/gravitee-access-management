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
package io.gravitee.am.reporter.jdbc;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.gravitee.am.reporter.api.ReporterConfiguration;
import io.gravitee.secrets.api.annotation.Secret;

import java.util.List;
import java.util.Map;

/**
 * @author Eric LELEU (eric.leleu at graviteesource.com)
 * @author GraviteeSource Team
 */
@JsonInclude(JsonInclude.Include.NON_ABSENT)
public class JdbcReporterConfiguration implements ReporterConfiguration {
    private String driver;
    private String host;
    private Integer port;
    private String database;
    private String username;
    @Secret
    private String password;
    private Integer acquireRetry;
    private Integer initialSize = 0;
    private Integer maxSize = 10;
    private Integer maxIdleTime = 30000;
    private Integer maxLifeTime = 30000;
    private Integer maxAcquireTime = 0 ;
    private Integer maxCreateConnectionTime = 0;
    private String validationQuery = "SELECT 1";
    private String tableSuffix;
    private Integer bulkActions = 1000;
    private Integer flushInterval = 5;
    private List<Map<String, String>> options;

    public String getDriver() {
        return driver;
    }

    public void setDriver(String driver) {
        this.driver = driver;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getDatabase() {
        return database;
    }

    public void setDatabase(String database) {
        this.database = database;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Integer getAcquireRetry() {
        return acquireRetry;
    }

    public void setAcquireRetry(Integer acquireRetry) {
        this.acquireRetry = acquireRetry;
    }

    public Integer getInitialSize() {
        return initialSize;
    }

    public void setInitialSize(Integer initialSize) {
        this.initialSize = initialSize;
    }

    public Integer getMaxSize() {
        return maxSize;
    }

    public void setMaxSize(Integer maxSize) {
        this.maxSize = maxSize;
    }

    public Integer getMaxIdleTime() {
        return maxIdleTime;
    }

    public void setMaxIdleTime(Integer maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
    }

    public Integer getMaxLifeTime() {
        return maxLifeTime;
    }

    public void setMaxLifeTime(Integer maxLifeTime) {
        this.maxLifeTime = maxLifeTime;
    }

    public Integer getMaxAcquireTime() {
        return maxAcquireTime;
    }

    public void setMaxAcquireTime(Integer maxAcquireTime) {
        this.maxAcquireTime = maxAcquireTime;
    }

    public Integer getMaxCreateConnectionTime() {
        return maxCreateConnectionTime;
    }

    public void setMaxCreateConnectionTime(Integer maxCreateConnectionTime) {
        this.maxCreateConnectionTime = maxCreateConnectionTime;
    }

    public String getValidationQuery() {
        return validationQuery;
    }

    public void setValidationQuery(String validationQuery) {
        this.validationQuery = validationQuery;
    }

    public String getTableSuffix() {
        return tableSuffix;
    }

    public void setTableSuffix(String tableSuffix) {
        this.tableSuffix = tableSuffix;
    }

    public Integer getBulkActions() {
        return bulkActions;
    }

    public void setBulkActions(Integer bulkActions) {
        this.bulkActions = bulkActions;
    }

    public Integer getFlushInterval() {
        return flushInterval;
    }

    public void setFlushInterval(Integer flushInterval) {
        this.flushInterval = flushInterval;
    }

    public List<Map<String, String>> getOptions() {
        return options;
    }

    public void setOptions(List<Map<String, String>> options) {
        this.options = options;
    }
}
