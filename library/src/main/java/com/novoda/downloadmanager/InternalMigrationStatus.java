package com.novoda.downloadmanager;

interface InternalMigrationStatus extends MigrationStatus {

    void onSingleBatchMigrated();

    void markAsMigrating();

    void markAsComplete();

    void markAsError(MigrationError.Error error);

    InternalMigrationStatus copy();

}
