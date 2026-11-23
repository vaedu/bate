package com.beat.pay.enums;

/** 会话吊销原因（sys_user_session.revoke_reason） */
public enum RevokeReason {
	LOGOUT,            // 退出当前设备
	LOGOUT_ALL,        // 退出全部设备
	DEVICE_REMOVED,    // 在设备列表里移除设备
	REPLAY,            // 检测到 Refresh Token 重放
	ACCOUNT_DELETION   // 账号注销
}