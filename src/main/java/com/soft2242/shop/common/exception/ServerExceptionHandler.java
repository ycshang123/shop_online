package com.soft2242.shop.common.exception;

import com.soft2242.shop.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * @author ycshang
 */
@Slf4j
@RestControllerAdvice
public class ServerExceptionHandler {

    /**
     * 处理自定义异常
     *
     * @param exception
     * @return
     */
    @ExceptionHandler(ServerException.class)
    public Result<String> handleException(ServerException exception) {
        return Result.error(exception.getCode(), exception.getMsg());
    }

    /**
     * 数据校验异常
     *
     * @param exception
     * @return
     */
    @ExceptionHandler(BindException.class)
    public Result<String> bindException(BindException exception) {
        FieldError fieldError = exception.getFieldError();
        assert fieldError != null;
        return Result.error(fieldError.getDefaultMessage());
    }

    /**
     * 其他异常情况
     *
     * @param exception
     * @return
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception exception) {
        log.error(exception.getMessage(), exception);
        return Result.error(ErrorCode.INTERNAL_SERVER_ERROR);
    }


}
