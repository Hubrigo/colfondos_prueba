package com.colfondos.contactupdate.client;

import java.util.Map;

/**
 * Puerto hacia Messaging API (doc. 06). No hay SLA ni política de reintentos
 * documentada (H-04): el orquestador aplica los suyos y nunca bloquea el resultado.
 */
public interface MessagingClient {

    enum Template { EMAIL_UPDATED_NEW, EMAIL_UPDATED_SECURITY_ALERT }

    record EmailNotification(String customerId, String to, Template template, Map<String, String> params) {
    }

    record MessageReceipt(String messageId) {
    }

    MessageReceipt sendEmail(EmailNotification notification);
}
