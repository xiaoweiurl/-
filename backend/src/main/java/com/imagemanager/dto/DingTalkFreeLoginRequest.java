package com.imagemanager.dto;

import lombok.Data;

/**
 * 钉钉 H5 免登：JSAPI authCode + 打样商品 id。
 */
@Data
public class DingTalkFreeLoginRequest {

    /** JSAPI {@code requestAuthCode} / {@code getAuthCode} 返回的临时码 */
    private String authCode;

    /** 与 authCode 同义，兼容钉钉字段名 {@code code} */
    private String code;

    /** 打样表单商品 id；未注册通讯录成员走 sampler 作用域会话时必填 */
    private Long goodsId;

    public String resolveAuthCode() {
        if (authCode != null && !authCode.isBlank()) {
            return authCode.trim();
        }
        if (code != null && !code.isBlank()) {
            return code.trim();
        }
        return null;
    }
}
