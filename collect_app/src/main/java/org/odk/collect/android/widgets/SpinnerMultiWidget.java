/*
 * Copyright (C) 2011 University of Washington
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */

package org.odk.collect.android.widgets;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.javarosa.core.model.SelectChoice;
import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.SelectMultiData;
import org.javarosa.core.model.data.helper.Selection;
import org.javarosa.form.api.FormEntryPrompt;
import org.javarosa.xpath.expr.XPathFuncExpr;
import org.odk.collect.android.R;
import org.odk.collect.android.external.ExternalDataUtil;

import java.util.ArrayList;
import java.util.List;

/**
 * SpinnerMultiWidget, like SelectMultiWidget handles multiple selection fields using checkboxes,
 * but the user clicks a button to see the checkboxes. The goal is to be more compact. If images,
 * audio, or video are specified in the select answers they are ignored. WARNING: There is a bug in
 * android versions previous to 2.0 that affects this widget. You can find the report here:
 * http://code.google.com/p/android/issues/detail?id=922 This bug causes text to be white in alert
 * boxes, which makes the select options invisible in this widget. For this reason, this widget
 * should not be used on phones with android versions lower than 2.0.
 *
 * @author Jeff Beorse (jeff@beorse.net)
 */
public class SpinnerMultiWidget extends QuestionWidget {

    List<SelectChoice> items;

    // The possible select answers
    CharSequence[] answerItems;

    // The button to push to display the answers to choose from
    Button button;

    // Defines which answers are selected
    boolean[] selections;

    // The alert box that contains the answer selection view
    AlertDialog.Builder alertBuilder;

    // Displays the current selections below the button
    TextView selectionText;


    @SuppressWarnings("unchecked")
    public SpinnerMultiWidget(final Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        // SurveyCTO-added support for dynamic select content (from .csv files)
        XPathFuncExpr xpathFuncExpr = ExternalDataUtil.getSearchXPathExpression(
                prompt.getAppearanceHint());
        if (xpathFuncExpr != null) {
            items = ExternalDataUtil.populateExternalChoices(prompt, xpathFuncExpr);
        } else {
            items = prompt.getSelectChoices();
        }

        formEntryPrompt = prompt;

        selections = new boolean[items.size()];
        answerItems = new CharSequence[items.size()];
        alertBuilder = new AlertDialog.Builder(context);
        button = new Button(context);
        selectionText = new TextView(getContext());

        // Build View
        for (int i = 0; i < items.size(); i++) {
            answerItems[i] = prompt.getSelectChoiceText(items.get(i));
        }

        selectionText.setText(context.getString(R.string.selected));
        selectionText.setTextColor(Color.BLACK);
        selectionText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, questionFontsize);
        selectionText.setVisibility(View.GONE);

        button.setText(context.getString(R.string.select_answer));
        button.setTextSize(TypedValue.COMPLEX_UNIT_DIP, questionFontsize);
        button.setPadding(0, 0, 0, 7);

        // Give the button a click listener. This defines the alert as well. All the
        // click and selection behavior is defined here.
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {

                alertBuilder.setTitle(formEntryPrompt.getQuestionText()).setPositiveButton(R.string.ok,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                List<String> selectedValues = new ArrayList<>();

                                for (int i = 0; i < selections.length; i++) {
                                    if (selections[i]) {
                                        selectedValues.add(answerItems[i].toString());
                                    }
                                }

                                selectionText.setText(String.format(context.getString(R.string.selected_answer),
                                        TextUtils.join(", ", selectedValues)));
                                selectionText.setVisibility(View.VISIBLE);
                            }
                        });

                alertBuilder.setMultiChoiceItems(answerItems, selections,
                        new DialogInterface.OnMultiChoiceClickListener() {

                            @Override
                            public void onClick(DialogInterface dialog, int which,
                                                boolean isChecked) {
                                selections[which] = isChecked;
                            }
                        });
                AlertDialog alert = alertBuilder.create();
                alert.show();
            }
        });

        // Fill in previous answers
        List<Selection> ve = new ArrayList<Selection>();
        if (prompt.getAnswerValue() != null) {
            ve = (List<Selection>) prompt.getAnswerValue().getValue();
        }

        if (ve != null) {
            List<String> selectedValues = new ArrayList<>();

            for (int i = 0; i < selections.length; i++) {
                String value = items.get(i).getValue();
                for (Selection s : ve) {
                    if (value.equals(s.getValue())) {
                        selections[i] = true;
                        selectedValues.add(answerItems[i].toString());
                        break;
                    }
                }
            }

            selectionText.setText(String.format(context.getString(R.string.selected_answer),
                    TextUtils.join(", ", selectedValues)));
            selectionText.setVisibility(View.VISIBLE);
        }

        LinearLayout answerLayout = new LinearLayout(getContext());
        answerLayout.setOrientation(LinearLayout.VERTICAL);
        answerLayout.addView(button);
        answerLayout.addView(selectionText);
        addAnswerView(answerLayout);
    }


    @Override
    public IAnswerData getAnswer() {
        clearFocus();
        List<Selection> vc = new ArrayList<Selection>();
        for (int i = 0; i < items.size(); i++) {
            if (selections[i]) {
                SelectChoice sc = items.get(i);
                vc.add(new Selection(sc));
            }
        }
        if (vc.size() == 0) {
            return null;
        } else {
            return new SelectMultiData(vc);
        }

    }


    @Override
    public void clearAnswer() {
        selectionText.setText(R.string.selected);
        selectionText.setVisibility(View.GONE);
        for (int i = 0; i < selections.length; i++) {
            selections[i] = false;
        }
    }


    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);

    }


    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        button.setOnLongClickListener(l);
    }


    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        button.cancelLongPress();
    }

}