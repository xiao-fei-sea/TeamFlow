package com.teamflow.common.result;

/**
 * 字段级校验错误项，序列化形如 {@code {"field": "username", "message": "..."}}。
 * 见接口设计文档 4. 错误响应示例。
 */
public record FieldErrorItem(String field, String message) {
}
