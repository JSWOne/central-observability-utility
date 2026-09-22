package com.jswone.demo;

import com.jswone.observability.ErrorEventLogger;
import com.jswone.observability.model.ErrorEventLog;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One endpoint per feature of the starter, so every path can be driven with a single curl.
 * The request/response logging aspect advises this class through
 * {@code jsw.observability.logging.aspect.base-packages}.
 */
@RestController
public class DemoController {

    private static final Logger log = LoggerFactory.getLogger(DemoController.class);

    private final ErrorEventLogger errorEventLogger;
    private final DemoAsyncWorker asyncWorker;

    public DemoController(ErrorEventLogger errorEventLogger, DemoAsyncWorker asyncWorker) {
        this.errorEventLogger = errorEventLogger;
        this.asyncWorker = asyncWorker;
    }

    /** Success path: masked arguments and result in the aspect's [REQUEST SUCCESS] block. */
    @GetMapping("/demo/hello")
    public String hello(@RequestParam(defaultValue = "john.doe@gmail.com") String email) {
        log.info("Handling greeting for {}", email);
        return "hello " + email;
    }

    /** PII sweep: every pattern PiiMasker knows, in one log line and one response. */
    @GetMapping("/demo/pii")
    public String pii() {
        String sensitive = "email=john.doe@gmail.com pan=ABCDE1234F gstin=27ABCDE1234F1Z5"
                + " mobile=9876543210 card=4111111111111111 aadhaar=123412341234"
                + " authorization=Bearer eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.signature";
        log.info("Sensitive payload: {}", sensitive);
        return sensitive;
    }

    /** Crosses the slow-call threshold, so the aspect logs a WARN instead of the success block. */
    @GetMapping("/demo/slow")
    public String slow() throws InterruptedException {
        Thread.sleep(2500);
        return "slow done";
    }

    /** Failure path: aspect [REQUEST FAILED] at ERROR, plus a structured business error event. */
    @GetMapping("/demo/fail")
    public String fail(@RequestParam(defaultValue = "P123") String productId) {
        try {
            throw new IllegalStateException("product " + productId + " is out of stock");
        } catch (IllegalStateException failure) {
            errorEventLogger.log(ErrorEventLog.builder()
                    .eventName("add_to_cart_failure")
                    .system("demo-boot2")
                    .failureReason(failure.getMessage())
                    .attributes(Map.of("productId", productId))
                    .build());
            throw failure;
        }
    }

    /** Hands work to another thread; the traceId in the async log line proves MDC propagation. */
    @GetMapping("/demo/async")
    public String async() {
        log.info("Dispatching async work, traceId={}", MDC.get("traceId"));
        asyncWorker.doWork();
        return "dispatched";
    }
}
