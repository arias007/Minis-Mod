package com.openminis.app.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * [local #362] The option parser runs on EVERY finished reply, so a false
 * positive puts a phantom menu under prose. Each test below is one way that
 * happened, or would have.
 */
class ReplyOptionsTest {

    private fun parse(s: String) = ReplyOptions.parse(s)?.options

    @Test
    fun `numbered options at the tail are parsed`() {
        assertEquals(
            listOf("重装系统", "恢复出厂设置", "先查日志"),
            parse("三个方向：\n1. 重装系统\n2. 恢复出厂设置\n3. 先查日志"),
        )
    }

    @Test
    fun `trailing blank lines are ignored`() {
        assertEquals(listOf("A", "B"), parse("选一个：\n1. A\n2. B\n\n"))
    }

    @Test
    fun `bulleted options are parsed`() {
        assertEquals(listOf("先跑测试", "直接发布"), parse("下一步：\n- 先跑测试\n- 直接发布"))
    }

    @Test
    fun `lettered options are parsed`() {
        assertEquals(listOf("方案甲", "方案乙"), parse("**A)** 方案甲\n**B)** 方案乙"))
    }

    /**
     * The shape that actually broke the first version: a lead-in line, a BLANK
     * line, then the list. The original parser stopped at the first blank and
     * required the run to be the last thing in the reply, so it matched
     * almost nothing.
     */
    @Test
    fun `blank line between lead-in and list still parses`() {
        assertEquals(
            listOf("方案一", "方案二"),
            parse("有两种做法可以解决：\n\n1. 方案一\n2. 方案二"),
        )
    }

    @Test
    fun `a long prose preamble does not defeat the parse`() {
        val prose = (1..12).joinToString("\n") { "这是第 $it 段背景说明，讲的是问题的来龙去脉与历史沿革。" }
        assertEquals(
            listOf("甲", "乙"),
            parse("$prose\n所以：\n1. 甲\n2. 乙"),
        )
    }

    @Test
    fun `a single option is not a choice`() {
        assertNull(parse("建议：\n1. 只有一个"))
    }

    @Test
    fun `five options is a document not a decision`() {
        assertNull(parse("1. a\n2. b\n3. c\n4. d\n5. e"))
    }

    @Test
    fun `a long option is a section heading`() {
        val long = "先确认所有依赖的版本是否兼容，然后再决定要不要升级到下一个大版本，因为升级会引入不兼容的改动"
        assertNull(parse("1. $long\n2. 另一个"))
    }

    @Test
    fun `a numbered list mid-answer does not become a menu`() {
        assertNull(parse("步骤如下：\n1. 打开设置\n2. 进入关于\n\n然后重启即可完成。"))
    }

    @Test
    fun `a fenced code block disqualifies the reply`() {
        assertNull(parse("示例：\n```sh\n1. foo\n2. bar\n```"))
    }

    @Test
    fun `a table disqualifies the reply`() {
        assertNull(parse("| 方案 | 耗时 |\n|---|---|\n| 1. A | 2s |"))
    }

    @Test
    fun `duplicate options are rejected`() {
        assertNull(parse("1. 同样的\n2. 同样的"))
    }

    @Test
    fun `plain prose yields nothing`() {
        assertNull(parse("我已经完成了全部修改，并且本地验证通过。"))
    }

    @Test
    fun `blank input yields nothing`() {
        assertNull(parse(""))
        assertNull(parse("   \n  "))
    }
}