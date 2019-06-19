/*
 *    Copyright (C) 2017 MINDORKS NEXTGEN PRIVATE LIMITED
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.sample;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.storage.StorageManager;
import android.os.storage.StorageVolume;
import android.support.v4.provider.DocumentFile;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.downloader.Error;
import com.downloader.OnCancelListener;
import com.downloader.OnDownloadListener;
import com.downloader.OnPauseListener;
import com.downloader.OnProgressListener;
import com.downloader.OnStartOrResumeListener;
import com.downloader.OnStoragePermissionsRequested;
import com.downloader.PRDownloader;
import com.downloader.PRDownloaderConfig;
import com.downloader.Progress;
import com.downloader.Status;
import com.downloader.request.DownloadRequest;
import com.sample.utils.Utils;

import org.jdeferred2.impl.DeferredObject;

import java.io.File;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

public class MainActivity extends AppCompatActivity implements OnStoragePermissionsRequested {

    private static String dirPath;

    final String URL1 = "";

    Button buttonOne, buttonCancelOne;
    TextView textViewProgressOne;
    ProgressBar progressBarOne;
    int downloadIdOne;

    private Map<String, DeferredObject> _pendingRequests = new HashMap<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try
        {
            PRDownloaderConfig config = PRDownloaderConfig.newBuilder().setDatabaseEnabled(true).setContext(this).setStoragePermissionsHandler(this).build();
            PRDownloader.initialize(this, config);
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        dirPath = Utils.getRootDirPath(getApplicationContext());

        init();

        onClickListenerOne();
    }

    private void init() {
        buttonOne = findViewById(R.id.buttonOne);
        buttonCancelOne = findViewById(R.id.buttonCancelOne);
        textViewProgressOne = findViewById(R.id.textViewProgressOne);
        progressBarOne = findViewById(R.id.progressBarOne);
    }

    public void onClickListenerOne() {
        buttonOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                if (Status.RUNNING == PRDownloader.getStatus(downloadIdOne)) {
                    PRDownloader.pause(downloadIdOne);
                    return;
                }

                buttonOne.setEnabled(false);
                progressBarOne.setIndeterminate(true);
                progressBarOne.getIndeterminateDrawable().setColorFilter(
                        Color.BLUE, android.graphics.PorterDuff.Mode.SRC_IN);

                if (Status.PAUSED == PRDownloader.getStatus(downloadIdOne)) {
                    PRDownloader.resume(downloadIdOne);
                    return;
                }

                downloadIdOne = PRDownloader.download(URL1, "/storage/80D9-DAD6/Download", "somefile.mp4", "video/mp4")
                        .build()
                        .setOnStartOrResumeListener(new OnStartOrResumeListener() {
                            @Override
                            public void onStartOrResume(final DownloadRequest request) {
                                progressBarOne.setIndeterminate(false);
                                buttonOne.setEnabled(true);
                                buttonOne.setText(R.string.pause);
                                buttonCancelOne.setEnabled(true);
                            }
                        })
                        .setOnPauseListener(new OnPauseListener() {
                            @Override
                            public void onPause(final DownloadRequest request) {
                                buttonOne.setText(R.string.resume);
                            }
                        })
                        .setOnCancelListener(new OnCancelListener() {
                            @Override
                            public void onCancel(final DownloadRequest request) {
                                buttonOne.setText(R.string.start);
                                buttonCancelOne.setEnabled(false);
                                progressBarOne.setProgress(0);
                                textViewProgressOne.setText("");
                                downloadIdOne = 0;
                                progressBarOne.setIndeterminate(false);
                            }
                        })
                        .setOnProgressListener(new OnProgressListener() {
                            @Override
                            public void onProgress(final DownloadRequest request, Progress progress) {
                                long progressPercent = progress.currentBytes * 100 / progress.totalBytes;
                                progressBarOne.setProgress((int) progressPercent);
                                textViewProgressOne.setText(Utils.getProgressDisplayLine(progress.currentBytes, progress.totalBytes));
                                progressBarOne.setIndeterminate(false);
                            }
                        })
                        .start(new OnDownloadListener() {
                            @Override
                            public void onDownloadComplete(final DownloadRequest request) {
                                buttonOne.setEnabled(false);
                                buttonCancelOne.setEnabled(false);
                                buttonOne.setText(R.string.completed);
                            }

                            @Override
                            public void onError(final DownloadRequest request, Error error) {
                                buttonOne.setText(R.string.start);
                                Toast.makeText(getApplicationContext(), getString(R.string.some_error_occurred) + " " + "1", Toast.LENGTH_SHORT).show();
                                textViewProgressOne.setText("");
                                progressBarOne.setProgress(0);
                                downloadIdOne = 0;
                                buttonCancelOne.setEnabled(false);
                                progressBarOne.setIndeterminate(false);
                                buttonOne.setEnabled(true);
                            }
                        });
            }
        });

        buttonCancelOne.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                PRDownloader.cancel(downloadIdOne);
            }
        });
    }

    @Override
    public void OnStoragePermissionRequested(final DeferredObject reference, final String pathToRoot)
    {
        runOnUiThread(new Runnable()
        {
            @Override
            public void run()
            {
                _pendingRequests.put(pathToRoot, reference);

                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N)
                {
                    File sdCard = new File(pathToRoot);
                    StorageManager storageManager = (StorageManager)getSystemService(Context.STORAGE_SERVICE);
                    StorageVolume storageVolume = storageManager.getStorageVolume(sdCard);
                    Intent intent = storageVolume.createAccessIntent(null);

                    startActivityForResult(intent, 1000);
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data)
    {
        for (String key : _pendingRequests.keySet())
        {
            DocumentFile file = DocumentFile.fromTreeUri(this, data.getData());
            _pendingRequests.get(key).resolve(file);
        }
    }
}

