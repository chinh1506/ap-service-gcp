package com.cyberlogitec.ap_service_gcp.service.helper;

import com.cyberlogitec.ap_service_gcp.configuration.GoogleClientPool;
import com.cyberlogitec.ap_service_gcp.dto.FolderInfoDTO;
import com.cyberlogitec.ap_service_gcp.dto.FolderStructureDTO;
import com.cyberlogitec.ap_service_gcp.util.Utilities;
import com.google.api.client.googleapis.batch.BatchRequest;
import com.google.api.client.googleapis.batch.json.JsonBatchCallback;
import com.google.api.client.googleapis.json.GoogleJsonError;
import com.google.api.client.http.HttpHeaders;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
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
    private final GoogleClientPool googleClientPool;

    private Drive getDriveService() throws IOException {
        return googleClientPool.getNextDriveClient();
    }


    public void archiveOldFiles(String sourceFolderId, String archiveFolderId) throws IOException {
        String query = "'" + sourceFolderId + "' in parents and mimeType != 'application/vnd.google-apps.folder' and trashed = false";
        String pageToken = null;

        do {
            Drive.Files.List request = getDriveService().files().list()
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
                        getDriveService().files().update(file.getId(), null)
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

    public FolderStructureDTO getExistingFolderStructure(String parentFolderId) throws IOException {
        return getExistingFolderStructure(parentFolderId, true);
    }

    public FolderStructureDTO getExistingFolderStructure(String parentFolderId, boolean includeNested) throws IOException {
        FolderStructureDTO structure = new FolderStructureDTO();
        List<String> childFolderIds = new ArrayList<>();

        String pageToken = null;
        do {
            Drive.Files.List request = getDriveService().files().list()
                    .setQ("'" + parentFolderId + "' in parents and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                    .setFields("files(id, name, webViewLink), nextPageToken")
                    .setPageSize(500)
                    .setSupportsAllDrives(true)
                    .setIncludeItemsFromAllDrives(true);

            if (pageToken != null) request.setPageToken(pageToken);

            var result = request.execute();
            if (result.getFiles() != null) {
                for (File file : result.getFiles()) {
                    structure.getFolderMap().put(file.getName(), new FolderInfoDTO(file.getId(), file.getWebViewLink()));
                    childFolderIds.add(file.getId());
                }
            }
            pageToken = result.getNextPageToken();
        } while (pageToken != null);

        if (childFolderIds.isEmpty() || !includeNested) return structure;

        int BATCH_SIZE = 120;
        for (int i = 0; i < childFolderIds.size(); i += BATCH_SIZE) {
            int end = Math.min(i + BATCH_SIZE, childFolderIds.size());
            List<String> batchIds = childFolderIds.subList(i, end);

            String parentQuery = batchIds.stream()
                    .map(id -> "('" + id + "' in parents)")
                    .collect(Collectors.joining(" or "));

            String archivePageToken = null;
            do {
                Drive.Files.List archiveRequest = getDriveService().files().list()
                        .setQ("(" + parentQuery + ") and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                        .setFields("files(id, name, webViewLink), nextPageToken")
                        .setPageSize(500)
                        .setOrderBy("createdTime asc")
                        .setCorpora("allDrives")
                        .setSupportsAllDrives(true)
                        .setIncludeItemsFromAllDrives(true);

                if (archivePageToken != null) archiveRequest.setPageToken(archivePageToken);

                var archiveResult = archiveRequest.execute();
                if (archiveResult.getFiles() != null) {
                    for (File file : archiveResult.getFiles()) {
                        structure.getArchiveMap().put(file.getName(), new FolderInfoDTO(file.getId(), file.getWebViewLink()));
                    }
                }
                archivePageToken = archiveResult.getNextPageToken();
            } while (archivePageToken != null);
        }

        return structure;
    }

    /**
     * Make copy a file and put it into a folder
     *
     * @param sourceFileId   file's id of a source file
     * @param targetFolderId folder's id will keep the new file
     * @param newFileName    File's name of the new file
     * @return New file id
     */
    public String copyAndMoveFile(String sourceFileId, String targetFolderId, String newFileName) throws IOException {
        File resource = new File();
        resource.setName(newFileName);
        resource.setParents(Collections.singletonList(targetFolderId));

        File copiedFile = Utilities.retry(() -> {
            return getDriveService().files().copy(sourceFileId, resource)
                    .setSupportsAllDrives(true)
                    .setFields("id")
                    .execute();
        }, 3);

        return copiedFile.getId();
    }

    /**
     * Tạo cấu trúc thư mục mới
     */
    public FolderStructureDTO createNewFolderStructure(String folderName, String archiveFolderName, String parentFolderId, List<String> editorEmails, String workFileId) throws IOException {
        FolderStructureDTO result = new FolderStructureDTO();

        // 1. Tạo Main
        File mainMeta = new File();
        mainMeta.setName(folderName);
        mainMeta.setMimeType("application/vnd.google-apps.folder");
        mainMeta.setParents(Collections.singletonList(parentFolderId));

        File mainFolder = getDriveService().files().create(mainMeta)
                .setFields("id, webViewLink")
                .setSupportsAllDrives(true)
                .execute();

        result.getFolderMap().put("main", new FolderInfoDTO(mainFolder.getId(), mainFolder.getWebViewLink()));

        // 2. Tạo Archive
        File archMeta = new File();
        archMeta.setName(archiveFolderName);
        archMeta.setMimeType("application/vnd.google-apps.folder");
        archMeta.setParents(Collections.singletonList(mainFolder.getId()));

        File archFolder = getDriveService().files().create(archMeta)
                .setFields("id, webViewLink")
                .setSupportsAllDrives(true)
                .execute();

        result.getArchiveMap().put("archive", new FolderInfoDTO(archFolder.getId(), archFolder.getWebViewLink()));

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
            System.err.println("Error occurs when trying get current permissions (" + countryFolderId + "): " + e.getMessage());
            throw e;
        }
        System.out.println("..Current Folder emails: " + currentFolderEditorEmails);

        // 4. Lấy danh sách editor của thư mục CHA (Parent)
        Set<String> parentFolderEditorEmails = new HashSet<>();
        try {
            File fileDetails = getDriveService().files().get(countryFolderId)
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
            System.err.println("Error when get PERM of Parent Folder: " + e.getMessage());
            throw e;
        }

        Set<String> finalMasterEmails = masterEmails;
        Set<String> finalParentEmails = parentFolderEditorEmails;
        Set<String> finalCurrentEmails = currentFolderEditorEmails;

        List<String> emailsToAdd = targetEmails.stream()
                .filter(email -> !finalCurrentEmails.contains(email))
                .filter(email -> !finalParentEmails.contains(email))
                .filter(email -> !finalMasterEmails.contains(email))
                .toList();

        System.out.println("..To Add: " + emailsToAdd);

        List<String> emailsToRemove = finalCurrentEmails.stream()
                .filter(email -> !targetEmails.contains(email))
                .filter(email -> !finalParentEmails.contains(email))
                .filter(email -> !finalMasterEmails.contains(email))
                .toList();

        System.out.println("..To Remove: " + emailsToRemove);

        if (!emailsToAdd.isEmpty()) {
            BatchRequest batchAdd = getDriveService().batch();

            for (String email : emailsToAdd) {
                Permission newPerm = new Permission()
                        .setRole("writer") // SharedDrive: writer = Contributor
                        .setType("user")
                        .setEmailAddress(email);

                try {
                    getDriveService().permissions().create(countryFolderId, newPerm)
                            .setSupportsAllDrives(true)
                            .setSendNotificationEmail(false)
                            .queue(batchAdd, new JsonBatchCallback<Permission>() {
                                @Override
                                public void onSuccess(Permission permission, HttpHeaders responseHeaders) {
                                    // Success silently or log debug
                                }

                                @Override
                                public void onFailure(GoogleJsonError e, HttpHeaders responseHeaders) {
                                    System.err.println("Failed when Batch Add " + email + ": " + e.getMessage());
                                }
                            });
                } catch (IOException e) {
                    System.err.println("Fail queue add permission: " + e.getMessage());
                }
            }

            if (batchAdd.size() > 0) {
                batchAdd.execute();
                System.out.println("..Added processing complete.");
            }
        }
        if (!emailsToRemove.isEmpty()) {
            for (String email : emailsToRemove) {
                Optional<Permission> permToRemove = currentFolderPermissionsRaw.stream()
                        .filter(p -> p.getEmailAddress() != null && p.getEmailAddress().equalsIgnoreCase(email))
                        .filter(p -> "writer".equals(p.getRole()) || "fileOrganizer".equals(p.getRole()))
                        .findFirst();

                if (permToRemove.isPresent()) {
                    try {
                        getDriveService().permissions().delete(countryFolderId, permToRemove.get().getId())
                                .setSupportsAllDrives(true)
                                .execute();
                    } catch (IOException e) {
                        System.err.println("Remove fail " + email + ": " + e.getMessage());
                    }
                } else {
                    System.out.println("Skip remove (Role protected or not found): " + email);
                }
            }
            System.out.println("..Removed processing complete.");
        }
    }

    private List<Permission> getAllPermissions(String fileId) throws IOException {
        List<Permission> allPermissions = new ArrayList<>();
        String pageToken = null;
        do {
            PermissionList result = getDriveService().permissions().list(fileId)
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

    private boolean isEditorRole(Permission p) {
        if (p == null || p.getRole() == null) return false;
        String role = p.getRole();
        return role.equals("writer") || role.equals("organizer") || role.equals("fileOrganizer");
    }

    public FileList findFileInSubfolderByName(String folderId, String name) throws IOException {
        return this.getDriveService().files().list()
                .setQ("'" + folderId + "' in parents and name contains '" + name + "' and trashed = false")
                .setFields("files(id, name, webViewLink)")
                .setSupportsAllDrives(true)
                .setCorpora("allDrives")
                .setIncludeItemsFromAllDrives(true)
                .execute();
    }

    public FileList findFolderInSubfolder(String folderId, String pageToken) throws IOException {
        return this.getDriveService().files().list()
                .setQ("'" + folderId + "' in parents  and mimeType = 'application/vnd.google-apps.folder' and trashed = false")
                .setFields("nextPageToken, files(id, name)")
                .setCorpora("allDrives")
                .setSupportsAllDrives(true)
                .setIncludeItemsFromAllDrives(true)
                .setPageToken(pageToken)
                .execute();
    }

    public  boolean isManagerInDrive(String email, String folderId) throws IOException {
        final List<String> ALLOWED_ROLES = List.of("organizer", "fileOrganizer");
        Drive.Permissions.List request = this.getDriveService().permissions().list(folderId)
                .setFields("permissions(emailAddress, role)")
                .setSupportsAllDrives(true);

        PermissionList permissions = request.execute();

        for (Permission p : permissions.getPermissions()) {
            if (email.equalsIgnoreCase(p.getEmailAddress())) {
                if (ALLOWED_ROLES.contains(p.getRole())) {
                    return true;
                }
            }
        }
        return false;
    }


}
