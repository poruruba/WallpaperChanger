package jp.poruru.product.wallpaperchanger;

import androidx.appcompat.app.AppCompatActivity;

import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.TableRow;
import android.widget.TextView;
import org.json.JSONException;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class PathSelectActivity extends AppCompatActivity {
    public static final String TAG = MainActivity.TAG;
    static final int PATH_TAB_SIZE = 20;
    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_path_select);

        prefs = getSharedPreferences("config", Context.MODE_PRIVATE);

        try{
            List<String> pathList = MainActivity.scanImagePaths(getContentResolver());

            List<String> list = PrefsUtils.loadPaths(prefs);
            ViewGroup vg = (ViewGroup) findViewById(R.id.table_path);
            for (int i = 0; i < pathList.size(); i++) {
                String path = pathList.get(i);
                View view;
                view = vg.getChildAt(i);
                if( view == null ) {
                    view = getLayoutInflater().inflate(R.layout.table_row, null);
                    vg.addView(view);
                }
                TextView text;
                text = (TextView) view.findViewById(R.id.textView);
                text.setText(path);
                CheckBox check;
                check = (CheckBox) view.findViewById(R.id.checkBox);
                check.setChecked(MainActivity.hasPath(list, path));
                check.setOnClickListener(new CustomClickListener(i, path));
                String[] t = path.split("/");
                view.setPadding(PATH_TAB_SIZE * t.length, 0, 0, 0);
                Button btn;
                btn = (Button)view.findViewById(R.id.button);
                btn.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        openSampleDialog(PathSelectActivity.this, new String[]{ path });
//                        List<MainActivity.ImageData> list = PrefsUtils.listImageData(getContentResolver(), new String[]{ path });
//                        AlertDialog.Builder builder = new AlertDialog.Builder(PathSelectActivity.this);
//                        builder.setNegativeButton("Close", null);
//                        try {
//                            final ImageView image = new ImageView(PathSelectActivity.this);
//                            image.setAdjustViewBounds(true);
//                            image.setOnClickListener(new View.OnClickListener() {
//                                @Override
//                                public void onClick(View view) {
//                                    try {
//                                        Bitmap bitmap = loadRandomBitmap(getContentResolver(), list);
//                                        image.setImageBitmap(bitmap);
//                                    }catch(Exception ex){
//                                        Log.e(TAG, ex.getMessage());
//                                    }
//                                }
//                            });
//                            builder.setView(image);
//                            final AlertDialog dialog = builder.create();
//                            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
//                            dialog.show();
//
//                            Bitmap bitmap = loadRandomBitmap(getContentResolver(), list);
//                            image.setImageBitmap(bitmap);
//                        }catch (Exception ex){
//                            Log.e(TAG, ex.getMessage());
//                        }
                    }
                });
            }
        }catch(Exception ex){
            Log.e(TAG, ex.getMessage());
        }
    }

//    static Bitmap loadRandomBitmap(ContentResolver contentResolver, List<MainActivity.ImageData> list) throws Exception{
//        int index = new Random().nextInt(list.size());
//        InputStream input = contentResolver.openInputStream(list.get(index).uri);
//        Bitmap bitmap = BitmapFactory.decodeStream(input);
//        input.close();
//        return bitmap;
//    }

    static void loadRandomBitmap(ContentResolver contentResolver, List<MainActivity.ImageData> list, ImageView image) throws Exception{
        int index = new Random().nextInt(list.size());
        image.setImageURI(list.get(index).uri);
    }


    public static void openSampleDialog(Context context, String[] paths){
        ContentResolver contentResolver = context.getContentResolver();
        List<MainActivity.ImageData> list = PrefsUtils.listImageData(contentResolver, paths);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setNegativeButton("閉じる", null);
        try {
            final ImageView image = new ImageView(context);
            image.setAdjustViewBounds(true);
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        loadRandomBitmap(contentResolver, list, image);
//                        Bitmap bitmap = loadRandomBitmap(contentResolver, list);
//                        image.setImageBitmap(bitmap);
                    }catch(Exception ex){
                        Log.e(TAG, ex.getMessage());
                    }
                }
            });
            builder.setView(image);
            final AlertDialog dialog = builder.create();
            dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
            dialog.show();

            loadRandomBitmap(contentResolver, list, image);
//            Bitmap bitmap = loadRandomBitmap(contentResolver, list);
//            image.setImageBitmap(bitmap);
        }catch (Exception ex){
            Log.e(TAG, ex.getMessage());
        }
    }

    class CustomClickListener implements View.OnClickListener {
        public int index;
        public String path;

        public CustomClickListener(int index, String path){
            this.index = index;
            this.path = path;
        }

        @Override
        public void onClick(View view) {
            CheckBox check = (CheckBox)view;
            boolean b = check.isChecked();
            Log.d(TAG, "onClick index=" + index + " path=" + path + " b=" + b);

            ViewGroup vg = (ViewGroup)findViewById(R.id.table_path);
            for( int i = 0 ; i < vg.getChildCount(); i++ ) {
                TableRow row = (TableRow)vg.getChildAt(i);
                CheckBox checkBox;
                checkBox = (CheckBox)row.getChildAt(1);
                TextView text;
                text = (TextView)row.getChildAt(0);
                String p = text.getText().toString();
                if( p.startsWith(path) )
                    checkBox.setChecked(b);
            }

            if( !b ){
                String[] _parts = path.split("/");
                String base = "";
                for( int i = 0 ; i < _parts.length - 1 ; i++ ){
                    base += _parts[i] + "/";
                    checkAll(base, b);
                }
            }

            try {
                List<String> list = extractPaths();
                PrefsUtils.savePaths(prefs, list);
            }catch(Exception ex){
                Log.e(TAG, ex.getMessage());
            }
        }

        private void checkAll(String path, boolean checked){
            ViewGroup vg = (ViewGroup)findViewById(R.id.table_path);
            for( int i = 0 ; i < vg.getChildCount(); i++ ) {
                TableRow row = (TableRow)vg.getChildAt(i);
                CheckBox checkBox;
                checkBox = (CheckBox)row.getChildAt(1);
                TextView text;
                text = (TextView)row.getChildAt(0);
                String _path = text.getText().toString();
                if( _path.equals(path) ) {
                    checkBox.setChecked(checked);
                    break;
                }
            }
        }
    }

    List<String> extractPaths() throws JSONException {
        Log.d(TAG, "extractPaths called");

        List<String> list = new ArrayList<>();
        ViewGroup vg = (ViewGroup)findViewById(R.id.table_path);
        for( int i = 0 ; i < vg.getChildCount(); i++ ) {
            TableRow row = (TableRow) vg.getChildAt(i);
            CheckBox checkBox;
            checkBox = (CheckBox) row.getChildAt(1);
            if( checkBox.isChecked() ) {
                TextView text;
                text = (TextView) row.getChildAt(0);
                String _path = text.getText().toString();
                list.add(_path);
            }
        }

        List<String> list2 = new ArrayList<>();
        if( list.contains("") ){
            list2.add("");
        }else {
            Iterator itr = list.iterator();
            while (itr.hasNext()) {
                String path = (String) itr.next();
                String[] s = path.split("/");
                String base = "";
                boolean found = false;
                for (int i = 0; i < s.length - 1; i++) {
                    base += s[i] + "/";
                    if (list.contains(base)) {
                        found = true;
                        break;
                    }
                }
                if (!found)
                    list2.add(path);
            }
        }
        return list2;
    }
}