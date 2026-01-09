package com.cyberlogitec.ap_service_gcp.service;

import com.google.cloud.storage.*;
import org.springframework.stereotype.Service;

@Service
public class GcsService {
    private final Storage storage;
    private final String bucketName;

    public GcsService() {
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.bucketName="run-sources-ethereal-hub-483507-d4-asia-southeast1";
    }

    public void upload(String objectName, byte[] data) {
        BlobId blobId = BlobId.of(this.bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        storage.create(blobInfo, data);
    }

    public byte[] getFile(String bucketName, String objectName) {
        Blob blob = storage.get(bucketName, objectName);

        if (blob == null) {
            throw new IllegalArgumentException(
                    "File not found: gs://" + bucketName + "/" + objectName
            );
        }

        return blob.getContent();
    }
}
