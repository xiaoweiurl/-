package com.imagemanager.dto;

import lombok.Data;

/**
 * 打样工作通知 magic ticket 核销：绑定 userid + goodsId 的短时签名。
 */
@Data
public class DingTalkSamplerTicketRequest {

    /** URL query {@code ticket}，不含 AppSecret */
    private String ticket;

    /** 当前打开的打样商品 id，必须与 ticket 内 goodsId 一致 */
    private Long goodsId;
}
