package com.soft2242.shop.service;

import com.soft2242.shop.common.result.PageResult;
import com.soft2242.shop.entity.UserOrder;
import com.baomidou.mybatisplus.extension.service.IService;
import com.soft2242.shop.query.CancelGoodsQuery;
import com.soft2242.shop.query.OrderPreQuery;
import com.soft2242.shop.query.OrderQuery;
import com.soft2242.shop.vo.OrderDetailVO;
import com.soft2242.shop.vo.SubmitOrderVO;
import com.soft2242.shop.vo.UserOrderVO;

import java.util.List;

/**
 * <p>
 * 服务类
 * </p>
 *
 * @author ycshang
 * @since 2023-11-07
 */
public interface UserOrderService extends IService<UserOrder> {

    /**
     * 提交订单
     *
     * @param userOrderVO
     * @return
     */
    Integer addGoodsOrder(UserOrderVO userOrderVO);


    /**
     * 订单详情
     *
     * @param id
     * @return
     */
    OrderDetailVO getOrderDetail(Integer id);


    /**
     * 获取预付订单
     *
     * @param userId
     * @return
     */
    SubmitOrderVO getPreOrderDetail(Integer userId);


    /**
     * 立即购买
     *
     * @param query
     * @return
     */
    SubmitOrderVO getPreNowOrderDetail(OrderPreQuery query);


    /**
     * 再次购买
     *
     * @param id
     * @return
     */
    SubmitOrderVO getRepurchaseOrderDetail(Integer id);

    /**
     * 订单列表
     *
     * @param query
     * @return
     */
    PageResult<OrderDetailVO> getOrderList(OrderQuery query);


    /**
     * 取消订单
     *
     * @param query
     * @return
     */

    OrderDetailVO cancelOrder(CancelGoodsQuery query);


    /**
     * 删除订单
     *
     * @param ids
     * @param userId
     */
    void deleteOrder(List<Integer> ids, Integer userId);

    /**
     * 订单支付
     *
     * @param id
     */
    void payOrder(Integer id);

    /**
     * 模拟发货
     *
     * @param id
     */
    void consignOrder(Integer id);


}
