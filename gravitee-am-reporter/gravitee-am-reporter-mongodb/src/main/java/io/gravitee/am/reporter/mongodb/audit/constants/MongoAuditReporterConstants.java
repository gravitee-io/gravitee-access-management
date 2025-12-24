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
package io.gravitee.am.reporter.mongodb.audit.constants;

import java.util.List;

/**
 * @author Rémi SULTAN (remi.sultan at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MongoAuditReporterConstants {

  private MongoAuditReporterConstants(){}

  public static final String FIELD_ID = "_id";
  public static final String FIELD_REFERENCE_TYPE = "referenceType";
  public static final String FIELD_REFERENCE_ID = "referenceId";
  public static final String FIELD_TIMESTAMP = "timestamp";
  public static final String FIELD_TYPE = "type";
  public static final String FIELD_STATUS = "outcome.status";
  public static final String FIELD_TARGET = "target.alternativeId";
  public static final String FIELD_TARGET_ID = "target.id";
  public static final String FIELD_ACTOR = "actor.alternativeId";
  public static final String FIELD_ACTOR_ID = "actor.id";
  public static final String FIELD_ACCESS_POINT_ID = "accessPoint.id";
  public static final String INDEX_REFERENCE_TIMESTAMP_NAME = "r1t_1";
  public static final String INDEX_TIMESTAMP_ID_NAME = "t1id1_1";
  public static final String INDEX_REFERENCE_TYPE_TIMESTAMP_NAME = "r1ty1t_1";
  public static final String INDEX_REFERENCE_TYPE_STATUS_SUCCESS_TIMESTAMP_NAME = "r1ty1s1t_1";
  public static final String INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME = "r1a1t_1";
  public static final String INDEX_REFERENCE_TARGET_TIMESTAMP_NAME = "r1ta1t_1";
  public static final String INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME = "r1a1ta1t_1";
  public static final String INDEX_REFERENCE_ACTOR_ID_TARGET_ID_TIMESTAMP_NAME = "r1a1ti1t_1";
  public static final String OLD_INDEX_REFERENCE_TIMESTAMP_NAME = "ref_1_time_-1";
  private static final String OLD_INDEX_REFERENCE_TYPE_TIMESTAMP_NAME = "ref_1_type_1_time_-1";
  private static final String OLD_INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME = "ref_1_actor_1_time_-1";
  private static final String OLD_INDEX_REFERENCE_TARGET_TIMESTAMP_NAME = "ref_1_target_1_time_-1";
  private static final String OLD_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME = "ref_1_actor_1_target_1_time_-1";
  private static final String OLD_INDEX_REFERENCE_ACTOR_ID_TARGET_ID_TIMESTAMP_NAME = "ref_1_actorId_1_targetId_1_time_-1";
  private static final String OLDER_INDEX_REFERENCE_TIMESTAMP_NAME = "referenceType_1_referenceId_1_timestamp_-1";
  private static final String OLDER_INDEX_REFERENCE_TYPE_TIMESTAMP_NAME = "referenceType_1_referenceId_1_type_1_timestamp_-1";
  private static final String OLDER_INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME = "referenceType_1_referenceId_1_actor.alternativeId_1_timestamp_-1";
  private static final String OLDER_INDEX_REFERENCE_TARGET_TIMESTAMP_NAME = "referenceType_1_referenceId_1_target.alternativeId_1_timestamp_-1";
  private static final String OLDER_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME = "referenceType_1_referenceId_1_actor.alternativeId_1_target.alternativeId_1_timestamp_-1";

    public static final Long MIN_READ_PREFERENCE_STALENESS = 90000l;

    public static final List<String> OLD_INDICES = List.of(
      OLD_INDEX_REFERENCE_TIMESTAMP_NAME,
      OLD_INDEX_REFERENCE_TYPE_TIMESTAMP_NAME,
      OLD_INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME,
      OLD_INDEX_REFERENCE_TARGET_TIMESTAMP_NAME,
      OLD_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME,
      OLD_INDEX_REFERENCE_ACTOR_ID_TARGET_ID_TIMESTAMP_NAME,
      OLDER_INDEX_REFERENCE_TIMESTAMP_NAME,
      OLDER_INDEX_REFERENCE_TYPE_TIMESTAMP_NAME,
      OLDER_INDEX_REFERENCE_ACTOR_TIMESTAMP_NAME,
      OLDER_INDEX_REFERENCE_TARGET_TIMESTAMP_NAME,
      OLDER_INDEX_REFERENCE_ACTOR_TARGET_TIMESTAMP_NAME,
      OLD_INDEX_REFERENCE_TIMESTAMP_NAME
  );
}
