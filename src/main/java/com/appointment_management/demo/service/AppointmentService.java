package com.appointment_management.demo.service;

import com.appointment_management.demo.entity.Appointment;
import com.appointment_management.demo.entity.ServiceEntity;
import com.appointment_management.demo.entity.User;
import com.appointment_management.demo.enums.AppointmentStatus;
import com.appointment_management.demo.repository.*;
import com.appointment_management.demo.websoket.AppointmentNotification;
import com.appointment_management.demo.websoket.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

@Service
@Transactional
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final UserRepository userRepository;
    private final ServiceRepository serviceRepository;
    private final ScheduleRepository scheduleRepository;
    private final HolidayRepository holidayRepository;
    private final NotificationService notificationService;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            UserRepository userRepository,
            ServiceRepository serviceRepository,
            ScheduleRepository scheduleRepository,
            HolidayRepository holidayRepository,
            NotificationService notificationService
    ) {
        this.appointmentRepository = appointmentRepository;
        this.userRepository = userRepository;
        this.serviceRepository = serviceRepository;
        this.scheduleRepository = scheduleRepository;
        this.holidayRepository = holidayRepository;
        this.notificationService = notificationService;
    }

    /**
     * ✅ إنشاء موعد جديد (PENDING)
     */
    public Appointment createAppointment(
            Long customerId,
            Long providerId,
            Long serviceId,
            LocalDateTime startDateTime,
            String note
    ) {
        // 0) فحوصات أساسية
        if (startDateTime == null) {
            throw new RuntimeException("startDateTime is required");
        }
        if (startDateTime.isBefore(LocalDateTime.now())) {
            throw new RuntimeException("Cannot book appointment in the past");
        }

        User customer = userRepository.findById(customerId)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        User provider = userRepository.findById(providerId)
                .orElseThrow(() -> new RuntimeException("Provider not found"));

        ServiceEntity service = serviceRepository.findById(serviceId)
                .orElseThrow(() -> new RuntimeException("Service not found"));

        // 1) احسب end
        LocalDateTime endDateTime = startDateTime.plusMinutes(service.getDurationMinutes());

        // 2) تحقق: عطلة
        LocalDate date = startDateTime.toLocalDate();
        if (holidayRepository.existsByProviderIdAndDate(providerId, date)) {
            throw new RuntimeException("Provider is on holiday");
        }

        // 3) تحقق: ضمن الدوام
        ensureWithinWorkingHours(providerId, startDateTime, endDateTime);

        // 4) تحقق: تداخل
        boolean overlap = appointmentRepository.existsOverlap(providerId, startDateTime, endDateTime);
        if (overlap) {
            throw new RuntimeException("Appointment overlaps with another one");
        }

        Appointment appointment = new Appointment();
        appointment.setCustomer(customer);
        appointment.setProvider(provider);
        appointment.setService(service);
        appointment.setStartDateTime(startDateTime);
        appointment.setEndDateTime(endDateTime);
        appointment.setNote(note);
        appointment.setStatus(AppointmentStatus.PENDING);
        appointmentRepository.save(appointment);

/*
send notification
        AppointmentNotification notification = new AppointmentNotification();
        notification.setAppointmentId(appointment.getId());
        notification.setStatus("pending");
        notification.setMessage("new appointment has created ");
        notificationService.notifyProvider(providerId, notification);
*/

        return appointment;
    }

    /**
     * ✅ قبول الموعد (APPROVED)
     * - فقط إذا كان PENDING
     * - مع إعادة فحص التداخل لحظة القبول
     */
    public Appointment approveAppointment(Long appointmentId) {
        Appointment appointment = getAppointmentById(appointmentId);

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new RuntimeException("Only PENDING appointments can be approved");
        }

        boolean overlap = appointmentRepository.existsOverlapExcludingId(
                appointment.getProvider().getId(),
                appointment.getId(),
                appointment.getStartDateTime(),
                appointment.getEndDateTime()
        );
        if (overlap) {
            throw new RuntimeException("Cannot approve: appointment overlaps with another one");
        }

        appointment.setStatus(AppointmentStatus.APPROVED);

        AppointmentNotification notification = new AppointmentNotification();
        notification.setAppointmentId(appointment.getId());
        notification.setStatus("approved");
        notification.setMessage("your appointment has approved ");
        notificationService.notifyCustomer(appointment.getCustomer().getId(), notification);

        return appointment;
    }

    /**
     * ✅ رفض الموعد (REJECTED)
     * - فقط إذا كان PENDING
     */
    public Appointment rejectAppointment(Long appointmentId, String reason) {
        Appointment appointment = getAppointmentById(appointmentId);

        if (appointment.getStatus() != AppointmentStatus.PENDING) {
            throw new RuntimeException("Only PENDING appointments can be rejected");
        }

        appointment.setStatus(AppointmentStatus.REJECTED);

        if (reason != null && !reason.isBlank()) {
            appointment.setNote(appendNote(appointment.getNote(), "Rejected: " + reason));
        }

        AppointmentNotification notification = new AppointmentNotification();
        notification.setAppointmentId(appointment.getId());
        notification.setStatus("rejected");
        notification.setMessage("your appointment has rejected ");
        notificationService.notifyCustomer(appointment.getCustomer().getId(), notification);

        return appointment;
    }

    /**
     * ✅ إلغاء الموعد (CANCELED)
     * - مسموح إذا كان PENDING أو APPROVED
     */
    public Appointment cancelAppointment(Long appointmentId, String reason) {
        Appointment appointment = getAppointmentById(appointmentId);

        if (appointment.getStatus() == AppointmentStatus.CANCELED
                || appointment.getStatus() == AppointmentStatus.FINISHED) {
            throw new RuntimeException("Appointment cannot be canceled");
        }

        appointment.setStatus(AppointmentStatus.CANCELED);

        if (reason != null && !reason.isBlank()) {
            appointment.setNote(appendNote(appointment.getNote(), "Canceled: " + reason));
        }

        return appointment;
    }

    /**
     * ✅ إنهاء الموعد (FINISHED)
     * - فقط إذا كان APPROVED
     * - وما بينتهي قبل endDateTime
     */
    public Appointment finishAppointment(Long appointmentId) {
        Appointment appointment = getAppointmentById(appointmentId);

        if (appointment.getStatus() != AppointmentStatus.APPROVED) {
            throw new RuntimeException("Only APPROVED appointments can be finished");
        }

        if (LocalDateTime.now().isBefore(appointment.getEndDateTime())) {
            throw new RuntimeException("Cannot finish before appointment end time");
        }

        AppointmentNotification notification = new AppointmentNotification();
        notification.setAppointmentId(appointment.getId());
        notification.setStatus("finished");
        notification.setMessage("your appointment has finished ");
        notificationService.notifyCustomer(appointment.getCustomer().getId(), notification);

        appointment.setStatus(AppointmentStatus.FINISHED);
        return appointment;
    }

    // ==========================
    // ✅ استعلامات
    // ==========================

    @Transactional(readOnly = true)
    public List<Appointment> getForCustomer(Long customerId) {
        return appointmentRepository.findByCustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<Appointment> getAppointmentForProvider(Long providerId) {
        return appointmentRepository.findByProviderId(providerId);
    }

    @Transactional(readOnly = true)
    public Appointment getAppointmentById(Long id) {
        return appointmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Appointment not found"));
    }

    // ==========================
    // ✅ Helpers
    // ==========================

    private void ensureWithinWorkingHours(Long providerId, LocalDateTime start, LocalDateTime end) {
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = end.toLocalTime();

        boolean inWorkingHours = scheduleRepository
                .findByProviderId(providerId)
                .stream()
                .anyMatch(s ->
                        s.getDayOfWeek() == start.getDayOfWeek()
                                && !startTime.isBefore(s.getStartTime())
                                && !endTime.isAfter(s.getEndTime())
                );

        if (!inWorkingHours) {
            throw new RuntimeException("Outside working hours");
        }
    }

    private String appendNote(String oldNote, String newPart) {
        if (oldNote == null || oldNote.isBlank()) return newPart;
        return oldNote + " | " + newPart;
    }
}
