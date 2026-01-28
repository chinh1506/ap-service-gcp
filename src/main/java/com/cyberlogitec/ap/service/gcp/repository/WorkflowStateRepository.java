package com.cyberlogitec.ap.service.gcp.repository;

import com.cyberlogitec.ap.service.gcp.model.WorkflowState;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;


@RequiredArgsConstructor
@Repository
public class WorkflowStateRepository {
    private final Firestore firestore;
    private static final String COLLECTION_NAME = "workflow_states";

    public String save(WorkflowState workflowState) throws ExecutionException, InterruptedException {
        if (workflowState.getWorkflowId() == null) {
            workflowState.setWorkflowId(firestore.collection(COLLECTION_NAME).document().getId());
        }

        ApiFuture<WriteResult> collectionsApiFuture =
                firestore.collection(COLLECTION_NAME).document(workflowState.getWorkflowId()).set(workflowState);

        return collectionsApiFuture.get().getUpdateTime().toString();
    }

    // --- READ (Get One) ---
    public WorkflowState get(String documentId) throws ExecutionException, InterruptedException {
        DocumentReference documentReference =
                firestore.collection(COLLECTION_NAME).document(documentId);
        ApiFuture<DocumentSnapshot> future = documentReference.get();
        DocumentSnapshot document = future.get();

        if (document.exists()) {
            return document.toObject(WorkflowState.class);
        }
        return null;
    }

    // --- READ (Get All) ---
    public List<WorkflowState> getAll() throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(COLLECTION_NAME);
        ApiFuture<QuerySnapshot> future = collection.get();

        List<QueryDocumentSnapshot> documents = future.get().getDocuments();
        List<WorkflowState> products = new ArrayList<>();

        for (QueryDocumentSnapshot document : documents) {
            products.add(document.toObject(WorkflowState.class));
        }
        return products;
    }

    // --- UPDATE (Partial Update) ---
    public String update(String id, String field, Object value) throws ExecutionException, InterruptedException {
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document(id);

        // Chỉ update trường được chỉ định, không ghi đè toàn bộ document
        ApiFuture<WriteResult> future = docRef.update(field, value);

        return future.get().getUpdateTime().toString();
    }

    // --- DELETE ---
    public void delete(String documentId) {
        firestore.collection(COLLECTION_NAME).document(documentId).delete();
    }

    /**
     * Tìm WorkflowState theo executionName (Unique).
     * Sử dụng limit(1) để tối ưu hiệu năng.
     */
    public WorkflowState findByExecutionName(String executionName) throws ExecutionException, InterruptedException {
        Query query = firestore.collection(COLLECTION_NAME)
                .whereEqualTo("executionName", executionName)
                .limit(1);
        ApiFuture<QuerySnapshot> querySnapshot = query.get();
        List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();

        if (documents.isEmpty()) {
            return null;
        }

        QueryDocumentSnapshot document = documents.get(0);
        WorkflowState state = document.toObject(WorkflowState.class);
        state.setWorkflowId(document.getId());

        return state;
    }
}
