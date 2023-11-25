package com.soft2242.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.soft2242.shop.common.exception.ServerException;
import com.soft2242.shop.common.result.PageResult;
import com.soft2242.shop.convert.UserAddressConvert;
import com.soft2242.shop.convert.UserOrderDetailConvert;
import com.soft2242.shop.entity.*;
import com.soft2242.shop.enums.OrderStatusEnum;
import com.soft2242.shop.mapper.*;
import com.soft2242.shop.query.CancelGoodsQuery;
import com.soft2242.shop.query.OrderGoodsQuery;
import com.soft2242.shop.query.OrderPreQuery;
import com.soft2242.shop.query.OrderQuery;
import com.soft2242.shop.service.UserOrderGoodsService;
import com.soft2242.shop.service.UserOrderService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soft2242.shop.service.UserShippingAddressService;
import com.soft2242.shop.vo.*;
import net.bytebuddy.asm.Advice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author ycshang
 * @since 2023-11-07
 */
@Service
public class UserOrderServiceImpl extends ServiceImpl<UserOrderMapper, UserOrder> implements UserOrderService {

    @Autowired
    private GoodsMapper goodsMapper;

    @Autowired
    private UserOrderGoodsService userOrderGoodsService;

    @Autowired
    private UserShippingAddressMapper userShippingAddressMapper;

    @Autowired
    private UserOrderGoodsMapper userOrderGoodsMapper;


    @Autowired
    private UserShoppingCartMapper userShoppingCartMapper;
    private ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);

    private ScheduledFuture<?> cancelTask;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Integer addGoodsOrder(UserOrderVO userOrderVO) {
//        1、声明订单总支付费用、总运费、总购买件数
        BigDecimal totalPrice = new BigDecimal(0);
        Integer totalCount = 0;
        BigDecimal totalFreight = new BigDecimal(0);
        UserOrder userOrder = new UserOrder();
        userOrder.setUserId(userOrderVO.getUserId());
        userOrder.setAddressId(userOrderVO.getAddressId());
//        订单编号使用uuid 随机生成不重复的编号
        userOrder.setOrderNumber(UUID.randomUUID().toString());
        userOrder.setDeliveryTimeType(userOrder.getDeliveryTimeType());
//        提交订单默认状态为待付款
        userOrder.setStatus(OrderStatusEnum.WAITING_FOR_SHIPMENT.getValue());
        if (userOrderVO.getBuyerMessage() != null) {
            userOrder.setBuyerMessage(userOrderVO.getBuyerMessage());
        }

        userOrder.setPayType(userOrderVO.getPayType());
        userOrder.setPayChannel(userOrderVO.getPayChannel());
        baseMapper.insert(userOrder);
//        异步取消创建的订单，如果订单创建30分钟后用户没有付款，修改订单状态为取消
        scheduleOrderCancel(userOrder);
        List<UserOrderGoods> orderGoodsList = new ArrayList<>();
//        遍历用户购买的商品列表， 订单 - 商品表批量添加数据

        for (OrderGoodsQuery goodsVO : userOrderVO.getGoods()) {
            Goods goods = goodsMapper.selectById(goodsVO.getId());
            if (goodsVO.getCount() > goods.getInventory()) {
                throw new ServerException(goods.getName() + "库存数量不足");
            }

            UserOrderGoods userOrderGoods = new UserOrderGoods();
            userOrderGoods.setGoodsId(goods.getId());
            userOrderGoods.setName(goods.getName());
            userOrderGoods.setCover(goods.getCover());
            userOrderGoods.setOrderId(userOrder.getId());
            userOrderGoods.setCount(goodsVO.getCount());
            userOrderGoods.setAttrsText(goodsVO.getSkus());
            userOrderGoods.setFreight(goods.getFreight());
            userOrderGoods.setPrice(goods.getPrice());

//            计算总付款金额，使用BigDecimal 避免精度缺失
            BigDecimal freight = new BigDecimal(userOrderGoods.getFreight().toString());
            BigDecimal goodsPrice = new BigDecimal(userOrderGoods.getPrice().toString());
            BigDecimal count = new BigDecimal(userOrderGoods.getCount().toString());


//            减库存
            goods.setInventory(goods.getInventory() - goodsVO.getCount());

//            增加销量
            goods.setSalesCount(goodsVO.getCount());
            BigDecimal price = goodsPrice.multiply(count).add(freight);

            totalPrice = totalPrice.add(price);
            totalCount += goodsVO.getCount();
            totalFreight = totalFreight.add(freight);
            orderGoodsList.add(userOrderGoods);
            goodsMapper.updateById(goods);
        }


        userOrderGoodsService.batchUserOrderGoods(orderGoodsList);


        userOrder.setTotalPrice(totalPrice.doubleValue());
        userOrder.setTotalCount(totalCount);
        userOrder.setTotalFreight(totalFreight.doubleValue());
        baseMapper.updateById(userOrder);
        return userOrder.getId();
    }


    @Override
    public OrderDetailVO getOrderDetail(Integer id) {
//        1、查询订单信息
        UserOrder userOrder = baseMapper.selectById(id);
        if (userOrder == null) {
            throw new ServerException("订单信息不存在");
        }

        OrderDetailVO orderDetailVO = UserOrderDetailConvert.INSTANCE.convertToOrderDetailVO(userOrder);

//        2、收货人信息

        UserShippingAddress userShippingAddress = userShippingAddressMapper.selectById(userOrder.getAddressId());

        if (userShippingAddress == null) {
            throw new ServerException("收货地址信息不存在");
        }

        orderDetailVO.setReceiverContact(userShippingAddress.getReceiver());
        orderDetailVO.setReceiverMobile(userShippingAddress.getContact());
        orderDetailVO.setReceiverAddress(userShippingAddress.getAddress());

//        3、商品集合

        List<UserOrderGoods> list = userOrderGoodsMapper.selectList(new LambdaQueryWrapper<UserOrderGoods>().eq(UserOrderGoods::getOrderId, id));

        orderDetailVO.setSkus(list);

//        订单截至创建30分钟之后
        orderDetailVO.setPayLatestTime(userOrder.getCreateTime().plusMinutes(30));

        if (orderDetailVO.getPayLatestTime().isAfter(LocalDateTime.now())) {
            Duration duration = Duration.between(LocalDateTime.now(), orderDetailVO.getPayLatestTime());
//        倒计时秒数
            orderDetailVO.setCountdown(duration.toMillisPart());
        }
        return orderDetailVO;
    }


    @Override
    public SubmitOrderVO getPreOrderDetail(Integer userId) {
        SubmitOrderVO submitOrderVO = new SubmitOrderVO();
        //        1、查询用户购物车中选中的商品列表,如果为空值直接返回 null
        List<UserShoppingCart> cartList = userShoppingCartMapper.selectList(new LambdaQueryWrapper<UserShoppingCart>().eq(UserShoppingCart::getUserId, userId).eq(UserShoppingCart::getSelected, true));
        if (cartList.size() == 0) {
            return null;
        }

        //        2、查询用户收货地址列表
        List<UserAddressVO> addressList = getAddressListByUserId(userId, null);
        //        3、声明订单应付款、总运费金额
        BigDecimal totalPrice = new BigDecimal(0);
        Integer totalCount = 0;
        BigDecimal totalPayPrice = new BigDecimal(0);
        BigDecimal totalFreight = new BigDecimal(0);
        //      4、查询商品信息并计算每个选购商品的总费用
        List<UserOrderGoodsVO> goodList = new ArrayList<>();
        for (UserShoppingCart shoppingCart : cartList) {
            Goods goods = goodsMapper.selectById(shoppingCart.getGoodsId());
            UserOrderGoodsVO userOrderGoodsVO = new UserOrderGoodsVO();
            userOrderGoodsVO.setId(goods.getId());
            userOrderGoodsVO.setName(goods.getName());
            userOrderGoodsVO.setPicture(goods.getCover());
            userOrderGoodsVO.setCount(shoppingCart.getCount());
            userOrderGoodsVO.setAttrsText(shoppingCart.getAttrsText());
            userOrderGoodsVO.setPrice(goods.getOldPrice());
            userOrderGoodsVO.setPayPrice(goods.getPrice());
            userOrderGoodsVO.setTotalPrice(goods.getFreight() + goods.getPrice() * shoppingCart.getCount());
            userOrderGoodsVO.setTotalPayPrice(userOrderGoodsVO.getTotalPrice());

            BigDecimal freight = new BigDecimal(goods.getFreight().toString());
            BigDecimal goodsPrice = new BigDecimal(goods.getPrice().toString());
            BigDecimal count = new BigDecimal(shoppingCart.getCount().toString());


            BigDecimal price = goodsPrice.multiply(count).add(freight);


            totalPrice = totalPrice.add(price);
            totalCount += userOrderGoodsVO.getCount();
            totalPayPrice = totalPayPrice.add(new BigDecimal(userOrderGoodsVO.getTotalPayPrice().toString()));
            totalFreight = totalFreight.add(freight);
            goodList.add(userOrderGoodsVO);
        }
//       5、费用综述信息
        OrderInfoVO orderInfoVO = new OrderInfoVO();
        orderInfoVO.setGoodsCount(totalCount);
        orderInfoVO.setTotalPayPrice(totalPayPrice.doubleValue());
        orderInfoVO.setTotalPrice(totalPrice.doubleValue());
        orderInfoVO.setPostFee(totalFreight.doubleValue());


        submitOrderVO.setUserAddresses(addressList);
        submitOrderVO.setGoods(goodList);
        submitOrderVO.setSummary(orderInfoVO);
        return submitOrderVO;
    }

    @Override
    public SubmitOrderVO getPreNowOrderDetail(OrderPreQuery query) {
        SubmitOrderVO submitOrderVO = new SubmitOrderVO();
//        1、查询用户收货地址
        List<UserAddressVO> addressList = getAddressListByUserId(query.getUserId(), query.getAddressId());

        List<UserOrderGoodsVO> goodList = new ArrayList<>();

//        2、商品信息
        Goods goods = goodsMapper.selectById(query.getId());
        if (goods == null) {
            throw new ServerException("商品信息不存在");
        }
        if (query.getCount() > goods.getInventory()) {
            throw new ServerException(goods.getName() + "库存数量不足");
        }
        UserOrderGoodsVO userOrderGoodsVO = new UserOrderGoodsVO();
        userOrderGoodsVO.setId(goods.getId());
        userOrderGoodsVO.setName(goods.getName());
        userOrderGoodsVO.setPicture(goods.getCover());
        userOrderGoodsVO.setCount(query.getCount());
        userOrderGoodsVO.setAttrsText(query.getAttrsText());
        userOrderGoodsVO.setPrice(goods.getOldPrice());
        userOrderGoodsVO.setPayPrice(goods.getPrice());

        BigDecimal freight = new BigDecimal(goods.getFreight().toString());
        BigDecimal price = new BigDecimal(goods.getPrice().toString());
        BigDecimal count = new BigDecimal(query.getCount().toString());
        userOrderGoodsVO.setTotalPrice(price.multiply(count).add(freight).doubleValue());
        userOrderGoodsVO.setTotalPayPrice(userOrderGoodsVO.getTotalPrice());
        goodList.add(userOrderGoodsVO);

//       3、费用综述信息
        OrderInfoVO orderInfoVO = new OrderInfoVO();
        orderInfoVO.setGoodsCount(query.getCount());
        orderInfoVO.setTotalPayPrice(userOrderGoodsVO.getTotalPayPrice());
        orderInfoVO.setTotalPrice(userOrderGoodsVO.getTotalPrice());
        orderInfoVO.setPostFee(goods.getFreight());
        orderInfoVO.setDiscountPrice(goods.getDiscount());

        submitOrderVO.setUserAddresses(addressList);
        submitOrderVO.setGoods(goodList);
        submitOrderVO.setSummary(orderInfoVO);
        return submitOrderVO;
    }

    @Async
    public void scheduleOrderCancel(UserOrder userOrder) {
        cancelTask = executorService.schedule(() -> {
            if (userOrder.getStatus() == OrderStatusEnum.WAITING_FOR_PAYMENT.getValue()) {
                userOrder.setStatus(OrderStatusEnum.CANCELLED.getValue());
                baseMapper.updateById(userOrder);
            }
        }, 30, TimeUnit.MINUTES);
    }


    public void cancelScheduledTask() {
        if (cancelTask != null && !cancelTask.isDone()) {
//            取消定时任务
            cancelTask.cancel(true);
        }
    }


    public List<UserAddressVO> getAddressListByUserId(Integer userId, Integer addressId) {
//        1、根据用户id 查询该用户的收货地址列表

        List<UserShippingAddress> list = userShippingAddressMapper.selectList(new LambdaQueryWrapper<UserShippingAddress>().eq(UserShippingAddress::getUserId, userId));
        UserShippingAddress userShippingAddress = null;
        UserAddressVO userAddressVO;
        if (list.size() == 0) {
            return null;
        }

//        2、如果用户已经有选中的地址，将选中的地址属性设置为true
        if (addressId != null) {
            userShippingAddress = list.stream().filter(item -> item.getId().equals(addressId)).collect(Collectors.toList()).get(0);
            list.remove(userShippingAddress);
        }

        List<UserAddressVO> addressList = UserAddressConvert.INSTANCE.convertToUserAddressVOList(list);
        if (userShippingAddress != null) {
            userAddressVO = UserAddressConvert.INSTANCE.convertToUserAddressVO(userShippingAddress);
            userAddressVO.setSelected(true);
            addressList.add(userAddressVO);
        }
        return addressList;
    }

    @Override
    public SubmitOrderVO getRepurchaseOrderDetail(Integer id) {
        SubmitOrderVO submitOrderVO = new SubmitOrderVO();
//        1、根据订单id查询订单信息获取 用户信息和地址
        UserOrder userOrder = baseMapper.selectById(id);
//       2、查询用户收货地址信息
        List<UserAddressVO> addressList = getAddressListByUserId(userOrder.getUserId(), userOrder.getAddressId());
//       3、商品信息
        List<UserOrderGoodsVO> goodsList = goodsMapper.getGoodsListByOrderId(id);
//        4、综述信息
        OrderInfoVO orderInfoVO = new OrderInfoVO();
        orderInfoVO.setGoodsCount(userOrder.getTotalCount());
        orderInfoVO.setTotalPrice(userOrder.getTotalPrice());
        orderInfoVO.setPostFee(userOrder.getTotalFreight());
        orderInfoVO.setTotalPayPrice(userOrder.getTotalPrice());
        orderInfoVO.setDiscountPrice(0.00);
        submitOrderVO.setUserAddresses(addressList);
        submitOrderVO.setGoods(goodsList);
        submitOrderVO.setSummary(orderInfoVO);
        return submitOrderVO;
    }

    @Override
    public PageResult<OrderDetailVO> getOrderList(OrderQuery query) {
//        1、设置分页参数
        List<OrderDetailVO> list = new ArrayList<>();
        Page<UserOrder> page = new Page<>(query.getPage(), query.getPageSize());
//        2、查询条件：当 orderType 为空 或者值为 0 时，查询订单，否则根据订单状态条件筛选
        LambdaQueryWrapper<UserOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserOrder::getUserId, query.getUserId());
        if (query.getOrderType() != null && query.getOrderType() != 0) {
            wrapper.eq(UserOrder::getStatus, query.getOrderType());
        }
        wrapper.orderByDesc(UserOrder::getCreateTime);
//        3、查询所有的订单列表(分页)
        List<UserOrder> orderRecords = baseMapper.selectPage(page, wrapper).getRecords();
//        4、查询订单列表为空，返回空的分页内容
        if (orderRecords.size() == 0) {
            return new PageResult<>(page.getTotal(), query.getPageSize(), query.getPage(), page.getPages(), list);
        }
//        5、查询订单对应的商品信息和收货信息
        for (UserOrder userOrder : orderRecords) {
            OrderDetailVO orderDetailVO = UserOrderDetailConvert.INSTANCE.convertToOrderDetailVO(userOrder);
            UserShippingAddress userShippingAddress = userShippingAddressMapper.selectById(userOrder.getAddressId());
            if (userShippingAddress != null) {
                orderDetailVO.setReceiverContact(userShippingAddress.getReceiver());
                orderDetailVO.setReceiverAddress(userShippingAddress.getAddress());
                orderDetailVO.setReceiverMobile(userOrder.getCancelReason());
            }

            List<UserOrderGoods> userOrderGoods = userOrderGoodsMapper.selectList(new LambdaQueryWrapper<UserOrderGoods>().eq(UserOrderGoods::getGoodsId, userOrder.getId()));
            orderDetailVO.setSkus(userOrderGoods);
            list.add(orderDetailVO);
        }


        return new PageResult<>(page.getTotal(), query.getPageSize(), query.getPage(), page.getPages(), list);
    }

    @Override
    public OrderDetailVO cancelOrder(CancelGoodsQuery query) {
//      1、查询订单是否存在
        UserOrder userOrder = baseMapper.selectById(query.getId());
        if (userOrder == null) {
            throw new ServerException("订单信息不存在");
        }

//        2、只有是未支付的订单才能取消

        if (userOrder.getStatus() != OrderStatusEnum.WAITING_FOR_PAYMENT.getValue()) {
            throw new ServerException("订单已支付，取消失败");
        }

//        3、修改订单状态
        userOrder.setStatus(OrderStatusEnum.CANCELLED.getValue());
        userOrder.setCancelReason(query.getCancelReason());
        userOrder.setCloseTime(LocalDateTime.now());
        baseMapper.updateById(userOrder);
        OrderDetailVO orderDetailVO = UserOrderDetailConvert.INSTANCE.convertToOrderDetailVO(userOrder);

//        4、查询订单地址信息
        UserShippingAddress userShippingAddress = userShippingAddressMapper.selectById(userOrder.getAddressId());
        if (userShippingAddress != null) {
            orderDetailVO.setReceiverContact(userShippingAddress.getReceiver());
            orderDetailVO.setReceiverAddress(userShippingAddress.getAddress());
            orderDetailVO.setReceiverMobile(userOrder.getCancelReason());
        }
//      5、查询购买的商品列表返回给客户端
        List<UserOrderGoods> goodsList = userOrderGoodsMapper.selectList(new LambdaQueryWrapper<UserOrderGoods>().eq(UserOrderGoods::getOrderId, userOrder.getId()));

        return orderDetailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteOrder(List<Integer> ids, Integer userId) {
//        仅在订单状态为 待评价、已完成、已取消时，可删除订单
        LambdaQueryWrapper<UserOrder> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(UserOrder::getUserId, userId);
        wrapper.eq(UserOrder::getStatus, OrderStatusEnum.WAITING_FOR_REVIEW.getValue()).or().eq(UserOrder::getStatus, OrderStatusEnum.COMPLETED.getValue()).or().eq(UserOrder::getStatus, OrderStatusEnum.CANCELLED.getValue());
        List<UserOrder> userOrders = baseMapper.selectList(wrapper);
//        将查询到的订单和要删除的订单列表取交集，避免误删订单
        List<UserOrder> list = userOrders.stream().filter(item -> ids.contains(item.getId())).collect(Collectors.toList());
//        当可删除的订单集合长度为0时，抛出暂无可删除订单的异常
        if (list.size() == 0) {
            throw new ServerException("暂无可以删除的订单");
        }
//        删除订单信息
        removeByIds(list);
//       删除购买的商品信息
        for (UserOrder userOrder : list) {
            userOrderGoodsMapper.delete(new LambdaQueryWrapper<UserOrderGoods>().eq(UserOrderGoods::getOrderId, userOrder.getId()));
        }
    }

}
