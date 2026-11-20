package com.example

import org.junit.Assert.*
import org.junit.Test

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
  @Test
  fun addition_isCorrect() {
    assertEquals(4, 2 + 2)
  }

  @Test
  fun testCurrencyHelperFormatting() {
    val idrFormatted = com.example.util.CurrencyHelper.format(1500000.0, "IDR")
    assertTrue(idrFormatted.contains("Rp"))
    assertTrue(idrFormatted.contains("1.500.000"))

    val usdFormatted = com.example.util.CurrencyHelper.format(100.0, "USD")
    assertTrue(usdFormatted.contains("$"))
  }
}
