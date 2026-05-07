package com.hmall.seckill.controller;

import com.hmall.seckill.domain.dto.SeckillOrderFormDTO;
import com.hmall.seckill.domain.vo.SeckillItemVO;
import com.hmall.seckill.domain.vo.SeckillOrderResultVO;
import com.hmall.seckill.service.ISeckillService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Api(tags = "Seckill APIs")
@RestController
@RequestMapping("/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final ISeckillService seckillService;

    @ApiOperation("Query seckill items")
    @GetMapping("/items")
    public List<SeckillItemVO> querySeckillItems() {
        return seckillService.querySeckillItems();
    }

    @ApiOperation("Create seckill order")
    @PostMapping("/orders")
    public SeckillOrderResultVO createSeckillOrder(@RequestBody SeckillOrderFormDTO formDTO) {
        return seckillService.createSeckillOrder(formDTO);
    }
}
