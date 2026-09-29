package com.meridianair.ams.web;

import com.meridianair.ams.booking.BookingService;
import com.meridianair.ams.dto.BookingSummary;
import com.meridianair.ams.dto.CreateBookingRequest;
import com.meridianair.ams.security.CurrentUserProvider;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    private final BookingService bookingService;
    private final CurrentUserProvider currentUserProvider;

    public BookingController(BookingService bookingService, CurrentUserProvider currentUserProvider) {
        this.bookingService = bookingService;
        this.currentUserProvider = currentUserProvider;
    }

    @PostMapping
    public BookingSummary create(@Valid @RequestBody CreateBookingRequest request) {
        return bookingService.createBooking(currentUserProvider.require(), request);
    }

    @GetMapping
    public List<BookingSummary> listMine() {
        return bookingService.listMine(currentUserProvider.require());
    }

    @DeleteMapping("/{id}")
    public void cancel(@PathVariable UUID id) {
        bookingService.cancelBooking(currentUserProvider.require(), id);
    }
}
