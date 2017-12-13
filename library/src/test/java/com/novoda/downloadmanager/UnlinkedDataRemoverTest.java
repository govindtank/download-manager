package com.novoda.downloadmanager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Test;

import static com.google.common.truth.Truth.assertThat;

public class UnlinkedDataRemoverTest {

    private static final String LINKED_FIRST_FILE = "a filename";
    private static final String LINKED_SECOND_FILE = "another filename";
    private static final String UNLINKED_THIRD_FILE = "yet another filename";

    private final DownloadsPersistence downloadsPersistence = new DownloadsPersistence() {
        @Override
        public void startTransaction() {

        }

        @Override
        public void endTransaction() {

        }

        @Override
        public void transactionSuccess() {

        }

        @Override
        public void persistBatch(DownloadsBatchPersisted batchPersisted) {

        }

        @Override
        public List<DownloadsBatchPersisted> loadBatches() {
            return null;
        }

        @Override
        public void persistFile(DownloadsFilePersisted filePersisted) {

        }

        @Override
        public List<DownloadsFilePersisted> loadFiles(DownloadBatchId batchId) {
            return null;
        }

        @Override
        public void delete(DownloadBatchId downloadBatchId) {

        }

        @Override
        public void update(DownloadBatchId downloadBatchId, DownloadBatchStatus.Status status) {

        }
    };
    private final LocalFilesDirectory localFilesDirectory = new FakeLocalFilesDirectory(Arrays.asList(LINKED_FIRST_FILE, LINKED_SECOND_FILE, UNLINKED_THIRD_FILE));
    private final UnlinkedDataRemover unlinkedDataRemover = instantiateTestSubject(downloadsPersistence, localFilesDirectory);

    @Test
    public void removesFiles_whenPresentInLocalStorageButNotInV2Database() {
        unlinkedDataRemover.remove();

        List<String> expectedContents = Arrays.asList(LINKED_FIRST_FILE, LINKED_SECOND_FILE);
        List<String> actualContents = localFilesDirectory.contents();
        assertThat(actualContents).isEqualTo(expectedContents);
    }

    private static UnlinkedDataRemover instantiateTestSubject(DownloadsPersistence downloadsPersistence, LocalFilesDirectory localFilesDirectory) {
        return new UnlinkedDataRemover(downloadsPersistence, localFilesDirectory);
    }

    private static class FakeLocalFilesDirectory implements LocalFilesDirectory {

        private List<String> fileList;

        FakeLocalFilesDirectory(List<String> fileList) {
            this.fileList = new ArrayList<>(fileList);
        }

        @Override
        public List<String> contents() {
            return fileList;
        }

        @Override
        public boolean deleteFile(String filename) {
            boolean fileWasRemoved = false;
            List<String> newFileList = new ArrayList<>();
            for (String s : fileList) {
                if (filename.equals(s)) {
                    fileWasRemoved = true;
                } else {
                    newFileList.add(s);
                }
            }
            fileList = newFileList;
            return fileWasRemoved;
        }
    }
}
