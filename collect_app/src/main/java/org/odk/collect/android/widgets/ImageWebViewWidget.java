/*
 * Copyright (C) 2012 University of Washington
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

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.MediaStore.Images;
import android.util.TypedValue;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import org.javarosa.core.model.data.IAnswerData;
import org.javarosa.core.model.data.StringData;
import org.javarosa.form.api.FormEntryPrompt;
import org.odk.collect.android.R;
import org.odk.collect.android.activities.FormEntryActivity;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.utilities.MediaUtils;

import java.io.File;
import java.util.Date;

import timber.log.Timber;

/**
 * Widget that allows user to take pictures, sounds or video and add them to the
 * form.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 * @author Yaw Anokwa (yanokwa@gmail.com)
 */
public class ImageWebViewWidget extends QuestionWidget implements IBinaryWidget {

    private Button captureButton;
    private Button chooseButton;
    private WebView imageDisplay;

    private String binaryName;

    private String instanceFolder;

    private TextView errorTextView;

    private String constructImageElement() {
        File f = new File(instanceFolder + File.separator + binaryName);

        DisplayMetrics metrics = getContext().getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;

        return f.exists() ? ("<img align=\"middle\" src=\"file:///"
                + f.getAbsolutePath()
                +
                // Appending the time stamp to the filename is a hack to prevent
                // caching.
                "?"
                + new Date().getTime()
                + "\" width=\""
                + Integer.toString(screenWidth - 10) + "\" >")
                : "";
    }

    public boolean suppressFlingGesture(MotionEvent e1, MotionEvent e2,
                                        float velocityX, float velocityY) {
        if (imageDisplay == null
                || imageDisplay.getVisibility() != View.VISIBLE) {
            return false;
        }

        Rect rect = new Rect();
        imageDisplay.getHitRect(rect);

        // Log.i(t, "hitRect: " + rect.left + "," + rect.top + " : " +
        // rect.right + "," + rect.bottom );
        // Log.i(t, "e1 Raw, Clean: " + e1.getRawX() + "," + e1.getRawY() +
        // " : " + e1.getX() + "," + e1.getY());
        // Log.i(t, "e2 Raw, Clean: " + e2.getRawX() + "," + e2.getRawY() +
        // " : " + e2.getX() + "," + e2.getY());

        // starts in WebView
        if (rect.contains((int) e1.getRawX(), (int) e1.getRawY())) {
            return true;
        }

        // ends in WebView
        if (rect.contains((int) e2.getRawX(), (int) e2.getRawY())) {
            return true;
        }

        // transits WebView
        if (rect.contains((int) ((e1.getRawX() + e2.getRawX()) / 2.0),
                (int) ((e1.getRawY() + e2.getRawY()) / 2.0))) {
            return true;
        }
        // Log.i(t, "NOT SUPPRESSED");
        return false;
    }

    public ImageWebViewWidget(Context context, FormEntryPrompt prompt) {
        super(context, prompt);

        instanceFolder = Collect.getInstance().getFormController()
                .getInstancePath().getParent();

        TableLayout.LayoutParams params = new TableLayout.LayoutParams();
        params.setMargins(7, 5, 7, 5);

        errorTextView = new TextView(context);
        errorTextView.setId(QuestionWidget.newUniqueId());
        errorTextView.setText(R.string.selected_invalid_image);

        // setup capture button
        captureButton = new Button(getContext());
        captureButton.setId(QuestionWidget.newUniqueId());
        captureButton.setText(getContext().getString(R.string.capture_image));
        captureButton
                .setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontsize);
        captureButton.setPadding(20, 20, 20, 20);
        captureButton.setEnabled(!prompt.isReadOnly());
        captureButton.setLayoutParams(params);

        // launch capture intent on click
        captureButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Collect.getInstance()
                        .getActivityLogger()
                        .logInstanceAction(this, "captureButton", "click",
                                formEntryPrompt.getIndex());
                errorTextView.setVisibility(View.GONE);
                Intent i = new Intent(
                        android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
                // We give the camera an absolute filename/path where to put the
                // picture because of bug:
                // http://code.google.com/p/android/issues/detail?id=1480
                // The bug appears to be fixed in Android 2.0+, but as of feb 2,
                // 2010, G1 phones only run 1.6. Without specifying the path the
                // images returned by the camera in 1.6 (and earlier) are ~1/4
                // the size. boo.

                // if this gets modified, the onActivityResult in
                // FormEntyActivity will also need to be updated.
                i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT,
                        Uri.fromFile(new File(Collect.TMPFILE_PATH)));
                try {
                    Collect.getInstance().getFormController()
                            .setIndexWaitingForData(formEntryPrompt.getIndex());
                    ((Activity) getContext()).startActivityForResult(i,
                            FormEntryActivity.IMAGE_CAPTURE);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(
                            getContext(),
                            getContext().getString(R.string.activity_not_found,
                                    "image capture"), Toast.LENGTH_SHORT)
                            .show();
                    Collect.getInstance().getFormController()
                            .setIndexWaitingForData(null);
                }

            }
        });

        // setup chooser button
        chooseButton = new Button(getContext());
        chooseButton.setId(QuestionWidget.newUniqueId());
        chooseButton.setText(getContext().getString(R.string.choose_image));
        chooseButton.setTextSize(TypedValue.COMPLEX_UNIT_DIP, answerFontsize);
        chooseButton.setPadding(20, 20, 20, 20);
        chooseButton.setEnabled(!prompt.isReadOnly());
        chooseButton.setLayoutParams(params);

        // launch capture intent on click
        chooseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Collect.getInstance()
                        .getActivityLogger()
                        .logInstanceAction(this, "chooseButton", "click",
                                formEntryPrompt.getIndex());
                errorTextView.setVisibility(View.GONE);
                Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                i.setType("image/*");

                try {
                    Collect.getInstance().getFormController()
                            .setIndexWaitingForData(formEntryPrompt.getIndex());
                    ((Activity) getContext()).startActivityForResult(i,
                            FormEntryActivity.IMAGE_CHOOSER);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(
                            getContext(),
                            getContext().getString(R.string.activity_not_found,
                                    "choose image"), Toast.LENGTH_SHORT).show();
                    Collect.getInstance().getFormController()
                            .setIndexWaitingForData(null);
                }

            }
        });

        // finish complex layout
        LinearLayout answerLayout = new LinearLayout(getContext());
        answerLayout.setOrientation(LinearLayout.VERTICAL);
        answerLayout.addView(captureButton);
        answerLayout.addView(chooseButton);
        answerLayout.addView(errorTextView);

        // and hide the capture and choose button if read-only
        if (prompt.isReadOnly()) {
            captureButton.setVisibility(View.GONE);
            chooseButton.setVisibility(View.GONE);
        }
        errorTextView.setVisibility(View.GONE);

        // retrieve answer from data model and update ui
        binaryName = prompt.getAnswerText();

        // Only add the imageView if the user has taken a picture
        if (binaryName != null) {
            imageDisplay = new WebView(getContext());
            imageDisplay.setId(QuestionWidget.newUniqueId());
            imageDisplay.getSettings().setCacheMode(WebSettings.LOAD_NO_CACHE);
            imageDisplay.getSettings().setBuiltInZoomControls(true);
            imageDisplay.getSettings().setDefaultZoom(
                    WebSettings.ZoomDensity.FAR);
            imageDisplay.setVisibility(View.VISIBLE);
            imageDisplay.setLayoutParams(params);

            // HTML is used to display the image.
            String html = "<body>" + constructImageElement() + "</body>";

            imageDisplay.loadDataWithBaseURL("file:///" + instanceFolder
                    + File.separator, html, "text/html", "utf-8", "");
            answerLayout.addView(imageDisplay);
        }
        addAnswerView(answerLayout);
    }

    private void deleteMedia() {
        // get the file path and delete the file
        String name = binaryName;
        // clean up variables
        binaryName = null;
        // delete from media provider
        int del = MediaUtils.deleteImageFileFromMediaProvider(
                instanceFolder + File.separator + name);
        Timber.i("Deleted %d rows from media content provider", del);
    }

    @Override
    public void clearAnswer() {
        // remove the file
        deleteMedia();

        if (imageDisplay != null) {
            // update HTML to not hold image file reference.
            String html = "<body></body>";
            imageDisplay.loadDataWithBaseURL("file:///" + instanceFolder
                    + File.separator, html, "text/html", "utf-8", "");

            imageDisplay.setVisibility(View.INVISIBLE);
        }

        errorTextView.setVisibility(View.GONE);

        // reset buttons
        captureButton.setText(getContext().getString(R.string.capture_image));
    }

    @Override
    public IAnswerData getAnswer() {
        if (binaryName != null) {
            return new StringData(binaryName);
        } else {
            return null;
        }
    }


    @Override
    public void setBinaryData(Object newImageObj) {
        // you are replacing an answer. delete the previous image using the
        // content provider.
        if (binaryName != null) {
            deleteMedia();
        }

        File newImage = (File) newImageObj;
        if (newImage.exists()) {
            // Add the new image to the Media content provider so that the
            // viewing is fast in Android 2.0+
            ContentValues values = new ContentValues(6);
            values.put(Images.Media.TITLE, newImage.getName());
            values.put(Images.Media.DISPLAY_NAME, newImage.getName());
            values.put(Images.Media.DATE_TAKEN, System.currentTimeMillis());
            values.put(Images.Media.MIME_TYPE, "image/jpeg");
            values.put(Images.Media.DATA, newImage.getAbsolutePath());

            Uri imageURI = getContext().getContentResolver().insert(
                    Images.Media.EXTERNAL_CONTENT_URI, values);
            Timber.i("Inserting image returned uri = %s", imageURI.toString());

            binaryName = newImage.getName();
            Timber.i("Setting current answer to %s", newImage.getName());
        } else {
            Timber.e("NO IMAGE EXISTS at: %s", newImage.getAbsolutePath());
        }

        Collect.getInstance().getFormController().setIndexWaitingForData(null);
    }

    @Override
    public void setFocus(Context context) {
        // Hide the soft keyboard if it's showing.
        InputMethodManager inputManager = (InputMethodManager) context
                .getSystemService(Context.INPUT_METHOD_SERVICE);
        inputManager.hideSoftInputFromWindow(this.getWindowToken(), 0);
    }

    @Override
    public boolean isWaitingForBinaryData() {
        return formEntryPrompt.getIndex().equals(
                Collect.getInstance().getFormController()
                        .getIndexWaitingForData());
    }

    @Override
    public void cancelWaitingForBinaryData() {
        Collect.getInstance().getFormController().setIndexWaitingForData(null);
    }

    @Override
    public void setOnLongClickListener(OnLongClickListener l) {
        captureButton.setOnLongClickListener(l);
        chooseButton.setOnLongClickListener(l);
    }

    @Override
    public void cancelLongPress() {
        super.cancelLongPress();
        captureButton.cancelLongPress();
        chooseButton.cancelLongPress();
    }

}
