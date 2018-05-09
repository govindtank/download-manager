package com.novoda.downloadmanager;

public interface MigrationStatus {

    enum Status {

        EXTRACTING,
        MIGRATING_FILES,
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

}
