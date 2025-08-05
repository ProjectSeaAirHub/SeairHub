package net.dima.project.dto;

import lombok.Builder;
import lombok.Data;
import net.dima.project.entity.Notification;
import java.time.format.DateTimeFormatter;

@Data
@Builder
public class NotificationDto {
    private Long id;
    private String message;
    private String url;
    private boolean isRead;
    private String createdAt;

    public static NotificationDto fromEntity(Notification notification) {
        return NotificationDto.builder()
                .id(notification.getId())
                .message(notification.getMessage())
                .url(notification.getUrl())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
                .build();
    }
}