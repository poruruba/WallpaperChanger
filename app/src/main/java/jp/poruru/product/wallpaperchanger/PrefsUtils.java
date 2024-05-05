package jp.poruru.product.wallpaperchanger;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public class PrefsUtils {
    public static final String TAG = MainActivity.TAG;
    public static final String KEY_INTERVAL = "interval";
    public static final String KEY_TARGET = "target";
    public static final String KEY_PATH_LIST = "pathList";
    public static final String KEY_LAST_IMAGE = "lastImage";

    public static class ApplicationConfig{
        public int interval;
        public int target;

        public ApplicationConfig(){
        }
    }

    public static class ImageInfo{
        public String displayName;
        public String path;

        public ImageInfo(String displayName, String path){
            this.displayName = displayName;
            this.path = path;
        }
    }

    public static void saveConfig(SharedPreferences prefs, ApplicationConfig config){
        Log.d(TAG, "saveConfig called");

        SharedPreferences.Editor editor = prefs.edit();
        editor.putInt(KEY_INTERVAL, config.interval);
        editor.putInt(KEY_TARGET, config.target);
        editor.apply();
    }

    public static ApplicationConfig loadConfig(SharedPreferences prefs){
        Log.d(TAG, "loadInterval called");

        ApplicationConfig config = new ApplicationConfig();
        config.interval = prefs.getInt(KEY_INTERVAL, MainActivity.INTERVAL_1day);
        config.target = prefs.getInt(KEY_TARGET, MainActivity.TARGET_SYSTEM);
        return config;
    }

    public static void savePaths(SharedPreferences prefs, List<String> list) throws JSONException {
        JSONArray array = new JSONArray(list);
        JSONObject json = new JSONObject();
        json.put("paths", array);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(KEY_PATH_LIST, json.toString());
        editor.apply();
    }

    public static List<String> loadPaths(SharedPreferences prefs) throws JSONException{
        Log.d(TAG, "loadPaths called");

        String paths = prefs.getString(KEY_PATH_LIST, "{}");
        JSONObject json = new JSONObject(paths);
        List<String> list = new ArrayList<>();
        try {
            JSONArray array = json.getJSONArray("paths");
            for (int i = 0; i < array.length(); i++) {
                list.add(array.getString(i));
            }
        }catch(Exception ex){
            Log.e(TAG, ex.getMessage());
        }
        return list;
    }

    public static ImageInfo loadImageInfo(SharedPreferences prefs){
        Log.d(TAG, "loadImageInfo called");

        try {
            String s = prefs.getString(KEY_LAST_IMAGE, "{}");
            JSONObject json = new JSONObject(s);
            String displayName = json.getString("displayName");
            String path = json.getString("path");
            return new ImageInfo(path, displayName);
        }catch(Exception ex){
            return null;
        }
    }

    public static void saveImageInfo(SharedPreferences prefs, ImageInfo info) throws JSONException {
        Log.d(TAG, "saveImageData called");

        SharedPreferences.Editor editor = prefs.edit();
        JSONObject obj = new JSONObject();
        obj.put("displayName", info.displayName);
        obj.put("path", info.path);
        editor.putString(KEY_LAST_IMAGE, obj.toString());
        editor.apply();
    }

    public static List<MainActivity.ImageData> listImageData(ContentResolver contentResolver, String[] paths){
        Log.d(TAG, "listImageData called");

        String[] projection = {
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.RELATIVE_PATH
        };
        String selection = "";
        String[] selectionArgs = new String[paths.length];
        for( int i = 0 ; i < paths.length ; i++ ){
            if( i != 0 )
                selection += " OR ";
            selection += MediaStore.Images.Media.RELATIVE_PATH + " LIKE ?";
            selectionArgs[i] = paths[i] + "%";
        }
        String sortOrder = MediaStore.Images.Media.RELATIVE_PATH + " ASC";
        Cursor cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, selection, selectionArgs, sortOrder
        );
        int displayNameIndex = cursor.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME);
        int idColumnIndex = cursor.getColumnIndex(MediaStore.Images.Media._ID);
        int pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH);

        List<MainActivity.ImageData> imageList = new ArrayList<>();
        while (cursor.moveToNext()) {
            String displayName = cursor.getString(displayNameIndex);
            String path = cursor.getString(pathIndex);
            String id = cursor.getString(idColumnIndex);
//            Log.d(TAG, "Path = " + path + " Display name = " + displayName);

            Uri photoUri = Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
            MainActivity.ImageData imageData = new MainActivity.ImageData(id, photoUri, path, displayName);
            imageList.add(imageData);
        }

        return imageList;
    }
}