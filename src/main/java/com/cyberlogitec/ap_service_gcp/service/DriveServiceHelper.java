package com.cyberlogitec.ap_service_gcp.service;

import com.cyberlogitec.ap_service_gcp.job.implement.bkg.CreateChildFoldersExternal;
import com.cyberlogitec.ap_service_gcp.model.FolderStructure;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.Permission;
import com.google.api.services.drive.model.PermissionList;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class DriveServiceHelper {
    private final Drive driveService;

    /**
     * Di chuyển tệp vào thư mục lưu trữ
     */
    public void archiveOldFiles(String sourceFolderId, String archiveFolderId) throws IOException {
        String query = "'" + sourceFolderId + "' in parents and mimeType != 'application/vnd.google-apps.folder' and trashed = false";
        String pageToken = null;

        do {
            Drive.Files.List request = driveService.files().list()
                    .setQ(query)
                    .setFields("nextPageToken, files(id, name)")
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true)
                    .setPageSize(1000);

            if (pageToken != null) request.setPageToken(pageToken);

            var result = request.execute();
            if (result.getFiles() != null) {
                for (File file : result.getFiles()) {
                    try {
                        driveService.files().update(file.getId(), null)
                                .setAddParents(archiveFolderId)
                                .setRemoveParents(sourceFolderId)
                                .setSupportsAllDrives(true)
                                .execute();
                    } catch (Exception e) {
                        System.err.println("Lỗi archive file: " + file.getName());
                    }
                }
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);
    }

    /**
     * Tải cấu trúc thư mục hiện có
     */
    public FolderStructure getExistingFolderStructure(String parentFolderId) throws IOException {
        FolderStructure structure = new FolderStructure();
        List<String> childFolderIds = new ArrayList<>();

        // Bước 1: Lấy Folder L1
        String pageToken = null;
        do {
            Drive.Files.List request = driveService.files().list()
                    .setQ("'" + parentFolderId + "' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                    .setFields("files(id, name, webViewLink), nextPageToken")
                    .setPageSize(500)
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true);

            if (pageToken != null) request.setPageToken(pageToken);

            var result = request.execute();
            if (result.getFiles() != null) {
                for (File file : result.getFiles()) {
                    structure.getFolderMap().put(file.getName(), new CreateChildFoldersExternal.FolderInfo(file.getId(), file.getWebViewLink()));
                    childFolderIds.add(file.getId());
                }
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        if (childFolderIds.isEmpty()) return structure;

        // Bước 2: Lấy Folder L2 (Archive) theo batch
        int BATCH_SIZE = 120;
        for (int i = 0; i < childFolderIds.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, childFolderIds.size());
            List<String> batchIds = childFolderIds.subList(i, end);

            // Tạo query OR
            String parentQuery = batchIds.stream()
                    .map(id -> "'" + id + "' in parents")
                    .collect(Collectors.joining(" or "));

            String archivePageToken = null;
            do {
                Drive.Files.List archiveRequest = driveService.files().list()
                        .setQ("(" + parentQuery + ") and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                        .setFields("files(id, name, webViewLink), nextPageToken")
                        .setSupportsAllDrives(true)
                        .setIncludeItemsFromAllDrives(true);

                if (archivePageToken != null) archiveRequest.setPageToken(archivePageToken);

                var archiveResult = archiveRequest.execute();
                if (archiveResult.getFiles() != null) {
                    for (File file : archiveResult.getFiles()) {
                        structure.getArchiveMap().put(file.getName(), new CreateChildFoldersExternal.FolderInfo(file.getId(), file.getWebViewLink()));
                    }
                }
                archivePageToken = archiveResult.getNextPageToken();
            } while (archivePageToken != null);
        }

        return structure;
    }

    /**
     * Copy và Move File
     */
    public String copyAndMoveFile(String sourceFileId, String targetFolderId, String newFileName) throws IOException {
        File resource = new File();
        resource.setName(newFileName);
        resource.setParents(Collections.singletonList(targetFolderId));

        File copiedFile = driveService.files().copy(sourceFileId, resource)
                .setSupportsAllDrives(true)
                .setFields("id")
                .execute();

        return copiedFile.getId();
    }

    /**
     * Tạo cấu trúc thư mục mới
     */
    public FolderStructure createNewFolderStructure(String folderName, String archiveFolderName, String parentFolderId, List<String> editorEmails, String workFileId) throws IOException {
        FolderStructure result = new FolderStructure();

        // 1. Tạo Main
        File mainMeta = new File();
        mainMeta.setName(folderName);
        mainMeta.setMimeType("application/vnd.google-apps.folder");
        mainMeta.setParents(Collections.singletonList(parentFolderId));

        File mainFolder = driveService.files().create(mainMeta)
                .setFields("id, webViewLink")
                .setSupportsAllDrives(true)
                .execute();

        result.getFolderMap().put("main", new CreateChildFoldersExternal.FolderInfo(mainFolder.getId(), mainFolder.getWebViewLink()));

        // 2. Tạo Archive
        File archMeta = new File();
        archMeta.setName(archiveFolderName);
        archMeta.setMimeType("application/vnd.google-apps.folder");
        archMeta.setParents(Collections.singletonList(mainFolder.getId()));

        File archFolder = driveService.files().create(archMeta)
                .setFields("id, webViewLink")
                .setSupportsAllDrives(true)
                .execute();

        result.getArchiveMap().put("archive", new CreateChildFoldersExternal.FolderInfo(archFolder.getId(), archFolder.getWebViewLink()));

        if (editorEmails != null && !editorEmails.isEmpty()) {
            foldersUpdate(workFileId, mainFolder.getId(), editorEmails);
        }

        return result;
    }

    public void foldersUpdate(String fileId, String countryFolderId, List<String> rawEmails) throws IOException {

        if (rawEmails == null) rawEmails = new ArrayList<>();

        Set<String> targetEmails = rawEmails.stream()
                .filter(Objects::nonNull)
                .map(email -> email.toLowerCase().replace(" ", ""))
                .filter(email -> !email.isEmpty())
                .collect(Collectors.toSet());

        System.out.println("..Target emails: " + targetEmails);

        // 2. Lấy danh sách master editors
        Set<String> masterEmails = new HashSet<>();
        try {
            List<Permission> masterPerms = getAllPermissions(fileId);
            masterEmails = masterPerms.stream()
                    .filter(this::isEditorRole)
                    .map(Permission::getEmailAddress)
                    .filter(Objects::nonNull)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            System.err.println("Lỗi lấy quyền Master (" + fileId + "): " + e.getMessage());
            throw e;
        }
        System.out.println("..Master emails: " + masterEmails);

        // 3. Lấy danh sách editor HIỆN TẠI của Folder
        List<Permission> currentFolderPermissionsRaw;
        Set<String> currentFolderEditorEmails = new HashSet<>();
        try {
            // Lưu giữ toàn bộ object Permission để sau này lấy ID dùng cho việc Xóa
            currentFolderPermissionsRaw = getAllPermissions(countryFolderId);

            currentFolderEditorEmails = currentFolderPermissionsRaw.stream()
                    .filter(this::isEditorRole)
                    .map(Permission::getEmailAddress)
                    .filter(Objects::nonNull)
                    .map(String::toLowerCase)
                    .collect(Collectors.toSet());
        } catch (IOException e) {
            System.err.println("Lỗi lấy quyền Folder hiện tại (" + countryFolderId + "): " + e.getMessage());
            throw e;
        }
        System.out.println("..Current Folder emails: " + currentFolderEditorEmails);

        // 4. Lấy danh sách editor của thư mục CHA (Parent)
        Set<String> parentFolderEditorEmails = new HashSet<>();
        try {
            File fileDetails = driveService.files().get(countryFolderId)
                    .setFields("parents")
                    .setSupportsAllDrives(true)
                    .execute();

            if (fileDetails.getParents() != null && !fileDetails.getParents().isEmpty()) {
                String parentId = fileDetails.getParents().get(0);
                List<Permission> parentPerms = getAllPermissions(parentId);

                parentFolderEditorEmails = parentPerms.stream()
                        .filter(this::isEditorRole)
                        .map(Permission::getEmailAddress)
                        .filter(Objects::nonNull)
                        .map(String::toLowerCase)
                        .collect(Collectors.toSet());
            }
        } catch (IOException e) {
            System.err.println("Lỗi lấy quyền Parent Folder: " + e.getMessage());
            throw e;
        }
        // System.out.println("..Parent emails: " + parentFolderEditorEmails);

        // =================================================================
        // TÍNH TOÁN (LOGIC CHÍNH)
        // =================================================================

        // 5. Tính toán THÊM (ADD)
        // Logic: Target - Current - Parent - Master
        Set<String> finalMasterEmails = masterEmails;
        Set<String> finalParentEmails = parentFolderEditorEmails;
        Set<String> finalCurrentEmails = currentFolderEditorEmails;

        List<String> emailsToAdd = targetEmails.stream()
                .filter(email -> !finalCurrentEmails.contains(email))
                .filter(email -> !finalParentEmails.contains(email))
                .filter(email -> !finalMasterEmails.contains(email))
                .collect(Collectors.toList());

        System.out.println("..To Add: " + emailsToAdd);

        // 6. Tính toán XÓA (REMOVE)
        // Logic: Current - Target - Parent - Master
        List<String> emailsToRemove = finalCurrentEmails.stream()
                .filter(email -> !targetEmails.contains(email))
                .filter(email -> !finalParentEmails.contains(email))
                .filter(email -> !finalMasterEmails.contains(email))
                .collect(Collectors.toList());

        System.out.println("..To Remove: " + emailsToRemove);

        // 7. Thực thi (EXECUTE)

        // --- A. THÊM QUYỀN ---
        if (!emailsToAdd.isEmpty()) {
            // Sử dụng Batch Request để tối ưu hiệu suất nếu danh sách dài
            BatchRequest batchAdd = driveService.batch();

            for (String email : emailsToAdd) {
                Permission newPerm = new Permission()
                        .setRole("writer") // SharedDrive: writer = Contributor
                        .setType("user")
                        .setEmailAddress(email);

                try {
                    driveService.permissions().create(countryFolderId, newPerm)
                            .setSupportsAllDrives(true)
                            .setSendNotificationEmail(false)
                            .queue(batchAdd, new JsonBatchCallback<Permission>() {
                                @Override
                                public void onSuccess(Permission permission, HttpHeaders responseHeaders) {
                                    // Success silently or log debug
                                }

                                @Override
                                public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
                                    System.err.println("Lỗi Batch Add " + email + ": " + e.getMessage());
                                }
                            });
                } catch (IOException e) {
                    System.err.println("Lỗi queue add permission: " + e.getMessage());
                }
            }

            if (batchAdd.size() > 0) {
                batchAdd.execute();
                System.out.println("..Added processing complete.");
            }
        }

        // --- B. XÓA QUYỀN ---
        if (!emailsToRemove.isEmpty()) {
            for (String email : emailsToRemove) {
                // Tìm Permission ID tương ứng với email cần xóa
                // Chỉ xóa nếu role là 'writer' hoặc 'fileOrganizer' (tránh xóa Organizer/Admin)
                Optional<Permission> permToRemove = currentFolderPermissionsRaw.stream()
                        .filter(p -> p.getEmailAddress() != null && p.getEmailAddress().equalsIgnoreCase(email))
                        .filter(p -> "writer".equals(p.getRole()) || "fileOrganizer".equals(p.getRole()))
                        .findFirst();

                if (permToRemove.isPresent()) {
                    try {
                        // Lưu ý: Permission Delete không hỗ trợ Batch trong một số trường hợp cũ,
                        // nhưng ở đây ta gọi trực tiếp để an toàn và dễ debug lỗi từng người.
                        driveService.permissions().delete(countryFolderId, permToRemove.get().getId())
                                .setSupportsAllDrives(true)
                                .execute();
                        // System.out.println("Deleted: " + email);
                    } catch (IOException e) {
                        System.err.println("Lỗi Remove " + email + ": " + e.getMessage());
                        // Có thể throw e nếu muốn dừng chương trình khi gặp lỗi nghiêm trọng
                    }
                } else {
                    System.out.println("Skip remove (Role protected or not found): " + email);
                }
            }
            System.out.println("..Removed processing complete.");
        }
    }

    // =================================================================
    // CÁC HÀM HELPER
    // =================================================================

    /**
     * Lấy TẤT CẢ permissions (xử lý phân trang nextPageToken)
     */
    private List<Permission> getAllPermissions(String fileId) throws IOException {
        List<Permission> allPermissions = new ArrayList<>();
        String pageToken = null;
        do {
            PermissionList result = driveService.permissions().list(fileId)
                    .setPageSize(100)
                    .setFields("nextPageToken, permissions(id, emailAddress, role)")
                    .setSupportsAllDrives(true)
                    .setPageToken(pageToken)
                    .execute();

            if (result.getPermissions() != null) {
                allPermissions.addAll(result.getPermissions());
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        return allPermissions;
    }

    /**
     * Helper check role Editor
     */
    private boolean isEditorRole(Permission p) {
        if (p == null || p.getRole() == null) return false;
        String role = p.getRole();
        return role.equals("writer") || role.equals("organizer") || role.equals("fileOrganizer");
    }

}
