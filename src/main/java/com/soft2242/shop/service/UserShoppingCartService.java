package com.soft2242.shop.service;

import com.soft2242.shop.entity.UserShoppingCart;
import com.baomidou.mybatisplus.extension.service.IService;
import com.soft2242.shop.query.CartQuery;
import com.soft2242.shop.query.EditCartQuery;
import com.soft2242.shop.vo.CartGoodsVO;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author ycshang
 * @since 2023-11-07
 */
public interface UserShoppingCartService extends IService<UserShoppingCart> {

    /**
     * 添加购物车
     *
     * @param query
     * @return
     */
    CartGoodsVO addShopCart(CartQuery query);


    /**
     * 获取购物车列表
     *
     * @param userId
     * @return
     */
    List<CartGoodsVO> shopCartList(Integer userId);

    /**
     * 修改购物车信息
     *
     * @param query
     * @return
     */
    CartGoodsVO editCart(EditCartQuery query);

}
