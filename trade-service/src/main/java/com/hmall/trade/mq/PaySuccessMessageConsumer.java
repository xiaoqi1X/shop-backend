package com.hmall.trade.mq;

import com.hmall.trade.service.IOrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaySuccessMessageConsumer implements InitializingBean, DisposableBean {

    private static final String NAME_SERVER = "192.168.150.102:9876";
    private static final String CONSUMER_GROUP = "trade-service-consumer-group";
    private static final String TOPIC = "trade_pay_success_topic";
    private static final String TAG = "pay_success";

    private final IOrderService orderService;

    private DefaultMQPushConsumer consumer;

    @Override
    public void afterPropertiesSet() throws Exception {
        consumer = new DefaultMQPushConsumer(CONSUMER_GROUP);
        consumer.setNamesrvAddr(NAME_SERVER);
        consumer.subscribe(TOPIC, TAG);
        consumer.registerMessageListener((MessageListenerConcurrently) (msgs, context) -> {
            for (MessageExt msg : msgs) {
                try {
                    String body = new String(msg.getBody(), StandardCharsets.UTF_8);
                    Long orderId = Long.valueOf(body);
                    orderService.markOrderPaySuccess(orderId);
                    log.debug("Consumed pay success message, orderId={}, msgId={}", orderId, msg.getMsgId());
                } catch (Exception e) {
                    log.error("Failed to consume pay success message, msgId={}", msg.getMsgId(), e);
                    return ConsumeConcurrentlyStatus.RECONSUME_LATER;
                }
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
    }

    @Override
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
