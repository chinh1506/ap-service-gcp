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

    /**
     * Hàm xóa file trên GCS
     * @param fileName Tên file (bao gồm cả folder nếu có, ví dụ: "data/report.json")
     * @return true nếu xóa thành công, false nếu file không tồn tại
     */
    public boolean deleteFile(String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        try {
            BlobId blobId = BlobId.of(bucketName, fileName);
            boolean deleted = storage.delete(blobId);

            return deleted;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}
