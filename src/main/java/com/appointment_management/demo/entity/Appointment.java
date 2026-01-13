package com.appointment_management.demo.entity;
import com.appointment_management.demo.enums.AppointmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name="appointments",
  indexes = {
    @Index(name="idx_appt_provider_start", columnList="provider_id,startDateTime")
  }
)
@Getter @Setter
public class Appointment {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional=false)
  private User customer; // CUSTOMER

  @ManyToOne(optional=false)
  private User provider; // STAFF

  @ManyToOne(optional=false)
  private ServiceEntity service;

  @Column(nullable=false)
  private LocalDateTime startDateTime;

  @Column(nullable=false)
  private LocalDateTime endDateTime;

  @Enumerated(EnumType.STRING)
  @Column(nullable=false)
  private AppointmentStatus status = AppointmentStatus.PENDING;

  private String note;
}
