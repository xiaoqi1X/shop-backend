package com.hmall.pay.mq;

import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class PaySuccessMessageProducer implements InitializingBean, DisposableBean {

    private static final String NAME_SERVER = "192.168.150.102:9876";
    private static final String PRODUCER_GROUP = "pay-service-producer-group";
    private static final String TOPIC = "trade_pay_success_topic";
    private static final String TAG = "pay_success";

    private DefaultMQProducer producer;

    @Override
    public void afterPropertiesSet() throws Exception {
        producer = new DefaultMQProducer(PRODUCER_GROUP);
        producer.setNamesrvAddr(NAME_SERVER);
        producer.start();
    }

    public void sendPaySuccessMessage(Long orderId) {
        try {
            Message message = new Message(
                    TOPIC,
                    TAG,
                    orderId.toString().getBytes(StandardCharsets.UTF_8)
            );
            SendResult result = producer.send(message);
            log.debug("Sent pay success message, orderId={}, result={}", orderId, result);
        } catch (Exception e) {
            throw new RuntimeException("Failed to send pay success message, orderId=" + orderId, e);
        }
    }

    @Override
    public void destroy() {
        if (producer != null) {
            producer.shutdown();
        }
    }
}
