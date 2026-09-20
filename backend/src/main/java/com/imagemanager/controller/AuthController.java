package com.imagemanager.controller;

import com.imagemanager.dto.*;
import com.imagemanager.dingtalk.DingTalkException;
import com.imagemanager.dingtalk.DingTalkFreeLoginException;
import com.imagemanager.dingtalk.DingTalkFreeLoginService;
import com.imagemanager.dingtalk.DingTalkJsapiConfigService;
import com.imagemanager.dingtalk.DingTalkSamplerTicketService;
import com.imagemanager.entity.User;
import com.imagemanager.exception.RegisterMatchException;
import com.imagemanager.repository.UserRepository;
import com.imagemanager.service.AuthService;
import com.imagemanager.service.ImageTableService;
import com.imagemanager.util.SessionIdExtractor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 认证控制器
 * 
 * @author Image Manager Team
 * @version 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/auth")
@Tag(name = "认证管理", description = "登录、登出、会话管理等")
public class AuthController {
    
    @Autowired
    private AuthService authService;

    @Autowired
    private ImageTableService imageTableService;
    
    @Autowired
    private UserRepository userRepository;
    
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DingTalkFreeLoginService dingTalkFreeLoginService;

    @Autowired
    private DingTalkJsapiConfigService dingTalkJsapiConfigService;

    @Autowired
    private DingTalkSamplerTicketService dingTalkSamplerTicketService;
    
    /**
     * 用户注册（钉钉姓名匹配）。初始密码固定 123456，首次登录强制改密。
     */
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "按钉钉通讯录姓名注册；同名多人返回 409 候选人；初始密码 123456 且必须改密")
    public ResponseEntity<ApiResponse<?>> register(
            @RequestBody RegisterRequest request,
            HttpServletResponse response) {

        try {
            LoginResponse loginResponse = authService.register(request);

            String sessionId = loginResponse.getSessionId();

            String username = loginResponse.getUser().getUsername();
            if (username != null && !username.isEmpty()) {
                imageTableService.ensureUserImageTable(username);
            }

            response.setHeader("X-Session-Id", sessionId);

            return ResponseEntity.ok(ApiResponse.success("注册成功", loginResponse));
        } catch (RegisterMatchException e) {
            log.info("钉钉姓名注册匹配: status={}, message={}", e.getHttpStatus(), e.getMessage());
            Map<String, Object> data = new HashMap<>();
            data.put("candidates", e.getCandidates());
            return ResponseEntity.status(e.getHttpStatus())
                    .body(ApiResponse.error(e.getHttpStatus(), e.getMessage(), data));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (Exception e) {
            log.error("注册失败: ", e);
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        }
    }
    
    /**
     * 用户登录
     */
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用用户名密码登录")
    public ApiResponse<LoginResponse> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response) {
        
        try {
            LoginResponse loginResponse = authService.login(request);

            // 如果用户已登录（SSO 检查），直接返回，不创建图片表
            if (Boolean.TRUE.equals(loginResponse.getAlreadyLoggedIn())) {
                log.info("用户已登录，返回确认提示: username={}", request.getUsername());
                return ApiResponse.success("用户已登录", loginResponse);
            }

            String sessionId = loginResponse.getSessionId();
            // 安全：会话标识不完整打印，仅保留前 8 位用于排障关联
            log.info("登录成功，sessionId: {}***", sessionId != null && sessionId.length() > 8 ? sessionId.substring(0, 8) : "***");

            // 登录成功后，确保用户图片表存在
            String username = loginResponse.getUser().getUsername();
            if (username != null && !username.isEmpty()) {
                imageTableService.ensureUserImageTable(username);
            }

            // CORS 头由 SecurityConfig 的 CorsConfigurationSource 按白名单统一输出，此处不再硬编码
            // 将 sessionId 通过响应头返回
            response.setHeader("X-Session-Id", sessionId);

            // 通知由前端统一管理

            return ApiResponse.success("登录成功", loginResponse);
        } catch (Exception e) {
            log.error("登录失败: ", e);
            return ApiResponse.error(401, e.getMessage());
        }
    }

    /**
     * 钉钉 H5 免登：JSAPI authCode 换本地会话。公开端点。
     */
    @PostMapping("/dingtalk")
    @Operation(summary = "钉钉免登", description = "用钉钉 JSAPI authCode 换取中台会话；未注册通讯录成员仅获得打样表单作用域")
    public ResponseEntity<ApiResponse<LoginResponse>> dingTalkFreeLogin(
            @RequestBody(required = false) DingTalkFreeLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        try {
            String clientId = "dingtalk:" + clientKey(httpRequest);
            if (!com.imagemanager.util.RateLimiter.allow(clientId, com.imagemanager.util.RateLimiter.LimitType.LOGIN)) {
                long resetTime = com.imagemanager.util.RateLimiter.getResetTime(
                        clientId, com.imagemanager.util.RateLimiter.LimitType.LOGIN);
                return ResponseEntity.status(429)
                        .body(ApiResponse.error(429, "免登尝试次数过多，请在 " + resetTime + " 秒后重试"));
            }
            String authCode = request == null ? null : request.resolveAuthCode();
            LoginResponse loginResponse = dingTalkFreeLoginService.login(authCode,
                    request == null ? null : request.getGoodsId());
            return completeDingTalkLogin(loginResponse, response, "钉钉免登成功");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (DingTalkFreeLoginException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(ApiResponse.error(e.getHttpStatus(), e.getMessage()));
        } catch (DingTalkException e) {
            log.warn("钉钉免登失败: {}", e.getMessage());
            int status = e.getMessage() != null && e.getMessage().contains("未配置") ? 503 : 401;
            return ResponseEntity.status(status).body(ApiResponse.error(status, e.getMessage()));
        } catch (Exception e) {
            log.error("钉钉免登失败: ", e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "钉钉免登失败"));
        }
    }

    /**
     * 打样工作通知 magic ticket 核销。公开端点；不依赖 JSAPI 域名微应用。
     */
    @PostMapping("/dingtalk/ticket")
    @Operation(summary = "打样通知 ticket 免登",
            description = "核销工作通知 URL 上的短时 HMAC ticket，会话规则与钉钉 JSAPI 免登相同")
    public ResponseEntity<ApiResponse<LoginResponse>> dingTalkSamplerTicket(
            @RequestBody(required = false) DingTalkSamplerTicketRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse response) {
        try {
            String clientId = "dingtalk-ticket:" + clientKey(httpRequest);
            if (!com.imagemanager.util.RateLimiter.allow(clientId, com.imagemanager.util.RateLimiter.LimitType.LOGIN)) {
                long resetTime = com.imagemanager.util.RateLimiter.getResetTime(
                        clientId, com.imagemanager.util.RateLimiter.LimitType.LOGIN);
                return ResponseEntity.status(429)
                        .body(ApiResponse.error(429, "免登尝试次数过多，请在 " + resetTime + " 秒后重试"));
            }
            String ticket = request == null ? null : request.getTicket();
            LoginResponse loginResponse = dingTalkSamplerTicketService.redeem(
                    ticket, request == null ? null : request.getGoodsId());
            return completeDingTalkLogin(loginResponse, response, "钉钉免登成功");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error(400, e.getMessage()));
        } catch (DingTalkFreeLoginException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(ApiResponse.error(e.getHttpStatus(), e.getMessage()));
        } catch (Exception e) {
            log.error("打样 ticket 免登失败: ", e);
            return ResponseEntity.status(401).body(ApiResponse.error(401, "打样通知免登失败"));
        }
    }

    private ResponseEntity<ApiResponse<LoginResponse>> completeDingTalkLogin(
            LoginResponse loginResponse, HttpServletResponse response, String successMessage) {
        String sessionId = loginResponse.getSessionId();
        if (sessionId != null) {
            response.setHeader("X-Session-Id", sessionId);
        }
        String username = loginResponse.getUser() != null ? loginResponse.getUser().getUsername() : null;
        if (username != null && !username.isEmpty()
                && !"sampler".equalsIgnoreCase(loginResponse.getUser().getRole())) {
            imageTableService.ensureUserImageTable(username);
        }
        return ResponseEntity.ok(ApiResponse.success(successMessage, loginResponse));
    }

    /**
     * 钉钉 JSAPI 配置（corpId / agentId / clientId=AppKey / dd.config 签名）。公开端点，不含 Secret。
     */
    @GetMapping("/dingtalk/config")
    @Operation(summary = "钉钉 JSAPI 配置", description = "返回 corpId、agentId、clientId（AppKey）；若传入本站 url 则附带 dd.config 签名。不含 AppSecret")
    public ApiResponse<java.util.Map<String, Object>> dingTalkJsapiConfig(
            @RequestParam(value = "url", required = false) String url) {
        try {
            return ApiResponse.success("ok", dingTalkJsapiConfigService.build(url));
        } catch (DingTalkException e) {
            log.warn("钉钉 JSAPI 配置失败: {}", e.getMessage());
            return ApiResponse.error(503, e.getMessage());
        } catch (Exception e) {
            log.error("钉钉 JSAPI 配置失败: ", e);
            return ApiResponse.error(500, "无法生成钉钉 JSAPI 配置");
        }
    }

    private static String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String ip = request.getRemoteAddr();
        return ip == null || ip.isBlank() ? "unknown" : ip;
    }
    
    /**
     * 用户登出
     */
    @PostMapping("/logout")
    @Operation(summary = "用户登出", description = "退出当前登录状态，删除 Redis 中的 session")
    public ApiResponse<Void> logout(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request,
            HttpServletResponse response) {

        // 优先从 body 获取 sessionId
        String sessionId = null;
        if (body != null && body.get("sessionId") != null && !body.get("sessionId").isEmpty()) {
            sessionId = body.get("sessionId");
        }
        // 兜底：从请求头/Cookie 获取
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = SessionIdExtractor.extract(request);
        }

        if (sessionId != null && !sessionId.isEmpty()) {
            authService.logout(sessionId);
        }

        response.setHeader("Set-Cookie", "session_id=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");

        return ApiResponse.success("登出成功", null);
    }

    /**
     * 删除当前用户在 Redis 中的 session（专用接口）
     * 前端登出时调用此接口确保 Redis session 被清除
     */
    @DeleteMapping("/session")
    @Operation(summary = "删除Redis会话", description = "删除当前用户在Redis中存储的sessionID")
    public ApiResponse<Void> deleteSession(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request,
            HttpServletResponse response) {

        // 优先从 body 获取 sessionId
        String sessionId = null;
        if (body != null && body.get("sessionId") != null && !body.get("sessionId").isEmpty()) {
            sessionId = body.get("sessionId");
        }
        // 兜底：从 header/cookie 获取
        if (sessionId == null || sessionId.isEmpty()) {
            sessionId = SessionIdExtractor.extract(request);
        }

        if (sessionId != null && !sessionId.isEmpty()) {
            authService.logout(sessionId);
        }

        response.setHeader("Set-Cookie", "session_id=; Path=/; Max-Age=0; HttpOnly; SameSite=Lax");

        return ApiResponse.success("Redis会话已删除", null);
    }

    /**
     * 验证会话
     */
    @GetMapping("/session")
    @Operation(summary = "验证会话", description = "检查当前会话是否有效")
    public ApiResponse<LoginResponse.UserInfo> validateSession(
            HttpServletRequest request,
            HttpServletResponse response) {

        String sessionId = SessionIdExtractor.extract(request);
        
        if (sessionId == null) {
            log.debug("验证会话失败：没有 session_id");
            return ApiResponse.error(401, "未登录");
        }
        
        LoginResponse.UserInfo user = authService.validateSession(sessionId);
        if (user == null) {
            log.debug("验证会话失败：session 无效");
            return ApiResponse.error(401, "会话已过期");
        }
        
        return ApiResponse.success(user);
    }
    
    /**
     * 绑定公司到用户（仅首次，已绑定不可更改）
     */
    @PostMapping("/bind-company")
    @Operation(summary = "绑定公司", description = "首次选择公司绑定到用户账号，绑定后不可更改")
    public ApiResponse<Void> bindCompany(@RequestBody java.util.Map<String, String> body) {
        String userId = body.get("userId");
        String company = body.get("company");
        
        if (userId == null || userId.isEmpty() || company == null || company.isEmpty()) {
            return ApiResponse.error(400, "缺少必要参数");
        }
        
        try {
            boolean success = authService.bindCompany(userId, company);
            if (success) {
                log.info("公司绑定成功: userId={}, company={}", userId, company);
                return ApiResponse.success("绑定成功", null);
            } else {
                return ApiResponse.error(400, "该账号已绑定公司，不可更改");
            }
        } catch (Exception e) {
            log.error("公司绑定失败: ", e);
            return ApiResponse.error(500, "绑定失败: " + e.getMessage());
        }
    }
    
    /**
     * 找回密码 - 获取用户验证信息（脱敏）
     */
    @GetMapping("/forgot-password/user-info")
    @Operation(summary = "找回密码-获取用户信息", description = "根据用户名获取脱敏的邮箱和手机号")
    public ApiResponse<java.util.Map<String, Object>> getForgotPasswordUserInfo(
            @RequestParam String username,
            HttpServletResponse response) {

        log.info("找回密码-查询用户信息: username={}", username);
        
        if (username == null || username.trim().isEmpty()) {
            return ApiResponse.error(400, "请输入用户名");
        }
        
        Optional<User> userOpt = userRepository.findByUsername(username.trim());
        if (userOpt.isEmpty()) {
            return ApiResponse.error(404, "用户不存在");
        }
        
        User user = userOpt.get();
        
        String email = user.getEmail();
        String phone = user.getPhone();
        
        // 脱敏处理
        String maskedEmail = maskEmail(email);
        String maskedPhone = maskPhone(phone);
        
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("username", user.getUsername());
        data.put("maskedEmail", maskedEmail);
        data.put("maskedPhone", maskedPhone);
        data.put("hasEmail", email != null && !email.isEmpty());
        data.put("hasPhone", phone != null && !phone.isEmpty());
        
        return ApiResponse.success("查询成功", data);
    }
    
    /**
     * 找回密码 - 重置密码
     */
    @PostMapping("/forgot-password/reset")
    @Operation(summary = "找回密码-重置密码", description = "验证邮箱或手机号后重置密码")
    public ApiResponse<Void> resetPassword(
            @RequestBody java.util.Map<String, String> body,
            HttpServletResponse response) {

        String username = body.get("username");
        String verifyValue = body.get("verifyValue");
        String verifyType = body.get("verifyType");
        String newPassword = body.get("newPassword");
        String confirmPassword = body.get("confirmPassword");
        
        log.info("找回密码-重置密码: username={}, verifyType={}", username, verifyType);
        
        // 参数校验
        if (username == null || username.trim().isEmpty()) {
            return ApiResponse.error(400, "请输入用户名");
        }
        if (newPassword == null || newPassword.length() < 6) {
            return ApiResponse.error(400, "密码长度至少6位");
        }
        if (!newPassword.equals(confirmPassword)) {
            return ApiResponse.error(400, "两次输入的密码不一致");
        }
        
        // 查找用户
        Optional<User> userOpt = userRepository.findByUsername(username.trim());
        if (userOpt.isEmpty()) {
            return ApiResponse.error(404, "用户不存在");
        }
        
        User user = userOpt.get();
        
        // 验证身份
        if ("email".equals(verifyType)) {
            if (user.getEmail() == null || !user.getEmail().equalsIgnoreCase(verifyValue)) {
                return ApiResponse.error(400, "邮箱验证失败，请检查输入的邮箱地址");
            }
        } else if ("phone".equals(verifyType)) {
            if (user.getPhone() == null || !user.getPhone().equals(verifyValue)) {
                return ApiResponse.error(400, "手机号验证失败，请检查输入的手机号码");
            }
        } else {
            return ApiResponse.error(400, "不支持的验证方式");
        }
        
        // 重置密码
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        
        log.info("密码重置成功: username={}", username);
        return ApiResponse.success("密码重置成功，请使用新密码登录", null);
    }
    
    /**
     * 邮箱脱敏：a***@example.com
     */
    private String maskEmail(String email) {
        if (email == null || email.isEmpty()) return "";
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) return "***";
        String prefix = email.substring(0, Math.min(1, atIndex));
        String suffix = email.substring(atIndex);
        return prefix + "***" + suffix;
    }
    
    /**
     * 手机号脱敏：138****1234
     */
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) return "****";
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }
    
}
