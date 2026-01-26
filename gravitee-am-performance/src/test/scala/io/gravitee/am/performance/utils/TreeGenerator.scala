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

object TreeGenerator {

  /**
   * Creates a tree of nodes with the specified characteristics.
   *
   * @param totalNodes   N - maximum number of nodes to generate
   * @param maxDepth     M - maximum tree depth (root is depth = 0)
   * @param maxChildren  P - max number of children per node
   * @return Seq of edges (childId, parentId)
   */
  def generateTreeEdges(totalNodes: Int, maxDepth: Int, maxChildren: Int): Seq[(Int, Int)] = {

    require(totalNodes >= 1, "Must request at least 1 node")
    require(maxDepth >= 1, "Max depth must be >= 1")
    require(maxChildren >= 1, "Max children must be >= 1")

    // Result set of edges
    val edges = scala.collection.mutable.ArrayBuffer.empty[(Int, Int)]

    // Queue for BFS: (nodeId, depth)
    val queue = scala.collection.mutable.Queue[(Int, Int)]()

    // We'll assign node IDs sequentially for reproducibility
    var nextId = 1

    // Root node
    val rootId = 0
    queue.enqueue((rootId, 0))

    while (queue.nonEmpty && nextId < totalNodes) {
      val (parent, depth) = queue.dequeue()

      // Stop expanding if we're at max depth
      if (depth < maxDepth) {
        // Determine how many children we can allocate at this point
        val children = math.min(maxChildren, totalNodes - nextId)

        // Create children
        (1 to children).foreach { _ =>
          val child = nextId
          nextId += 1

          edges += ((child, parent))
          queue.enqueue((child, depth + 1))
        }
      }
    }

    edges.toSeq
  }

  def generateTreeEdges(totalNodes: Int, depth: Int): Seq[(Int, Int)] = {
    val maxChildren = calculateBranchingFactor(totalNodes, depth)
    generateTreeEdges(totalNodes, depth, maxChildren)
  }

  private def calculateBranchingFactor(totalNodes: Int, depth: Int): Int = {
    require(depth >= 1, "Depth must be >= 1")

    // If depth = 1, then N = 1 (root) + P children => P = N - 1
    if (depth == 1) return (totalNodes - 1).max(1)

    // Try P from 1 up to totalNodes (practically it will stop much earlier)
    (1 to totalNodes).find { p =>
      val fullCount = (math.pow(p, depth + 1) - 1) / (p - 1)
      fullCount >= totalNodes
    }.getOrElse(1)
  }
}
