/*
 * Copyright 2017 Nafundi
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.odk.collect.android.widgets;

import android.content.Context;

import org.javarosa.core.model.data.DecimalData;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.form.api.FormEntryPrompt;

public class RangeDecimalWidget extends RangeWidget {

    public RangeDecimalWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);
    }

    @Override
    public IAnswerData getAnswer() {
        return actualValue == null ? null : new DecimalData(actualValue.doubleValue());
    }

    @Override
    protected void setUpActualValueLabel() {
        currentValue.setText(String.valueOf(actualValue.doubleValue()));
    }
}
