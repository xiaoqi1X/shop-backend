package com.hmall.seckill.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmall.seckill.domain.po.SeckillStock;
import org.apache.ibatis.annotations.Param;

public interface SeckillStockMapper extends BaseMapper<SeckillStock> {

    int deductStock(@Param("seckillId") Long seckillId, @Param("num") Integer num);
}
