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

package org.odk.collect.android.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import org.javarosa.core.model.FormDef;
import org.odk.collect.android.R;
import org.odk.collect.android.application.Collect;
import org.odk.collect.android.utilities.ToastUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;

import static org.odk.collect.android.preferences.AdminKeys.KEY_FORM_PROCESSING_LOGIC;

/**
 * Handles admin preferences, which are password-protectable and govern which app features and
 * general preferences the end user of the app will be able to see.
 *
 * @author Thomas Smyth, Sassafras Tech Collective (tom@sassafrastech.com; constraint behavior
 *         option)
 */
public class AdminPreferencesActivity extends PreferenceActivity {
    private static final int SAVE_PREFS_MENU = Menu.FIRST;
    public static String ADMIN_PREFERENCES = "admin_prefs";

    public static boolean saveSharedPreferencesToFile(File dst, Context context) {
        // this should be in a thread if it gets big, but for now it's tiny
        boolean res = false;
        ObjectOutputStream output = null;
        try {
            output = new ObjectOutputStream(new FileOutputStream(dst));
            SharedPreferences pref = PreferenceManager
                    .getDefaultSharedPreferences(context);
            SharedPreferences adminPreferences = context.getSharedPreferences(
                    AdminPreferencesActivity.ADMIN_PREFERENCES, 0);

            output.writeObject(pref.getAll());
            output.writeObject(adminPreferences.getAll());

            res = true;
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (output != null) {
                    output.flush();
                    output.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        return res;
    }

    public static FormDef.EvalBehavior getConfiguredFormProcessingLogic(Context context) {
        FormDef.EvalBehavior mode;

        SharedPreferences adminPreferences = context.getSharedPreferences(ADMIN_PREFERENCES, 0);
        String formProcessingLoginIndex = adminPreferences.getString(KEY_FORM_PROCESSING_LOGIC,
                context.getString(R.string.default_form_processing_logic));
        try {
            if ("-1".equals(formProcessingLoginIndex)) {
                mode = FormDef.recommendedMode;
            } else {
                int preferredModeIndex = Integer.parseInt(formProcessingLoginIndex);
                switch (preferredModeIndex) {
                    case 0: {
                        mode = FormDef.EvalBehavior.Fast_2014;
                        break;
                    }
                    case 1: {
                        mode = FormDef.EvalBehavior.Safe_2014;
                        break;
                    }
                    case 2: {
                        mode = FormDef.EvalBehavior.April_2014;
                        break;
                    }
                    case 3: {
                        mode = FormDef.EvalBehavior.Legacy;
                        break;
                    }
                    default: {
                        mode = FormDef.recommendedMode;
                        break;
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            Log.w("AdminPrefActivity",
                    "Unable to get EvalBehavior -- defaulting to recommended mode");
            mode = FormDef.recommendedMode;
        }

        return mode;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getFragmentManager().beginTransaction().replace(android.R.id.content, new AdminPreferencesFragment()).commit();
        setTitle(getString(R.string.admin_preferences));
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        Collect.getInstance().getActivityLogger()
                .logAction(this, "onCreateOptionsMenu", "show");
        super.onCreateOptionsMenu(menu);

        menu
                .add(0, SAVE_PREFS_MENU, 0, R.string.save_preferences)
                .setIcon(R.drawable.ic_menu_save)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case SAVE_PREFS_MENU:
                File writeDir = new File(Collect.SETTINGS);
                if (!writeDir.exists()) {
                    if (!writeDir.mkdirs()) {
                        ToastUtils.showShortToast("Error creating directory "
                                        + writeDir.getAbsolutePath());
                        return false;
                    }
                }

                File dst = new File(writeDir.getAbsolutePath()
                        + "/collect.settings");
                boolean success = AdminPreferencesActivity.saveSharedPreferencesToFile(dst, this);
                if (success) {
                    ToastUtils.showLongToast("Settings successfully written to "
                            + dst.getAbsolutePath());
                } else {
                    ToastUtils.showLongToast("Error writing settings to " + dst.getAbsolutePath());
                }
                return true;

        }
        return super.onOptionsItemSelected(item);
    }
}
