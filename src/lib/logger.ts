/**
 * 统一日志模块
 * 提供结构化日志、安全过滤、日志级别控制等功能
 */

// 日志级别
export enum LogLevel {
  DEBUG = 0,
  INFO = 1,
  WARN = 2,
  ERROR = 3,
  FATAL = 4,
}

// 日志条目
export interface LogEntry {
  timestamp: string;
  level: LogLevel;
  levelName: string;
  module: string;
  message: string;
  details?: Record<string, unknown>;
  requestId?: string;
  userId?: string;
  ip?: string;
  duration?: number;
  error?: {
    name: string;
    message: string;
    stack?: string;
  };
}

// 敏感字段列表
const SENSITIVE_FIELDS = new Set([
  'password',
  'passwd',
  'pwd',
  'secret',
  'token',
  'access_token',
  'refresh_token',
  'api_key',
  'apikey',
  'private_key',
  'authorization',
  'auth',
  'session_id',
  'sessionid',
  'cookie',
  'x-csrf-token',
  'csrf_token',
  'new_password',
  'confirm_password',
  'current_password',
  'old_password',
  'credit_card',
  'card_number',
  'cvv',
  'ssn',
  'social_security',
]);

/**
 * 检测字段是否为敏感字段
 */
function isSensitiveField(key: string): boolean {
  const lowerKey = key.toLowerCase();
  return SENSITIVE_FIELDS.has(lowerKey) ||
    lowerKey.includes('password') ||
    lowerKey.includes('secret') ||
    lowerKey.includes('token') ||
    lowerKey.includes('key') ||
    lowerKey.includes('credential');
}

/**
 * 深度过滤对象中的敏感信息
 */
export function filterSensitiveData<T extends Record<string, unknown>>(data: T): T {
  if (!data || typeof data !== 'object') {
    return data;
  }

  const result: Record<string, unknown> = {};
  
  for (const [key, value] of Object.entries(data)) {
    if (isSensitiveField(key)) {
      // 敏感字段用占位符替换
      result[key] = '[REDACTED]';
    } else if (value && typeof value === 'object') {
      if (Array.isArray(value)) {
        // 数组：检查每个元素
        result[key] = value.map(item => {
          if (item && typeof item === 'object' && !Array.isArray(item)) {
            return filterSensitiveData(item as Record<string, unknown>);
          }
          return item;
        });
      } else {
        // 对象：递归过滤
        result[key] = filterSensitiveData(value as Record<string, unknown>);
      }
    } else {
      result[key] = value;
    }
  }
  
  return result as T;
}

/**
 * 获取当前日志级别
 */
function getCurrentLogLevel(): LogLevel {
  const envLevel = process.env.LOG_LEVEL?.toUpperCase();
  switch (envLevel) {
    case 'DEBUG': return LogLevel.DEBUG;
    case 'INFO': return LogLevel.INFO;
    case 'WARN': return LogLevel.WARN;
    case 'ERROR': return LogLevel.ERROR;
    case 'FATAL': return LogLevel.FATAL;
    default: return process.env.NODE_ENV === 'production' ? LogLevel.INFO : LogLevel.DEBUG;
  }
}

/**
 * 日志格式化
 */
function formatLog(entry: LogEntry): string {
  const parts = [
    `[${entry.timestamp}]`,
    `[${entry.levelName}]`,
    `[${entry.module}]`,
    entry.message,
  ];
  
  if (entry.requestId) parts.push(`[req:${entry.requestId}]`);
  if (entry.userId) parts.push(`[user:${entry.userId}]`);
  if (entry.ip) parts.push(`[ip:${entry.ip}]`);
  if (entry.duration) parts.push(`[${entry.duration}ms]`);
  
  if (entry.details && Object.keys(entry.details).length > 0) {
    parts.push(JSON.stringify(filterSensitiveData(entry.details)));
  }
  
  if (entry.error) {
    parts.push(`Error: ${entry.error.name}: ${entry.error.message}`);
    if (entry.error.stack) {
      parts.push(entry.error.stack);
    }
  }
  
  return parts.join(' ');
}

/**
 * 创建日志条目
 */
function createLogEntry(
  level: LogLevel,
  module: string,
  message: string,
  details?: Record<string, unknown>,
  error?: Error
): LogEntry {
  return {
    timestamp: new Date().toISOString(),
    level,
    levelName: LogLevel[level],
    module,
    message,
    details,
    ...(error && {
      error: {
        name: error.name,
        message: error.message,
        stack: error.stack,
      },
    }),
  };
}

/**
 * 记录日志
 */
function log(level: LogLevel, module: string, message: string, details?: Record<string, unknown>, error?: Error): void {
  const currentLevel = getCurrentLogLevel();
  if (level < currentLevel) return;
  
  const entry = createLogEntry(level, module, message, details, error);
  const formatted = formatLog(entry);
  
  switch (level) {
    case LogLevel.ERROR:
    case LogLevel.FATAL:
      console.error(formatted);
      break;
    case LogLevel.WARN:
      console.warn(formatted);
      break;
    default:
      console.log(formatted);
  }
  
  // 生产环境写入文件日志（如果配置了）
  if (process.env.NODE_ENV === 'production' && typeof window === 'undefined') {
    writeToFile(entry);
  }
}

// 日志写入队列（用于异步文件写入）
const logQueue: LogEntry[] = [];
const LOG_FILE_PATH = '/app/work/logs/bypass/app.log';

/**
 * 异步写入日志文件
 */
async function writeToFile(entry: LogEntry): Promise<void> {
  logQueue.push(entry);
  
  // 批量写入，每10条或每秒一次
  if (logQueue.length >= 10 || entry.level >= LogLevel.ERROR) {
    const logs = logQueue.splice(0, logQueue.length);
    const content = logs.map(e => JSON.stringify(filterSensitiveData(e as unknown as Record<string, unknown>))).join('\n') + '\n';
    
    try {
      // 使用动态import避免SSR问题
      const { appendFileSync } = await import('fs');
      appendFileSync(LOG_FILE_PATH, content);
    } catch {
      // 忽略文件写入错误
    }
  }
}

/**
 * Debug 级别日志
 */
export function debug(module: string, message: string, details?: Record<string, unknown>): void {
  log(LogLevel.DEBUG, module, message, details);
}

/**
 * Info 级别日志
 */
export function info(module: string, message: string, details?: Record<string, unknown>): void {
  log(LogLevel.INFO, module, message, details);
}

/**
 * Warn 级别日志
 */
export function warn(module: string, message: string, details?: Record<string, unknown>): void {
  log(LogLevel.WARN, module, message, details);
}

/**
 * Error 级别日志
 */
export function error(module: string, message: string, error?: Error, details?: Record<string, unknown>): void {
  log(LogLevel.ERROR, module, message, details, error);
}

/**
 * Fatal 级别日志
 */
export function fatal(module: string, message: string, error?: Error, details?: Record<string, unknown>): void {
  log(LogLevel.FATAL, module, message, details, error);
}

/**
 * 创建带上下文的日志器
 */
export function createLogger(module: string) {
  return {
    debug: (message: string, details?: Record<string, unknown>) => debug(module, message, details),
    info: (message: string, details?: Record<string, unknown>) => info(module, message, details),
    warn: (message: string, details?: Record<string, unknown>) => warn(module, message, details),
    error: (message: string, err?: Error, details?: Record<string, unknown>) => logError(module, message, err, details),
    fatal: (message: string, err?: Error, details?: Record<string, unknown>) => logFatal(module, message, err, details),
  };
}

/**
 * 错误级别日志
 */
function logError(module: string, message: string, err?: Error, details?: Record<string, unknown>): void {
  log(LogLevel.ERROR, module, message, details, err);
}

/**
 * Fatal 级别日志
 */
function logFatal(module: string, message: string, err?: Error, details?: Record<string, unknown>): void {
  log(LogLevel.FATAL, module, message, details, err);
}

/**
 * API请求日志包装器
 */
export async function withRequestLog<T>(
  module: string,
  requestInfo: {
    method: string;
    path: string;
    userId?: string;
    ip?: string;
  },
  handler: () => Promise<T>
): Promise<T> {
  const startTime = Date.now();
  const requestId = Math.random().toString(36).substring(2, 15);
  
  debug(module, `${requestInfo.method} ${requestInfo.path} started`, {
    requestId,
    ...requestInfo,
  });
  
  try {
    const result = await handler();
    const duration = Date.now() - startTime;
    
    info(module, `${requestInfo.method} ${requestInfo.path} completed`, {
      requestId,
      duration,
      ...requestInfo,
    });
    
    return result;
  } catch (err) {
    const duration = Date.now() - startTime;
    const errorObj = err instanceof Error ? err : new Error(String(err));
    
    logError(module, `${requestInfo.method} ${requestInfo.path} failed`, errorObj, {
      requestId,
      duration,
      ...requestInfo,
    });
    
    throw err;
  }
}

export default {
  debug,
  info,
  warn,
  error,
  fatal,
  createLogger,
  withRequestLog,
  filterSensitiveData,
};
