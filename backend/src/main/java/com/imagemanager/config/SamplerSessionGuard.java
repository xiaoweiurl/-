package com.imagemanager.config;

import com.imagemanager.dto.LoginResponse;

/**
 * 钉钉通讯录未注册成员的打样作用域会话：只允许读写指定商品表单，
 * 不能进商品库列表、知识库、组织、ERP、管理端。
 */
public final class SamplerSessionGuard {

    public static final String SCOPE_SAMPLER = "sampler";
    public static final String ROLE_SAMPLER = "sampler";

    private SamplerSessionGuard() {
    }

    public static boolean isSamplerScope(LoginResponse.UserInfo user) {
        if (user == null) {
            return false;
        }
        if (ROLE_SAMPLER.equalsIgnoreCase(user.getRole())) {
            return true;
        }
        return SCOPE_SAMPLER.equalsIgnoreCase(user.getScope());
    }

    /**
     * @return true 表示放行；sampler 作用域以外的会话一律放行（由原有 RBAC 处理）
     */
    public static boolean allows(String path, String method, LoginResponse.UserInfo user) {
        if (!isSamplerScope(user)) {
            return true;
        }
        if (path == null || path.isBlank()) {
            return false;
        }
        String goodsId = user.getSamplerGoodsId();
        if (goodsId == null || goodsId.isBlank() || !goodsId.chars().allMatch(Character::isDigit)) {
            return false;
        }
        String m = method == null ? "GET" : method.toUpperCase();
        String item = "/goods-library/" + goodsId;
        if (path.equals(item)) {
            return "GET".equals(m) || "PUT".equals(m) || "HEAD".equals(m);
        }
        if (path.equals(item + "/images")) {
            return "POST".equals(m) || "DELETE".equals(m);
        }
        return false;
    }
}
