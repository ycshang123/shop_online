package com.soft2242.shop.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.soft2242.shop.entity.Category;
import com.soft2242.shop.enums.CategoryRecommendEnum;
import com.soft2242.shop.mapper.CategoryMapper;
import com.soft2242.shop.service.CategoryService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
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
public class CategoryServiceImpl extends ServiceImpl<CategoryMapper, Category> implements CategoryService {
    @Override
    public List<Category> getIndexCategoryList() {
        LambdaQueryWrapper<Category> wrapper = new LambdaQueryWrapper<>();
        //        查询首页和分类页都推荐的分类以及在首页推荐的分类
        wrapper.eq(Category::getIsRecommend, CategoryRecommendEnum.ALL_RECOMMEND).or().eq(Category::getIsRecommend, CategoryRecommendEnum.INDEX_RECOMMEND);
        wrapper.orderByDesc(Category::getCreateTime);
        List<Category> list = baseMapper.selectList(wrapper);
        return list;
    }
}
