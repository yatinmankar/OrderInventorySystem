package com.example.payment.listener;

import com.example.common.Topics;
import com.example.common.aop.TraceOrder;
import com.example.common.events.Messages.*;
import com.example.payment.service.PaymentService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentListener {

    private final PaymentService paymentService;
    private final KafkaTemplate<String, Object> jsonKafkaTemplate;

    public PaymentListener(PaymentService paymentService, KafkaTemplate<String, Object> jsonKafkaTemplate) {
        this.paymentService = paymentService;
        this.jsonKafkaTemplate = jsonKafkaTemplate;
    }

    @KafkaListener(topics = Topics.PAYMENT_PROCESS_CMD, groupId = "payment-service")
    @TraceOrder
    public void onProcess(ProcessPaymentCommand cmd) {
        // On a transient PSP outage this throws -> Kafka error handler retries -> DLQ (no reply sent).
        PaymentService.Outcome outcome = paymentService.process(cmd.orderId(), cmd.amount());
        jsonKafkaTemplate.send(Topics.PAYMENT_PROCESS_REPLY, cmd.orderId(),
                new PaymentReply(cmd.orderId() + ":payment-reply", cmd.orderId(),
                        outcome.success(), outcome.paymentId(), outcome.reason()));
    }

    @KafkaListener(topics = Topics.PAYMENT_REFUND_CMD, groupId = "payment-service")
    @TraceOrder
    public void onRefund(RefundPaymentCommand cmd) {
        paymentService.refund(cmd.orderId());
    }
}
