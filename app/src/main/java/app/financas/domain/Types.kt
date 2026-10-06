package app.financas.domain

/** Direção do lançamento: entrada ou saída de dinheiro. */
enum class TxType { IN, OUT }

/** Periodicidade de uma conta recorrente. */
enum class Frequency { WEEKLY, MONTHLY, YEARLY }
