package com.cyberlogitec.ap_service_gcp.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.WriteChannel;
import com.google.cloud.storage.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.channels.Channels;

@Service
public class GcsService {
    private final Storage storage;
    private final String bucketName;
    private final ObjectMapper objectMapper;

    public GcsService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.storage = StorageOptions.getDefaultInstance().getService();
        this.bucketName = "run-sources-ethereal-hub-483507-d4-asia-southeast1";
    }

    public void upload(String objectName, byte[] data) {
        BlobId blobId = BlobId.of(this.bucketName, objectName);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).build();
        storage.create(blobInfo, data);
    }

    public byte[] getFile(String objectName) {
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
     *
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

    public void uploadStreaming(String gcsPath, Object data) throws IOException {
        BlobId blobId = BlobId.of(this.bucketName, gcsPath);
        BlobInfo blobInfo = BlobInfo.newBuilder(blobId).setContentType("application/json").build();

        try (WriteChannel writer = storage.writer(blobInfo);
             OutputStream os = Channels.newOutputStream(writer)) {
            objectMapper.writeValue(os, data);
        }
    }
}
