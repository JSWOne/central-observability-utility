package com.jswone.observability.masking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PiiMaskerTest {

    @Test
    void masksEmailButKeepsFirstCharacterAndDomain() {
        // The exact shape of the log line that motivated this work.
        String masked =
                PiiMasker.mask(
                        "No data for key razorPayBA found for customer email john14@gmail.com");

        assertFalse(masked.contains("john14@gmail.com"));
        assertTrue(masked.contains("j***@gmail.com"), masked);
        // Non-PII context must survive so the line is still diagnosable.
        assertTrue(masked.contains("razorPayBA"), masked);
    }

    @Test
    void masksJwt() {
        String masked =
                PiiMasker.mask(
                        "token=eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjM0NSJ9.dBjftJeZ4CVPmB92K27uhbUJU1p1r_wW1g");
        assertEquals("token=eyJ***", masked);
    }

    @Test
    void masksBearerCredentials() {
        assertEquals(
                "Authorization: Bearer ***",
                PiiMasker.mask("Authorization: Bearer abcdef1234567890"));
    }

    @Test
    void masksPanAndGstin() {
        assertEquals("pan=***", PiiMasker.mask("pan=ABCDE1234F"));

        String maskedGstin = PiiMasker.mask("gstin=27AAPFU0939F1ZV");
        assertFalse(maskedGstin.contains("AAPFU0939F"), maskedGstin);
        assertTrue(maskedGstin.startsWith("gstin=27"), maskedGstin);
    }

    @Test
    void masksMobileKeepingEnoughToDistinguish() {
        String masked = PiiMasker.mask("mobile=9876543210");
        assertFalse(masked.contains("9876543210"));
        assertEquals("mobile=987***10", masked);
    }

    @Test
    void masksCardLikeDigitRuns() {
        String masked = PiiMasker.mask("card=4111111111111111");
        assertFalse(masked.contains("4111111111111111"));
        assertEquals("card=411111***1111", masked);
    }

    @Test
    void leavesOrdinaryIdentifiersAlone() {
        // Cart / order ids and short numbers must not be mangled, or logs become unusable.
        String text = "cartId=b1f2c3d4-1111-2222-3333-444455556666 qty=250 amount=13500.75";
        assertEquals(text, PiiMasker.mask(text));
    }

    @Test
    void isNullSafe() {
        assertNull(PiiMasker.mask(null));
        assertEquals("", PiiMasker.mask(""));
    }
}
