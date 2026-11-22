package com.beat.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.beat.pay.entity.SysUserIdentity;

/**
 * 注意：带 user_id 的业务实体禁止直接使用 selectById / updateById / deleteById 处理他人可传入的ID，
 * 必须使用 LambdaQueryWrapper 同时带上 user_id 条件，防止越权。
 */
public interface SysUserIdentityMapper extends BaseMapper<SysUserIdentity> {
}
