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
package io.gravitee.am.reporter.kafka.dto;

import java.time.Instant;

/**
 * Copied from File reporter and renamed to match Kafka Semantics
 * @author Florent Amaridon
 * @author Visiativ
 */
public class AuditMessageValueDto {

  /**
   * Identifier for a specific audited event
   */
  private String id;

  /**
   * The log transaction id for event correlation
   */
  private String transactionId;

  /**
   * Indicator for type of action performed during the event that generated the audit
   */
  private String type;

  /**
   * The type of the resource who triggered the action
   */
  private String referenceType;

  /**
   * The id of the resource who triggered the action
   */
  private String referenceId;

  /**
   * The access point that performs the event (OAuth client or HTTP client (e.g web browser) with ip address, user agent, geographical information)
   */
  private AuditAccessPointDto accessPoint;

  /**
   * Describes the user, app, client, or other entity who performed an action on a target
   */
  private AuditEntityDto actor;

  /**
   * The entity upon which an actor performs an action
   */
  private AuditEntityDto target;

  /**
   * Indicates whether the event succeeded or failed
   */
  private String status;

  /**
   * The date when the event was logged
   */
  private Instant timestamp;

  /**
   * The environment identifier
   */
  private String environmentId;

  /**
   * The organization identifier
   */
  private String organizationId;

  private String domainId;

  private String nodeId;

  private String nodeHostname;

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getReferenceType() {
    return referenceType;
  }

  public void setReferenceType(String referenceType) {
    this.referenceType = referenceType;
  }

  public String getReferenceId() {
    return referenceId;
  }

  public void setReferenceId(String referenceId) {
    this.referenceId = referenceId;
  }

  public String getType() {
    return type;
  }

  public void setType(String type) {
    this.type = type;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }

  public String getTransactionId() {
    return transactionId;
  }

  public void setTransactionId(String transactionId) {
    this.transactionId = transactionId;
  }

  public AuditAccessPointDto getAccessPoint() {
    return accessPoint;
  }

  public void setAccessPoint(AuditAccessPointDto accessPoint) {
    this.accessPoint = accessPoint;
  }

  public AuditEntityDto getActor() {
    return actor;
  }

  public void setActor(AuditEntityDto actor) {
    this.actor = actor;
  }

  public AuditEntityDto getTarget() {
    return target;
  }

  public void setTarget(AuditEntityDto target) {
    this.target = target;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getEnvironmentId() {
    return environmentId;
  }

  public void setEnvironmentId(String environmentId) {
    this.environmentId = environmentId;
  }

  public String getDomainId() {
    return domainId;
  }

  public void setDomainId(String domainId) {
    this.domainId = domainId;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
  }

  public String getNodeId() {
    return nodeId;
  }

  public void setNodeId(String nodeId) {
    this.nodeId = nodeId;
  }

  public String getNodeHostname() {
    return nodeHostname;
  }

  public void setNodeHostname(String nodeHostname) {
    this.nodeHostname = nodeHostname;
  }
}
