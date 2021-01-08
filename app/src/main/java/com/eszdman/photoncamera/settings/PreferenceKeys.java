package com.eszdman.photoncamera.settings;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.camera2.CameraManager;
import android.util.Log;
import androidx.annotation.StringRes;
import com.eszdman.photoncamera.R;
import com.eszdman.photoncamera.api.CameraManager2;
import com.eszdman.photoncamera.app.PhotonCamera;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static android.content.Context.CAMERA_SERVICE;

/**
 * Created by Vibhor 06/09/2020
 */
public class PreferenceKeys {
    public static final String SCOPE_GLOBAL = "default_scope";
    private static final String TAG = "PreferenceKeys";
    private static final Set<String> COMMON_KEYS = new HashSet<>();
    private static final String PER_LENS_KEY_PREFIX = "settings_for_camera_";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    /**
     * Scope to use SCOPE_GLOBAL => DefaultSharedPreferences
     */
    public static String current_scope = SCOPE_GLOBAL;
    private static PreferenceKeys preferenceKeys;

    static {
        COMMON_KEYS.add(Preference.CAMERA_ID.mValue);
        COMMON_KEYS.add(Preference.KEY_SAVE_PER_LENS_SETTINGS.mValue);
        COMMON_KEYS.add(Preference.KEY_SHOW_AF_DATA.mValue);
        COMMON_KEYS.add(Preference.KEY_THEME_ACCENT.mValue);
        COMMON_KEYS.add(Preference.KEY_THEME.mValue);
        COMMON_KEYS.add(Preference.KEY_SHOW_GRID.mValue);
        COMMON_KEYS.add(Preference.KEY_SHOW_WATERMARK.mValue);
        COMMON_KEYS.add(Preference.KEY_SHOW_ROUND_EDGE.mValue);
        COMMON_KEYS.add(Preference.KEY_CAMERA_SOUNDS.mValue);
        COMMON_KEYS.add(Preference.KEY_SHOW_GRADIENT.mValue);
        COMMON_KEYS.add(Preference.KEY_AF_MODE.mValue);
    }

    private final SettingsManager settingsManager;

    private PreferenceKeys(SettingsManager settingsManager) {
        this.settingsManager = settingsManager;
    }

    public static void initialise(SettingsManager settingsManager) {
        preferenceKeys = new PreferenceKeys(settingsManager);
    }

    public static void setDefaults(Context context) {
        SettingsManager settingsManager = preferenceKeys.settingsManager;
        CameraManager2 cameraManager2 = new CameraManager2((CameraManager) context.getSystemService(CAMERA_SERVICE), settingsManager);
        Resources resources = context.getResources();
        if (!settingsManager.isSet(current_scope, Preference.KEY_HDRX)) {
            settingsManager.set(current_scope, Preference.KEY_HDRX, resources.getBoolean(R.bool.pref_hdrx_mode_default));
            settingsManager.set(current_scope, Preference.KEY_EIS_PHOTO, resources.getBoolean(R.bool.pref_eis_photo_default));
            settingsManager.set(current_scope, Preference.KEY_QUAD_BAYER, resources.getBoolean(R.bool.pref_quad_bayer_default));
            settingsManager.set(current_scope, Preference.KEY_REMOSAIC, resources.getBoolean(R.bool.pref_remosaic_default));
            settingsManager.set(current_scope, Preference.KEY_FPS_PREVIEW, resources.getBoolean(R.bool.pref_fps_preview_default));
        }
        settingsManager.setDefaults(Preference.CAMERA_ID, resources.getString(R.string.camera_id_default), new String[]{"0", "1"});
        settingsManager.setDefaults(Preference.TONEMAP, resources.getString(R.string.tonemap_default), new String[]{resources.getString(R.string.tonemap_default)});
        settingsManager.setDefaults(Preference.GAMMA, resources.getString(R.string.gamma_default), new String[]{resources.getString(R.string.gamma_default)});

        Map<String, ?> map = settingsManager.getDefaultPreferences().getAll();
        map.keySet().removeAll(COMMON_KEYS);
        String json = GSON.toJson(map);
        for (String cameraId : cameraManager2.getCameraIdList()) { //Makes a copy of default settings for each camera
            if (!settingsManager.isSet(Preference.PER_LENS_FILE_NAME.mValue, PER_LENS_KEY_PREFIX + cameraId)) {
                settingsManager.set(Preference.PER_LENS_FILE_NAME.mValue, PER_LENS_KEY_PREFIX + cameraId, json);
            }
        }
        settingsManager.addListener((settingsManager1, key) -> {
            if (isPerLensSettingsOn()) {
                if (key.equals(Preference.CAMERA_ID.mValue)) {
                    loadSettingsForCamera(getCameraID());
                }
                if (!COMMON_KEYS.contains(key)) {
                    saveJsonForCamera(getCameraID());
                }
            }
            PhotonCamera.getSettings().loadCache();
            Log.d(TAG, key + " : changed!");
        });
    }

    private static void saveJsonForCamera(String cameraID) {
        SettingsManager settingsManager = preferenceKeys.settingsManager;
        Map<String, ?> map = settingsManager.getDefaultPreferences().getAll();
        map.keySet().removeAll(COMMON_KEYS);
        String hashmapAsJson = GSON.toJson(map);
        String alreadySavedJSON = settingsManager.getString(Preference.PER_LENS_FILE_NAME.mValue, PER_LENS_KEY_PREFIX + cameraID, "");
        if (!alreadySavedJSON.equals(hashmapAsJson)) {
            settingsManager.set(Preference.PER_LENS_FILE_NAME.mValue, PER_LENS_KEY_PREFIX + getCameraID(), hashmapAsJson);
//            Log.d(TAG, PER_LENS_KEY_PREFIX + getCameraID() + " : JSON : " + hashmapAsJson);
        }
    }

    public static void loadSettingsForCamera(String cameraID) {
        SettingsManager settingsManager = preferenceKeys.settingsManager;
        String alreadySavedJSON = settingsManager.getString(Preference.PER_LENS_FILE_NAME.mValue, PER_LENS_KEY_PREFIX + cameraID, null);
        HashMap<String, ?> map = GSON.fromJson(alreadySavedJSON, HashMap.class);
        for (Map.Entry<String, ?> e : map.entrySet()) {
            settingsManager.set(current_scope, e.getKey(), e.getValue().toString());
        }
    }

    public static void setActivityTheme(Activity activity) {
        Map<String, Integer> map = new HashMap<>();
        map.put("default", 0);
        map.put("red", R.style.RedTheme);
        map.put("blue", R.style.BlueTheme);
        map.put("orange", R.style.OrangeTheme);
        map.put("green", R.style.GreenTheme);
        map.put("eszdman", R.style.EszdmanTheme);

        SettingsManager sm = preferenceKeys.settingsManager;

        String theme = sm.getString(SCOPE_GLOBAL, Preference.KEY_THEME_ACCENT, activity.getResources().getString(R.string.pref_theme_accent_default_value));
        boolean showGradient = sm.getBoolean(SCOPE_GLOBAL, Preference.KEY_SHOW_GRADIENT, activity.getResources().getBoolean(R.bool.pref_show_gradient_def_value));

        if (showGradient) {
            activity.getTheme().applyStyle(R.style.GradientBackgroundTheme, true);
        }
        if (theme != null) {
            Integer themeRes = map.get(theme.toLowerCase());
            activity.getTheme().applyStyle(themeRes == null ? 0 : themeRes, true);
        }

    }

    /**
     * Helper functions for some keys defined in PreferenceFragment.
     */
    public static boolean isAfDataOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_SHOW_AF_DATA);
    }

    public static int isSystemNrOn() {
        return preferenceKeys.settingsManager.getInteger(current_scope, Preference.KEY_ENABLE_SYSTEM_NR);
    }

    public static boolean isRemosaicOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_REMOSAIC);
    }

    public static boolean isDisableAligningOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_DISABLE_ALIGNINIG);
    }

    public static boolean isShowWatermarkOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_SHOW_WATERMARK);
    }

    public static boolean isPerLensSettingsOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_SAVE_PER_LENS_SETTINGS);
    }

    public static boolean isEnhancedProcessionOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_ENHANCED_PROCESSING);
    }

    public static boolean isHdrxNrOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_HDRX_NR);
    }

    public static boolean isSaveRawOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_SAVE_RAW);
    }

    public static boolean isRoundEdgeOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_SHOW_ROUND_EDGE);
    }

    public static int getGridValue() {
        return preferenceKeys.settingsManager.getInteger(current_scope, Preference.KEY_SHOW_GRID);
    }

    public static void setGridValue(int value) {
        preferenceKeys.settingsManager.set(current_scope, Preference.KEY_SHOW_GRID, value);
    }

    public static boolean isCameraSoundsOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_CAMERA_SOUNDS);
    }

    public static int getChromaNrValue() {
        return preferenceKeys.settingsManager.getInteger(current_scope, Preference.KEY_CHROMA_NR_SEEKBAR);
    }

    public static int getLumaNrValue() {
        return preferenceKeys.settingsManager.getInteger(current_scope, Preference.KEY_LUMA_NR_SEEKBAR);
    }

    public static int getFrameCountValue() {
        return preferenceKeys.settingsManager.getInteger(current_scope, Preference.KEY_FRAME_COUNT);
    }

    public static float getSharpnessValue() {
        return preferenceKeys.settingsManager.getFloat(current_scope, Preference.KEY_SHARPNESS_SEEKBAR);
    }

    public static float getCompressorValue() {
        return preferenceKeys.settingsManager.getFloat(current_scope, Preference.KEY_COMPRESSOR_SEEKBAR);
    }

    public static float getGainValue() {
        return preferenceKeys.settingsManager.getFloat(current_scope, Preference.KEY_GAIN_SEEKBAR);
    }

    public static float getSaturationValue() {
        return preferenceKeys.settingsManager.getFloat(current_scope, Preference.KEY_SATURATION_SEEKBAR);
    }

    public static float getContrastValue() {
        return preferenceKeys.settingsManager.getFloat(current_scope, Preference.KEY_CONTRAST_SEEKBAR);
    }

    public static int getAlignMethodValue() {
        return preferenceKeys.settingsManager.getInteger(current_scope, Preference.KEY_ALIGN_METHOD);
    }

    public static int getCFAValue() {
        return preferenceKeys.settingsManager.getInteger(current_scope, Preference.KEY_CFA);
    }

    public static int getThemeValue() {
        return preferenceKeys.settingsManager.getInteger(current_scope, Preference.KEY_THEME);
    }

    /**
     * Helper functions for other keys such as viewfinder buttons, etc.
     */
    public static boolean isHdrXOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_HDRX);
    }

    public static void setHdrX(boolean value) {
        preferenceKeys.settingsManager.set(current_scope, Preference.KEY_HDRX, value);
    }

    public static boolean isEisPhotoOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_EIS_PHOTO);
    }

    public static void setEisPhoto(boolean value) {
        preferenceKeys.settingsManager.set(current_scope, Preference.KEY_EIS_PHOTO, value);
    }

    public static boolean isFpsPreviewOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_FPS_PREVIEW);
    }

    public static void setFpsPreview(boolean value) {
        preferenceKeys.settingsManager.set(current_scope, Preference.KEY_FPS_PREVIEW, value);
    }

    public static boolean isQuadBayerOn() {
        return preferenceKeys.settingsManager.getBoolean(current_scope, Preference.KEY_QUAD_BAYER);
    }

    public static void setQuadBayer(boolean value) {
        preferenceKeys.settingsManager.set(current_scope, Preference.KEY_QUAD_BAYER, value);
    }

    public static String getCameraID() {
        return preferenceKeys.settingsManager.getString(Preference.CAMERAS_PREFERENCE_FILE_NAME.mValue, Preference.CAMERA_ID);
    }

    public static void setCameraID(String value) {
        preferenceKeys.settingsManager.set(Preference.CAMERAS_PREFERENCE_FILE_NAME.mValue, Preference.CAMERA_ID, value);
    }

    public static int getAfMode() {
        return preferenceKeys.settingsManager.getInteger(current_scope, Preference.KEY_AF_MODE);
    }

    /*public static int getCameraMode() {
        return preferenceKeys.settingsManager.getInteger(current_scope, Preference.CAMERA_MODE);
    }*/

    /*public static void setCameraMode(int value) {
        preferenceKeys.settingsManager.set(current_scope, Preference.CAMERA_MODE, value);
    }*/

    public static String getToneMap() {
        return preferenceKeys.settingsManager.getString(current_scope, Preference.TONEMAP);
    }

    public static String getPref(Preference preference) {
        return preferenceKeys.settingsManager.getString(current_scope, preference);
    }

    public static boolean getBool(Preference preference) {
        return preferenceKeys.settingsManager.getBoolean(current_scope, preference);
    }

    public static float getFloat(Preference preference) {
        return preferenceKeys.settingsManager.getFloat(current_scope, preference);
    }

    /**
     * Keys visible in Settings
     */
    public enum Preference {
        KEY_SHOW_AF_DATA(R.string.pref_show_afdata_key),
        KEY_ENABLE_SYSTEM_NR(R.string.pref_enable_system_nr_key),
        KEY_SAVE_PER_LENS_SETTINGS(R.string.pref_save_per_lens_settings),
        KEY_DISABLE_ALIGNINIG(R.string.pref_disable_aligning_key),
        KEY_SHOW_WATERMARK(R.string.pref_show_watermark_key),
        KEY_ENERGY_SAVING(R.string.pref_energy_safe_key),
        KEY_ENHANCED_PROCESSING(R.string.pref_enhanced_processing_key),
        KEY_HDRX_NR(R.string.pref_hdrx_nr_key),
        KEY_SAVE_RAW(R.string.pref_save_raw_key),
        KEY_SHOW_ROUND_EDGE(R.string.pref_show_roundedge_key),
        KEY_SHOW_GRID(R.string.pref_show_grid_key),
        KEY_CAMERA_SOUNDS(R.string.pref_camera_sounds_key),
        KEY_CHROMA_NR_SEEKBAR(R.string.pref_chroma_nr_seekbar_key),
        KEY_LUMA_NR_SEEKBAR(R.string.pref_luma_nr_seekbar_key),
        KEY_COMPRESSOR_SEEKBAR(R.string.pref_compressor_seekbar_key),
        KEY_NOISESTR_SEEKBAR(R.string.pref_noise_seekbar_key),
        KEY_GAIN_SEEKBAR(R.string.pref_gain_seekbar_key),
        KEY_FRAME_COUNT(R.string.pref_frame_count_key),
        KEY_CONTRAST_SEEKBAR(R.string.pref_contrast_seekbar_key),
        KEY_SHARPNESS_SEEKBAR(R.string.pref_sharpness_seekbar_key),
        KEY_SATURATION_SEEKBAR(R.string.pref_saturation_seekbar_key),
        KEY_ALIGN_METHOD(R.string.pref_align_method_key),
        KEY_CFA(R.string.pref_cfa_key),
        KEY_REMOSAIC(R.string.pref_remosaic_key),////TODO
        KEY_TELEGRAM(R.string.pref_telegram_channel_key),
        KEY_CONTRIBUTORS(R.string.pref_contributors_key),
        KEY_THEME(R.string.pref_theme_key),
        KEY_THEME_ACCENT(R.string.pref_theme_accent_key),
        KEY_SHOW_GRADIENT(R.string.pref_show_gradient_key),
        KEY_AF_MODE(R.string.pref_af_mode_key),
        /**
         * Other Keys
         */
        KEY_HDRX(R.string.pref_hdrx_key),
        KEY_EIS_PHOTO(R.string.pref_eis_photo_key),
        KEY_QUAD_BAYER(R.string.pref_quad_bayer_key),
        KEY_FPS_PREVIEW(R.string.pref_fps_preview_key),
        CAMERA_ID(R.string.camera_id),
        TONEMAP(R.string.tonemap_key),
        GAMMA(R.string.gamma_key),
        CAMERA_MODE(R.string.pref_camera_mode_key),

        /* CameraManager 2 keys */
        CAMERAS_PREFERENCE_FILE_NAME(R.string._cameras),
        ALL_CAMERA_IDS_KEY(R.string.all_camera_ids),
        FRONT_IDS_KEY(R.string.front_camera_ids),
        BACK_IDS_KEY(R.string.back_camera_ids),
        FOCAL_IDS_KEY(R.string.all_camera_focals),
        CAMERA_COUNT_KEY(R.string.all_camera_count),

        /* SupportedDevice keys */
        DEVICES_PREFERENCE_FILE_NAME(R.string._devices),
        ALL_DEVICES_NAMES_KEY(R.string.all_devices_names),
        /**
         * Per Lens File
         */
        PER_LENS_FILE_NAME(R.string._per_lens);
        public final String mValue;

        Preference(@StringRes int stringId) {
            mValue = PhotonCamera.getStringStatic(stringId);
        }
    }
}
