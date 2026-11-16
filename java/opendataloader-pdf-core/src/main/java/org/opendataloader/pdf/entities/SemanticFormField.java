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
package org.opendataloader.pdf.entities;

import org.verapdf.wcag.algorithms.entities.SemanticParagraph;

import java.util.Objects;

/**
 * A check box or a radio button of a tagged PDF: a {@code Form} structure element with
 * {@code PrintField} attributes (ISO 32000-1, 14.8.5.6). The text of the paragraph is the label of
 * the field. The JSON and the Markdown output show the field; the other outputs show its label as a
 * paragraph.
 */
public class SemanticFormField extends SemanticParagraph {

    public static final String CHECK_BOX = "check box";
    public static final String RADIO_BUTTON = "radio button";
    public static final String ON = "on";
    public static final String OFF = "off";
    public static final String NEUTRAL = "neutral";

    private final String role;
    private final String checked;
    private final boolean labelOnPage;
    private String description;

    /**
     * @param label       the label of the field
     * @param role        {@link #CHECK_BOX} or {@link #RADIO_BUTTON}
     * @param checked     {@link #ON}, {@link #OFF}, or {@link #NEUTRAL}
     * @param description the {@code Desc} attribute, or null
     * @param labelOnPage false if the label is the {@code Desc} attribute, not text of the page
     */
    public SemanticFormField(SemanticParagraph label, String role, String checked, String description,
                             boolean labelOnPage) {
        super(label);
        this.role = role;
        this.checked = checked;
        this.description = description;
        this.labelOnPage = labelOnPage;
    }

    public String getRole() {
        return role;
    }

    public String getChecked() {
        return checked;
    }

    public boolean isChecked() {
        return ON.equals(checked);
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    /** False if the label is the {@code Desc} attribute: the page shows no text for the field. */
    public boolean isLabelOnPage() {
        return labelOnPage;
    }

    @Override
    public boolean equals(Object object) {
        if (!super.equals(object) || !(object instanceof SemanticFormField)) {
            return false;
        }
        SemanticFormField that = (SemanticFormField) object;
        return labelOnPage == that.labelOnPage && Objects.equals(role, that.role)
            && Objects.equals(checked, that.checked) && Objects.equals(description, that.description);
    }

    /** The description is left out: the sensitive-data filter can change it after the field is made. */
    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), role, checked, labelOnPage);
    }
}
