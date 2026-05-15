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
package org.opendataloader.pdf.exceptions;

import java.io.IOException;

/**
 * Thrown when tagged-pdf generation is requested for an encrypted document.
 *
 * <p>Encrypted PDFs (those with an {@code /Encrypt} dictionary in the trailer)
 * are not supported for tagged-pdf output. Writing a tagged copy requires
 * re-serializing the document, which interacts poorly with the document's
 * encryption state and is also not permitted by the PDF specification for
 * documents whose permissions deny modification.
 */
public class EncryptedTaggedPdfNotSupportedException extends IOException {

    private static final long serialVersionUID = 1L;

    public EncryptedTaggedPdfNotSupportedException(String message) {
        super(message);
    }
}
