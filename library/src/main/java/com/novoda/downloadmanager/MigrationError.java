package com.novoda.downloadmanager;

public class MigrationError {

    public enum Error {
        MIGRATING_V1_FILES_TO_V2_LOCATION,
        DELETING_V1_FILES
    }

    private final Error error;

    MigrationError(Error error) {
        this.error = error;
    }

    public Error error() {
        return error;
    }
}
