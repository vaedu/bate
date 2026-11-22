package com.beat.pay.client;

/** 华为服务端换取得到的身份信息；华为的 Access Token 仅用于本次换取，不落库 */
public record HuaweiIdentity(String unionId, String openId, String nickname, String avatarUrl) {
}
