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

package com.downloader;

import android.content.Context;

import com.downloader.httpclient.DefaultHttpClient;
import com.downloader.httpclient.HttpClient;

/**
 * Created by amitshekhar on 13/11/17.
 */

public class PRDownloaderConfig {

    private int readTimeout;
    private int connectTimeout;
    private String userAgent;
    private HttpClient httpClient;
    private boolean databaseEnabled;
    private Context context;
    private OnStoragePermissionsRequested storagePermissionsHandler;

    private PRDownloaderConfig(Builder builder) {
        this.readTimeout = builder.readTimeout;
        this.connectTimeout = builder.connectTimeout;
        this.userAgent = builder.userAgent;
        this.httpClient = builder.httpClient;
        this.databaseEnabled = builder.databaseEnabled;
        this.context = builder.context;
        this.storagePermissionsHandler = builder.storagePermissionsHandler;
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
        return userAgent;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    public boolean isDatabaseEnabled() {
        return databaseEnabled;
    }

    public void setDatabaseEnabled(boolean databaseEnabled) {
        this.databaseEnabled = databaseEnabled;
    }

    public Context getContext() { return context; }

    public OnStoragePermissionsRequested getStoragePermissionsHandler() { return storagePermissionsHandler; }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        int readTimeout = Constants.DEFAULT_READ_TIMEOUT_IN_MILLS;
        int connectTimeout = Constants.DEFAULT_CONNECT_TIMEOUT_IN_MILLS;
        String userAgent = Constants.DEFAULT_USER_AGENT;
        HttpClient httpClient = new DefaultHttpClient();
        boolean databaseEnabled = false;
        Context context = null;
        OnStoragePermissionsRequested storagePermissionsHandler = null;

        public Builder setReadTimeout(int readTimeout) {
            this.readTimeout = readTimeout;
            return this;
        }

        public Builder setConnectTimeout(int connectTimeout) {
            this.connectTimeout = connectTimeout;
            return this;
        }

        public Builder setUserAgent(String userAgent) {
            this.userAgent = userAgent;
            return this;
        }

        public Builder setHttpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public Builder setDatabaseEnabled(boolean databaseEnabled) {
            this.databaseEnabled = databaseEnabled;
            return this;
        }

        public Builder setContext(Context context)
        {
            this.context = context;
            return this;
        }

        public Builder setStoragePermissionsHandler(OnStoragePermissionsRequested storagePermissionsHandler)
        {
            this.storagePermissionsHandler = storagePermissionsHandler;
            return this;
        }

        public PRDownloaderConfig build() throws Exception
        {
            if (this.context == null) throw new Exception("'Context' is a required parameter and should be set before calling this method.");
            if (this.storagePermissionsHandler == null) throw new Exception("'OnStoragePermissionsRequested' is a required parameter and should be set before calling this method.");

            return new PRDownloaderConfig(this);
        }
    }
}
