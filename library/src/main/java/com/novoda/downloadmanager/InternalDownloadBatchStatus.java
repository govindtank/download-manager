package com.novoda.downloadmanager;

interface InternalDownloadBatchStatus extends DownloadBatchStatus {

    LiteDownloadBatchStatus update(long currentBytesDownloaded, long totalBatchSizeBytes);

    LiteDownloadBatchStatus markAsDownloading(DownloadsBatchStatusPersistence persistence);

    LiteDownloadBatchStatus markAsPaused(DownloadsBatchStatusPersistence persistence);

    LiteDownloadBatchStatus markAsQueued(DownloadsBatchStatusPersistence persistence);

    LiteDownloadBatchStatus markAsDeleted();

    LiteDownloadBatchStatus markAsError(Optional<DownloadError> downloadError, DownloadsBatchStatusPersistence persistence);

    LiteDownloadBatchStatus markAsDownloaded(DownloadsBatchStatusPersistence persistence);

    LiteDownloadBatchStatus markAsWaitingForNetwork(DownloadsBatchPersistence persistence);
}
