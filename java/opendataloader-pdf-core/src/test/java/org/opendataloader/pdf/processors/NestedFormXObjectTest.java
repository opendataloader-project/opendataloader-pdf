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
package org.opendataloader.pdf.processors;

import org.junit.jupiter.api.Test;
import org.verapdf.as.ASAtom;
import org.verapdf.cos.COSDictionary;
import org.verapdf.cos.COSName;
import org.verapdf.cos.COSObject;
import org.verapdf.cos.COSStream;
import org.verapdf.pd.PDResources;
import org.verapdf.pd.images.PDXObject;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Finding a form that a page form draws.
 *
 * <p>Content inside a form inside a form names the inner form, which the
 * page's resources do not list. Not found, its piece was given an integer
 * MCID, which names the page's own marked content: a scanned signature's
 * Figure took MCID 0, the page's first paragraph.
 */
class NestedFormXObjectTest {

    private static COSObject form(COSObject resources) {
        COSObject stream = COSStream.construct();
        stream.setKey(ASAtom.TYPE, COSName.construct(ASAtom.XOBJECT));
        stream.setKey(ASAtom.SUBTYPE, COSName.construct(ASAtom.FORM));
        stream.setKey(ASAtom.RESOURCES, resources);
        return stream;
    }

    private static COSObject resourcesWith(String name, COSObject xObject) {
        COSObject xObjects = COSDictionary.construct();
        xObjects.setKey(ASAtom.getASAtom(name), xObject);
        COSObject resources = COSDictionary.construct();
        resources.setKey(ASAtom.XOBJECT, xObjects);
        return resources;
    }

    @Test
    void aFormInsideAPageFormIsFound() {
        COSObject inner = form(COSDictionary.construct());
        COSObject outer = form(resourcesWith("F0", inner));
        PDResources page = new PDResources(resourcesWith("FFT0", outer));

        PDXObject found = AutoTaggingProcessor.findXObject(page, ASAtom.getASAtom("F0"), 0);

        assertNotNull(found);
        assertSame(inner.getDirectBase(), found.getObject().getDirectBase());
    }

    @Test
    void aNameNoFormHoldsIsNotFound() {
        PDResources page = new PDResources(resourcesWith("FFT0", form(COSDictionary.construct())));

        assertNull(AutoTaggingProcessor.findXObject(page, ASAtom.getASAtom("F9"), 0));
    }
}
