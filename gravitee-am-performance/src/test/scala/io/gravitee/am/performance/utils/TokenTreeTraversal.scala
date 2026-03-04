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
package io.gravitee.am.performance.utils

object TokenTreeTraversal {

  final case class ExchangeRequest(subjectToken: String, actorToken: Option[String])

  final case class CursorState(subjectCursor: Int, actorCursor: Int)

  def selectActorToken(currentLevel: Vector[String], subjectToken: String, actorCursor: Int): String = {
    val baseActor = currentLevel(actorCursor % currentLevel.size)
    if (currentLevel.size > 1 && baseActor == subjectToken) {
      currentLevel((actorCursor + 1) % currentLevel.size)
    } else {
      baseActor
    }
  }

  def nextBoundedWidthRequest(currentLevel: Vector[String], state: CursorState, useDelegationMode: Boolean): (ExchangeRequest, CursorState) = {
    val subjectToken = currentLevel(state.subjectCursor % currentLevel.size)
    val nextState = CursorState(state.subjectCursor + 1, if (useDelegationMode) state.actorCursor + 1 else state.actorCursor)

    if (useDelegationMode) {
      val actorToken = selectActorToken(currentLevel, subjectToken, state.actorCursor)
      (ExchangeRequest(subjectToken, Some(actorToken)), nextState)
    } else {
      (ExchangeRequest(subjectToken, None), nextState)
    }
  }

  def nextBranchFactorRequest(
      currentLevel: Vector[String],
      subjectToken: String,
      actorCursor: Int,
      useDelegationMode: Boolean
  ): (ExchangeRequest, Int) = {
    if (useDelegationMode) {
      val actorToken = selectActorToken(currentLevel, subjectToken, actorCursor)
      (ExchangeRequest(subjectToken, Some(actorToken)), actorCursor + 1)
    } else {
      (ExchangeRequest(subjectToken, None), actorCursor)
    }
  }
}
