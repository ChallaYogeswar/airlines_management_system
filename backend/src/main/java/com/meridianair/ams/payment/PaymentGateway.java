package com.meridianair.ams.payment;

import java.math.BigDecimal;

/**
 * Every real payment integration boils down to "charge this amount with
 * these card details, tell me if it worked" at the point where booking
 * logic touches it - the actual complexity (tokenization, 3DS,
 * retries, webhooks) belongs behind this seam, not in front of it.
 *
 * This app ships {@link SimulatedPaymentGateway} as the only
 * implementation, because integrating a real provider needs an account
 * and API keys nobody but you can provision. To go live: implement this
 * interface against Stripe/Razorpay/etc, mark it @Primary (or remove
 * SimulatedPaymentGateway's @Component), and nothing in
 * PaymentService or the booking flow needs to change - they only know
 * about PaymentGateway, never the concrete implementation.
 */
public interface PaymentGateway {
    PaymentGatewayResult charge(PaymentGatewayRequest request);

    record PaymentGatewayRequest(
            BigDecimal amount,
            String cardNumber,
            String expiryMonth,
            String expiryYear,
            String cvv,
            String cardholderName
    ) {}

    record PaymentGatewayResult(
            boolean success,
            String providerReference,
            String cardLast4,
            String declineReason
    ) {
        public static PaymentGatewayResult succeeded(String providerReference, String cardLast4) {
            return new PaymentGatewayResult(true, providerReference, cardLast4, null);
        }

        public static PaymentGatewayResult failed(String cardLast4, String declineReason) {
            return new PaymentGatewayResult(false, null, cardLast4, declineReason);
        }
    }
}
