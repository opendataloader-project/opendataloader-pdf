/*
 * Copyright 2025-2026 Hancom Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.opendataloader.pdf.llm;

import org.opendataloader.pdf.graph.GraphNode;

import java.util.List;
import java.util.Optional;

public interface LlmFallback {

    /**
     * Attempt to resolve a low-confidence node using an LLM.
     * Returns an Optional containing a replacement node, or empty if the LLM cannot help.
     * Implementors MUST NOT return null; return Optional.empty() to indicate no replacement.
     */
    Optional<GraphNode> resolve(GraphNode node, List<GraphNode> context);
}
