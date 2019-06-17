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

package com.downloader.utils;

import android.content.Context;
import android.os.Environment;
import android.support.v4.provider.DocumentFile;

import com.downloader.Constants;
import com.downloader.OnStoragePermissionsRequested;
import com.downloader.core.Core;
import com.downloader.database.DownloadModel;
import com.downloader.httpclient.HttpClient;
import com.downloader.internal.ComponentHolder;
import com.downloader.request.DownloadRequest;

import org.jdeferred2.DoneCallback;
import org.jdeferred2.Promise;
import org.jdeferred2.impl.DeferredObject;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by amitshekhar on 13/11/17.
 */

public final class Utils {

    private final static int MAX_REDIRECTION = 10;

    private Utils() {
        // no instance
    }

    public static String getPath(String dirPath, String fileName)
    {
        dirPath = normalizePath(dirPath);
        return dirPath + File.separator + fileName;
    }

    public static String getTempPath(String dirPath, String fileName) {
        return getPath(dirPath, fileName) + ".temp";
    }

    public static void renameFileName(String oldPath, String newPath) throws IOException {
        final File oldFile = new File(oldPath);
        try {
            final File newFile = new File(newPath);
            if (newFile.exists()) {
                if (!newFile.delete()) {
                    throw new IOException("Deletion Failed");
                }
            }
            if (!oldFile.renameTo(newFile)) {
                throw new IOException("Rename Failed");
            }
        } finally {
            if (oldFile.exists()) {
                //noinspection ResultOfMethodCallIgnored
                oldFile.delete();
            }
        }
    }

    public static void deleteTempFileAndDatabaseEntryInBackground(final DownloadRequest.DownloadDetails downloadDetails, final int downloadId) {
        Core.getInstance().getExecutorSupplier().forBackgroundTasks().execute(new Runnable()
        {
                    @Override
                    public void run()
                    {
                        ComponentHolder.getInstance().getDbHelper().remove(downloadId);

                        DeferredObject object = new DeferredObject();
                        Promise promise = object.promise();
                        OnStoragePermissionsRequested permissionsHandler = ComponentHolder.getInstance().getStoragePermissionsHandler();

                        try
                        {
                            object.done(new DoneCallback()
                            {
                                @Override
                                public void onDone(Object result)
                                {
                                    downloadDetails.removeFile((DocumentFile)result);
                                }
                            });
                            ;
                            permissionsHandler.OnStoragePermissionRequested(object, downloadDetails.getStorageRoot());
                            promise.waitSafely();
                        }
                        catch (InterruptedException e) { e.printStackTrace(); }
                    }
                });
    }

    public static void deleteUnwantedModelsAndTempFiles(final int days) {
        Core.getInstance().getExecutorSupplier().forBackgroundTasks()
                .execute(new Runnable() {
                    @Override
                    public void run() {
                        List<DownloadModel> models = ComponentHolder.getInstance()
                                .getDbHelper()
                                .getUnwantedModels(days);
                        if (models != null) {
                            for (DownloadModel model : models) {
                                ComponentHolder.getInstance().getDbHelper().remove(model.getId());

                                final DownloadRequest.DownloadDetails downloadDetails = new DownloadRequest.DownloadDetails(model.getDirPath(), model.getFileName(), "");
                                DeferredObject object = new DeferredObject();
                                Promise promise = object.promise();
                                OnStoragePermissionsRequested permissionsHandler = ComponentHolder.getInstance().getStoragePermissionsHandler();

                                try
                                {
                                    object.done(new DoneCallback()
                                    {
                                        @Override
                                        public void onDone(Object result)
                                        {
                                            downloadDetails.removeFile((DocumentFile)result);
                                        }
                                    });

                                    permissionsHandler.OnStoragePermissionRequested(object, downloadDetails.getStorageRoot());
                                    promise.waitSafely();
                                }
                                catch (InterruptedException e) { e.printStackTrace(); }
                            }
                        }
                    }
                });
    }

    public static int getUniqueId(String url, String dirPath, String fileName) {

        String string = url + File.separator + dirPath + File.separator + fileName;

        byte[] hash;

        try {
            hash = MessageDigest.getInstance("MD5").digest(string.getBytes("UTF-8"));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("NoSuchAlgorithmException", e);
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException("UnsupportedEncodingException", e);
        }

        StringBuilder hex = new StringBuilder(hash.length * 2);

        for (byte b : hash) {
            if ((b & 0xFF) < 0x10) hex.append("0");
            hex.append(Integer.toHexString(b & 0xFF));
        }

        return hex.toString().hashCode();

    }

    public static HttpClient getRedirectedConnectionIfAny(HttpClient httpClient,
                                                          DownloadRequest request)
            throws IOException, IllegalAccessException {
        int redirectTimes = 0;
        int code = httpClient.getResponseCode();
        String location = httpClient.getResponseHeader("Location");

        while (isRedirection(code)) {
            if (location == null) {
                throw new IllegalAccessException("Location is null");
            }
            httpClient.close();

            request.setUrl(location);
            httpClient = ComponentHolder.getInstance().getHttpClient();
            httpClient.connect(request);
            code = httpClient.getResponseCode();
            location = httpClient.getResponseHeader("Location");
            redirectTimes++;
            if (redirectTimes >= MAX_REDIRECTION) {
                throw new IllegalAccessException("Max redirection done");
            }
        }

        return httpClient;
    }

    private static boolean isRedirection(int code) {
        return code == HttpURLConnection.HTTP_MOVED_PERM
                || code == HttpURLConnection.HTTP_MOVED_TEMP
                || code == HttpURLConnection.HTTP_SEE_OTHER
                || code == HttpURLConnection.HTTP_MULT_CHOICE
                || code == Constants.HTTP_TEMPORARY_REDIRECT
                || code == Constants.HTTP_PERMANENT_REDIRECT;
    }

    /* This method will return the root directories of all the usable storage locations . The output paths does not end with the separator */
    public static String[] getAllStorageLocations(Context context)
    {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.KITKAT) throw new UnsupportedOperationException("This method cannot be called on Android systems prior KitKat (4.4)");

        File[] directories = context.getExternalFilesDirs(Environment.DIRECTORY_DCIM);
        List<String> rootDirectories = new ArrayList<>();

        for (File directory : directories)
        {
            String directoryPath = directory.getAbsolutePath();
            directoryPath = directoryPath.substring(0, directoryPath.indexOf("/Android/"));

            rootDirectories.add(directoryPath);
        }

        return rootDirectories.toArray(new String[0]);
    }

    public static String getCorrespondingStorageLocation(Context context, String path)
    {
        String[] rootDirectories = getAllStorageLocations(context);
        if (rootDirectories.length == 0) return null;

        // According to the documentation, any value after the first
        // denotes a removable storage path.
        for (int index = 0 ; index < rootDirectories.length ; index++)
        {
            String removableStorageLocation = rootDirectories[index];
            if (path.startsWith(removableStorageLocation)) return removableStorageLocation;
        }

        return null;
    }

    public static String normalizePath(String path)
    {
        path = path.trim();
        if (path.endsWith(File.separator))
            path = path.substring(0, path.length() - 1);

        return path;
    }
}
