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
package org.opendataloader.pdf.utils;

/**
 * Thrown when a sanitization rule cannot run on the text of a document,
 * for example when its pattern overflows the stack on a long line.
 * The message names the rule and does not contain document text.
 */
public class SanitizationRuleException extends IllegalStateException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates the exception.
     *
     * @param message the message, which names the rule.
     */
    public SanitizationRuleException(String message) {
        super(message);
    }
}
