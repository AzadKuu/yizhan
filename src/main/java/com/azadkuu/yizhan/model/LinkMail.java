package com.azadkuu.yizhan.model;

import java.util.UUID;

/** 一封「链接邮件」：只能承载一条白名单链接，按子服隔离。 */
public record LinkMail(long id, UUID sender, String senderName, String url, long createdAt, boolean read) {
}
