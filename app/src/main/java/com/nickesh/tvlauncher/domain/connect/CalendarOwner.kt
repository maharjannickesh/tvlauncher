package com.nickesh.tvlauncher.domain.connect

import java.net.URI
import java.net.URLDecoder

object CalendarOwner {
    private const val GOOGLE_HOST = "calendar.google.com"
    private const val ICAL_SEGMENT = "/ical/"

    /**
     * The calendar owner's address from a Google "secret address in iCal format" link
     * (`…/calendar/ical/<url-encoded email>/private-…/basic.ics`). Null for group/resource
     * calendars (not an address of a person) and for any non-Google link.
     */
    fun emailFromUrl(url: String): String? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (!uri.host.equals(GOOGLE_HOST, ignoreCase = true)) return null
        val path = uri.rawPath ?: return null
        val idx = path.indexOf(ICAL_SEGMENT)
        if (idx < 0) return null
        val raw = path.substring(idx + ICAL_SEGMENT.length).substringBefore('/')
        val decoded = runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull() ?: return null
        if (!decoded.contains('@') || decoded.endsWith("calendar.google.com", ignoreCase = true)) return null
        return decoded
    }
}
