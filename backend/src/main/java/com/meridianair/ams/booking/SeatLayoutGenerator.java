package com.meridianair.ams.booking;

import com.meridianair.ams.domain.FlightOffer;
import com.meridianair.ams.domain.Seat;
import com.meridianair.ams.domain.SeatClass;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SeatLayoutGenerator {

    private static final String[] COLUMNS = {"A", "B", "C", "D"};

    /** Small flights (this app's seed data uses 2-12 seats, not a real
     * plane's 150+) stay all-economy; anything with a full front row's
     * worth of headroom gets one business row. */
    public List<Seat> generate(FlightOffer offer, int totalSeats) {
        int businessRows = totalSeats > COLUMNS.length * 2 ? 1 : 0;
        return generateRange(offer, 0, totalSeats, businessRows);
    }

    /** Appends seats for a capacity increase. Always ECONOMY, regardless
     * of whether the original layout had a business row - retroactively
     * reclassifying already-generated seats when capacity changes would
     * be more surprising than useful, so business class is fixed at
     * initial creation and growth only ever adds economy seats. */
    public List<Seat> generateAdditional(FlightOffer offer, int fromIndex, int toIndex) {
        return generateRange(offer, fromIndex, toIndex, 0);
    }

    private List<Seat> generateRange(FlightOffer offer, int fromIndex, int toIndex, int businessRows) {
        List<Seat> seats = new ArrayList<>();
        for (int index = fromIndex; index < toIndex; index++) {
            int row = index / COLUMNS.length + 1;
            String column = COLUMNS[index % COLUMNS.length];
            SeatClass seatClass = row <= businessRows ? SeatClass.BUSINESS : SeatClass.ECONOMY;
            seats.add(new Seat(offer, row + column, seatClass));
        }
        return seats;
    }
}
