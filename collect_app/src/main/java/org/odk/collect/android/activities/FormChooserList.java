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
import android.content.ContentUris;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;

import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.dao.FormsDao;
import org.odk.collect.android.listeners.DiskSyncListener;
import org.odk.collect.android.provider.FormsProviderAPI.FormsColumns;
import org.odk.collect.android.tasks.DiskSyncTask;
import org.odk.collect.android.utilities.ApplicationConstants;
import org.odk.collect.android.utilities.VersionHidingCursorAdapter;

import timber.log.Timber;

/**
 * Responsible for displaying all the valid forms in the forms directory. Stores the path to
 * selected form for use by {@link MainMenuActivity}.
 *
 * @author Yaw Anokwa (yanokwa@gmail.com)
 * @author Carl Hartung (carlhartung@gmail.com)
 */
public class FormChooserList extends FormListActivity implements DiskSyncListener, AdapterView.OnItemClickListener {
    private static final String FORM_CHOOSER_LIST_SORTING_ORDER = "formChooserListSortingOrder";

    private static final boolean EXIT = true;
    private static final String syncMsgKey = "syncmsgkey";

    private DiskSyncTask diskSyncTask;

    @Override
    public void onCreate(Bundle savedInstanceState) {

        // must be at the beginning of any activity that can be called from an external intent
        try {
            Collect.createODKDirs();
        } catch (RuntimeException e) {
            createErrorDialog(e.getMessage(), EXIT);
            return;
        }

        setContentView(R.layout.chooser_list_layout);
        super.onCreate(savedInstanceState);

        setTitle(getString(R.string.enter_data));

        setupAdapter();

        if (savedInstanceState != null && savedInstanceState.containsKey(syncMsgKey)) {
            TextView tv = (TextView) findViewById(R.id.status_text);
            tv.setText((savedInstanceState.getString(syncMsgKey)).trim());
        }

        // DiskSyncTask checks the disk for any forms not already in the content provider
        // that is, put here by dragging and dropping onto the SDCard
        diskSyncTask = (DiskSyncTask) getLastCustomNonConfigurationInstance();
        if (diskSyncTask == null) {
            Timber.i("Starting new disk sync task");
            diskSyncTask = new DiskSyncTask();
            diskSyncTask.setDiskSyncListener(this);
            diskSyncTask.execute((Void[]) null);
        }
        sortingOptions = new String[]{
                getString(R.string.sort_by_name_asc), getString(R.string.sort_by_name_desc),
                getString(R.string.sort_by_date_asc), getString(R.string.sort_by_date_desc),
        };
    }


    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        // pass the thread on restart
        return diskSyncTask;
    }


    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        TextView tv = (TextView) findViewById(R.id.status_text);
        outState.putString(syncMsgKey, tv.getText().toString().trim());
    }


    /**
     * Stores the path of selected form and finishes.
     */
    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // get uri to form
        long idFormsTable = listView.getAdapter().getItemId(position);
        Uri formUri = ContentUris.withAppendedId(FormsColumns.CONTENT_URI, idFormsTable);

        Collect.getInstance().getActivityLogger().logAction(this, "onListItemClick",
                formUri.toString());

        String action = getIntent().getAction();
        if (Intent.ACTION_PICK.equals(action)) {
            // caller is waiting on a picked form
            setResult(RESULT_OK, new Intent().setData(formUri));
        } else {
            // caller wants to view/edit a form, so launch formentryactivity
            Intent intent = new Intent(Intent.ACTION_EDIT, formUri);
            intent.putExtra(ApplicationConstants.BundleKeys.FORM_MODE, ApplicationConstants.FormModes.EDIT_SAVED);
            startActivity(intent);
        }

        finish();
    }


    @Override
    protected void onResume() {
        diskSyncTask.setDiskSyncListener(this);
        super.onResume();

        if (diskSyncTask.getStatus() == AsyncTask.Status.FINISHED) {
            syncComplete(diskSyncTask.getStatusMessage());
        }
    }


    @Override
    protected void onPause() {
        diskSyncTask.setDiskSyncListener(null);
        super.onPause();
    }


    @Override
    protected void onStart() {
        super.onStart();
        Collect.getInstance().getActivityLogger().logOnStart(this);
    }

    @Override
    protected void onStop() {
        Collect.getInstance().getActivityLogger().logOnStop(this);
        super.onStop();
    }


    /**
     * Called by DiskSyncTask when the task is finished
     */

    @Override
    public void syncComplete(String result) {
        Timber.i("Disk sync task complete");
        TextView tv = (TextView) findViewById(R.id.status_text);
        tv.setText(result.trim());
    }

    private void setupAdapter() {
        String[] data = new String[]{
                FormsColumns.DISPLAY_NAME, FormsColumns.DISPLAY_SUBTEXT, FormsColumns.JR_VERSION
        };
        int[] view = new int[]{
                R.id.text1, R.id.text2, R.id.text3
        };

        listAdapter =
                new VersionHidingCursorAdapter(FormsColumns.JR_VERSION, this, R.layout.two_item, getCursor(), data, view);

        listView.setAdapter(listAdapter);
    }

    @Override
    protected String getSortingOrderKey() {
        return FORM_CHOOSER_LIST_SORTING_ORDER;
    }

    @Override
    protected void updateAdapter() {
        listAdapter.changeCursor(getCursor());
    }

    private Cursor getCursor() {
        return new FormsDao().getFormsCursor(getFilterText(), getSortingOrder());
    }

    /**
     * Creates a dialog with the given message. Will exit the activity when the user preses "ok" if
     * shouldExit is set to true.
     */
    private void createErrorDialog(String errorMsg, final boolean shouldExit) {

        Collect.getInstance().getActivityLogger().logAction(this, "createErrorDialog", "show");

        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        alertDialog.setIcon(android.R.drawable.ic_dialog_info);
        alertDialog.setMessage(errorMsg);
        DialogInterface.OnClickListener errorListener = new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                switch (i) {
                    case DialogInterface.BUTTON_POSITIVE:
                        Collect.getInstance().getActivityLogger().logAction(this,
                                "createErrorDialog",
                                shouldExit ? "exitApplication" : "OK");
                        if (shouldExit) {
                            finish();
                        }
                        break;
                }
            }
        };
        alertDialog.setCancelable(false);
        alertDialog.setButton(getString(R.string.ok), errorListener);
        alertDialog.show();
    }
}
