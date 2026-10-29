package com.uplink.app

import java.math.BigDecimal
import java.math.MathContext

object CalcEngine {
    fun eval(expr: String): Double {
        val parser = Parser(expr.replace(" ", ""))
        val value = parser.expression()
        if (!parser.done()) throw IllegalArgumentException("Unexpected input")
        if (value.isNaN() || value.isInfinite()) throw ArithmeticException("Invalid result")
        return value
    }

    fun format(value: Double): String {
        if (value == Math.floor(value) && Math.abs(value) < 1e15) return value.toLong().toString()
        return BigDecimal(value).round(MathContext(12)).stripTrailingZeros().toPlainString()
    }

    private class Parser(private val s: String) {
        private var pos = 0

        fun done(): Boolean = pos >= s.length

        fun expression(): Double {
            var value = term()
            while (pos < s.length && (s[pos] == '+' || s[pos] == '-')) {
                val op = s[pos++]
                val right = term()
                value = if (op == '+') value + right else value - right
            }
            return value
        }

        private fun term(): Double {
            var value = factor()
            while (pos < s.length && (s[pos] == '*' || s[pos] == '/')) {
                val op = s[pos++]
                val right = factor()
                value = if (op == '*') value * right else value / right
            }
            return value
        }

        private fun factor(): Double {
            if (pos < s.length && s[pos] == '-') {
                pos++
                return -factor()
            }
            if (pos < s.length && s[pos] == '(') {
                pos++
                val inner = expression()
                expect(')')
                return inner
            }
            val start = pos
            while (pos < s.length && (s[pos].isDigit() || s[pos] == '.')) pos++
            if (start == pos) throw IllegalArgumentException("Number expected")
            return s.substring(start, pos).toDouble()
        }

        private fun expect(c: Char) {
            if (pos < s.length && s[pos] == c) pos++ else throw IllegalArgumentException("'$c' expected")
        }
    }
}
