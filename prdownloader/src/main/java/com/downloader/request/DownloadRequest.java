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

package com.downloader.request;

import android.content.ContentResolver;
import android.content.Context;
import android.os.Build;
import android.os.ParcelFileDescriptor;
import android.support.v4.provider.DocumentFile;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;
import android.util.Log;

import com.downloader.Error;
import com.downloader.OnCancelListener;
import com.downloader.OnDownloadListener;
import com.downloader.OnPauseListener;
import com.downloader.OnProgressListener;
import com.downloader.OnStartOrResumeListener;
import com.downloader.OnStoragePermissionsRequested;
import com.downloader.Priority;
import com.downloader.Response;
import com.downloader.Status;
import com.downloader.core.Core;
import com.downloader.internal.ComponentHolder;
import com.downloader.internal.DownloadRequestQueue;
import com.downloader.internal.SynchronousCall;
import com.downloader.utils.Utils;

import org.jdeferred2.impl.DefaultDeferredManager;
import org.slf4j.helpers.Util;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.FileChannel;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Future;

/**
 * Created by amitshekhar on 13/11/17.
 */

public class DownloadRequest
{
    public static class DownloadDetails
    {
        private String _parentDirectory;
        private String _mimeType;
        private String _fileName;

        public DownloadDetails(String parentDirectory, String fileName, String mimeType)
        {
            _parentDirectory = Utils.normalizePath(parentDirectory);
            _fileName = fileName;
            _mimeType = mimeType;
        }

        public String getParentDirectory() { return _parentDirectory; }

        public String getFileName() { return _fileName; }

        public String getMimeType() { return _mimeType; }

        public String getStorageRoot()
        {
            String absolutePath = Utils.getPath(_parentDirectory, _fileName);
            String externalStorageLocation = Utils.getCorrespondingStorageLocation(ComponentHolder.getInstance().getContext(), absolutePath);
            return externalStorageLocation;
        }

        public boolean doesFileExist(DocumentFile rootDirectory)
        {
            return getFile(rootDirectory) != null;
        }

        public DocumentFile getFile(DocumentFile rootDirectory)
        {
            String absolutePath = Utils.getPath(_parentDirectory, _fileName);
            String externalStorageLocation = Utils.getCorrespondingStorageLocation(ComponentHolder.getInstance().getContext(), absolutePath);
            if (externalStorageLocation == null) return null;

            String[] segments = absolutePath.substring(externalStorageLocation.length() + 1).split(File.separator);
            DocumentFile parentDirectory = rootDirectory;

            for (String segment : segments)
            {
                parentDirectory = parentDirectory.findFile(segment);
                if (parentDirectory == null) return null;
            }

            return parentDirectory;
        }

        public DocumentFile createFile(DocumentFile rootDirectory)
        {
            String externalStorageLocation = Utils.getCorrespondingStorageLocation(ComponentHolder.getInstance().getContext(), _parentDirectory);
            if (externalStorageLocation == null) return null;

            String[] segments = _parentDirectory.substring(externalStorageLocation.length() + 1).split(File.separator);
            DocumentFile parentDirectory = rootDirectory;

            for (String segment : segments)
            {
                DocumentFile foundDirectory = parentDirectory.findFile(segment);
                if (foundDirectory == null) foundDirectory = parentDirectory.createDirectory(segment);

                parentDirectory = foundDirectory;
            }

            parentDirectory = parentDirectory.createFile(_mimeType, _fileName);
            return parentDirectory;
        }

        public void removeFile(DocumentFile rootDirectory)
        {
            DocumentFile file = getFile(rootDirectory);
            if (file != null) file.delete();
        }

        public DocumentFile findOrCreateFile(DocumentFile rootDirectory)
        {
            DocumentFile file = getFile(rootDirectory);
            if (file == null) file = createFile(rootDirectory);

            return file;
        }

        public OutputStream createOutputStream(DocumentFile rootDirectory) throws FileNotFoundException
        {
            DocumentFile file = findOrCreateFile(rootDirectory);
            return ComponentHolder.getInstance().getContext().getContentResolver().openOutputStream(file.getUri());
        }

        public OutputStream createOutputStream(DocumentFile rootDirectory, long offset) throws IOException
        {
            DocumentFile file = findOrCreateFile(rootDirectory);
            Context context = ComponentHolder.getInstance().getContext();
            ParcelFileDescriptor pfd = context.getContentResolver().openFileDescriptor(file.getUri(), "rw");
            FileDescriptor fd = pfd.getFileDescriptor();

            FileOutputStream stream = new FileOutputStream(fd);
            FileChannel channel = stream.getChannel();

            channel.position(offset);

            return stream;
        }
    }

    private Priority priority;
    private Object tag;
    private String url;
    private DownloadDetails downloadDetails;
    private int sequenceNumber;
    private Future future;
    private long downloadedBytes;
    private long totalBytes;
    private int readTimeout;
    private int connectTimeout;
    private String userAgent;
    private OnProgressListener onProgressListener;
    private OnDownloadListener onDownloadListener;
    private OnStartOrResumeListener onStartOrResumeListener;
    private OnPauseListener onPauseListener;
    private OnCancelListener onCancelListener;
    private int downloadId;
    private HashMap<String, List<String>> headerMap;
    private Status status;

    DownloadRequest(DownloadRequestBuilder builder) {
        this.url = builder.url;
        this.downloadDetails = builder.downloadDetails;
        this.headerMap = builder.headerMap;
        this.priority = builder.priority;
        this.tag = builder.tag;
        this.readTimeout =
                builder.readTimeout != 0 ?
                        builder.readTimeout :
                        getReadTimeoutFromConfig();
        this.connectTimeout =
                builder.connectTimeout != 0 ?
                        builder.connectTimeout :
                        getConnectTimeoutFromConfig();
        this.userAgent = builder.userAgent;
    }

    public Priority getPriority() {
        return priority;
    }

    public void setPriority(Priority priority) {
        this.priority = priority;
    }

    public Object getTag() {
        return tag;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public DownloadDetails getDownloadDetails() { return this.downloadDetails; }

    public int getSequenceNumber() {
        return sequenceNumber;
    }

    public void setSequenceNumber(int sequenceNumber) {
        this.sequenceNumber = sequenceNumber;
    }

    public HashMap<String, List<String>> getHeaders() {
        return headerMap;
    }

    public Future getFuture() {
        return future;
    }

    public void setFuture(Future future) {
        this.future = future;
    }

    public long getDownloadedBytes() {
        return downloadedBytes;
    }

    public void setDownloadedBytes(long downloadedBytes) {
        this.downloadedBytes = downloadedBytes;
    }

    public long getTotalBytes() {
        return totalBytes;
    }

    public void setTotalBytes(long totalBytes) {
        this.totalBytes = totalBytes;
    }

    public int getReadTimeout() {
        return readTimeout;
    }

    public void setReadTimeout(int readTimeout) {
        this.readTimeout = readTimeout;
    }

    public int getConnectTimeout() {
        return connectTimeout;
    }

    public void setConnectTimeout(int connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    public String getUserAgent() {
        if (userAgent == null) {
            userAgent = ComponentHolder.getInstance().getUserAgent();
        }
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public int getDownloadId() {
        return downloadId;
    }

    public void setDownloadId(int downloadId) {
        this.downloadId = downloadId;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public OnProgressListener getOnProgressListener() {
        return onProgressListener;
    }

    public DownloadRequest setOnStartOrResumeListener(OnStartOrResumeListener onStartOrResumeListener) {
        this.onStartOrResumeListener = onStartOrResumeListener;
        return this;
    }

    public DownloadRequest setOnProgressListener(OnProgressListener onProgressListener) {
        this.onProgressListener = onProgressListener;
        return this;
    }

    public DownloadRequest setOnPauseListener(OnPauseListener onPauseListener) {
        this.onPauseListener = onPauseListener;
        return this;
    }

    public DownloadRequest setOnCancelListener(OnCancelListener onCancelListener) {
        this.onCancelListener = onCancelListener;
        return this;
    }

    public int start(OnDownloadListener onDownloadListener) {
        this.onDownloadListener = onDownloadListener;
        downloadId = Utils.getUniqueId(url, downloadDetails.getParentDirectory(), downloadDetails.getFileName());
        DownloadRequestQueue.getInstance().addRequest(this);
        return downloadId;
    }

    public Response executeSync() {
        downloadId = Utils.getUniqueId(url, downloadDetails.getParentDirectory(), downloadDetails.getFileName());
        return new SynchronousCall(this).execute();
    }

    public void deliverError(final Error error) {
        if (status != Status.CANCELLED) {
            setStatus(Status.FAILED);
            Core.getInstance().getExecutorSupplier().forMainThreadTasks()
                    .execute(new Runnable() {
                        public void run() {
                            if (onDownloadListener != null) {
                                onDownloadListener.onError(DownloadRequest.this, error);
                            }
                            finish();
                        }
                    });
        }
    }

    public void deliverSuccess() {
        if (status != Status.CANCELLED) {
            setStatus(Status.COMPLETED);
            Core.getInstance().getExecutorSupplier().forMainThreadTasks()
                    .execute(new Runnable() {
                        public void run() {
                            if (onDownloadListener != null) {
                                onDownloadListener.onDownloadComplete(DownloadRequest.this);
                            }
                            finish();
                        }
                    });
        }
    }

    public void deliverStartEvent() {
        if (status != Status.CANCELLED) {
            Core.getInstance().getExecutorSupplier().forMainThreadTasks()
                    .execute(new Runnable() {
                        public void run() {
                            if (onStartOrResumeListener != null) {
                                onStartOrResumeListener.onStartOrResume(DownloadRequest.this);
                            }
                        }
                    });
        }
    }

    public void deliverPauseEvent() {
        if (status != Status.CANCELLED) {
            Core.getInstance().getExecutorSupplier().forMainThreadTasks()
                    .execute(new Runnable() {
                        public void run() {
                            if (onPauseListener != null) {
                                onPauseListener.onPause(DownloadRequest.this);
                            }
                        }
                    });
        }
    }

    private void deliverCancelEvent() {
        Core.getInstance().getExecutorSupplier().forMainThreadTasks()
                .execute(new Runnable() {
                    public void run() {
                        if (onCancelListener != null) {
                            onCancelListener.onCancel(DownloadRequest.this);
                        }
                    }
                });
    }

    public void cancel() {
        status = Status.CANCELLED;
        if (future != null) {
            future.cancel(true);
        }

        deliverCancelEvent();
        Utils.deleteTempFileAndDatabaseEntryInBackground(downloadDetails, downloadId);
    }

    private void finish() {
        destroy();
        DownloadRequestQueue.getInstance().finish(this);
    }

    private void destroy() {
        this.onProgressListener = null;
        this.onDownloadListener = null;
        this.onStartOrResumeListener = null;
        this.onPauseListener = null;
        this.onCancelListener = null;
    }

    private int getReadTimeoutFromConfig() {
        return ComponentHolder.getInstance().getReadTimeout();
    }

    private int getConnectTimeoutFromConfig() {
        return ComponentHolder.getInstance().getConnectTimeout();
    }

}
