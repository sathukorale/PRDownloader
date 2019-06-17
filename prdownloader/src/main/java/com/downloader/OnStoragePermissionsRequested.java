package com.downloader;

import org.jdeferred2.impl.DeferredObject;

public interface OnStoragePermissionsRequested
{
    void OnStoragePermissionRequested(DeferredObject reference, String pathToRoot);
}
