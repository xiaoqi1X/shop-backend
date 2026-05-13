package com.hmall.seckill.service;

import com.hmall.seckill.domain.dto.SeckillOrderFormDTO;
import com.hmall.seckill.domain.vo.SeckillItemVO;
import com.hmall.seckill.domain.vo.SeckillOrderResultVO;

import java.util.List;

public interface ISeckillService {

    List<SeckillItemVO> querySeckillItems();

    SeckillOrderResultVO createSeckillOrder(SeckillOrderFormDTO formDTO);

    SeckillOrderResultVO queryOrderResult(String requestId);
}
