package com.imagemanager.exception;

import com.imagemanager.dto.DingTalkContactCandidate;

import java.util.List;

/**
 * 注册时钉钉通讯录匹配结果：0 条 → 404；多条 → 409 + 候选人。
 */
public class RegisterMatchException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public enum Kind {
        NONE,
        NOT_SYNCED,
        MULTIPLE,
        ALREADY_REGISTERED,
        OTHER
    }

    private final int httpStatus;
    private final List<DingTalkContactCandidate> candidates;
    private final Kind kind;

    public RegisterMatchException(int httpStatus, String message) {
        this(httpStatus, message, List.of(), Kind.OTHER);
    }

    public RegisterMatchException(int httpStatus, String message, List<DingTalkContactCandidate> candidates) {
        this(httpStatus, message, candidates, Kind.OTHER);
    }

    public RegisterMatchException(int httpStatus, String message,
                                  List<DingTalkContactCandidate> candidates, Kind kind) {
        super(message);
        this.httpStatus = httpStatus;
        this.candidates = candidates == null ? List.of() : List.copyOf(candidates);
        this.kind = kind == null ? Kind.OTHER : kind;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public List<DingTalkContactCandidate> getCandidates() {
        return candidates;
    }

    public Kind getKind() {
        return kind;
    }

    public static RegisterMatchException none() {
        return new RegisterMatchException(404,
                "未在钉钉通讯录中找到该姓名，请确认管理员已同步组织或联系管理员",
                List.of(), Kind.NONE);
    }

    public static RegisterMatchException notSynced() {
        return new RegisterMatchException(404,
                "尚未同步钉钉通讯录，请联系管理员先执行「同步钉钉组织」",
                List.of(), Kind.NOT_SYNCED);
    }

    public static RegisterMatchException multiple(List<DingTalkContactCandidate> candidates) {
        return new RegisterMatchException(409, "找到多名同名员工，请选择您的身份",
                candidates, Kind.MULTIPLE);
    }

    public static RegisterMatchException alreadyRegistered() {
        return new RegisterMatchException(409, "该钉钉通讯录成员已注册账号，请直接登录",
                List.of(), Kind.ALREADY_REGISTERED);
    }
}
