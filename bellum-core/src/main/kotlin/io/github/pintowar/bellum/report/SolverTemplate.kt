package io.github.pintowar.bellum.report

object SolverTemplate {
    fun generateHtml(jsonData: String): String {
        val template = SolverTemplate::class.java.getResource("/report/solver.tpl.html")?.readText() ?: ""
        return template.replace("[[jsonData]]", jsonData)
    }
}
