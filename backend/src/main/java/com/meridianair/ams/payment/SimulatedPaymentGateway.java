package com.meridianair.ams.payment;

import org.springframework.stereotype.Component;

import java.security.SecureRandom;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;

/**
 * Never talks to a real network, moves no real money - but the
 * validation it does perform is real: Luhn checksum on the card number
 * (the same algorithm every real card issuer uses to catch typos before
 * a charge attempt even reaches them) and an actual expiry-date check.
 *
 * Two fixed test numbers behave deterministically, matching a convention
 * anyone who's integrated Stripe will recognize:
 *   4242 4242 4242 4242  -> always succeeds
 *   4000 0000 0000 0002  -> always declines ("card declined")
 * Any other Luhn-valid, unexpired card succeeds ~90% of the time, so the
 * flow feels like a real gateway (occasional declines happen) without
 * every arbitrary test input needing to be memorized.
 */
@Component
public class SimulatedPaymentGateway implements PaymentGateway {

    private static final String ALWAYS_SUCCEEDS = "4242424242424242";
    private static final String ALWAYS_DECLINES = "4000000000000002";
    private static final double RANDOM_SUCCESS_RATE = 0.9;

    private final SecureRandom random = new SecureRandom();

    @Override
    public PaymentGatewayResult charge(PaymentGatewayRequest request) {
        String digits = request.cardNumber() == null ? "" : request.cardNumber().replaceAll("\\s|-", "");

        if (!isLuhnValid(digits)) {
            return PaymentGatewayResult.failed(null, "Card number failed validation");
        }

        String last4 = digits.substring(digits.length() - 4);

        if (!isUnexpired(request.expiryMonth(), request.expiryYear())) {
            return PaymentGatewayResult.failed(last4, "Card has expired");
        }
        if (request.cvv() == null || !request.cvv().matches("\\d{3,4}")) {
            return PaymentGatewayResult.failed(last4, "Invalid security code");
        }

        if (digits.equals(ALWAYS_DECLINES)) {
            return PaymentGatewayResult.failed(last4, "Card declined by issuer");
        }
        if (digits.equals(ALWAYS_SUCCEEDS)) {
            return PaymentGatewayResult.succeeded(generateReference(), last4);
        }

        boolean approved = random.nextDouble() < RANDOM_SUCCESS_RATE;
        return approved
                ? PaymentGatewayResult.succeeded(generateReference(), last4)
                : PaymentGatewayResult.failed(last4, "Card declined by issuer");
    }

    /** Standard mod-10 checksum (implemented the same way by every card
     * network) - doubles every second digit from the right, subtracts 9
     * from anything over 9, sums, and checks divisibility by 10. */
    private boolean isLuhnValid(String digits) {
        if (digits.length() < 12 || digits.length() > 19 || !digits.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int sum = 0;
        boolean doubleDigit = false;
        for (int i = digits.length() - 1; i >= 0; i--) {
            int n = digits.charAt(i) - '0';
            if (doubleDigit) {
                n *= 2;
                if (n > 9) n -= 9;
            }
            sum += n;
            doubleDigit = !doubleDigit;
        }
        return sum % 10 == 0;
    }

    private boolean isUnexpired(String month, String year) {
        try {
            int m = Integer.parseInt(month);
            String normalizedYear = year.length() == 2 ? "20" + year : year;
            YearMonth expiry = YearMonth.of(Integer.parseInt(normalizedYear), m);
            return !expiry.isBefore(YearMonth.now());
        } catch (NumberFormatException | DateTimeParseException | NullPointerException ex) {
            return false;
        }
    }

    private String generateReference() {
        return "sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18);
    }
}
