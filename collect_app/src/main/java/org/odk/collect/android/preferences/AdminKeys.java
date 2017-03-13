package org.odk.collect.android.preferences;

import static org.odk.collect.android.preferences.AdminAndGeneralKeys.ag;

public final class AdminKeys {
    // key for this preference screen
    public static String KEY_ADMIN_PW = "admin_pw";

    // keys for each preference

    // main menu
    public  static String KEY_EDIT_SAVED                = "edit_saved";
    public  static String KEY_SEND_FINALIZED            = "send_finalized";
    public  static String KEY_VIEW_SENT                 = "view_sent";
    public  static String KEY_GET_BLANK                 = "get_blank";
    public  static String KEY_DELETE_SAVED              = "delete_saved";

    // form entry
    public  static String KEY_SAVE_MID                  = "save_mid";
    public  static String KEY_JUMP_TO                   = "jump_to";
    public  static String KEY_CHANGE_LANGUAGE           = "change_language";
    public  static String KEY_ACCESS_SETTINGS           = "access_settings";
    public  static String KEY_SAVE_AS                   = "save_as";
    public  static String KEY_MARK_AS_FINALIZED         = "mark_as_finalized";

    // server
            static String KEY_CHANGE_ADMIN_PASSWORD     = "admin_password";
            static String KEY_CHANGE_GOOGLE_ACCOUNT     = "change_google_account";
    private static String KEY_CHANGE_SERVER             = "change_server";
    private static String KEY_CHANGE_USERNAME           = "change_username";
    private static String KEY_CHANGE_PASSWORD           = "change_password";
    private static String KEY_CHANGE_PROTOCOL_SETTINGS  = "change_protocol_settings";

    // client
            static String KEY_FORM_PROCESSING_LOGIC     = "form_processing_logic";

    private static String KEY_CHANGE_FONT_SIZE          = "change_font_size";
    private static String KEY_DEFAULT_TO_FINALIZED      = "default_to_finalized";
    private static String KEY_HIGH_RESOLUTION           = "high_resolution";
    private static String KEY_SHOW_SPLASH_SCREEN        = "show_splash_screen";
    private static String KEY_DELETE_AFTER_SEND         = "delete_after_send";
    private static String KEY_INSTANCE_FORM_SYNC        = "instance_form_sync";

    private static String KEY_AUTOSEND_WIFI             = "autosend_wifi";
    private static String KEY_AUTOSEND_NETWORK          = "autosend_network";

    private static String KEY_NAVIGATION                = "navigation";
    private static String KEY_CONSTRAINT_BEHAVIOR       = "constraint_behavior";

    private static String KEY_SHOW_MAP_SDK              = "show_map_sdk";
    private static String KEY_SHOW_MAP_BASEMAP          = "show_map_basemap";

    private static String KEY_ANALYTICS                 = "analytics";

    /**
     * The admin preferences allow removing general preferences. This array contains
     * tuples of admin keys and the keys of general preferences that are removed if the admin
     * preference is false.
     */
    static AdminAndGeneralKeys[] adminToGeneral = new AdminAndGeneralKeys[] {
        ag(KEY_CHANGE_SERVER,              PreferenceKeys.KEY_PROTOCOL),
        ag(KEY_CHANGE_PROTOCOL_SETTINGS,   PreferenceKeys.KEY_PROTOCOL_SETTINGS),
        ag(KEY_AUTOSEND_WIFI,              PreferenceKeys.KEY_AUTOSEND_WIFI),
        ag(KEY_AUTOSEND_NETWORK,           PreferenceKeys.KEY_AUTOSEND_NETWORK),
        ag(KEY_DEFAULT_TO_FINALIZED,       PreferenceKeys.KEY_COMPLETED_DEFAULT),
        ag(KEY_DELETE_AFTER_SEND,          PreferenceKeys.KEY_DELETE_AFTER_SEND),
        ag(KEY_HIGH_RESOLUTION,            PreferenceKeys.KEY_HIGH_RESOLUTION),
        ag(KEY_ANALYTICS,                  PreferenceKeys.KEY_ANALYTICS),
        ag(KEY_SHOW_SPLASH_SCREEN,         PreferenceKeys.KEY_SHOW_SPLASH),
        ag(KEY_SHOW_SPLASH_SCREEN,         PreferenceKeys.KEY_SPLASH_PATH),
        ag(KEY_CHANGE_FONT_SIZE,           PreferenceKeys.KEY_FONT_SIZE),
        ag(KEY_CONSTRAINT_BEHAVIOR,        PreferenceKeys.KEY_CONSTRAINT_BEHAVIOR),
        ag(KEY_SHOW_MAP_SDK,               PreferenceKeys.KEY_MAP_SDK),
        ag(KEY_SHOW_MAP_BASEMAP,           PreferenceKeys.KEY_MAP_BASEMAP),
        ag(KEY_NAVIGATION,                 PreferenceKeys.KEY_NAVIGATION),
        ag(KEY_CHANGE_PASSWORD,            PreferenceKeys.KEY_PASSWORD),
        ag(KEY_CHANGE_USERNAME,            PreferenceKeys.KEY_USERNAME),
        ag(KEY_CHANGE_GOOGLE_ACCOUNT,      PreferenceKeys.KEY_SELECTED_GOOGLE_ACCOUNT),
        ag(KEY_INSTANCE_FORM_SYNC,         PreferenceKeys.KEY_INSTANCE_SYNC)
    };
}
