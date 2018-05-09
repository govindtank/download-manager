package com.novoda.downloadmanager;

import android.support.annotation.Nullable;

public interface MigrationStatus {

    enum Status {

        EXTRACTING,
        MIGRATING_FILES,
        ERROR,
        COMPLETE;

        public String toRawValue() {
            return this.name();
        }

    }

    String migrationId();

    int numberOfMigratedBatches();

    int totalNumberOfBatchesToMigrate();

    int percentageMigrated();

    Status status();

    /**
     * @return null if {@link DownloadBatchStatus#status()} is not {@link DownloadBatchStatus.Status#ERROR}.
     */
    @Nullable
    MigrationError error();

}
