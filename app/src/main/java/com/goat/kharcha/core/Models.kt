package com.goat.kharcha.core

/** Direction of money. OUT is spending, IN is money received. */
enum class Flow { OUT, IN }

enum class Mood(val label: String) { NONE(""), WORTH("Worth it"), MEH("Meh"), REGRET("Regret") }

/** All amounts are stored in paisa (1 rupee = 100 paisa) so sums never drift. */
data class Tx(
    val id: Long = 0,
    val amount: Long,
    val flow: Flow = Flow.OUT,
    val merchant: String?,
    val categoryId: String? = null,
    val time: Long,
    val source: String = "",
    val packages: String = "",
    val raw: String = "",
    val mood: Mood = Mood.NONE,
    val note: String = "",
    val owed: Boolean = false,
    val owedBy: String = "",
    val manual: Boolean = false,
    val sample: Boolean = false,
) {
    val needsTag: Boolean get() = categoryId == null
    val payee: String get() = merchant?.takeIf { it.isNotBlank() } ?: "Unknown payee"
    val packageList: List<String> get() = packages.split(',').map { it.trim() }.filter { it.isNotEmpty() }
}

data class Category(
    val id: String,
    val name: String,
    val color: Long,
    val icon: String,
    val budget: Long,
    val order: Int,
    val income: Boolean,
)

enum class AskMode { NEW_PLACES, EVERY }
enum class Scope { SMART, FINANCE_ONLY }
enum class ThemeMode { SYSTEM, DARK, LIGHT }

data class Settings(
    val name: String = "",
    val budget: Long = 0,
    val cycleDay: Int = 1,
    val hourlyRate: Long = 0,
    val askMode: AskMode = AskMode.NEW_PLACES,
    val confirmAuto: Boolean = true,
    val detectIncome: Boolean = true,
    val scope: Scope = Scope.SMART,
    val lock: Boolean = false,
    val recap: Boolean = true,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val onboarded: Boolean = false,
)

/** Lowercase letters and digits only, used to match the same place across spellings. */
fun merchantKey(s: String?): String = s?.lowercase()?.filter { it in 'a'..'z' || it in '0'..'9' } ?: ""

object Defaults {
    val categories: List<Category> = listOf(
        Category("food", "Food", 0xFFF08A5D, "food", 16_000_00, 0, false),
        Category("groceries", "Groceries", 0xFF6FC07A, "cart", 18_000_00, 1, false),
        Category("transport", "Fuel & Rides", 0xFF5AA8F2, "car", 12_000_00, 2, false),
        Category("bills", "Bills", 0xFFA68CF2, "receipt", 14_000_00, 3, false),
        Category("shopping", "Shopping", 0xFFEE6E9F, "bag", 8_000_00, 4, false),
        Category("health", "Health", 0xFF3FC4BA, "health", 4_000_00, 5, false),
        Category("fun", "Fun & Subs", 0xFFE9B949, "ticket", 6_000_00, 6, false),
        Category("family", "Family", 0xFFE0957A, "home", 7_000_00, 7, false),
        Category("work", "Work tools", 0xFF8EA2C0, "box", 8_000_00, 8, false),
        Category("other", "Other", 0xFF9BA1AA, "dots", 0, 9, false),
        Category("client", "Client work", 0xFF6CD4A2, "briefcase", 0, 20, true),
        Category("salary", "Salary", 0xFF7FB7FF, "banknote", 0, 21, true),
        Category("transfer_in", "Transfers in", 0xFFB59CFF, "arrows", 0, 22, true),
        Category("refund", "Refunds", 0xFFF2BA4E, "undo", 0, 23, true),
    )

    /** Colors offered when making or editing a category. */
    val palette: List<Long> = listOf(
        0xFFF08A5D, 0xFFF2BA4E, 0xFFE9B949, 0xFF6FC07A, 0xFF3FC4BA, 0xFF5AA8F2,
        0xFF7FB7FF, 0xFFA68CF2, 0xFFB59CFF, 0xFFEE6E9F, 0xFFE0957A, 0xFF8EA2C0, 0xFF9BA1AA,
    )
}
