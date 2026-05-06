package com.hmall.trade.service;

import com.hmall.trade.domain.dto.SeckillOrderFormDTO;
import com.hmall.trade.domain.vo.SeckillItemVO;
import com.hmall.trade.domain.vo.SeckillOrderResultVO;

import java.util.List;

public interface ISeckillService {

    List<SeckillItemVO> querySeckillItems();

    SeckillOrderResultVO createSeckillOrder(SeckillOrderFormDTO formDTO);
}
