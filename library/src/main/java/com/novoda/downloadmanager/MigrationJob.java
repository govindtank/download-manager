package com.novoda.downloadmanager;

import android.content.Context;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

class MigrationJob {

    private static final int NO_COMPLETED_MIGRATIONS = 0;
    private static final int RANDOMLY_CHOSEN_BUFFER_SIZE_THAT_SEEMS_TO_WORK = 4096;
    private static final float TEN_PERCENT = 0.1f;

    private final Context context;
    private final String jobIdentifier;
    private final List<Migration> partialMigrations;
    private final List<Migration> completeMigrations;
    private final List<MigrationCallback> migrationCallbacks = new ArrayList<>();

    MigrationJob(Context context, String jobIdentifier, List<Migration> partialMigrations, List<Migration> completeMigrations) {
        this.context = context;
        this.jobIdentifier = jobIdentifier;
        this.partialMigrations = partialMigrations;
        this.completeMigrations = completeMigrations;
    }

    void addCallback(MigrationCallback callback) {
        migrationCallbacks.add(callback);
    }

    public void migrate() {
        FilePersistenceCreator filePersistenceCreator = FilePersistenceCreator.newInternalFilePersistenceCreator(context);
        StorageRequirementsRule storageRequirementsRule = StorageRequirementsRule.withPercentageOfStorageRemaining(TEN_PERCENT);
        filePersistenceCreator.withStorageRequirementsRule(storageRequirementsRule);
        FilePersistence filePersistence = filePersistenceCreator.create();

        DownloadsPersistence downloadsPersistence = RoomDownloadsPersistence.newInstance(context);

        int totalNumberOfMigrations = partialMigrations.size() + completeMigrations.size();
        InternalMigrationStatus migrationStatus = new VersionOneToVersionTwoMigrationStatus(
                jobIdentifier,
                MigrationStatus.Status.MIGRATING_FILES,
                NO_COMPLETED_MIGRATIONS,
                totalNumberOfMigrations,
                Optional.absent()
        );

        migrationStatus.markAsMigrating();
        onUpdate(migrationStatus);

        migrateCompleteDownloads(migrationStatus, completeMigrations, downloadsPersistence, filePersistence);
        migratePartialDownloads(migrationStatus, partialMigrations, downloadsPersistence);

        migrationStatus.markAsComplete();
        onUpdate(migrationStatus);
    }

    private void onUpdate(InternalMigrationStatus migrationStatus) {
        for (MigrationCallback migrationCallback : migrationCallbacks) {
            migrationCallback.onUpdate(migrationStatus.copy());
        }
    }

    private void migrateCompleteDownloads(InternalMigrationStatus migrationStatus,
                                          List<Migration> completeMigrations,
                                          DownloadsPersistence downloadsPersistence,
                                          FilePersistence filePersistence) {
        for (Migration completeMigration : completeMigrations) {

            migrateV1FilesToV2Location(migrationStatus, filePersistence, completeMigration);
            deleteVersionOneFiles(migrationStatus, completeMigration);
            if (migrationStatus.status() == MigrationStatus.Status.ERROR) {
                onUpdate(migrationStatus);
                return;
            }

            migrateV1DataToV2Database(downloadsPersistence, completeMigration, true);

            migrationStatus.onSingleBatchMigrated();
            onUpdate(migrationStatus);
        }
    }

    private void migrateV1FilesToV2Location(InternalMigrationStatus migrationStatus, FilePersistence filePersistence, Migration migration) {
        for (Migration.FileMetadata fileMetadata : migration.getFileMetadata()) {
            FileSize fileSize = FileSizeCreator.createFromTotalSize(fileMetadata.totalSizeInBytes());
            FilePath filePath = new LiteFilePath(fileMetadata.newFileLocation());
            filePersistence.create(filePath, fileSize);
            FileInputStream inputStream = null;
            try {
                // open the v1 file
                inputStream = new FileInputStream(new File(fileMetadata.originalFileLocation()));
                byte[] bytes = new byte[RANDOMLY_CHOSEN_BUFFER_SIZE_THAT_SEEMS_TO_WORK];

                // read the v1 file
                int readLast = 0;
                while (readLast != -1) {
                    readLast = inputStream.read(bytes);
                    if (readLast != 0 && readLast != -1) {
                        // write the v1 file to the v2 location
                        filePersistence.write(bytes, 0, readLast);
                    }
                }
            } catch (IOException e) {
                Logger.e(e.getMessage());
                migrationStatus.markAsError(MigrationError.Error.MIGRATING_V1_FILES_TO_V2_LOCATION);
            } finally {
                try {
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    Logger.e(e.getMessage());
                    migrationStatus.markAsError(MigrationError.Error.MIGRATING_V1_FILES_TO_V2_LOCATION);
                }
            }
        }
    }

    private void deleteVersionOneFiles(InternalMigrationStatus migrationStatus, Migration migration) {
        for (Migration.FileMetadata metadata : migration.getFileMetadata()) {
            if (hasValidFileLocation(metadata)) {
                File file = new File(metadata.originalFileLocation());
                boolean deleted = file.delete();
                if (!deleted) {
                    migrationStatus.markAsError(MigrationError.Error.DELETING_V1_FILES);
                    String message = String.format("Could not delete File or Directory: %s", file.getPath());
                    Logger.e(getClass().getSimpleName(), message);
                }
            }
        }
    }

    private boolean hasValidFileLocation(Migration.FileMetadata metadata) {
        return metadata.originalFileLocation() != null && !metadata.originalFileLocation().isEmpty();
    }

    private void migrateV1DataToV2Database(DownloadsPersistence downloadsPersistence,
                                           Migration migration,
                                           boolean notificationSeen) {
        downloadsPersistence.startTransaction();

        Batch batch = migration.batch();

        DownloadBatchId downloadBatchId = batch.downloadBatchId();
        DownloadBatchTitle downloadBatchTitle = new LiteDownloadBatchTitle(batch.title());
        DownloadBatchStatus.Status downloadBatchStatus = batchStatusFrom(migration);
        long downloadedDateTimeInMillis = migration.downloadedDateTimeInMillis();

        DownloadsBatchPersisted persistedBatch = new LiteDownloadsBatchPersisted(
                downloadBatchTitle,
                downloadBatchId,
                downloadBatchStatus,
                downloadedDateTimeInMillis,
                notificationSeen
        );
        downloadsPersistence.persistBatch(persistedBatch);

        for (Migration.FileMetadata fileMetadata : migration.getFileMetadata()) {
            String url = fileMetadata.originalNetworkAddress();

            String rawDownloadFileId = rawFileIdFrom(batch, fileMetadata);
            DownloadFileId downloadFileId = DownloadFileIdCreator.createFrom(rawDownloadFileId);

            FilePath filePath = new LiteFilePath(fileMetadata.newFileLocation());

            DownloadsFilePersisted persistedFile = new LiteDownloadsFilePersisted(
                    downloadBatchId,
                    downloadFileId,
                    filePath,
                    fileMetadata.totalSizeInBytes(),
                    url,
                    FilePersistenceType.INTERNAL
            );
            downloadsPersistence.persistFile(persistedFile);
        }

        downloadsPersistence.transactionSuccess();
        downloadsPersistence.endTransaction();
    }

    private DownloadBatchStatus.Status batchStatusFrom(Migration migration) {
        return migration.type() == Migration.Type.COMPLETE ? DownloadBatchStatus.Status.DOWNLOADED : DownloadBatchStatus.Status.QUEUED;
    }

    private String rawFileIdFrom(Batch batch, Migration.FileMetadata fileMetadata) {
        if (fileMetadata.fileId() == null || fileMetadata.fileId().isEmpty()) {
            return batch.title() + System.nanoTime();
        } else {
            return fileMetadata.fileId();
        }
    }

    private void migratePartialDownloads(InternalMigrationStatus migrationStatus,
                                         List<Migration> partialMigrations,
                                         DownloadsPersistence downloadsPersistence) {
        for (Migration partialMigration : partialMigrations) {
            downloadsPersistence.startTransaction();

            migrateV1DataToV2Database(downloadsPersistence, partialMigration, false);
            deleteVersionOneFiles(migrationStatus, partialMigration);

            downloadsPersistence.transactionSuccess();
            downloadsPersistence.endTransaction();
            migrationStatus.onSingleBatchMigrated();
            onUpdate(migrationStatus);
        }
    }

}
