package com.imagemanager.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 通知 DTO
 */
@Data
public class NotificationDTO {
    private String id;
    private String userId;
    private String title;
    private String content;
    private String type; // info, success, warning, error
    private String link;
    private boolean read;
    
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private LocalDateTime createdAt;
}
