package io.github.AlexIllinois2.snub.utils

import io.github.AlexIllinois2.snub.app.HailData

/** 使用莱文斯坦距离 (Levenshtein distance) 实现模糊搜索 */
object FuzzySearch {

    /**
     * 两个字符串差异小于原始字符串长度 且 原始字符串依次包含输入字符串的每个字符 则显示在搜索结果中
     * @param raw 需要匹配的原始字符串
     * @param query 输入的字符串
     */
    fun search(raw: String?, query: String?): Boolean {
        if (query.isNullOrEmpty()) return true
        if (raw.isNullOrEmpty()) return false
        if (raw.contains(query, true)) return true
        if (!HailData.fuzzySearch) return false
        val rawUpp = raw.uppercase()
        val queryUpp = query.uppercase()
        val diff = distance(rawUpp, queryUpp)
        return diff < rawUpp.length && containsInOrder(rawUpp, queryUpp)
    }

    /** 按码点计算莱文斯坦距离；commons-text 的实现实现 java.util.function.BiFunction，API 23 上加载不了 */
    private fun distance(first: String, second: String): Int {
        val a = first.toCodePoints()
        val b = second.toCodePoints()
        if (a.isEmpty()) return b.size
        var previous = IntArray(b.size + 1) { it }
        var current = IntArray(b.size + 1)
        for (row in a.indices) {
            current[0] = row + 1
            for (column in b.indices) {
                val cost = if (a[row] == b[column]) 0 else 1
                current[column + 1] = minOf(previous[column + 1] + 1, current[column] + 1, previous[column] + cost)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.size]
    }

    private fun String.toCodePoints(): IntArray {
        val points = IntArray(length)
        var size = 0
        var index = 0
        while (index < length) {
            val point = Character.codePointAt(this, index)
            points[size++] = point
            index += Character.charCount(point)
        }
        return points.copyOf(size)
    }

    /** 判断字符串A是否依次包含字符串B的每个字符 */
    private fun containsInOrder(strA: String, strB: String): Boolean {
        var indexA = 0  // 用于跟踪字符串A中的位置
        for (charB in strB) {
            // 在字符串A的当前位置之后查找字符charB
            val foundIndex = strA.indexOf(charB, indexA)
            // 如果未找到字符或者字符的位置不是当前位置，表示不包含按顺序的字符
            if (foundIndex == -1) return false
            // 移动到下一个位置，以便查找下一个字符
            indexA = foundIndex + 1
        }
        return true
    }
}