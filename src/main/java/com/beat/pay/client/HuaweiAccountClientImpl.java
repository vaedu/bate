package com.beat.pay.client;

import com.beat.pay.common.BizException;
import com.beat.pay.common.ErrorCode;
import com.beat.pay.config.HuaweiProperties;
import jakarta.annotation.Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import java.util.Map;

@Component
@Slf4j
@RequiredArgsConstructor
public class HuaweiAccountClientImpl implements HuaweiAccountClient {

	@Resource
	private RestTemplate restTemplate;
	private final HuaweiProperties props;

	@Override
	public HuaweiIdentity exchangeAuthCode(String authorizationCode) {
		try {
			// 1) 用 clientId/clientSecret + 授权码换取 Access Token
			HttpHeaders tokenHeaders = new HttpHeaders();
			tokenHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
			MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
			params.add("client_id", props.getClientId());
			params.add("client_secret", props.getClientSecret());
			params.add("grant_type", "authorization_code");
			params.add("code", authorizationCode);
			ResponseEntity<Map> response = restTemplate.postForEntity(props.getTokenUrl(),
					new HttpEntity<>(params, tokenHeaders), Map.class);
			Map<String, Object> body = response.getBody();
			if (!response.getStatusCode().is2xxSuccessful() || body == null
					|| body.containsKey("error") || body.get("access_token") == null) {
				// 只打印字段名，不打印字段值（值里可能有 Token）
				log.warn("华为换取令牌失败 status={} 响应字段={}", response.getStatusCode(),
						body == null ? null : body.keySet());
				throw new BizException(ErrorCode.HUAWEI_AUTH_FAILED);
			}
			String accessToken = body.get("access_token").toString();

			// 2) 用 Access Token 换取用户信息
			// 再加一个 "Authorization: Basic <令牌>" 请求头既不规范，也可能让华为拒绝请求
			HttpHeaders infoHeaders = new HttpHeaders();
			infoHeaders.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
			MultiValueMap<String, String> infoParams = new LinkedMultiValueMap<>();
			infoParams.add("access_token", accessToken);
			infoParams.add("getNickName", "1");        // 获取昵称
			infoParams.add("headPictureURL", "1");     // 获取头像
			ResponseEntity<Map> infoResponse = restTemplate.postForEntity(props.getUserInfoUrl(),
					new HttpEntity<>(infoParams, infoHeaders), Map.class);
			Map<String, Object> infoBody = infoResponse.getBody();
			if (!infoResponse.getStatusCode().is2xxSuccessful() || infoBody == null
					|| infoBody.containsKey("error")) {
				log.warn("华为取用户信息失败 status={} 响应字段={}", infoResponse.getStatusCode(),
						infoBody == null ? null : infoBody.keySet());
				throw new BizException(ErrorCode.HUAWEI_AUTH_FAILED);
			}
			log.info("华为用户信息返回字段: {}", infoBody.keySet());
			// 昵称、头像可能没有（没授权 profile 时），取不到就是 null，不能直接 .toString()
			String unionId = text(infoBody, "unionID", "unionId");
			String openId = text(infoBody, "openID", "openId");
			if (unionId == null) {
				// 需求附录 D.2：默认授权范围下 UnionID 是否一定返回属于待实测项
				log.warn("华为用户信息中没有 unionID，响应字段: {}", infoBody.keySet());
				throw new BizException(ErrorCode.HUAWEI_AUTH_FAILED);
			}
			String nickname = text(infoBody, "nickname");
			String headPictureUrl = text(infoBody, "headPictureURL");
			return new HuaweiIdentity(unionId, openId, nickname, headPictureUrl);

		} catch (BizException e) {
			throw e;
		} catch (HttpStatusCodeException e) {
			// RestTemplate 遇到 4xx/5xx 会直接抛这个异常；华为的错误响应体是错误码和描述，不含令牌
			log.warn("华为接口返回错误 status={} body={}", e.getStatusCode(), e.getResponseBodyAsString());
			throw new BizException(ErrorCode.HUAWEI_AUTH_FAILED);
		} catch (Exception e) {
			// 超时、解析失败等：授权码已经作废，不重试，统一按授权失败处理
			log.warn("华为账号请求异常: {}", e.getClass().getSimpleName());
			throw new BizException(ErrorCode.HUAWEI_AUTH_FAILED);
		}
	}

	/** 按候选键依次取值；缺失或为空串返回 null，不会空指针 */
	private static String text(Map<String, Object> map, String... keys) {
		for (String k : keys) {
			Object v = map.get(k);
			if (v != null && !v.toString().isBlank()) {
				return v.toString();
			}
		}
		return null;
	}
}
