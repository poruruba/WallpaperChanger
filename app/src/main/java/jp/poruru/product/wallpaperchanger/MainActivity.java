package jp.poruru.product.wallpaperchanger;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;

import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;
import android.Manifest;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    public static final String TAG = "MainActivity";

    WorkManager manager;
    SharedPreferences prefs;
    boolean initialized = false;
    SimpleDateFormat sdf;
    ContentResolver contentResolver;

    static final int PERMISSION_REQUEST_CODE = 100;
    static final int INTERVAL_1day = 0;
    static final int INTERVAL_12hour = 1;
    static final int INTERVAL_2hour = 2;
    static final int INTERVAL_1hour = 3;
    static final int INTERVAL_30minute = 4;
    static final int INTERVAL_15minute = 5;
    static final int TARGET_SYSTEM = 0;
    static final int TARGET_SYSTEM_AND_LOCK = 1;
    static final int TARGET_LOCK = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        manager = WorkManager.getInstance(this);
        prefs = getSharedPreferences("config", Context.MODE_PRIVATE);
        contentResolver = getContentResolver();
        sdf = new SimpleDateFormat(getString(R.string.sdt_format));

        PrefsUtils.ApplicationConfig config = PrefsUtils.loadConfig(prefs);

        Spinner spin;
        ArrayAdapter<String> adapter;
        adapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item,
                new String[]{ getString(R.string.txt_interval_1day), getString(R.string.txt_interval_12hours),getString(R.string.txt_interval_2hours),getString(R.string.txt_interval_1hours),getString(R.string.txt_interval_30minute),getString(R.string.txt_interval_15minute) }
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spin = (Spinner)findViewById(R.id.spin_interval);
        spin.setAdapter(adapter);
        spin.setSelection(config.interval);

        adapter = new ArrayAdapter<>(
                this,
                R.layout.spinner_item,
                new String[]{ getString(R.string.txt_target_system), getString(R.string.txt_target_system_and_lock), getString(R.string.txt_target_lock) }
        );
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_item);
        spin = (Spinner)findViewById(R.id.spin_target);
        spin.setAdapter(adapter);
        spin.setSelection(config.target);

        Button btn;
        btn = (Button)findViewById(R.id.btn_change_paths);
        btn.setOnClickListener(this);
        btn = (Button)findViewById(R.id.btn_stop_wallpaper);
        btn.setOnClickListener(this);
        btn = (Button)findViewById(R.id.btn_update_wallpaper);
        btn.setOnClickListener(this);
        btn = (Button)findViewById(R.id.btn_open_sample);
        btn.setOnClickListener(this);

        checkApplicationPermissions();
    }

    private final ActivityResultLauncher<Intent> launcher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    updateStatus();
                }
            });

    private boolean checkApplicationPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSION_REQUEST_CODE
            );
            return false;
        }else{
            initialized = true;
            updateStatus();
            return true;
        }
    }

    private void showPermissionRequestDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.dlg_permission_title));
        builder.setMessage(getString(R.string.dlg_permission_message));
        builder.setPositiveButton(getString(R.string.dlg_permission_ok), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    Uri uri = Uri.fromParts("package", getPackageName(), null);
                    intent.setData(uri);
                    startActivity(intent);
                }
            });
        builder.setNegativeButton(getString(R.string.dlg_permission_cancel), null);
        builder.create().show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case PERMISSION_REQUEST_CODE: {
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    initialized = true;
                    updateStatus();
                }else{
                    Toast.makeText(this, getString(R.string.toast_message2), Toast.LENGTH_LONG).show();
                    showPermissionRequestDialog();
//                    handler.sendIntMessage(0);
                }
            }
        }
    }

    private void updateStatus(){
        Log.d(TAG, "updateStatus called");

        try {
            TextView text;
            text = (TextView)findViewById(R.id.txt_status);
            ListenableFuture<List<WorkInfo>> listenable = manager.getWorkInfosForUniqueWork(PeriodicWorker.WORKER_TAG);
            List<WorkInfo> list = listenable.get();
            int num = list.size();
            if( num == 0 ){
                text.setText("Not Running");
            }else
            if(num > 1 ) {
                text.setText("Error");
                throw new Exception("error list.size() != 1");
            }else{
                WorkInfo info = list.get(0);
                Date date = new Date(info.getNextScheduleTimeMillis());
                WorkInfo.State state = info.getState();
                if( state.equals(WorkInfo.State.ENQUEUED))
                    text.setText(state.toString() + " " + sdf.format(date));
                else
                    text.setText(state.toString());
            }

            List<String> paths = PrefsUtils.loadPaths(prefs);
            text = (TextView) findViewById(R.id.txt_paths);
            if( paths.size() <= 0) {
                text.setText(getString(R.string.txt_message1));
            }else{
                String s = "";
                for (int i = 0; i < paths.size(); i++){
                    if (i != 0)
                        s += "\n";
                    s += "・" + paths.get(i);
                }
                text.setText(s);
            }
        }catch(Exception ex){
            Log.e(TAG, ex.getMessage());
        }
    }

    @Override
    public void onClick(View view) {
        if( !initialized ){
            if( !checkApplicationPermissions() )
                return;
        }

        int id = view.getId();
        if( id == R.id.btn_open_sample){
            try {
                List<String> paths = PrefsUtils.loadPaths(prefs);
                if( paths.size() <= 0 ){
                    Toast.makeText(this, getString(R.string.toast_message1), Toast.LENGTH_LONG).show();
                    return;
                }
                String[] pathList = paths.toArray(new String[paths.size()]);
                PathSelectActivity.openSampleDialog(this, pathList);
            }catch(Exception ex){
                Log.e(TAG, ex.getMessage());
            }
        }else
        if( id == R.id.btn_change_paths ){
            Intent intent = new Intent(this, PathSelectActivity.class);
            launcher.launch(intent);
        }else
        if( id == R.id.btn_update_wallpaper){
            Spinner spin;
            spin = (Spinner) findViewById(R.id.spin_interval);
            int interval = spin.getSelectedItemPosition();
            spin = (Spinner) findViewById(R.id.spin_target);
            int target = spin.getSelectedItemPosition();

            PeriodicWorkRequest request;
            switch(interval){
                case INTERVAL_12hour: {
                    request = new PeriodicWorkRequest.Builder( PeriodicWorker.class,12, TimeUnit.HOURS)
                            .addTag(PeriodicWorker.WORKER_TAG)
                            .build();
                    break;
                }
                case INTERVAL_2hour: {
                    request = new PeriodicWorkRequest.Builder( PeriodicWorker.class,2, TimeUnit.HOURS)
                            .addTag(PeriodicWorker.WORKER_TAG)
                            .build();
                    break;
                }
                case INTERVAL_1hour: {
                    request = new PeriodicWorkRequest.Builder( PeriodicWorker.class,1, TimeUnit.HOURS)
                            .addTag(PeriodicWorker.WORKER_TAG)
                            .build();
                    break;
                }
                case INTERVAL_30minute: {
                    request = new PeriodicWorkRequest.Builder( PeriodicWorker.class, 30, TimeUnit.MINUTES)
                            .addTag(PeriodicWorker.WORKER_TAG)
                            .build();
                    break;
                }
                case INTERVAL_15minute: {
                    request = new PeriodicWorkRequest.Builder( PeriodicWorker.class, 15, TimeUnit.MINUTES)
                            .addTag(PeriodicWorker.WORKER_TAG)
                            .build();
                    break;
                }
                default: {
                    // 1日ごと(INTERVAL_1day)
                    request = new PeriodicWorkRequest.Builder( PeriodicWorker.class,1, TimeUnit.DAYS)
                            .addTag(PeriodicWorker.WORKER_TAG)
                            .build();
                    break;
                }
            }

            PrefsUtils.ApplicationConfig config = PrefsUtils.loadConfig(prefs);
            config.interval = interval;
            config.target = target;
            PrefsUtils.saveConfig(prefs, config);
            manager.enqueueUniquePeriodicWork(PeriodicWorker.WORKER_TAG, ExistingPeriodicWorkPolicy.REPLACE, request);

            Toast.makeText(this, getString(R.string.toast_message3), Toast.LENGTH_LONG).show();
            updateStatus();
        }else
        if( id == R.id.btn_stop_wallpaper ){
            manager.cancelUniqueWork(PeriodicWorker.WORKER_TAG);
            updateStatus();
        }
    }

    public static class ImageData{
        public String id;
        public Uri uri;
        public String path;
        public String displayName;

        public ImageData(String id, Uri uri, String path, String displayname){
            this.id = id;
            this.uri = uri;
            this.path = path;
            this.displayName = displayname;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        if( initialized )
            updateStatus();
    }

    public static boolean hasPath(List<String> paths, String path){
//        Log.d(TAG, "hasPath called");

        for (int i = 0; i < paths.size(); i++) {
            if( path.startsWith(paths.get(i)) )
                return true;
        }
        return false;
    }

    public static List<String> scanImagePaths(ContentResolver contentResolver){
        Log.d(TAG, "scanPath called");

        String[] projection = {
                MediaStore.Images.Media.RELATIVE_PATH
        };
        Cursor cursor = contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null
        );
        int pathIndex = cursor.getColumnIndex(MediaStore.Images.Media.RELATIVE_PATH);

        List<String> pathList = new ArrayList<>();
        pathList.add("");

        while (cursor.moveToNext()) {
            String path = cursor.getString(pathIndex);
            String[] parts = path.split("/");
            String base = "";
            for (String part : parts) {
                base += part + "/";
                if (!pathList.contains(base))
                    pathList.add(base);
            }
        }

        Collections.sort(pathList);

        return pathList;
    }
}