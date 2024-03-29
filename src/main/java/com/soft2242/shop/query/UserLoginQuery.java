package com.soft2242.shop.query;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * @author ycshang
 */
@Data
public class UserLoginQuery {
    @NotBlank(message = "code不能为空")
    private String code;
}