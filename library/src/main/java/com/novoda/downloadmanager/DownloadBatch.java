package com.novoda.downloadmanager;

import android.support.annotation.Nullable;

import com.novoda.notils.logger.simple.Log;

import java.util.List;
import java.util.Map;

import static com.novoda.downloadmanager.DownloadBatchStatus.Status.DELETED;
import static com.novoda.downloadmanager.DownloadBatchStatus.Status.DOWNLOADED;
import static com.novoda.downloadmanager.DownloadBatchStatus.Status.DOWNLOADING;
import static com.novoda.downloadmanager.DownloadBatchStatus.Status.ERROR;
import static com.novoda.downloadmanager.DownloadBatchStatus.Status.PAUSED;
import static com.novoda.downloadmanager.DownloadBatchStatus.Status.QUEUED;
import static com.novoda.downloadmanager.DownloadBatchStatus.Status.WAITING_FOR_NETWORK;

// This model knows how to interact with low level components.
@SuppressWarnings({"PMD.CyclomaticComplexity", "PMD.StdCyclomaticComplexity", "PMD.ModifiedCyclomaticComplexity"})
class DownloadBatch {

    private static final int ZERO_BYTES = 0;

    private final Map<DownloadFileId, Long> fileBytesDownloadedMap;
    private final List<DownloadFile> downloadFiles;
    private final DownloadsBatchPersistence downloadsBatchPersistence;
    private final CallbackThrottle callbackThrottle;
    private final ConnectionChecker connectionChecker;

    private long totalBatchSizeBytes;
    private InternalDownloadBatchStatus downloadBatchStatus;
    private DownloadBatchStatusCallback callback;

    DownloadBatch(InternalDownloadBatchStatus internalDownloadBatchStatus,
                  List<DownloadFile> downloadFiles,
                  Map<DownloadFileId, Long> fileBytesDownloadedMap,
                  DownloadsBatchPersistence downloadsBatchPersistence,
                  CallbackThrottle callbackThrottle,
                  ConnectionChecker connectionChecker) {
        this.downloadFiles = downloadFiles;
        this.fileBytesDownloadedMap = fileBytesDownloadedMap;
        this.downloadBatchStatus = internalDownloadBatchStatus;
        this.downloadsBatchPersistence = downloadsBatchPersistence;
        this.callbackThrottle = callbackThrottle;
        this.connectionChecker = connectionChecker;
    }

    void setCallback(DownloadBatchStatusCallback callback) {
        this.callback = callback;
        callbackThrottle.setCallback(callback);
    }

    void download() {
        Log.v("start download batch " + downloadBatchStatus.getDownloadBatchId().rawId());
        DownloadBatchStatus.Status status = downloadBatchStatus.status();

        if (status == DELETED) {
            deleteBatchIfNeeded();
            notifyCallback(downloadBatchStatus);
            Log.v("abort deleted download batch " + downloadBatchStatus.getDownloadBatchId().rawId());
            return;
        }

        if (status == PAUSED) {
            notifyCallback(downloadBatchStatus);
            Log.v("abort paused download batch " + downloadBatchStatus.getDownloadBatchId().rawId());
            return;
        }

        if (connectionNotAllowedForDownload(status)) {
            processNetworkError();
            notifyCallback(downloadBatchStatus);
            Log.v("abort not allowed download batch " + downloadBatchStatus.getDownloadBatchId().rawId());
            return;
        }

        if (status != DOWNLOADED) {
            downloadBatchStatus = downloadBatchStatus.markAsDownloading(downloadsBatchPersistence);
            notifyCallback(downloadBatchStatus);
        }

        if (totalBatchSizeBytes == 0) {
            totalBatchSizeBytes = getTotalSize(downloadFiles);
        }

        if (totalBatchSizeBytes <= ZERO_BYTES) {
            deleteBatchIfNeeded();
            processNetworkError();
            notifyCallback(downloadBatchStatus);
            Log.v("abort total size is zero download batch " + downloadBatchStatus.getDownloadBatchId().rawId());
            return;
        }

        for (DownloadFile downloadFile : downloadFiles) {
            if (connectionNotAllowedForDownload(status)) {
                downloadBatchStatus = downloadBatchStatus.markAsWaitingForNetwork(downloadsBatchPersistence);
                notifyCallback(downloadBatchStatus);
                break;
            }
            downloadFile.download(fileDownloadCallback);
            if (batchCannotContinue()) {
                break;
            }
        }

        if (networkError()) {
            processNetworkError();
        }

        notifyCallback(downloadBatchStatus);
        deleteBatchIfNeeded();
        callbackThrottle.stopUpdates();
        Log.v("end download batch " + downloadBatchStatus.getDownloadBatchId().rawId());
    }

    private void deleteBatchIfNeeded() {
        if (downloadBatchStatus.status() == DELETED) {
            downloadsBatchPersistence.deleteAsync(downloadBatchStatus.getDownloadBatchId());
        }
    }

    private void processNetworkError() {
        if (downloadBatchStatus.status() == DELETED) {
            return;
        }
        downloadBatchStatus = downloadBatchStatus.markAsWaitingForNetwork(downloadsBatchPersistence);
        notifyCallback(downloadBatchStatus);
        DownloadsNetworkRecoveryCreator.getInstance().scheduleRecovery();
    }

    private boolean connectionNotAllowedForDownload(DownloadBatchStatus.Status status) {
        return !connectionChecker.isAllowedToDownload() && status != DOWNLOADED;
    }

    private final DownloadFile.Callback fileDownloadCallback = new DownloadFile.Callback() {
        @Override
        public void onUpdate(InternalDownloadFileStatus downloadFileStatus) {
            fileBytesDownloadedMap.put(downloadFileStatus.downloadFileId(), downloadFileStatus.bytesDownloaded());
            long currentBytesDownloaded = getBytesDownloadedFrom(fileBytesDownloadedMap);
            downloadBatchStatus = downloadBatchStatus.update(currentBytesDownloaded, totalBatchSizeBytes);

            if (currentBytesDownloaded == totalBatchSizeBytes && totalBatchSizeBytes != ZERO_BYTES) {
                downloadBatchStatus = downloadBatchStatus.markAsDownloaded(downloadsBatchPersistence);
            }

            if (downloadFileStatus.isMarkedAsError()) {
                downloadBatchStatus = downloadBatchStatus.markAsError(downloadFileStatus.error(), downloadsBatchPersistence);
            }

            if (downloadFileStatus.isMarkedAsWaitingForNetwork()) {
                downloadBatchStatus = downloadBatchStatus.markAsWaitingForNetwork(downloadsBatchPersistence);
            }

            callbackThrottle.update(downloadBatchStatus);
        }
    };

    private boolean networkError() {
        DownloadBatchStatus.Status status = downloadBatchStatus.status();
        if (status == WAITING_FOR_NETWORK) {
            return true;
        } else if (status == ERROR) {
            DownloadError.Error downloadErrorType = downloadBatchStatus.getDownloadErrorType();
            if (downloadErrorType == DownloadError.Error.NETWORK_ERROR_CANNOT_DOWNLOAD_FILE) {
                return true;
            }
        }
        return false;
    }

    private boolean batchCannotContinue() {
        DownloadBatchStatus.Status status = downloadBatchStatus.status();
        return status == ERROR || status == DELETED || status == PAUSED || status == WAITING_FOR_NETWORK;
    }

    private long getBytesDownloadedFrom(Map<DownloadFileId, Long> fileBytesDownloadedMap) {
        long bytesDownloaded = 0;
        for (Map.Entry<DownloadFileId, Long> entry : fileBytesDownloadedMap.entrySet()) {
            bytesDownloaded += entry.getValue();
        }
        return bytesDownloaded;
    }

    private void notifyCallback(InternalDownloadBatchStatus downloadBatchStatus) {
        if (callback != null) {
            callback.onUpdate(downloadBatchStatus);
        }
    }

    private long getTotalSize(List<DownloadFile> downloadFiles) {
        long totalBatchSize = 0;
        for (DownloadFile downloadFile : downloadFiles) {
            if (downloadBatchStatus.status() == DELETED) {
                return 0;
            }
            Log.v("DownloadBatch.getTotalSize() " + downloadBatchStatus.getDownloadBatchId().rawId() + ", status: " + downloadBatchStatus.status());
            long totalFileSize = downloadFile.getTotalSize();
            if (totalFileSize == 0) {
                return 0;
            }

            totalBatchSize += totalFileSize;
        }
        return totalBatchSize;
    }

    void pause() {
        DownloadBatchStatus.Status status = downloadBatchStatus.status();
        if (status == PAUSED || status == DOWNLOADED) {
            return;
        }
        downloadBatchStatus = downloadBatchStatus.markAsPaused(downloadsBatchPersistence);
        notifyCallback(downloadBatchStatus);

        for (DownloadFile downloadFile : downloadFiles) {
            downloadFile.pause();
        }
    }

    void waitForNetwork() {
        DownloadBatchStatus.Status status = downloadBatchStatus.status();
        if (status != DOWNLOADING) {
            return;
        }

        for (DownloadFile downloadFile : downloadFiles) {
            downloadFile.waitForNetwork();
        }
    }

    void resume() {
        DownloadBatchStatus.Status status = downloadBatchStatus.status();
        if (status == QUEUED || status == DOWNLOADING || status == DOWNLOADED) {
            return;
        }
        downloadBatchStatus = downloadBatchStatus.markAsQueued(downloadsBatchPersistence);
        notifyCallback(downloadBatchStatus);
        for (DownloadFile downloadFile : downloadFiles) {
            downloadFile.resume();
        }
    }

    void delete() {
        if (downloadBatchStatus.status() == PAUSED) {
            downloadsBatchPersistence.deleteAsync(downloadBatchStatus.getDownloadBatchId());
        }

        downloadBatchStatus = downloadBatchStatus.markAsDeleted();
        notifyCallback(downloadBatchStatus);
        for (DownloadFile downloadFile : downloadFiles) {
            downloadFile.delete();
        }
    }

    DownloadBatchId getId() {
        return downloadBatchStatus.getDownloadBatchId();
    }

    InternalDownloadBatchStatus status() {
        return downloadBatchStatus;
    }

    @Nullable
    DownloadFileStatus downloadFileStatusWith(DownloadFileId downloadFileId) {
        for (DownloadFile downloadFile : downloadFiles) {
            if (downloadFile.matches(downloadFileId)) {
                return downloadFile.fileStatus();
            }
        }
        return null;
    }

    void persistAsync() {
        downloadsBatchPersistence.persistAsync(
                downloadBatchStatus.getDownloadBatchTitle(),
                downloadBatchStatus.getDownloadBatchId(),
                downloadBatchStatus.status(),
                downloadFiles,
                downloadBatchStatus.downloadedDateTimeInMillis(),
                downloadBatchStatus.notificationSeen()
        );
    }

    public void setStatus(InternalDownloadBatchStatus downloadBatchStatus) {
        this.downloadBatchStatus = downloadBatchStatus;
    }
}
