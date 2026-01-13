package com.appointment_management.demo.websoket;

import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NotificationService {

    private final SimpMessagingTemplate messagingTemplate;

    public void notifyProvider(Long providerId, AppointmentNotification notification) {
        // قناة خاصة بكل مزود
        messagingTemplate.convertAndSend(
                "/topic/providers/" + providerId + "/appointments",
                notification
        );
    }
    public void notifyCustomer(Long customerId, AppointmentNotification notification) {
        // قناة خاصة بكل مزود
        messagingTemplate.convertAndSend(
                "/topic/providers/" + customerId + "/appointments",
                notification
        );
    }
}
