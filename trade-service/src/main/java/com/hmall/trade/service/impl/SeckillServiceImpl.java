package com.hmall.trade.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.hmall.api.client.ItemClient;
import com.hmall.api.dto.ItemDTO;
import com.hmall.common.utils.UserContext;
import com.hmall.trade.domain.dto.SeckillOrderFormDTO;
import com.hmall.trade.domain.enums.SeckillStatus;
import com.hmall.trade.domain.mq.SeckillRequestMessage;
import com.hmall.trade.domain.po.SeckillActivity;
import com.hmall.trade.domain.po.SeckillOrder;
import com.hmall.trade.domain.po.SeckillStock;
import com.hmall.trade.domain.vo.SeckillItemVO;
import com.hmall.trade.domain.vo.SeckillOrderResultVO;
import com.hmall.trade.mapper.SeckillActivityMapper;
import com.hmall.trade.mapper.SeckillOrderMapper;
import com.hmall.trade.mapper.SeckillStockMapper;
import com.hmall.trade.mq.SeckillRequestMessageProducer;
import com.hmall.trade.service.ISeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SeckillServiceImpl implements ISeckillService {

    private static final int ACTIVITY_ENABLED = 1;
    private static final int DEFAULT_NUM = 1;

    private final SeckillActivityMapper activityMapper;
    private final SeckillStockMapper stockMapper;
    private final SeckillOrderMapper orderMapper;
    private final ItemClient itemClient;
    private final SeckillRequestMessageProducer requestMessageProducer;

    @Override
    public List<SeckillItemVO> querySeckillItems() {
        List<SeckillActivity> activities = activityMapper.selectList(null);
        if (activities == null || activities.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> seckillIds = activities.stream().map(SeckillActivity::getId).collect(Collectors.toSet());
        Map<Long, SeckillStock> stockMap = stockMapper.selectList(
                        Wrappers.<SeckillStock>lambdaQuery().in(SeckillStock::getSeckillId, seckillIds))
                .stream()
                .collect(Collectors.toMap(SeckillStock::getSeckillId, Function.identity(), (a, b) -> a));

        Set<Long> itemIds = activities.stream().map(SeckillActivity::getItemId).collect(Collectors.toSet());
        Map<Long, ItemDTO> itemMap = itemClient.queryItemByIds(itemIds).stream()
                .collect(Collectors.toMap(ItemDTO::getId, Function.identity(), (a, b) -> a));

        LocalDateTime now = LocalDateTime.now();
        Map<Long, ItemDTO> finalItemMap = itemMap;
        return activities.stream()
                .map(activity -> buildItemVO(activity, stockMap.get(activity.getId()), finalItemMap.get(activity.getItemId()), now))
                .collect(Collectors.toList());
    }

    @Override
    public SeckillOrderResultVO createSeckillOrder(SeckillOrderFormDTO formDTO) {
        Long userId = UserContext.getUser();
        if (userId == null) {
            return result(null, null, SeckillStatus.FAILED, null, null, "无法获取登录用户");
        }
        if (formDTO == null || formDTO.getSeckillId() == null || formDTO.getItemId() == null) {
            return result(null, null, SeckillStatus.INVALID_ACTIVITY, null, null);
        }
        int num = formDTO.getNum() == null ? DEFAULT_NUM : formDTO.getNum();
        if (num <= 0) {
            return result(formDTO.getSeckillId(), formDTO.getItemId(), SeckillStatus.FAILED, null, null, "购买数量必须大于0");
        }

        SeckillActivity activity = activityMapper.selectById(formDTO.getSeckillId());
        SeckillStatus invalidStatus = validateActivity(activity, formDTO.getItemId(), num, LocalDateTime.now());
        if (invalidStatus != null) {
            return result(formDTO.getSeckillId(), formDTO.getItemId(), invalidStatus, null, null);
        }

        Integer duplicateCount = orderMapper.selectCount(Wrappers.<SeckillOrder>lambdaQuery()
                .eq(SeckillOrder::getUserId, userId)
                .eq(SeckillOrder::getSeckillId, activity.getId()));
        if (duplicateCount != null && duplicateCount > 0) {
            return result(activity.getId(), activity.getItemId(), SeckillStatus.DUPLICATE_ORDER, null, null);
        }

        String requestId = generateRequestId();
        int totalFee = activity.getSeckillPrice() * num;
        SeckillRequestMessage message = new SeckillRequestMessage()
                .setRequestId(requestId)
                .setSeckillId(activity.getId())
                .setItemId(activity.getItemId())
                .setUserId(userId)
                .setNum(num)
                .setSeckillPrice(activity.getSeckillPrice())
                .setTotalFee(totalFee)
                .setCreateTime(LocalDateTime.now());
        try {
            requestMessageProducer.send(message);
        } catch (RuntimeException e) {
            return result(activity.getId(), activity.getItemId(), SeckillStatus.FAILED, null, null, "秒杀请求入队失败，请稍后重试");
        }

        return result(activity.getId(), activity.getItemId(), SeckillStatus.ACCEPTED, null, totalFee, requestId, SeckillStatus.ACCEPTED.getMessage());
    }

    private SeckillItemVO buildItemVO(SeckillActivity activity, SeckillStock stock, ItemDTO item, LocalDateTime now) {
        SeckillStatus status = computeStatus(activity, stock, now);
        SeckillItemVO vo = new SeckillItemVO();
        vo.setSeckillId(activity.getId());
        vo.setItemId(activity.getItemId());
        vo.setName(item == null ? null : item.getName());
        vo.setImage(item == null ? null : item.getImage());
        vo.setOriginalPrice(item == null ? null : item.getPrice());
        vo.setSeckillPrice(activity.getSeckillPrice());
        vo.setStartTime(activity.getStartTime());
        vo.setEndTime(activity.getEndTime());
        vo.setLimitCount(activity.getLimitCount());
        vo.setAvailableStock(stock == null ? 0 : stock.getAvailableStock());
        vo.setSoldCount(stock == null ? 0 : stock.getSoldCount());
        vo.setStatus(status.name());
        vo.setStatusText(status.getMessage());
        return vo;
    }

    private SeckillStatus validateActivity(SeckillActivity activity, Long itemId, int num, LocalDateTime now) {
        if (activity == null || !Objects.equals(activity.getItemId(), itemId) || !Objects.equals(activity.getStatus(), ACTIVITY_ENABLED)) {
            return SeckillStatus.INVALID_ACTIVITY;
        }
        if (now.isBefore(activity.getStartTime())) {
            return SeckillStatus.NOT_STARTED;
        }
        if (now.isAfter(activity.getEndTime())) {
            return SeckillStatus.ENDED;
        }
        if (activity.getLimitCount() != null && num > activity.getLimitCount()) {
            return SeckillStatus.LIMIT_EXCEEDED;
        }
        return null;
    }

    private SeckillStatus computeStatus(SeckillActivity activity, SeckillStock stock, LocalDateTime now) {
        if (!Objects.equals(activity.getStatus(), ACTIVITY_ENABLED)) {
            return SeckillStatus.DISABLED;
        }
        if (now.isBefore(activity.getStartTime())) {
            return SeckillStatus.NOT_STARTED;
        }
        if (now.isAfter(activity.getEndTime())) {
            return SeckillStatus.ENDED;
        }
        if (stock == null || stock.getAvailableStock() == null || stock.getAvailableStock() <= 0) {
            return SeckillStatus.SOLD_OUT;
        }
        return SeckillStatus.IN_PROGRESS;
    }

    private SeckillOrderResultVO result(Long seckillId, Long itemId, SeckillStatus status, Long orderId, Integer totalFee) {
        return result(seckillId, itemId, status, orderId, totalFee, null, status.getMessage());
    }

    private SeckillOrderResultVO result(Long seckillId, Long itemId, SeckillStatus status, Long orderId, Integer totalFee, String message) {
        return result(seckillId, itemId, status, orderId, totalFee, null, message);
    }

    private SeckillOrderResultVO result(Long seckillId, Long itemId, SeckillStatus status, Long orderId, Integer totalFee, String requestId, String message) {
        SeckillOrderResultVO vo = new SeckillOrderResultVO();
        vo.setSeckillOrderId(orderId);
        vo.setRequestId(requestId);
        vo.setSeckillId(seckillId);
        vo.setItemId(itemId);
        vo.setStatus(status.name());
        vo.setMessage(message);
        vo.setTotalFee(totalFee);
        return vo;
    }

    private String generateRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
