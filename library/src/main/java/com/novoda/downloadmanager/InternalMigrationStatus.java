package com.novoda.downloadmanager;

interface InternalMigrationStatus extends MigrationStatus {

    void onSingleBatchMigrated();

    void markAsMigrating();

    void markAsComplete();

    InternalMigrationStatus copy();

}
