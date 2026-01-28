package com.cyberlogitec.ap.service.gcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmailAttachment {
    private String filename;      // Tên file (vd: chart.png)
    private byte[] data;          // Dữ liệu file
    private String mimeType;      // Loại file (vd: image/png, application/pdf)
    private boolean isInline;     // True nếu muốn hiện ảnh trong nội dung mail (dùng cid)
    private String contentId;     // ID để map vào HTML (vd: "chart1" -> src="cid:chart1")
}
