package com.beat.pay.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.beat.pay.common.BizException;
import com.beat.pay.common.ErrorCode;
import com.beat.pay.config.BizProperties;
import com.beat.pay.dto.auth.DeleteAccountRequest;
import com.beat.pay.dto.auth.UserInfoVO;
import com.beat.pay.entity.SysUser;
import com.beat.pay.entity.SysUserSession;
import com.beat.pay.enums.RevokeReason;
import com.beat.pay.enums.SessionStatus;
import com.beat.pay.enums.UserStatus;
import com.beat.pay.mapper.SysUserMapper;
import com.beat.pay.mapper.SysUserSessionMapper;
import com.beat.pay.service.UserService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@AllArgsConstructor
public class UserServiceImpl implements UserService {
	private final SysUserMapper userMapper;
	private final SysUserSessionMapper sessionMapper;
    private final BizProperties bizProps;
	@Override
	public UserInfoVO me(Long userId) {
		// 查询用户信息
		SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
				.eq(SysUser::getId, userId));
		return BeanUtil.copyProperties(user, UserInfoVO.class);
	}

	@Override
    @Transactional(rollbackFor = Exception.class)
	public UserInfoVO deleteAccount(Long userId, DeleteAccountRequest req) {
        // 确定注销时间及拟删除时间
        long now = System.currentTimeMillis();
        long scheduled = now + Duration.ofDays(bizProps.getAccount().getDeletionCoolingDays()).toMillis();
        // 先查用户
		SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
				.eq(SysUser::getId, userId));
        if (user == null){
            throw new BizException(ErrorCode.ACCESS_TOKEN_EXPIRED);
        }
        // 非正常状态用户返回403状态码
		if (!user.getStatus().equals(UserStatus.ACTIVE)){
            throw new BizException(ErrorCode.ACCOUNT_RESTRICTED);
        }
        // 修改状态并吊销会话
        user.setStatus(UserStatus.PENDING_DELETION);
        user.setDeletionRequestedAt(now);
        user.setDeletionScheduledAt(scheduled);
        int rows = userMapper.update(null, new LambdaUpdateWrapper<SysUser>()
                .eq(SysUser::getId, userId)
                .eq(SysUser::getStatus, UserStatus.ACTIVE)
                .set(SysUser::getStatus, UserStatus.PENDING_DELETION)
                .set(SysUser::getDeletionRequestedAt, now)
                .set(SysUser::getDeletionScheduledAt, scheduled)
                .set(SysUser::getUpdatedAt, now));
        if (rows != 1) {
            throw new BizException(ErrorCode.ACCOUNT_RESTRICTED);
        }
        // 吊销会话
        sessionMapper.update(null,new LambdaUpdateWrapper<SysUserSession>()
                .eq(SysUserSession::getUserId,userId)
                .eq(SysUserSession::getStatus, SessionStatus.ACTIVE)
                .set(SysUserSession::getStatus, SessionStatus.REVOKED)
                .set(SysUserSession::getRevokedAt,now)
                .set(SysUserSession::getRevokeReason, RevokeReason.ACCOUNT_DELETION)
                .set(SysUserSession::getUpdatedAt, now));
        return BeanUtil.copyProperties(user, UserInfoVO.class);
	}
}
