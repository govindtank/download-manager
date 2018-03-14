package com.novoda.downloadmanager;

import android.support.annotation.Nullable;

class LiteDownloadBatchStatus implements InternalDownloadBatchStatus {

    private static final long ZERO_BYTES = 0;
    private static final int TOTAL_PERCENTAGE = 100;
    private static final boolean NOTIFICATION_NOT_SEEN = false;

    private final DownloadBatchTitle downloadBatchTitle;
    private final DownloadBatchId downloadBatchId;
    private final long downloadedDateTimeInMillis;
    private final Status status;
    private final boolean notificationSeen;
    private final long bytesDownloaded;
    private final long totalBatchSizeBytes;
    private final int percentageDownloaded;
    private final Optional<DownloadError> downloadError;

    LiteDownloadBatchStatus(DownloadBatchId downloadBatchId,
                            DownloadBatchTitle downloadBatchTitle,
                            long downloadedDateTimeInMillis,
                            Status status,
                            boolean notificationSeen,
                            long bytesDownloaded,
                            long totalBatchSizeBytes,
                            Optional<DownloadError> downloadError) {
        this.downloadBatchTitle = downloadBatchTitle;
        this.downloadBatchId = downloadBatchId;
        this.downloadedDateTimeInMillis = downloadedDateTimeInMillis;
        this.status = status;
        this.notificationSeen = notificationSeen;
        this.bytesDownloaded = bytesDownloaded;
        this.totalBatchSizeBytes = totalBatchSizeBytes;
        this.percentageDownloaded = getPercentageFrom(bytesDownloaded, totalBatchSizeBytes);
        this.downloadError = downloadError;
    }

    private int getPercentageFrom(long bytesDownloaded, long totalFileSizeBytes) {
        if (totalBatchSizeBytes <= ZERO_BYTES) {
            return 0;
        } else {
            return (int) ((((float) bytesDownloaded) / ((float) totalFileSizeBytes)) * TOTAL_PERCENTAGE);
        }
    }

    @Override
    public long bytesDownloaded() {
        return bytesDownloaded;
    }

    @Override
    public long bytesTotalSize() {
        return totalBatchSizeBytes;
    }

    @Override
    public LiteDownloadBatchStatus update(long currentBytesDownloaded, long totalBatchSizeBytes) {
        return copy(currentBytesDownloaded, totalBatchSizeBytes);
    }

    @Override
    public int percentageDownloaded() {
        return percentageDownloaded;
    }

    @Override
    public DownloadBatchId getDownloadBatchId() {
        return downloadBatchId;
    }

    @Override
    public DownloadBatchTitle getDownloadBatchTitle() {
        return downloadBatchTitle;
    }

    @Override
    public Status status() {
        return status;
    }

    @Override
    public long downloadedDateTimeInMillis() {
        return downloadedDateTimeInMillis;
    }

    @Override
    public LiteDownloadBatchStatus markAsDownloading(DownloadsBatchStatusPersistence persistence) {
        updateStatusAsync(Status.DOWNLOADING, persistence);
        return copy(Status.DOWNLOADING);
    }

    @Override
    public LiteDownloadBatchStatus markAsPaused(DownloadsBatchStatusPersistence persistence) {
        updateStatusAsync(Status.PAUSED, persistence);
        return copy(Status.PAUSED);
    }

    @Override
    public LiteDownloadBatchStatus markAsQueued(DownloadsBatchStatusPersistence persistence) {
        updateStatusAsync(Status.QUEUED, persistence);
        return copy(Status.QUEUED);
    }

    @Override
    public LiteDownloadBatchStatus markAsDeleted() {
        return copy(Status.DELETED, NOTIFICATION_NOT_SEEN);
    }

    @Override
    public LiteDownloadBatchStatus markAsError(Optional<DownloadError> downloadError, DownloadsBatchStatusPersistence persistence) {
        updateStatusAsync(status, persistence);
        return copy(Status.ERROR, downloadError);
    }

    @Override
    public LiteDownloadBatchStatus markAsDownloaded(DownloadsBatchStatusPersistence persistence) {
        updateStatusAsync(status, persistence);
        return copy(Status.DOWNLOADED);
    }

    @Override
    public LiteDownloadBatchStatus markAsWaitingForNetwork(DownloadsBatchPersistence persistence) {
        updateStatusAsync(status, persistence);
        return copy(Status.WAITING_FOR_NETWORK);
    }

    private void updateStatusAsync(Status status, DownloadsBatchStatusPersistence persistence) {
        persistence.updateStatusAsync(downloadBatchId, status);
    }

    @Nullable
    @Override
    public DownloadError.Error getDownloadErrorType() {
        if (downloadError.isPresent()) {
            return downloadError.get().error();
        } else {
            return null;
        }
    }

    @Override
    public boolean notificationSeen() {
        return notificationSeen;
    }

    private LiteDownloadBatchStatus copy(long currentBytesDownloaded, long totalBatchSizeBytes) {
        return new LiteDownloadBatchStatus(
                downloadBatchId,
                downloadBatchTitle,
                downloadedDateTimeInMillis,
                status,
                notificationSeen,
                currentBytesDownloaded,
                totalBatchSizeBytes,
                downloadError
        );
    }

    private LiteDownloadBatchStatus copy(Status status) {
        return new LiteDownloadBatchStatus(
                downloadBatchId,
                downloadBatchTitle,
                downloadedDateTimeInMillis,
                status,
                notificationSeen,
                bytesDownloaded,
                totalBatchSizeBytes,
                downloadError
        );
    }

    private LiteDownloadBatchStatus copy(Status status, boolean notificationSeen) {
        return new LiteDownloadBatchStatus(
                downloadBatchId,
                downloadBatchTitle,
                downloadedDateTimeInMillis,
                status,
                notificationSeen,
                bytesDownloaded,
                totalBatchSizeBytes,
                downloadError
        );
    }

    private LiteDownloadBatchStatus copy(Status status, Optional<DownloadError> downloadError) {
        return new LiteDownloadBatchStatus(
                downloadBatchId,
                downloadBatchTitle,
                downloadedDateTimeInMillis,
                status,
                notificationSeen,
                bytesDownloaded,
                totalBatchSizeBytes,
                downloadError
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        LiteDownloadBatchStatus that = (LiteDownloadBatchStatus) o;

        if (downloadedDateTimeInMillis != that.downloadedDateTimeInMillis) {
            return false;
        }
        if (bytesDownloaded != that.bytesDownloaded) {
            return false;
        }
        if (totalBatchSizeBytes != that.totalBatchSizeBytes) {
            return false;
        }
        if (percentageDownloaded != that.percentageDownloaded) {
            return false;
        }
        if (notificationSeen != that.notificationSeen) {
            return false;
        }
        if (downloadBatchTitle != null ? !downloadBatchTitle.equals(that.downloadBatchTitle) : that.downloadBatchTitle != null) {
            return false;
        }
        if (downloadBatchId != null ? !downloadBatchId.equals(that.downloadBatchId) : that.downloadBatchId != null) {
            return false;
        }
        if (status != that.status) {
            return false;
        }
        return downloadError != null ? downloadError.equals(that.downloadError) : that.downloadError == null;
    }

    @Override
    public int hashCode() {
        int result = downloadBatchTitle != null ? downloadBatchTitle.hashCode() : 0;
        result = 31 * result + (downloadBatchId != null ? downloadBatchId.hashCode() : 0);
        result = 31 * result + (int) (downloadedDateTimeInMillis ^ (downloadedDateTimeInMillis >>> 32));
        result = 31 * result + (int) (bytesDownloaded ^ (bytesDownloaded >>> 32));
        result = 31 * result + (int) (totalBatchSizeBytes ^ (totalBatchSizeBytes >>> 32));
        result = 31 * result + percentageDownloaded;
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + (notificationSeen ? 1 : 0);
        result = 31 * result + (downloadError != null ? downloadError.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "LiteDownloadBatchStatus{"
                + "downloadBatchTitle=" + downloadBatchTitle
                + ", downloadBatchId=" + downloadBatchId
                + ", downloadedDateTimeInMillis=" + downloadedDateTimeInMillis
                + ", bytesDownloaded=" + bytesDownloaded
                + ", totalBatchSizeBytes=" + totalBatchSizeBytes
                + ", percentageDownloaded=" + percentageDownloaded
                + ", status=" + status
                + ", notificationSeen=" + notificationSeen
                + ", downloadError=" + downloadError
                + '}';
    }
}
