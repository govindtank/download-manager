package com.novoda.downloadmanager;

import android.support.annotation.Nullable;

class VersionOneToVersionTwoMigrationStatus implements InternalMigrationStatus {

    private static final int TOTAL_PERCENTAGE = 100;

    private final String migrationId;
    private final int totalNumberOfMigrations;

    private Status status;
    private int numberOfMigrationsCompleted;
    private Optional<MigrationError> migrationError;

    VersionOneToVersionTwoMigrationStatus(String migrationId,
                                          Status status,
                                          int numberOfMigrationsCompleted,
                                          int totalNumberOfMigrations,
                                          Optional<MigrationError> migrationError) {
        this.migrationId = migrationId;
        this.status = status;
        this.numberOfMigrationsCompleted = numberOfMigrationsCompleted;
        this.totalNumberOfMigrations = totalNumberOfMigrations;
        this.migrationError = migrationError;
    }

    @Override
    public void onSingleBatchMigrated() {
        numberOfMigrationsCompleted++;
    }

    @Override
    public void markAsMigrating() {
        status = Status.MIGRATING_FILES;
    }

    @Override
    public void markAsComplete() {
        status = Status.COMPLETE;
    }

    @Override
    public void markAsError(MigrationError.Error error) {
        status = Status.ERROR;
        this.migrationError = Optional.of(new MigrationError(error));
    }

    @Override
    public String migrationId() {
        return migrationId;
    }

    @Override
    public int numberOfMigratedBatches() {
        return numberOfMigrationsCompleted;
    }

    @Override
    public int totalNumberOfBatchesToMigrate() {
        return totalNumberOfMigrations;
    }

    @Override
    public int percentageMigrated() {
        return (int) ((((float) numberOfMigrationsCompleted) / ((float) totalNumberOfMigrations)) * TOTAL_PERCENTAGE);
    }

    @Override
    public Status status() {
        return status;
    }

    @Nullable
    @Override
    public MigrationError error() {
        if (migrationError.isPresent()) {
            return migrationError.get();
        } else {
            return null;
        }
    }

    @Override
    public InternalMigrationStatus copy() {
        return new VersionOneToVersionTwoMigrationStatus(
                migrationId,
                status,
                numberOfMigrationsCompleted,
                totalNumberOfMigrations,
                migrationError
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

        VersionOneToVersionTwoMigrationStatus that = (VersionOneToVersionTwoMigrationStatus) o;

        if (totalNumberOfMigrations != that.totalNumberOfMigrations) {
            return false;
        }
        if (numberOfMigrationsCompleted != that.numberOfMigrationsCompleted) {
            return false;
        }
        if (migrationId != null ? !migrationId.equals(that.migrationId) : that.migrationId != null) {
            return false;
        }
        if (status != that.status) {
            return false;
        }
        return migrationError != null ? migrationError.equals(that.migrationError) : that.migrationError == null;
    }

    @Override
    public int hashCode() {
        int result = migrationId != null ? migrationId.hashCode() : 0;
        result = 31 * result + totalNumberOfMigrations;
        result = 31 * result + (status != null ? status.hashCode() : 0);
        result = 31 * result + numberOfMigrationsCompleted;
        result = 31 * result + (migrationError != null ? migrationError.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "VersionOneToVersionTwoMigrationStatus{"
                + "migrationId='" + migrationId + '\''
                + ", totalNumberOfMigrations=" + totalNumberOfMigrations
                + ", status=" + status
                + ", numberOfMigrationsCompleted=" + numberOfMigrationsCompleted
                + ", migrationError=" + migrationError
                + '}';
    }
}
