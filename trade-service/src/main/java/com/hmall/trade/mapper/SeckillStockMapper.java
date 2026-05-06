package com.hmall.trade.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmall.trade.domain.po.SeckillStock;
import org.apache.ibatis.annotations.Param;

public interface SeckillStockMapper extends BaseMapper<SeckillStock> {

    int deductStock(@Param("seckillId") Long seckillId, @Param("num") Integer num);
}
