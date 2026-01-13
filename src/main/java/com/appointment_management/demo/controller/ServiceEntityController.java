package com.appointment_management.demo.controller;

import com.appointment_management.demo.dto.CreateServiceRequest;
import com.appointment_management.demo.dto.UpdateServiceRequest;
import com.appointment_management.demo.entity.ServiceEntity;
import com.appointment_management.demo.entity.User;
import com.appointment_management.demo.enums.Role;
import com.appointment_management.demo.service.ServiceEntityService;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static com.appointment_management.demo.service.UserService.getUser;

@RestController
@RequestMapping("/services")
public class ServiceEntityController {

    private final ServiceEntityService serviceEntityService;

    public ServiceEntityController(ServiceEntityService serviceEntityService) {
        this.serviceEntityService = serviceEntityService;
    }

    @GetMapping
    public ResponseEntity<Page<ServiceEntity>> getAll(@RequestParam Integer page, @RequestParam Integer size) {
        return ResponseEntity.ok(serviceEntityService.getAll(page, size));
    }

    @GetMapping("/times")
    public ResponseEntity<List<LocalTime>> getAvailableTimes
            (@RequestParam Long serviceId, @RequestParam LocalDate date) {
        return ResponseEntity.ok(serviceEntityService.getAvailableTimes(serviceId, date));
    }

    @PostMapping
    @PreAuthorize("hasRole('STAFF')")
    public ResponseEntity<ServiceEntity> create(@RequestBody CreateServiceRequest req) {
        User user = getUser();
        if (user ==null || !Role.STAFF.equals(user.getRole()))
            throw new RuntimeException("user is not found");
        ServiceEntity s = serviceEntityService.createService(
                req.name, req.durationMinutes, req.price, user.getId()
        );
        return ResponseEntity.ok(s);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ServiceEntity> update(@PathVariable Long id,
                                                @RequestBody UpdateServiceRequest req) {
        ServiceEntity s = serviceEntityService.updateService(
                id, req.name, req.durationMinutes, req.price, req.providerId
        );
        return ResponseEntity.ok(s);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceEntity> getById(@PathVariable Long id) {
        return ResponseEntity.ok(serviceEntityService.getById(id));
    }

    @GetMapping("/provider/{providerId}")
    public ResponseEntity<List<ServiceEntity>> byProvider(@PathVariable Long providerId) {
        return ResponseEntity.ok(serviceEntityService.getByProvider(providerId));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        serviceEntityService.delete(id);
        return ResponseEntity.noContent().build();
    }
}