package jp.poruru.product.wallpaperchanger;

import android.app.WallpaperManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.MediaStore;
import android.util.Log;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class PeriodicWorker extends Worker {
    public final static String WORKER_TAG = "PeriodicWorker";
    public final static String TAG = "PeriodicWorkerTAG";

    public PeriodicWorker(Context context, WorkerParameters workerParams) {
        super(context, workerParams);
        Log.d(TAG, "PeriodicWorker constructor called");
    }

    @Override
    public Result doWork() {
        Log.d(TAG, "doWork called");

        try{
            Context context = getApplicationContext();
            SharedPreferences prefs = context.getSharedPreferences("config", Context.MODE_PRIVATE);
            List<String> paths = PrefsUtils.loadPaths(prefs);
            String[] pathList = paths.toArray(new String[paths.size()]);
            List<MainActivity.ImageData> list = PrefsUtils.listImageData(context.getContentResolver(), pathList);

            if( list.size() > 0) {
                int index = new Random().nextInt(list.size());
                MainActivity.ImageData imageData = list.get(index);
                PrefsUtils.saveImageInfo(prefs, new PrefsUtils.ImageInfo(imageData.displayName, imageData.path));

                PrefsUtils.ApplicationConfig config = PrefsUtils.loadConfig(prefs);
                updateWallpaper(context, imageData, config.target);
            }
        }catch(Exception ex){
            Log.e(TAG, ex.getMessage());
            return Result.retry();
        }

        return Result.success();
    }

    public static void updateWallpaper(Context context, MainActivity.ImageData imageData, int target) throws Exception{
        Log.d(TAG, "updateWallpaper called");

        WallpaperManager wpm = WallpaperManager.getInstance(context);
        int Dw = wpm.getDesiredMinimumWidth();
        int Dh = wpm.getDesiredMinimumHeight();
        Log.d( TAG, "desiredMinimumWidth=" + Dw + " desiredMinimumHeight=" + Dh);

        InputStream input = context.getContentResolver().openInputStream(imageData.uri);
        Bitmap image = BitmapFactory.decodeStream(input);
        input.close();

        int Iw = image.getWidth();
        int Ih = image.getHeight();
        Log.d( TAG, "imageWidth=" + Iw + " imageHeight=" + Ih);

        int Ch = (int)((float)Iw / (float)Dw * Dh);
        Bitmap bitmap = image;
        if( Ih < Ch ){
            int Cw = (int)((float)Dw / (float)Dh * Ih);
            int startX = (Iw - Cw) / 2;
            bitmap = Bitmap.createBitmap(Cw, Ih, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            Rect rectSrc = new Rect(startX, 0, startX + Cw, Ih);
            Rect rectDest = new Rect(0, 0, Cw, Ih);
            canvas.drawBitmap(image, rectSrc, rectDest, null);
        }
        switch(target){
            case MainActivity.TARGET_SYSTEM: {
                wpm.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM);
                break;
            }
            case MainActivity.TARGET_SYSTEM_AND_LOCK: {
                wpm.setBitmap(bitmap, null, false, WallpaperManager.FLAG_SYSTEM);
                wpm.setBitmap(image, null, false, WallpaperManager.FLAG_LOCK);
                break;
            }
            case MainActivity.TARGET_LOCK: {
                wpm.setBitmap(image, null, false, WallpaperManager.FLAG_LOCK);
                break;
            }
        }
        Log.d(TAG, "Wallpaper Updated");
    }
}
