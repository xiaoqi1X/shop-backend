package com.hmall.trade.controller;

import com.hmall.trade.domain.dto.SeckillOrderFormDTO;
import com.hmall.trade.domain.vo.SeckillItemVO;
import com.hmall.trade.domain.vo.SeckillOrderResultVO;
import com.hmall.trade.service.ISeckillService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api(tags = "秒杀业务接口")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final ISeckillService seckillService;

    @ApiOperation("查询秒杀商品列表")
    @GetMapping("/items")
    public List<SeckillItemVO> querySeckillItems() {
        return seckillService.querySeckillItems();
    }

    @ApiOperation("秒杀下单")
    @PostMapping("/orders")
    public SeckillOrderResultVO createSeckillOrder(@RequestBody SeckillOrderFormDTO formDTO) {
        return seckillService.createSeckillOrder(formDTO);
    }
}
