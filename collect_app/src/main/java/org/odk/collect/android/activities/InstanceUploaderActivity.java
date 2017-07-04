/*
 * Copyright (C) 2009 University of Washington
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

package org.odk.collect.android.activities;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.InstancesDao;
import org.odk.collect.android.listeners.InstanceUploaderListener;
import org.odk.collect.android.provider.InstanceProviderAPI.InstanceColumns;
import org.odk.collect.android.tasks.InstanceUploaderTask;
import org.odk.collect.android.utilities.ApplicationConstants;
import org.odk.collect.android.utilities.AuthDialogUtility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

import timber.log.Timber;

/**
 * Activity to upload completed forms.
 *
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class InstanceUploaderActivity extends AppCompatActivity implements InstanceUploaderListener,
        AuthDialogUtility.AuthDialogUtilityResultListener {
    private static final int PROGRESS_DIALOG = 1;
    private static final int AUTH_DIALOG = 2;

    private static final String AUTH_URI = "auth";
    private static final String ALERT_MSG = "alertmsg";
    private static final String ALERT_SHOWING = "alertshowing";
    private static final String TO_SEND = "tosend";

    private ProgressDialog progressDialog;
    private AlertDialog alertDialog;

    private String alertMsg;
    private boolean alertShowing;

    private InstanceUploaderTask instanceUploaderTask;

    // maintain a list of what we've yet to send, in case we're interrupted by auth requests
    private Long[] instancesToSend;

    // maintain a list of what we've sent, in case we're interrupted by auth requests
    private HashMap<String, String> uploadedInstances;
    private String url;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Timber.i("onCreate: %s", ((savedInstanceState == null) ? "creating" : "re-initializing"));

        alertMsg = getString(R.string.please_wait);
        alertShowing = false;

        uploadedInstances = new HashMap<String, String>();

        setTitle(getString(R.string.send_data));

        // get any simple saved state...
        if (savedInstanceState != null) {
            if (savedInstanceState.containsKey(ALERT_MSG)) {
                alertMsg = savedInstanceState.getString(ALERT_MSG);
            }
            if (savedInstanceState.containsKey(ALERT_SHOWING)) {
                alertShowing = savedInstanceState.getBoolean(ALERT_SHOWING, false);
            }

            url = savedInstanceState.getString(AUTH_URI);
        }

        // and if we are resuming, use the TO_SEND list of not-yet-sent submissions
        // Otherwise, construct the list from the incoming intent value
        long[] selectedInstanceIDs = null;
        if (savedInstanceState != null && savedInstanceState.containsKey(TO_SEND)) {
            selectedInstanceIDs = savedInstanceState.getLongArray(TO_SEND);
        } else {
            // get instances to upload...
            Intent intent = getIntent();
            selectedInstanceIDs = intent.getLongArrayExtra(FormEntryActivity.KEY_INSTANCES);
        }

        instancesToSend = new Long[(selectedInstanceIDs == null) ? 0 : selectedInstanceIDs.length];
        if (selectedInstanceIDs != null) {
            for (int i = 0; i < selectedInstanceIDs.length; ++i) {
                instancesToSend[i] = selectedInstanceIDs[i];
            }
        }

        // at this point, we don't expect this to be empty...
        if (instancesToSend.length == 0) {
            Timber.e("onCreate: No instances to upload!");
            // drop through -- everything will process through OK
        } else {
            Timber.i("onCreate: Beginning upload of %d instances!", instancesToSend.length);
        }

        // get the task if we've changed orientations. If it's null it's a new upload.
        instanceUploaderTask = (InstanceUploaderTask) getLastCustomNonConfigurationInstance();
        if (instanceUploaderTask == null) {
            // setup dialog and upload task
            showDialog(PROGRESS_DIALOG);
            instanceUploaderTask = new InstanceUploaderTask();

            // register this activity with the new uploader task
            instanceUploaderTask.setUploaderListener(InstanceUploaderActivity.this);

            instanceUploaderTask.execute(instancesToSend);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        Collect.getInstance().getActivityLogger().logOnStart(this);
    }

    @Override
    protected void onResume() {
        Timber.i("onResume: Resuming upload of %d instances!", instancesToSend.length);
        if (instanceUploaderTask != null) {
            instanceUploaderTask.setUploaderListener(this);
        }
        if (alertShowing) {
            createAlertDialog(alertMsg);
        }
        super.onResume();
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(ALERT_MSG, alertMsg);
        outState.putBoolean(ALERT_SHOWING, alertShowing);
        outState.putString(AUTH_URI, url);

        long[] toSend = new long[instancesToSend.length];
        for (int i = 0; i < instancesToSend.length; ++i) {
            toSend[i] = instancesToSend[i];
        }
        outState.putLongArray(TO_SEND, toSend);
    }


    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return instanceUploaderTask;
    }

    @Override
    protected void onPause() {
        Timber.i("onPause: Pausing upload of %d instances!", instancesToSend.length);
        super.onPause();
        if (alertDialog != null && alertDialog.isShowing()) {
            alertDialog.dismiss();
        }
    }


    @Override
    protected void onStop() {
        Collect.getInstance().getActivityLogger().logOnStop(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        if (instanceUploaderTask != null) {
            instanceUploaderTask.setUploaderListener(null);
        }
        super.onDestroy();
    }

    @Override
    public void uploadingComplete(HashMap<String, String> result) {
        Timber.i("uploadingComplete: Processing results (%d) from upload of %d instances!",
                result.size(), instancesToSend.length);

        try {
            dismissDialog(PROGRESS_DIALOG);
        } catch (Exception e) {
            // tried to close a dialog not open. don't care.
        }


        Set<String> keys = result.keySet();
        Iterator<String> it = keys.iterator();

        StringBuilder message = new StringBuilder();
        int count = keys.size();
        while (count > 0) {
            String[] selectionArgs = null;

            if (count > ApplicationConstants.SQLITE_MAX_VARIABLE_NUMBER) {
                selectionArgs = new String[ApplicationConstants.SQLITE_MAX_VARIABLE_NUMBER];
            } else {
                selectionArgs = new String[count];
            }

            StringBuilder selection = new StringBuilder();
            selection.append(InstanceColumns._ID + " IN (");

            int i = 0;
            while (it.hasNext() && i < selectionArgs.length) {
                selectionArgs[i] = it.next();
                selection.append("?");

                if (i != selectionArgs.length - 1) {
                    selection.append(",");
                }
                i++;
            }

            selection.append(")");
            count -= selectionArgs.length;

            StringBuilder queryMessage = new StringBuilder();
            Cursor results = null;
            try {
                results = new InstancesDao().getInstancesCursor(selection.toString(), selectionArgs);
                if (results.getCount() > 0) {
                    results.moveToPosition(-1);
                    while (results.moveToNext()) {
                        String name =
                                results.getString(
                                        results.getColumnIndex(InstanceColumns.DISPLAY_NAME));
                        String id = results.getString(results.getColumnIndex(InstanceColumns._ID));
                        String text = localizeDefaultAggregateSuccessfulText(result.get(id));
                        queryMessage
                                .append(name)
                                .append(" - ")
                                .append(text)
                                .append("\n\n");
                    }
                }
            } catch (SQLException e) {
                Timber.e(e);
            } finally {
                if (results != null) {
                    results.close();
                }
            }
            message.append(queryMessage.toString());
        }
        if (message.length() == 0) {
            message.append(getString(R.string.no_forms_uploaded));
        }
        createAlertDialog(message.toString().trim());
    }

    private String localizeDefaultAggregateSuccessfulText(String text) {
        if (text.equals("full submission upload was successful!")) {
            text = getString(R.string.success);
        }
        return text;
    }

    @Override
    public void progressUpdate(int progress, int total) {
        alertMsg = getString(R.string.sending_items, String.valueOf(progress), String.valueOf(total));
        progressDialog.setMessage(alertMsg);
    }


    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case PROGRESS_DIALOG:
                Collect.getInstance().getActivityLogger().logAction(this,
                        "onCreateDialog.PROGRESS_DIALOG", "show");

                progressDialog = new ProgressDialog(this);
                DialogInterface.OnClickListener loadingButtonListener =
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                Collect.getInstance().getActivityLogger().logAction(this,
                                        "onCreateDialog.PROGRESS_DIALOG", "cancel");
                                dialog.dismiss();
                                instanceUploaderTask.cancel(true);
                                instanceUploaderTask.setUploaderListener(null);
                                finish();
                            }
                        };
                progressDialog.setTitle(getString(R.string.uploading_data));
                progressDialog.setMessage(alertMsg);
                progressDialog.setIndeterminate(true);
                progressDialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                progressDialog.setCancelable(false);
                progressDialog.setButton(getString(R.string.cancel), loadingButtonListener);
                return progressDialog;
            case AUTH_DIALOG:
                Timber.i("onCreateDialog(AUTH_DIALOG): for upload of %d instances!",
                        instancesToSend.length);
                Collect.getInstance().getActivityLogger().logAction(this,
                        "onCreateDialog.AUTH_DIALOG", "show");


                // Get the server, username, and password from the settings
                SharedPreferences settings =
                        PreferenceManager.getDefaultSharedPreferences(getBaseContext());

                return new AuthDialogUtility().createDialog(this, this);
        }

        return null;
    }


    @Override
    public void authRequest(Uri url, HashMap<String, String> doneSoFar) {
        if (progressDialog.isShowing()) {
            // should always be showing here
            progressDialog.dismiss();
        }

        // add our list of completed uploads to "completed"
        // and remove them from our toSend list.
        ArrayList<Long> workingSet = new ArrayList<Long>();
        Collections.addAll(workingSet, instancesToSend);
        if (doneSoFar != null) {
            Set<String> uploadedInstances = doneSoFar.keySet();
            Iterator<String> itr = uploadedInstances.iterator();

            while (itr.hasNext()) {
                Long removeMe = Long.valueOf(itr.next());
                boolean removed = workingSet.remove(removeMe);
                if (removed) {
                    Timber.i("%d was already sent, removing from queue before restarting task",
                            removeMe);
                }
            }
            this.uploadedInstances.putAll(doneSoFar);
        }

        // and reconstruct the pending set of instances to send
        Long[] updatedToSend = new Long[workingSet.size()];
        for (int i = 0; i < workingSet.size(); ++i) {
            updatedToSend[i] = workingSet.get(i);
        }
        instancesToSend = updatedToSend;

        this.url = url.toString();
        showDialog(AUTH_DIALOG);
    }


    private void createAlertDialog(String message) {
        Collect.getInstance().getActivityLogger().logAction(this, "createAlertDialog", "show");

        alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setTitle(getString(R.string.upload_results));
        alertDialog.setMessage(message);
        DialogInterface.OnClickListener quitListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE: // ok
                        Collect.getInstance().getActivityLogger().logAction(this,
                                "createAlertDialog", "OK");
                        // always exit this activity since it has no interface
                        alertShowing = false;
                        finish();
                        break;
                }
            }
        };
        alertDialog.setCancelable(false);
        alertDialog.setButton(getString(R.string.ok), quitListener);
        alertDialog.setIcon(android.R.drawable.ic_dialog_info);
        alertShowing = true;
        alertMsg = message;
        alertDialog.show();
    }

    @Override
    public void updatedCredentials() {
        showDialog(PROGRESS_DIALOG);
        instanceUploaderTask = new InstanceUploaderTask();

        // register this activity with the new uploader task
        instanceUploaderTask.setUploaderListener(InstanceUploaderActivity.this);
        instanceUploaderTask.execute(instancesToSend);
    }

    @Override
    public void cancelledUpdatingCredentials() {
        finish();
    }
}