package com.hmdp.config;

import com.hmdp.dto.Result;
import com.hmdp.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class WebExceptionAdviceTest {

    private final WebExceptionAdvice advice = new WebExceptionAdvice();

    @Test
    void exposesExpectedBusinessMessage() {
        Result result = advice.handleBusinessException(new BusinessException("秒杀活动不存在"));

        assertFalse(result.getSuccess());
        assertEquals("秒杀活动不存在", result.getErrorMsg());
    }

    @Test
    void hidesUnexpectedRuntimeExceptionDetails() {
        Result result = advice.handleRuntimeException(new RuntimeException("database password leaked"));

        assertFalse(result.getSuccess());
        assertEquals("服务器异常", result.getErrorMsg());
    }
}
