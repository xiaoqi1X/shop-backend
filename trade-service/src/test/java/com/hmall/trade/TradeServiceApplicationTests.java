package com.hmall.trade;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import com.hmall.trade.mq.PaySuccessMessageConsumer;

@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.cloud.nacos.discovery.enabled=false",
                "spring.cloud.service-registry.auto-registration.enabled=false"
        }
)
class TradeServiceApplicationTests {

    @MockBean
    private PaySuccessMessageConsumer paySuccessMessageConsumer;

    @Test
    void contextLoads() {
    }

}
