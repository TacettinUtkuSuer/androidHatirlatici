package com.utkusuer.hatirlatici.data.local.entity

/** Ödeme (gider) mi, beklenen para girişi (gelir) mi? */
enum class EntryKind { EXPENSE, INCOME }

/** Kaydın türü; görsel gruplama ve varsayılan davranışlar için. */
enum class PaymentCategory(val kind: EntryKind) {
    CREDIT_CARD(EntryKind.EXPENSE),
    BILL(EntryKind.EXPENSE),
    LOAN(EntryKind.EXPENSE),
    INSTALLMENT(EntryKind.EXPENSE),
    SUBSCRIPTION(EntryKind.EXPENSE),
    RENT(EntryKind.EXPENSE),
    OTHER(EntryKind.EXPENSE),
    SALARY(EntryKind.INCOME),
    RENT_INCOME(EntryKind.INCOME),
    OTHER_INCOME(EntryKind.INCOME),
}

/** Tek gün (fatura) mi, tarih aralığı (kredi kartı ekstresi) mı? */
enum class ScheduleType { SINGLE_DAY, DATE_RANGE }

/** Tekrarlama: her ay mı, tek seferlik mi? */
enum class Recurrence { MONTHLY, ONE_TIME }

/** Son gün hafta sonuna denk gelirse ne yapılacağı. */
enum class WeekendRule { NONE, BEFORE, AFTER, NEAREST }

/** Bir dönemin durumu. Gelirlerde PAID = "Para geldi". */
enum class CycleStatus { PENDING, PAID, SKIPPED }

/** Log kayıt türleri. */
enum class LogType(val isPaymentEvent: Boolean) {
    PAID(true),
    RECEIVED(true),
    UNPAID(true),
    CREATED(false),
    UPDATED(false),
    DELETED(false),
    ACTIVATED(false),
    DEACTIVATED(false),
}
