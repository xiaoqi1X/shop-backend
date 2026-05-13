package com.hmall.seckill.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.hmall.api.client.ItemClient;
import com.hmall.api.dto.ItemDTO;
import com.hmall.common.utils.UserContext;
import com.hmall.seckill.domain.dto.SeckillOrderFormDTO;
import com.hmall.seckill.domain.enums.SeckillStatus;
import com.hmall.seckill.domain.mq.SeckillRequestMessage;
import com.hmall.seckill.domain.po.SeckillActivity;
import com.hmall.seckill.domain.po.SeckillStock;
import com.hmall.seckill.domain.redis.SeckillActivitySnapshot;
import com.hmall.seckill.domain.vo.SeckillItemVO;
import com.hmall.seckill.domain.vo.SeckillOrderResultVO;
import com.hmall.seckill.mapper.SeckillActivityMapper;
import com.hmall.seckill.mapper.SeckillStockMapper;
import com.hmall.seckill.mq.SeckillRequestMessageProducer;
import com.hmall.seckill.service.SeckillActivityCacheService;
import com.hmall.seckill.service.SeckillResultService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SeckillServiceImplTest {

    private static final long USER_ID = 100L;
    private static final long ITEM_ID = 1000001L;

    @Mock
    private SeckillActivityMapper activityMapper;
    @Mock
    private SeckillStockMapper stockMapper;
    @Mock
    private ItemClient itemClient;
    @Mock
    private SeckillRequestMessageProducer requestMessageProducer;
    @Mock
    private SeckillActivityCacheService activityCacheService;
    @Mock
    private SeckillResultService resultService;

    private SeckillServiceImpl seckillService;

    @BeforeEach
    void setUp() {
        seckillService = new SeckillServiceImpl(activityMapper, stockMapper, itemClient, requestMessageProducer, activityCacheService, resultService);
        UserContext.setUser(USER_ID);
    }

    @AfterEach
    void tearDown() {
        UserContext.removeUser();
    }

    @Test
    void querySeckillItemsShouldComputeDisplayStatuses() {
        LocalDateTime now = LocalDateTime.now();
        List<SeckillActivity> activities = Arrays.asList(
                activity(1L, now.minusHours(1), now.plusHours(1), 1),
                activity(2L, now.plusHours(1), now.plusHours(2), 1),
                activity(3L, now.minusHours(2), now.minusHours(1), 1),
                activity(4L, now.minusHours(1), now.plusHours(1), 1),
                activity(5L, now.minusHours(1), now.plusHours(1), 0)
        );
        when(activityMapper.selectList(null)).thenReturn(activities);
        when(stockMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(Arrays.asList(
                stock(1L, 8, 2),
                stock(2L, 10, 0),
                stock(3L, 10, 0),
                stock(4L, 0, 10),
                stock(5L, 10, 0)
        ));
        when(itemClient.queryItemByIds(anyCollection())).thenReturn(Collections.singletonList(item()));

        List<SeckillItemVO> result = seckillService.querySeckillItems();

        assertThat(result).extracting(SeckillItemVO::getStatus)
                .containsExactly(
                        SeckillStatus.IN_PROGRESS.name(),
                        SeckillStatus.NOT_STARTED.name(),
                        SeckillStatus.ENDED.name(),
                        SeckillStatus.SOLD_OUT.name(),
                        SeckillStatus.DISABLED.name()
                );
        assertThat(result.get(0).getName()).isEqualTo("测试商品");
        assertThat(result.get(0).getOriginalPrice()).isEqualTo(19900);
    }

    @Test
    void createSeckillOrderShouldSendRequestMessageWhenValid() {
        LocalDateTime now = LocalDateTime.now();
        when(activityCacheService.getActivity(1L)).thenReturn(snapshot(1L, now.minusHours(1), now.plusHours(1), 1));

        SeckillOrderResultVO result = seckillService.createSeckillOrder(form(1L, ITEM_ID, 1));

        assertThat(result.getStatus()).isEqualTo(SeckillStatus.ACCEPTED.name());
        assertThat(result.getRequestId()).isNotBlank();
        assertThat(result.getSeckillOrderId()).isNull();
        assertThat(result.getTotalFee()).isEqualTo(9900);

        ArgumentCaptor<SeckillRequestMessage> captor = ArgumentCaptor.forClass(SeckillRequestMessage.class);
        verify(requestMessageProducer).send(captor.capture());
        SeckillRequestMessage message = captor.getValue();
        assertThat(message.getRequestId()).isEqualTo(result.getRequestId());
        assertThat(message.getUserId()).isEqualTo(USER_ID);
        assertThat(message.getSeckillId()).isEqualTo(1L);
        assertThat(message.getItemId()).isEqualTo(ITEM_ID);
        assertThat(message.getNum()).isEqualTo(1);
        assertThat(message.getSeckillPrice()).isEqualTo(9900);
        assertThat(message.getTotalFee()).isEqualTo(9900);
        assertThat(message.getCreateTime()).isNotNull();
        verify(resultService).save(message, SeckillStatus.ACCEPTED, null);
        verify(stockMapper, never()).deductStock(any(), any());
    }

    @Test
    void createSeckillOrderShouldReturnNotReadyWhenActivitySnapshotMissing() {
        when(activityCacheService.getActivity(1L)).thenReturn(null);

        SeckillOrderResultVO result = seckillService.createSeckillOrder(form(1L, ITEM_ID, 1));

        assertThat(result.getStatus()).isEqualTo(SeckillStatus.NOT_READY.name());
        verify(requestMessageProducer, never()).send(any(SeckillRequestMessage.class));
        verify(stockMapper, never()).deductStock(any(), any());
    }

    @Test
    void createSeckillOrderShouldNotQueryOrderMapperForDuplicateCheckAtEntry() {
        LocalDateTime now = LocalDateTime.now();
        when(activityCacheService.getActivity(1L)).thenReturn(snapshot(1L, now.minusHours(1), now.plusHours(1), 1));

        SeckillOrderResultVO result = seckillService.createSeckillOrder(form(1L, ITEM_ID, 1));

        assertThat(result.getStatus()).isEqualTo(SeckillStatus.ACCEPTED.name());
        verify(requestMessageProducer).send(any(SeckillRequestMessage.class));
        verify(stockMapper, never()).deductStock(any(), any());
    }

    @Test
    void createSeckillOrderShouldReturnFailedWhenMessageSendFails() {
        LocalDateTime now = LocalDateTime.now();
        when(activityCacheService.getActivity(1L)).thenReturn(snapshot(1L, now.minusHours(1), now.plusHours(1), 1));
        doAnswer(invocation -> {
            throw new RuntimeException("mq unavailable");
        }).when(requestMessageProducer).send(any(SeckillRequestMessage.class));

        SeckillOrderResultVO result = seckillService.createSeckillOrder(form(1L, ITEM_ID, 1));

        assertThat(result.getStatus()).isEqualTo(SeckillStatus.FAILED.name());
        assertThat(result.getMessage()).isEqualTo("Failed to enqueue seckill request, please retry later");
        verify(stockMapper, never()).deductStock(any(), any());
    }

    @Test
    void createSeckillOrderShouldRejectNotStartedEndedAndLimitExceeded() {
        LocalDateTime now = LocalDateTime.now();

        when(activityCacheService.getActivity(2L)).thenReturn(snapshot(2L, now.plusHours(1), now.plusHours(2), 1));
        assertThat(seckillService.createSeckillOrder(form(2L, ITEM_ID, 1)).getStatus())
                .isEqualTo(SeckillStatus.NOT_STARTED.name());

        when(activityCacheService.getActivity(3L)).thenReturn(snapshot(3L, now.minusHours(2), now.minusHours(1), 1));
        assertThat(seckillService.createSeckillOrder(form(3L, ITEM_ID, 1)).getStatus())
                .isEqualTo(SeckillStatus.ENDED.name());

        when(activityCacheService.getActivity(4L)).thenReturn(snapshot(4L, now.minusHours(1), now.plusHours(1), 1));
        assertThat(seckillService.createSeckillOrder(form(4L, ITEM_ID, 2)).getStatus())
                .isEqualTo(SeckillStatus.LIMIT_EXCEEDED.name());

        verify(stockMapper, never()).deductStock(any(), any());
    }

    @Test
    void createSeckillOrderShouldRejectInvalidActivity() {
        LocalDateTime now = LocalDateTime.now();
        when(activityCacheService.getActivity(99L)).thenReturn(snapshot(99L, now.minusHours(1), now.plusHours(1), 1).setItemId(999L));

        SeckillOrderResultVO result = seckillService.createSeckillOrder(form(99L, ITEM_ID, 1));

        assertThat(result.getStatus()).isEqualTo(SeckillStatus.INVALID_ACTIVITY.name());
        verify(stockMapper, never()).deductStock(any(), any());
    }

    private SeckillOrderFormDTO form(Long seckillId, Long itemId, Integer num) {
        SeckillOrderFormDTO form = new SeckillOrderFormDTO();
        form.setSeckillId(seckillId);
        form.setItemId(itemId);
        form.setNum(num);
        return form;
    }

    private SeckillActivity activity(Long seckillId, LocalDateTime startTime, LocalDateTime endTime, Integer status) {
        return new SeckillActivity()
                .setId(seckillId)
                .setItemId(ITEM_ID)
                .setSeckillPrice(9900)
                .setStartTime(startTime)
                .setEndTime(endTime)
                .setLimitCount(1)
                .setStatus(status);
    }

    private SeckillActivitySnapshot snapshot(Long seckillId, LocalDateTime startTime, LocalDateTime endTime, Integer status) {
        return new SeckillActivitySnapshot()
                .setSeckillId(seckillId)
                .setItemId(ITEM_ID)
                .setSeckillPrice(9900)
                .setStartTime(startTime)
                .setEndTime(endTime)
                .setLimitCount(1)
                .setStatus(status);
    }

    private SeckillStock stock(Long seckillId, Integer availableStock, Integer soldCount) {
        return new SeckillStock()
                .setId(seckillId)
                .setSeckillId(seckillId)
                .setTotalStock(availableStock + soldCount)
                .setAvailableStock(availableStock)
                .setSoldCount(soldCount);
    }

    private ItemDTO item() {
        ItemDTO item = new ItemDTO();
        item.setId(ITEM_ID);
        item.setName("测试商品");
        item.setImage("http://example.com/item.png");
        item.setPrice(19900);
        return item;
    }
}
