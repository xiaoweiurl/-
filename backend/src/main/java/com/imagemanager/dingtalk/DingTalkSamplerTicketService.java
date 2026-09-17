package com.imagemanager.dingtalk;

import com.imagemanager.config.DingTalkProperties;
import com.imagemanager.dto.LoginResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

/**
 * 打样工作通知 magic ticket：签发与核销。
 * 核销后走 {@link DingTalkFreeLoginService#loginByUserid}，权限不高于现有免登。
 */
@Slf4j
@Service
public class DingTalkSamplerTicketService {

    /** 仅 local profile 且未配置专用密钥 / AppSecret 时使用，禁止用于生产。 */
    static final String LOCAL_DEFAULT_SECRET = "local-only-dingtalk-sampler-ticket-secret";

    private final DingTalkProperties properties;
    private final DingTalkFreeLoginService freeLoginService;
    private final Environment environment;
    private final Clock clock;

    /**
     * Spring injection constructor. Required {@code @Autowired}: a package-private
     * 4-arg test constructor also exists, so Spring will not treat this as the
     * unique constructor and otherwise falls back to a missing no-arg {@code <init>()}.
     */
    @Autowired
    public DingTalkSamplerTicketService(DingTalkProperties properties,
                                        DingTalkFreeLoginService freeLoginService,
                                        Environment environment) {
        this(properties, freeLoginService, environment, Clock.systemUTC());
    }

    DingTalkSamplerTicketService(DingTalkProperties properties,
                                 DingTalkFreeLoginService freeLoginService,
                                 Environment environment,
                                 Clock clock) {
        this.properties = properties;
        this.freeLoginService = freeLoginService;
        this.environment = environment;
        this.clock = clock == null ? Clock.systemUTC() : clock;
    }

    /**
     * 签发 ticket。密钥不可用时返回 empty（通知仍可发送，仅无免登 query）。
     */
    public Optional<String> mint(String dingUserId, long goodsId) {
        if (dingUserId == null || dingUserId.isBlank() || goodsId <= 0) {
            return Optional.empty();
        }
        String secret = resolveSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("打样 ticket 未签发：缺少 DINGTALK_SAMPLER_TICKET_SECRET / DINGTALK_APP_SECRET"
                    + "（非 local 环境无内置默认） goodsId={}", goodsId);
            return Optional.empty();
        }
        try {
            Instant exp = clock.instant().plus(properties.resolveSamplerTicketTtlDays(), ChronoUnit.DAYS);
            String ticket = DingTalkSamplerTicket.mint(dingUserId.trim(), goodsId, exp, secret);
            log.info("打样 ticket 已签发: goodsId={}, userid={}***, ttlDays={}, prefix={}",
                    goodsId,
                    dingUserId.trim().substring(0, Math.min(4, dingUserId.trim().length())),
                    properties.resolveSamplerTicketTtlDays(),
                    DingTalkSamplerTicket.logSafePrefix(ticket));
            return Optional.of(ticket);
        } catch (RuntimeException e) {
            log.warn("打样 ticket 签发失败（通知仍发送）: goodsId={}, err={}", goodsId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 校验签名 / 过期 / goodsId 后签发与 JSAPI 免登相同的会话。
     */
    public LoginResponse redeem(String ticket, Long goodsId) {
        String prefix = DingTalkSamplerTicket.logSafePrefix(ticket);
        if (goodsId == null || goodsId <= 0) {
            log.warn("打样 ticket 核销失败: reason=missing-goodsId, prefix={}", prefix);
            throw DingTalkFreeLoginException.goodsRequired();
        }
        String secret = resolveSecret();
        if (secret == null || secret.isBlank()) {
            log.warn("打样 ticket 核销失败: goodsId={}, reason=no-secret, prefix={}", goodsId, prefix);
            throw DingTalkFreeLoginException.ticketInvalid();
        }
        DingTalkSamplerTicket.Payload payload;
        try {
            payload = DingTalkSamplerTicket.verify(ticket, secret, clock.instant());
        } catch (DingTalkSamplerTicket.InvalidTicketException e) {
            log.warn("打样 ticket 核销失败: goodsId={}, reason={}, prefix={}",
                    goodsId, e.getReason(), prefix);
            throw DingTalkFreeLoginException.ticketInvalid();
        } catch (IllegalArgumentException e) {
            log.warn("打样 ticket 核销失败: goodsId={}, reason=malformed, prefix={}", goodsId, prefix);
            throw DingTalkFreeLoginException.ticketInvalid();
        }
        if (payload.goodsId() != goodsId) {
            log.warn("打样 ticket 核销失败: goodsId={}, ticketGoodsId={}, reason=goods-mismatch, prefix={}",
                    goodsId, payload.goodsId(), prefix);
            throw DingTalkFreeLoginException.ticketGoodsMismatch();
        }
        LoginResponse response = freeLoginService.loginByUserid(payload.userid(), payload.goodsId());
        log.info("打样 ticket 核销成功: goodsId={}, userid={}***, prefix={}",
                goodsId,
                payload.userid().substring(0, Math.min(4, payload.userid().length())),
                prefix);
        return response;
    }

    /**
     * 专用密钥 → AppSecret → local 内置默认。生产二者皆空则无法签发。
     */
    String resolveSecret() {
        String dedicated = properties.getSamplerTicketSecret();
        if (dedicated != null && !dedicated.isBlank()) {
            return dedicated.trim();
        }
        String appSecret = properties.getAppSecret();
        if (appSecret != null && !appSecret.isBlank()) {
            return appSecret.trim();
        }
        if (environment != null && environment.acceptsProfiles(Profiles.of("local"))) {
            return LOCAL_DEFAULT_SECRET;
        }
        return "";
    }
}
