package com.downloader.internal.stream;

import android.support.v4.provider.DocumentFile;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;

public class FileDownloadRandomAccessFile implements FileDownloadOutputStream {

    private final BufferedOutputStream out;

    private FileDownloadRandomAccessFile(OutputStream stream) throws IOException {
        out = new BufferedOutputStream(stream);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    @Override
    public void flushAndSync() throws IOException {
        out.flush();
    }

    @Override
    public void close() throws IOException {
        out.close();
    }

    @Override
    public void seek(long offset) throws UnsupportedOperationException { throw new UnsupportedOperationException("This method is not supported and should not be called."); }

    @Override
    public void setLength(long totalBytes) throws UnsupportedOperationException { throw new UnsupportedOperationException("This method is not supported and should not be called."); }

    public static FileDownloadOutputStream create(OutputStream stream) throws IOException {
        return new FileDownloadRandomAccessFile(stream);
    }

}
