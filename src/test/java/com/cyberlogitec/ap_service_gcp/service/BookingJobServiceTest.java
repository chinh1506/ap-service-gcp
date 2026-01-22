package com.cyberlogitec.ap_service_gcp.service;

import com.cyberlogitec.ap_service_gcp.dto.FolderStructure;
import com.cyberlogitec.ap_service_gcp.dto.bkg.CreateFileToShareDTO;
import com.cyberlogitec.ap_service_gcp.dto.request.NotifyToPicRequest;
import com.cyberlogitec.ap_service_gcp.job.extension.JobContext;
import com.cyberlogitec.ap_service_gcp.util.ScriptSetting;
import com.cyberlogitec.ap_service_gcp.util.ScriptSettingLoader;
import com.cyberlogitec.ap_service_gcp.util.Utilities;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingJobServiceTest {

    @InjectMocks
    private BookingJobService bookingJobService;

    @Mock
    private CloudRunJobService cloudRunJobService;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private SheetServiceHelper sheetServiceHelper;

    @Mock
    private DriveServiceHelper driveServiceHelper;

    @Mock
    private ScriptSettingLoader scriptSettingLoader;

    @Mock
    private ScriptSetting scriptSettingMock;

    // Để mock static method Utilities.logMemory nếu cần thiết (optional)
    // private MockedStatic<Utilities> utilitiesMockedStatic;

    @BeforeEach
    void setUp() {
        // Nếu Utilities.logMemory gây lỗi trong test, hãy uncomment đoạn này để mock static
        // utilitiesMockedStatic = mockStatic(Utilities.class);
    }

    // @AfterEach
    // void tearDown() {
    //    if (utilitiesMockedStatic != null) utilitiesMockedStatic.close();
    // }

    @Test
    @DisplayName("Test prepareToCreateChildFoldersExternal thành công")
    void testPrepareToCreateChildFoldersExternal() throws IOException {
        // 1. Prepare Data
        Object payload = new Object();
        CreateFileToShareDTO dto = new CreateFileToShareDTO();
        dto.setToShareFolderId("folderId123");
        dto.setWorkFileId("workFileId1");
        dto.setWorkFilePart2Id("workFileId2");
        dto.setFileToShareId("fileShareId1");
        dto.setTaskCount(5);
        dto.setTotalElement(100);

        FolderStructure folderStructure = new FolderStructure(); // Giả lập object

        // 2. Mock behavior
        when(objectMapper.convertValue(payload, CreateFileToShareDTO.class)).thenReturn(dto);
        when(driveServiceHelper.getExistingFolderStructure(anyString())).thenReturn(folderStructure);

        // Mock ScriptSettingLoader trả về mock ScriptSetting object
        when(scriptSettingLoader.getSettingsMap(anyString())).thenReturn(scriptSettingMock);

        // Mock các giá trị trả về từ ScriptSetting (Range names)
        when(scriptSettingMock.getAsString("control_MakeCopy_DistList_DataRange_External")).thenReturn("DistRange");
        when(scriptSettingMock.getAsString("control_MakeCopy_FOEditors_DataRange_External")).thenReturn("EditorRange");
        when(scriptSettingMock.getAsString("control_MakeCopy_FileUnitContract_DataRange_External")).thenReturn("ContractRange");
        when(scriptSettingMock.getAsString("fileToShare_ApBookingDataRange")).thenReturn("ApBookingRange");

        // Mock SheetServiceHelper inputs
        List<List<Object>> dummyList = Collections.singletonList(Collections.singletonList("data"));
        when(sheetServiceHelper.inputAPI(anyString(), eq("ApBookingRange"))).thenReturn(dummyList);

        Map<String, List<List<Object>>> batchData = new HashMap<>();
        batchData.put("DistRange", dummyList);
        batchData.put("EditorRange", dummyList);
        batchData.put("ContractRange", dummyList);

        when(sheetServiceHelper.getMappedBatchData(eq("workFileId1"), anyList())).thenReturn(batchData);

        // 3. Execute method
        bookingJobService.prepareToCreateChildFoldersExternal(payload);

        // 4. Verify
        verify(sheetServiceHelper).clearRange(eq("fileShareId1"), eq("ApBookingRange"));

        // Capture JobContext để kiểm tra dữ liệu bên trong
        ArgumentCaptor<JobContext> contextCaptor = ArgumentCaptor.forClass(JobContext.class);
        verify(cloudRunJobService).runJob(eq("CreateChildFoldersExternal"), contextCaptor.capture());

        JobContext capturedContext = contextCaptor.getValue();
        assertNotNull(capturedContext.getJobId());
        assertEquals(5, capturedContext.getTaskCount());

        CreateFileToShareDTO capturedPayload = (CreateFileToShareDTO) capturedContext.getPayload();
        assertNotNull(capturedPayload.getGs());
        assertEquals(dummyList, capturedPayload.getApBookingData());
        assertEquals(dummyList, capturedPayload.getFileUnits());
    }

    @Test
    @DisplayName("Test notifyToPIC (External=true) thành công")
    void testNotifyToPIC_External() throws IOException {
        // 1. Prepare Data
        NotifyToPicRequest request = NotifyToPicRequest.builder()
                .toShareFolderId("shareFolder123")
                .workFileId("workFile123")
                .isExternal(true)
                .taskCount(2)
                .totalElement(50)
                .build();

        // 2. Mock behavior
        when(scriptSettingLoader.getSettingsMap("workFile123")).thenReturn(scriptSettingMock);

        // Mock key mapping cho trường hợp External = true
        when(scriptSettingMock.getAsString("control_MakeCopy_DistList_DataRange_External")).thenReturn("DistRangeEx");
        when(scriptSettingMock.getAsString("ae1_NotificationSettings_CClist_External")).thenReturn("CCRangeEx");
        when(scriptSettingMock.getAsString("ae1_NotificationSettings_BCClist_External")).thenReturn("BCCRangeEx");
        when(scriptSettingMock.getAsString("ae1_NotificationSettings_EmailContentRange_External")).thenReturn("ContentRangeEx");
        when(scriptSettingMock.getAsString("ae1_NotificationSettings_targetWeek")).thenReturn("WeekRange");
        when(scriptSettingMock.getAsString("control_MakeCopy_FOEditors_DataRange_External")).thenReturn("EditorRangeEx");
        when(scriptSettingMock.getAsString("control_MakeCopy_FileUnitContract_DataRange_External")).thenReturn("ContractRangeEx");

        // Mock Data trả về từ Sheet
        Map<String, List<List<Object>>> allData = new HashMap<>();
        List<List<Object>> dummyData = new ArrayList<>();
        allData.put("DistRangeEx", dummyData);
        allData.put("CCRangeEx", dummyData);
        allData.put("BCCRangeEx", dummyData);
        allData.put("ContentRangeEx", dummyData);
        allData.put("WeekRange", dummyData);
        allData.put("EditorRangeEx", dummyData);
        allData.put("ContractRangeEx", dummyData);

        when(sheetServiceHelper.getMappedBatchData(eq("workFile123"), anyList())).thenReturn(allData);
        when(driveServiceHelper.getExistingFolderStructure("shareFolder123")).thenReturn(new FolderStructure());

        // 3. Execute
        bookingJobService.notifyToPIC(request);

        // 4. Verify
        ArgumentCaptor<JobContext> contextCaptor = ArgumentCaptor.forClass(JobContext.class);
        verify(cloudRunJobService).runJob(eq("BkgNotifyToPIC"), contextCaptor.capture());

        JobContext capturedContext = contextCaptor.getValue();
        assertNotNull(capturedContext);
        assertEquals(2, capturedContext.getTaskCount());
    }

    @Test
    @DisplayName("Test notifyToPIC (External=false) thành công")
    void testNotifyToPIC_Internal() throws IOException {
        // 1. Prepare Data
        NotifyToPicRequest request = NotifyToPicRequest.builder()
                .toShareFolderId("shareFolder123")
                .workFileId("workFile123")
                .isExternal(false)
                .taskCount(1)
                .build();

        // 2. Mock behavior
        when(scriptSettingLoader.getSettingsMap("workFile123")).thenReturn(scriptSettingMock);

        // Mock key mapping cho trường hợp External = false
        // Lưu ý: Code gọi wfScriptSetting.getAsString("control_MakeCopy_DistList_DataRange") (không có _External)
        when(scriptSettingMock.getAsString("control_MakeCopy_DistList_DataRange")).thenReturn("DistRange");
        when(scriptSettingMock.getAsString("ae1_NotificationSettings_CClist")).thenReturn("CCRange");
        when(scriptSettingMock.getAsString("ae1_NotificationSettings_BCClist")).thenReturn("BCCRange");
        when(scriptSettingMock.getAsString("ae1_NotificationSettings_EmailContentRange")).thenReturn("ContentRange");
        when(scriptSettingMock.getAsString("ae1_NotificationSettings_targetWeek")).thenReturn("WeekRange");
        when(scriptSettingMock.getAsString("control_MakeCopy_FOEditors_DataRange")).thenReturn("EditorRange");
        // Dòng này vẫn được gọi dù isExternal=false để lấy key cho list request, nhưng trong map getMappedBatchData
        // logic code: isExternal ? get(...) : new ArrayList() cho contract data.
        when(scriptSettingMock.getAsString("control_MakeCopy_FileUnitContract_DataRange_External")).thenReturn("ContractRangeEx");

        Map<String, List<List<Object>>> allData = new HashMap<>();
        List<List<Object>> dummyData = new ArrayList<>();
        allData.put("DistRange", dummyData);
        allData.put("CCRange", dummyData);
        allData.put("BCCRange", dummyData);
        allData.put("ContentRange", dummyData);
        allData.put("WeekRange", dummyData);
        allData.put("EditorRange", dummyData);
        allData.put("ContractRangeEx", dummyData); // Vẫn phải put vào map vì sheetServiceHelper request list có key này

        when(sheetServiceHelper.getMappedBatchData(eq("workFile123"), anyList())).thenReturn(allData);
        when(driveServiceHelper.getExistingFolderStructure("shareFolder123")).thenReturn(new FolderStructure());

        // 3. Execute
        bookingJobService.notifyToPIC(request);

        // 4. Verify
        verify(cloudRunJobService).runJob(eq("BkgNotifyToPIC"), any(JobContext.class));
    }
}