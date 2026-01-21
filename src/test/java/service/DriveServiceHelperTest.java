package service;

import com.cyberlogitec.ap_service_gcp.dto.FolderStructure;
import com.cyberlogitec.ap_service_gcp.service.DriveServiceHelper;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest
public class DriveServiceHelperTest {
    @InjectMocks
    private final DriveServiceHelper driveServiceHelper;

    public DriveServiceHelperTest(DriveServiceHelper driveServiceHelper) {
        this.driveServiceHelper = driveServiceHelper;
    }

    @Test
    void getExistingFolderStructureTestOverall() throws IOException {
        FolderStructure folderStructure = driveServiceHelper.getExistingFolderStructure("1sWJD5TwY9ufmKmGG6Tf_gbcimESWiCQH");
        System.out.println(folderStructure);

    }

}
