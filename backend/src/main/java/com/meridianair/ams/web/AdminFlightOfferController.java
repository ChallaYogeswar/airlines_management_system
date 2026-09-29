package com.meridianair.ams.web;

import com.meridianair.ams.booking.AdminFlightOfferService;
import com.meridianair.ams.dto.AdminFlightOfferSummary;
import com.meridianair.ams.dto.CreateFlightOfferRequest;
import com.meridianair.ams.dto.UpdateFlightOfferRequest;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/admin/flights")
@PreAuthorize("hasRole('ADMIN')")
public class AdminFlightOfferController {

    private final AdminFlightOfferService adminFlightOfferService;

    public AdminFlightOfferController(AdminFlightOfferService adminFlightOfferService) {
        this.adminFlightOfferService = adminFlightOfferService;
    }

    @GetMapping
    public Page<AdminFlightOfferSummary> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size
    ) {
        return adminFlightOfferService.listAll(PageRequest.of(page, Math.min(size, 200)));
    }

    @PostMapping
    public AdminFlightOfferSummary create(@Valid @RequestBody CreateFlightOfferRequest request) {
        return adminFlightOfferService.create(request);
    }

    @PutMapping("/{id}")
    public AdminFlightOfferSummary update(@PathVariable UUID id, @Valid @RequestBody UpdateFlightOfferRequest request) {
        return adminFlightOfferService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        adminFlightOfferService.delete(id);
    }
}
