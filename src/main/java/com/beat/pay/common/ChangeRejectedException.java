package com.beat.pay.common;

public class ChangeRejectedException extends BizException {
	public ChangeRejectedException(ErrorCode errorCode) {
		super(errorCode);
	}

	public ChangeRejectedException(ErrorCode errorCode, String message) {
		super(errorCode, message);
	}

	public ChangeRejectedException(ErrorCode errorCode, String message, Object data) {
		super(errorCode, message, data);
	}
}
