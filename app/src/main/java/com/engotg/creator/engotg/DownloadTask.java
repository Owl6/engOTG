package com.engotg.creator.engotg;
import android.content.Context;
import android.os.AsyncTask;
import android.os.Environment;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.net.ssl.HttpsURLConnection;

import io.paperdb.Paper;

public class DownloadTask {
    private static final String TAG = "Download Task";
    private Button topic_1, topic_2, topic_3;
    private Context context;
    private static ProgressBar bar;
    private static TextView loadingText;
    private final String topics[] = {"Forces Moments", "Forces Moments", "Internal External Forces", "Internal External Forces"
            , "Internal Forces Stresses", "Internal Forces Stresses"};
    private final String testTopics[] = {"Forces Moments", "Internal External Forces", "Internal Forces Stresses"};
    private final String subTestTopipcs[] = {"Choices", "Questions", "Answer", "Explanations"};
    private final String subTopics[] = {"Forces", "Moments", "External Forces", "Internal Forces", "Internal Forces", "Internal Stresses"};
    private String version;
    private ArrayList<HashMap<String, String>> audioList;

    private final String downloadDirectory = "EngOTG_data";

    public DownloadTask(Context context, Button
            topic_1, Button topic_2, Button topic_3, ProgressBar bar,
                        final TextView loadingText, String serverVer){
        Paper.init(context);
        Paper.book().destroy();
        this.context = context;
        this.topic_1 = topic_1;
        this.topic_2 = topic_2;
        this.topic_3 = topic_3;
        this.bar = bar;
        this.loadingText = loadingText;
        version = serverVer;
        audioList = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            audioList.add(new LinkedHashMap<String, String>());
        }

        // Gets audio list from database
        FirebaseDatabase database = FirebaseDatabase.getInstance();
        DatabaseReference reference = database.getReference();
        reference.addListenerForSingleValueEvent(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                loadingText.setText("Getting audio list...");
                for (int i = 0; i < audioList.size(); i++) {
                    for (DataSnapshot iter : dataSnapshot.child("audios").child(topics[i]).child(subTopics[i]).getChildren()) {
                        audioList.get(i).put(iter.getKey(), iter.getValue().toString());
                    }
                }
                loadingText.setText("Getting test material...");
                for (int i = 0; i < testTopics.length; i++) { // Get each test topics (3 total)
                    for (int j = 1; j <= 5; j++) { // Get each test sets (5 total)
                        for (int k = 0; k < subTestTopipcs.length; k++) {
                            for (DataSnapshot iter : dataSnapshot.child("tests").child(testTopics[i]).child("set " + j).child(subTestTopipcs[k]).getChildren()) {
                                String dest = testTopics[i] + "|" + "set " + j + "|" + subTestTopipcs[k] + "|" + iter.getKey();
                                if (k == 0) { // Choices
                                    ArrayList<String> arr = (ArrayList<String>) iter.getValue();
                                    Set<String> set = new HashSet<>(arr);
                                    Paper.book().write(dest, set);
                                } else { // Questions, Answers, Explanations
                                    for (DataSnapshot inner : iter.getChildren()) {
                                        Paper.book().write(dest, inner.getValue().toString());
                                    }
                                }
                            }
                        }

                    }
                }
                // Runs download tasks after database query completes
                new DownloadingTask().execute();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError databaseError) {
                Log.e("Database Error", databaseError.getDetails());
            }
        });
    }

    private class DownloadingTask extends AsyncTask<Void, Void, Void>{
        File apkStorage = new File(context.getFilesDir() + "/"
                + downloadDirectory);
        File outputFile = null;
        FileOutputStream fos = null;
        InputStream is = null;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            loadingText.setText("Downloading audio files...");
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            if(outputFile != null){
                Paper.book().write("version", version);
                loadingText.setText("Update success.");
                Log.d(TAG, "Download success.");
            } else {
                loadingText.setText("Update failed. Please retry.");
                Log.e(TAG, "Download failed.");
            }
            topic_1.setEnabled(true);
            topic_2.setEnabled(true);
            topic_3.setEnabled(true);
            topic_1.setTextColor(0xFF5E6762);
            topic_2.setTextColor(0xFF5E6762);
            topic_3.setTextColor(0xFF5E6762);
            bar.setVisibility(View.GONE);
            super.onPostExecute(aVoid);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            for (int i = 0; i < audioList.size(); i++) {
                try{
                    Iterator it = audioList.get(i).entrySet().iterator();
                    while (it.hasNext()) {
                        Map.Entry pair = (Map.Entry) it.next();
                        URL url = new URL(pair.getValue().toString());
                        HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                        conn.setRequestMethod("GET");
                        conn.connect();

                        // Connection response is not OK show logs
                        if(conn.getResponseCode() != HttpsURLConnection.HTTP_OK){
                            Log.e(TAG, "Server returned HTTP " + conn.getResponseCode() + " " + conn.getResponseMessage());
                        }

                        // Setting internal storage path
                        apkStorage = new File(context.getFilesDir() + "/"
                                + downloadDirectory + "/" + topics[i] + "/" + subTopics[i] + "/");

                        // If file is not present, create directory
                        if(!apkStorage.exists()){
                            apkStorage.mkdirs();
                            Log.d(TAG, "Directory created.");
                            Log.d(TAG, apkStorage.getAbsolutePath());
                        }
                        // Create output file in main file
                        outputFile = new File(apkStorage, pair.getKey().toString() + ".mp3");
                        // Create new file if not present
                        if(!outputFile.exists() || outputFile.length() == 0){
                            outputFile.createNewFile();

                            fos = new FileOutputStream(outputFile);

                            is = conn.getInputStream();
                            byte[] buffer = new byte[1024];
                            int len = 0;
                            while ((len = is.read(buffer)) != -1){
                                fos.write(buffer, 0, len); // write new file
                            }
                            fos.close();
                            is.close();
                            Log.d(TAG, "File created.");
                        } else {
                            Log.e(TAG, "File already exists.");
                        }
                    }
                    // Delete files not found on database
                    List<String> files = new ArrayList<>(Arrays.asList(apkStorage.list()));
                    String str;
                    File file;
                    for (int j = 0; j < files.size(); j++) {
                        str = files.get(j).replaceFirst("[.][^.]+$", "");
                        if(!audioList.get(i).containsKey(str)){
                            file = new File(apkStorage, files.get(j));
                            if(file.delete()){
                                Log.d(TAG, "File deleted.");
                            } else {
                                Log.e(TAG, "Failed to delete file");
                            }
                        }
                    }
                } catch (Exception e){
                    e.printStackTrace();
                    Log.e(TAG, "Download Error Exception: " + e.getMessage());
                }
            }
            return null;
        }


    }
}
