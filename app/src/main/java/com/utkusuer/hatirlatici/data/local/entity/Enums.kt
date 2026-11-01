package com.utkusuer.hatirlatici.data.local.entity

/** Kaydın türü; yalnızca görsel gruplama ve varsayılan davranışlar için. */
enum class PaymentCategory { CREDIT_CARD, BILL, LOAN, INSTALLMENT, SUBSCRIPTION, RENT, OTHER }

/** Tek gün (fatura) mi, tarih aralığı (kredi kartı ekstresi) mı? */
enum class ScheduleType { SINGLE_DAY, DATE_RANGE }

/** Tekrarlama: her ay mı, tek seferlik mi? */
enum class Recurrence { MONTHLY, ONE_TIME }

/** Bir dönemin (ayın) ödeme durumu. */
enum class CycleStatus { PENDING, PAID, SKIPPED }
