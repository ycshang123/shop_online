package com.soft2242.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soft2242.shop.common.exception.ServerException;
import com.soft2242.shop.convert.AddressConvert;
import com.soft2242.shop.entity.UserShippingAddress;
import com.soft2242.shop.enums.AddressDefaultEnum;
import com.soft2242.shop.mapper.UserShippingAddressMapper;
import com.soft2242.shop.service.UserShippingAddressService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.soft2242.shop.vo.AddressVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author ycshang
 * @since 2023-11-07
 */
@Service
public class UserShippingAddressServiceImpl extends ServiceImpl<UserShippingAddressMapper, UserShippingAddress> implements UserShippingAddressService {


    @Override
    public Integer saveShippingAddress(AddressVO addressVO) {
        UserShippingAddress convert = AddressConvert.INSTANCE.convert(addressVO);
        if (addressVO.getIsDefault().equals(AddressDefaultEnum.DEFAULT_ADDRESS.getValue())) {
            List<UserShippingAddress> list = baseMapper.selectList(new LambdaQueryWrapper<UserShippingAddress>().eq(UserShippingAddress::getIsDefault, AddressDefaultEnum.DEFAULT_ADDRESS.getValue()));
            if (list.size() > 0) {
                throw new ServerException("已经存在默认地址，请勿重复操作");
            }
        }
        save(convert);
        return convert.getId();
    }

    @Override
    public Integer editShippingAddress(AddressVO addressVO) {
        UserShippingAddress userShippingAddress = baseMapper.selectById(addressVO.getId());
        if (userShippingAddress == null) {
            throw new ServerException("地址不存在");
        }
        if (addressVO.getIsDefault().equals(AddressDefaultEnum.DEFAULT_ADDRESS.getValue())) {
//            查询该用户是否已经存在默认地址
            UserShippingAddress address = baseMapper.selectOne(new LambdaQueryWrapper<UserShippingAddress>().eq(UserShippingAddress::getUserId, addressVO.getUserId()).eq(UserShippingAddress::getIsDefault, AddressDefaultEnum.DEFAULT_ADDRESS.getValue()));
//           如果存在将之前的默认地址修改掉
            if (address != null) {
                address.setIsDefault(AddressDefaultEnum.NOT_DEFAULT_ADDRESS.getValue());
                updateById(address);
            }
        }
        UserShippingAddress address = AddressConvert.INSTANCE.convert(addressVO);
        updateById(address);
        return address.getId();
    }
}
