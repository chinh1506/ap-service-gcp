package com.cyberlogitec.ap_service_gcp.service.helper;

import com.cyberlogitec.ap_service_gcp.dto.EmailAttachment;
import com.cyberlogitec.ap_service_gcp.dto.EmailDTO;
import com.sendgrid.Method;
import com.sendgrid.Request;
import com.sendgrid.Response;
import com.sendgrid.SendGrid;
import com.sendgrid.helpers.mail.Mail;
import com.sendgrid.helpers.mail.objects.Attachments;
import com.sendgrid.helpers.mail.objects.Content;
import com.sendgrid.helpers.mail.objects.Email;
import com.sendgrid.helpers.mail.objects.Personalization;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

import java.io.IOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SendGridService {
    private final SendGrid sendGrid;
    private final Email fromEmail;
    private final SpringTemplateEngine templateEngine;
    private static final String HISTORY_SKIPPED = "Skipped";
    public static final String HISTORY_ERROR = "Error Sending";
    public static final String HISTORY_ERROR_FILE_NOT_FOUND = "File to share not found";

    /**
     * Hàm gửi email đa năng (Full option)
     *
     * @param toEmails    Danh sách người nhận chính
     * @param ccEmails    Danh sách CC (có thể null)
     * @param bccEmails   Danh sách BCC (có thể null)
     * @param subject     Tiêu đề
     * @param htmlBody    Nội dung HTML
     * @param attachments Danh sách file đính kèm (có thể null)
     */
    public boolean sendEmail(Set<String> toEmails, Set<String> ccEmails, Set<String> bccEmails, String subject, String htmlBody, List<EmailAttachment> attachments) {

        if (toEmails == null || toEmails.isEmpty()) {
            System.err.println("To List is empty");
            return false;
        }

        try {
            Mail mail = new Mail();
            mail.setFrom(fromEmail);
            mail.setSubject(subject);

            Content content = new Content("text/html", htmlBody);
            mail.addContent(content);
            Personalization personalization = new Personalization();
            for (String email : toEmails) {
                personalization.addTo(new Email(email));
            }
            if (ccEmails != null) {
                for (String email : ccEmails) {
                    if (!toEmails.contains(email)) {
                        personalization.addCc(new Email(email));
                    }
                }
            }

            if (bccEmails != null) {
                for (String email : bccEmails) {
                    if (!toEmails.contains(email) && (ccEmails == null || !ccEmails.contains(email))) {
                        personalization.addBcc(new Email(email));
                    }
                }
            }

            mail.addPersonalization(personalization);

            if (attachments != null && !attachments.isEmpty()) {
                for (EmailAttachment att : attachments) {
                    Attachments sendGridAttachment = new Attachments();
                    String base64Content = Base64.getEncoder().encodeToString(att.getData());

                    sendGridAttachment.setContent(base64Content);
                    sendGridAttachment.setType(att.getMimeType());
                    sendGridAttachment.setFilename(att.getFilename());

                    if (att.isInline()) {
                        sendGridAttachment.setDisposition("inline");
                        sendGridAttachment.setContentId(att.getContentId());
                    } else {
                        sendGridAttachment.setDisposition("attachment");
                    }

                    mail.addAttachments(sendGridAttachment);
                }
            }
            Request request = new Request();
            request.setMethod(Method.POST);
            request.setEndpoint("mail/send");
            request.setBody(mail.build());

            Response response = sendGrid.api(request);

            if (response.getStatusCode() >= 200 && response.getStatusCode() < 300) {
                System.out.println("Email sent to " + toEmails);
                return true;
            } else {
                System.err.println("SendGrid Error: " + response.getBody());
                return false;
            }

        } catch (IOException ex) {
            System.err.println("Exception sending email: " + ex.getMessage());
            ex.printStackTrace();
            return false;
        }
    }

    public void sendReportEmail(Set<String> toEmails, Set<String> ccEmails, Set<String> bccEmails, String subject, String content, byte[] chartImage) {
        Context context = new Context();
        String chartContentId = "chart_image_id";
        context.setVariable("chartContentId", chartContentId);
        context.setVariable("content", content);
        String logoContentId = "logo_image_id";
        context.setVariable("logoContentId", logoContentId);
        String htmlBody = templateEngine.process("create-file-to-share-external-report", context);

        List<EmailAttachment> attachments = new ArrayList<>();
        if (chartImage != null) {
            attachments.add(EmailAttachment.builder().filename("chart.png").data(chartImage).mimeType("image/png").isInline(true)           // Set là Inline
                    .contentId(chartContentId)
                    .build());
        }
        byte[] logoBytes = loadImageFromStatic("static/images/LOGO-ONE.png");
        if (logoBytes != null) {
            attachments.add(EmailAttachment.builder()
                    .filename("logo.png")
                    .data(logoBytes)
                    .mimeType("image/png")
                    .isInline(true)           // Quan trọng: Inline
                    .contentId(logoContentId) // Phải trùng với biến Thymeleaf
                    .build());
        }
        this.sendEmail(toEmails, ccEmails, bccEmails, subject, htmlBody, attachments);
    }

    public byte[] loadImageFromStatic(String path) {
        try {
            // path ví dụ: "static/images/logo.png"
            ClassPathResource imageFile = new ClassPathResource(path);
            return StreamUtils.copyToByteArray(imageFile.getInputStream());
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }


    public String createEmailSubject(List<List<Object>> emailContent, String tradeName, String foName) {
        String today = ZonedDateTime.now(ZoneId.of("Asia/Singapore")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        for (List<Object> row : emailContent) {
            if (row.size() >= 2 && "subject".equalsIgnoreCase(row.get(0).toString())) {
                return row.get(1).toString()
                        .replace("<TRADE NAME>", tradeName)
                        .replace("<FO NAME>", foName)
                        .replace("<Date>", today);
            }
        }
        return "";
    }
    public String createEmailBody(List<List<Object>> emailContent, String foName, String fileLink, String tradeName, String targetWeek) {
        String today = ZonedDateTime.now(ZoneId.of("Asia/Singapore")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        for (List<Object> row : emailContent) {
            if (row.size() >= 2 && "body".equalsIgnoreCase(row.get(0).toString())) {
                return row.get(1).toString()
                        .replace("<FO NAME>", foName)
                        .replace("<FO INPUT LINK>", fileLink)
                        .replace("<Trade Name>", tradeName)
                        .replace("<TargetWeek>", targetWeek)
                        .replace("<Date>", today)
                        .replace("\r\n", "\n").replace("\n", "<br>");
            }
        }
        return "";
    }
    public List<String> sendEmailSendGrid(EmailDTO emailData) {
        String currentDate = ZonedDateTime.now(ZoneId.of("Asia/Singapore")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss Z"));
        try {
            if (emailData.getTo() == null || emailData.getTo().isEmpty()) {
                return Arrays.asList(emailData.getFo(), HISTORY_SKIPPED, String.join(",", emailData.getTo()), "");
            }
            Set<String> toSet = emailData.getTo();
            Set<String> ccSet = emailData.getCc();
            Set<String> bccSet = emailData.getBcc();
            ccSet.removeAll(toSet);
            bccSet.removeAll(toSet);
            bccSet.removeAll(ccSet);
            this.sendEmail(toSet, ccSet, bccSet, emailData.getSubject(), emailData.getBody(), null);
            return Arrays.asList(emailData.getFo(), currentDate, String.join(",", emailData.getTo()), String.join(",", emailData.getCc()), String.join(",", emailData.getBcc()));

        } catch (Exception e) {
            e.printStackTrace();
            return Arrays.asList(emailData.getFo(), HISTORY_ERROR, String.join(",", emailData.getTo()), String.join(",", emailData.getCc()), String.join(",", emailData.getBcc()));
        }
    }

}
