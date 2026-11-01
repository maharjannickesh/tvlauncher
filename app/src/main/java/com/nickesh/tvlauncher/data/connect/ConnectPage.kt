package com.nickesh.tvlauncher.data.connect

/** The phone page: one self-contained HTML string. Every dynamic string is escaped; the token is never echoed. */
object ConnectPage {
    fun html(results: List<ConnectOutcome>, calendars: List<String>, todoistMasked: String?): String {
        val lines = results.mapNotNull { r ->
            when (r) {
                is ConnectOutcome.CalendarAdded -> "<p class=ok>${esc("Calendar connected · ${r.name} · ${r.events} events")}</p>"
                is ConnectOutcome.TodoistConnected -> "<p class=ok>${esc("Todoist connected · ${r.openTasks} open tasks")}</p>"
                is ConnectOutcome.Failed -> "<p class=bad>${esc(r.message)}</p>"
                ConnectOutcome.Nothing -> null
            }
        }.joinToString("")
        val connected = buildString {
            calendars.forEach { append("<li>${esc(it)}</li>") }
            todoistMasked?.let { append("<li>Todoist ${esc(it)}</li>") }
        }.let { if (it.isEmpty()) "" else "<h2>Connected</h2><ul>$it</ul>" }
        return """<!doctype html><html lang=en><head><meta charset=utf-8>
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>TV Launcher · Connect</title>
<style>body{font:16px system-ui,sans-serif;margin:0;padding:20px;background:#0f1115;color:#eee;max-width:560px}
label{display:block;margin-top:18px;font-weight:600}.hint{font-size:14px;color:#9aa;margin:4px 0}
input{width:100%;box-sizing:border-box;padding:12px;font-size:16px;border-radius:8px;border:1px solid #445;background:#1b2238;color:#eee}
button{margin-top:22px;padding:14px 28px;font-size:17px;border:0;border-radius:8px;background:#8fb8ff;color:#0f1115;font-weight:700}
.ok{color:#5fd68a}.bad{color:#ff6b6b}ul{padding-left:20px;color:#9aa}</style></head><body>
<h1>TV Launcher</h1>$lines
<form method=post action="">
<label for=calendar>Google Calendar link</label>
<p class=hint>Google Calendar → Settings → your calendar → Secret address in iCal format. Paste the link.</p>
<input id=calendar name=calendar type=url autocomplete=off autocapitalize=off>
<label for=token>Todoist token</label>
<p class=hint>Todoist → Settings → Integrations → Developer → API token. Paste it.</p>
<input id=token name=token type=text autocomplete=off autocapitalize=off>
<button type=submit>Save</button></form>$connected</body></html>"""
    }

    private fun esc(s: String) = buildString {
        for (c in s) when (c) {
            '&' -> append("&amp;"); '<' -> append("&lt;"); '>' -> append("&gt;")
            '"' -> append("&quot;"); '\'' -> append("&#39;"); else -> append(c)
        }
    }
}
