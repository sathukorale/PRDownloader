package com.downloader;

import com.downloader.request.DownloadRequest;

import org.jdeferred2.impl.DeferredObject;

public interface OnStoragePermissionsRequested
{
    void OnStoragePermissionRequested(DeferredObject reference, String pathToRoot);
}
