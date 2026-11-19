package com.msg.ppap.approval.application;

/**
 * Requested element as entered.
 *
 * @param requirement {@code REQUIRED} or {@code ON_REQUEST}; default: requirement at the level, else REQUIRED
 */
public record ElementInput(String code, String requirement, String note) {}
