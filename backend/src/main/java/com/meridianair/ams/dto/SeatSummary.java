package com.meridianair.ams.dto;

import com.meridianair.ams.domain.SeatClass;
import com.meridianair.ams.domain.SeatStatus;

import java.util.UUID;

/** heldByMe lets the frontend distinguish "held by someone else" (just
 * show unavailable) from "held by you" (show as your current selection,
 * even after a page reload mid-hold). */
public record SeatSummary(
        UUID id,
        String seatNumber,
        SeatClass seatClass,
        SeatStatus status,
        boolean heldByMe
) {}
